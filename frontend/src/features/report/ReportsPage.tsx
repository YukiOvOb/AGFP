import { useState, type ReactNode } from 'react';
import { useSearchParams } from 'react-router';
import { createColumnHelper } from '@tanstack/react-table';
import { AlertTriangle, ChartColumn, ClockAlert, Wrench } from 'lucide-react';
import { message } from '@/api/client';
import { EmptyState, ErrorState, LoadingState } from '@/components/Feedback';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { DataTable } from '@/components/ui/data-table';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { PageHeader } from '@/components/ui/page-header';
import { maintenanceStatus } from '@/lib/status';
import { formatDateTime } from '@/lib/time';
import { cn } from '@/lib/utils';
import { BarChartCard } from './BarChartCard';
import {
  customPeriod,
  DEFAULT_DAYS,
  formatCount,
  formatHours,
  formatPercent,
  lastDays,
  presetOf,
  presets,
  sgtDate,
  type Period,
} from './period';
import { useOperationsReport, type Report } from './queries';
type Overdue = Report['overdueNow'][number];
type Utilisation = Report['equipmentUtilisation'][number];
type LateReturns = Report['overdueByCategory'][number];
type Repairs = Report['maintenanceByEquipment'][number];
const overdueColumns = (() => {
  const h = createColumnHelper<Overdue>();
  return [
    h.accessor('equipmentName', {
      header: 'Equipment',
      cell: (c) => (
        <>
          <strong>{c.getValue()}</strong>
          <span className="cell-sub">{c.row.original.assetTag}</span>
        </>
      ),
    }),
    h.accessor('category', { header: 'Category' }),
    h.accessor('borrowerName', { header: 'Borrower' }),
    h.accessor('dueAt', { header: 'Due (SGT)', cell: (c) => formatDateTime(c.getValue()) }),
    h.accessor('overdueHours', {
      header: 'Overdue for',
      cell: (c) => <span className="tabular-nums">{formatHours(c.getValue())}</span>,
    }),
  ];
})();
const utilisationColumns = (() => {
  const h = createColumnHelper<Utilisation>();
  return [
    h.accessor('name', {
      header: 'Equipment',
      cell: (c) => (
        <>
          <strong>{c.getValue()}</strong>
          <span className="cell-sub">{c.row.original.assetTag}</span>
        </>
      ),
    }),
    h.accessor('category', { header: 'Category' }),
    h.accessor('loans', {
      header: 'Loans',
      cell: (c) => <span className="tabular-nums">{c.getValue()}</span>,
    }),
    h.accessor('loanedHours', {
      header: 'Time on loan',
      cell: (c) => <span className="tabular-nums">{formatHours(c.getValue())}</span>,
    }),
    h.accessor('utilisation', {
      header: 'Utilisation',
      cell: (c) => <span className="tabular-nums">{formatPercent(c.getValue())}</span>,
    }),
  ];
})();
const lateColumns = (() => {
  const h = createColumnHelper<LateReturns>();
  return [
    h.accessor('category', { header: 'Category' }),
    h.accessor('loansDue', {
      header: 'Loans due',
      cell: (c) => <span className="tabular-nums">{c.getValue()}</span>,
    }),
    h.accessor('returnedLate', {
      header: 'Returned late',
      cell: (c) => <span className="tabular-nums">{c.getValue()}</span>,
    }),
    h.accessor('stillOverdue', {
      header: 'Still out',
      cell: (c) => <span className="tabular-nums">{c.getValue()}</span>,
    }),
    h.accessor((row) => (row.loansDue ? (row.returnedLate + row.stillOverdue) / row.loansDue : 0), {
      id: 'rate',
      header: 'Late rate',
      cell: (c) => <span className="tabular-nums">{formatPercent(c.getValue())}</span>,
    }),
  ];
})();
const repairColumns = (() => {
  const h = createColumnHelper<Repairs>();
  return [
    h.accessor('name', {
      header: 'Equipment',
      cell: (c) => (
        <>
          <strong>{c.getValue()}</strong>
          <span className="cell-sub">{c.row.original.assetTag}</span>
        </>
      ),
    }),
    h.accessor('category', { header: 'Category' }),
    h.accessor('cases', {
      header: 'Cases reported',
      cell: (c) => <span className="tabular-nums">{c.getValue()}</span>,
    }),
  ];
})();
function usePeriod(): [Period, (period: Period) => void] {
  const [params, setParams] = useSearchParams();
  const from = params.get('from');
  const to = params.get('to');
  const valid =
    from && to && !Number.isNaN(Date.parse(from)) && Date.parse(from) < Date.parse(to ?? '');
  const period = valid ? { from, to } : lastDays(DEFAULT_DAYS);
  return [period, (next) => setParams({ from: next.from, to: next.to }, { replace: true })];
}
function Tile({ label, value, note }: { label: string; value: ReactNode; note?: ReactNode }) {
  return (
    <Card className="flex flex-col gap-1 p-5">
      <p className="m-0 text-xs text-muted-foreground">{label}</p>
      <p className="m-0 text-2xl font-semibold">{value}</p>
      {note && <div className="text-xs text-muted-foreground">{note}</div>}
    </Card>
  );
}
function Section({
  title,
  description,
  children,
}: {
  title: string;
  description: string;
  children: ReactNode;
}) {
  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle>{title}</CardTitle>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
      {children}
    </Card>
  );
}
/** Administrator reports (Proposal 5.7) for a chosen period. */
export default function ReportsPage() {
  const [period, setPeriod] = usePeriod();
  const [first, setFirst] = useState(sgtDate(period.from));
  const [last, setLast] = useState(sgtDate(new Date(Date.parse(period.to) - 1).toISOString()));
  const [rangeError, setRangeError] = useState('');
  const report = useOperationsReport(period.from, period.to);
  const selected = presetOf(period);
  function choose(next: Period) {
    setRangeError('');
    setFirst(sgtDate(next.from));
    setLast(sgtDate(new Date(Date.parse(next.to) - 1).toISOString()));
    setPeriod(next);
  }
  function applyCustom() {
    const next = customPeriod(first, last);
    if (!next) {
      setRangeError('Choose a first day on or before the last day, at most 366 days apart.');
      return;
    }
    choose(next);
  }
  const data = report.data;
  const refreshing = report.isFetching && report.isPlaceholderData;
  return (
    <>
      <PageHeader
        eyebrow="ADMINISTRATION"
        title="Reports"
        description="Reservations, equipment use, overdue loans and maintenance for the period you choose. Times are Singapore time."
      />
      <div
        role="group"
        aria-label="Report period"
        className="mb-6 flex flex-wrap items-end gap-3 rounded-lg border border-border bg-card p-4"
      >
        <div className="flex flex-wrap gap-2">
          {presets.map((p) => (
            <Button
              key={p.days}
              size="sm"
              variant={selected === p.days ? 'default' : 'outline'}
              aria-pressed={selected === p.days}
              onClick={() => choose(lastDays(p.days))}
            >
              {p.label}
            </Button>
          ))}
        </div>
        <div className="ml-auto flex flex-wrap items-end gap-3">
          <Field label="First day" className="w-40">
            <Input type="date" value={first} onChange={(e) => setFirst(e.target.value)} />
          </Field>
          <Field label="Last day" className="w-40">
            <Input type="date" value={last} onChange={(e) => setLast(e.target.value)} />
          </Field>
          <Button variant="outline" onClick={applyCustom}>
            Show period
          </Button>
        </div>
        {rangeError && (
          <p className="m-0 w-full text-xs text-destructive" role="alert">
            {rangeError}
          </p>
        )}
      </div>
      {report.isPending ? (
        <LoadingState />
      ) : report.isError ? (
        <ErrorState message={message(report.error)} onRetry={() => void report.refetch()} />
      ) : (
        data && (
          <div
            className={cn('grid gap-6 transition-opacity', refreshing && 'opacity-60')}
            aria-busy={refreshing || undefined}
          >
            <p className="m-0 text-xs text-muted-foreground">
              {formatDateTime(data.from)} – {formatDateTime(data.to)} SGT. Use and late returns are
              measured until {formatDateTime(data.measuredUntil)}.
            </p>
            <Summary report={data} />
            <div className="grid gap-6 xl:grid-cols-2">
              <BarChartCard
                title="Reservations by category"
                description="Reservations whose time slot starts in the period, in any status."
                rows={data.reservationsByCategory.map((c) => ({
                  label: c.category,
                  value: c.total,
                }))}
                format={formatCount}
                categoryLabel="Category"
                valueLabel="Reservations"
                empty={
                  <EmptyState
                    className="min-h-0 py-8"
                    icon={<ChartColumn size={26} />}
                    title="No reservations"
                    description="Nothing was booked to start in this period."
                  />
                }
              />
              <BarChartCard
                title="Utilisation by category"
                description="Time on loan as a share of the time the category's equipment existed in the period."
                rows={data.utilisationByCategory.map((c) => ({
                  label: c.category,
                  value: c.utilisation,
                }))}
                format={formatPercent}
                categoryLabel="Category"
                valueLabel="Utilisation"
                max={1}
                empty={
                  <EmptyState
                    className="min-h-0 py-8"
                    icon={<ChartColumn size={26} />}
                    title="No equipment"
                    description="There is no equipment to measure yet."
                  />
                }
              />
            </div>
            <Section
              title="Overdue now"
              description="Loans past their return time at the moment this report was made, longest first."
            >
              <DataTable
                columns={overdueColumns}
                data={[...data.overdueNow].sort((a, b) => b.overdueHours - a.overdueHours)}
                caption="Loans that are overdue now"
                empty={
                  <EmptyState
                    className="min-h-0 py-8"
                    icon={<ClockAlert size={26} />}
                    title="Nothing is overdue"
                    description="Every loan is within its return time."
                  />
                }
              />
            </Section>
            <Section
              title="Late returns by category"
              description="Loans that fell due in the period: returned after their due time, or still out."
            >
              <DataTable
                columns={lateColumns}
                data={data.overdueByCategory}
                caption="Late returns by category"
                empty={
                  <EmptyState
                    className="min-h-0 py-8"
                    icon={<ClockAlert size={26} />}
                    title="No loans fell due"
                    description="No loan reached its return time in this period."
                  />
                }
              />
            </Section>
            <Section
              title="Equipment utilisation"
              description="Each item's time on loan in the period. Sort by any column."
            >
              <DataTable
                columns={utilisationColumns}
                data={data.equipmentUtilisation}
                caption="Equipment utilisation"
                initialSorting={[{ id: 'utilisation', desc: true }]}
                empty={
                  <EmptyState
                    className="min-h-0 py-8"
                    icon={<ChartColumn size={26} />}
                    title="No equipment"
                    description="There is no equipment to measure yet."
                  />
                }
              />
            </Section>
            <div className="grid gap-6 xl:grid-cols-2">
              <BarChartCard
                title="Maintenance cases by status"
                description="All cases now, whenever they were reported."
                rows={Object.entries(maintenanceStatus).map(([code, status]) => ({
                  label: status.label,
                  value: data.maintenance.casesByStatus[code] ?? 0,
                }))}
                format={formatCount}
                categoryLabel="Status"
                valueLabel="Cases"
                empty={null}
              />
              <Section
                title="Most maintenance"
                description="Equipment with the most cases reported in the period."
              >
                <DataTable
                  columns={repairColumns}
                  data={data.maintenanceByEquipment}
                  caption="Equipment with the most maintenance cases"
                  empty={
                    <EmptyState
                      className="min-h-0 py-8"
                      icon={<Wrench size={26} />}
                      title="No faults reported"
                      description="No maintenance case was opened in this period."
                    />
                  }
                />
              </Section>
            </div>
          </div>
        )
      )}
    </>
  );
}
function Summary({ report }: { report: Report }) {
  const measuredHours =
    Math.max(0, Date.parse(report.measuredUntil) - Date.parse(report.from)) / 3.6e6;
  const items = report.utilisationByCategory.reduce((n, c) => n + c.equipment, 0);
  const loaned = report.utilisationByCategory.reduce((n, c) => n + c.loanedHours, 0);
  const utilisation = items && measuredHours ? Math.min(1, loaned / (items * measuredHours)) : 0;
  const due = report.overdueByCategory.reduce((n, c) => n + c.loansDue, 0);
  const late = report.overdueByCategory.reduce((n, c) => n + c.returnedLate + c.stillOverdue, 0);
  const reservations = report.reservationsByCategory.reduce((n, c) => n + c.total, 0);
  const overdue = report.overdueNow.length;
  const repair = report.maintenance.averageCompletionHours;
  return (
    <section aria-label="Summary" className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
      <Tile label="Reservations" value={formatCount(reservations)} note="Starting in the period" />
      <Tile
        label="Average utilisation"
        value={formatPercent(utilisation)}
        note={`Across ${formatCount(items)} items`}
      />
      <Tile
        label="Overdue now"
        value={formatCount(overdue)}
        note={
          overdue > 0 ? (
            <Badge tone="bad" className="inline-flex items-center gap-1">
              <AlertTriangle size={12} aria-hidden="true" />
              Needs follow-up
            </Badge>
          ) : (
            'None outstanding'
          )
        }
      />
      <Tile
        label="Late returns"
        value={due ? formatPercent(late / due) : '—'}
        note={due ? `${formatCount(late)} of ${formatCount(due)} loans due` : 'No loan fell due'}
      />
      <Tile
        label="Maintenance cases reported"
        value={formatCount(report.maintenance.reportedInPeriod)}
        note={`${formatCount(report.maintenance.completedInPeriod)} completed in the period`}
      />
      <Tile
        label="Average repair time"
        value={repair == null ? '—' : formatHours(repair)}
        note={repair == null ? 'No case completed in the period' : 'Reported to resolved'}
      />
    </section>
  );
}
