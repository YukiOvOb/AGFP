import type { ReactNode } from 'react';
import { createColumnHelper } from '@tanstack/react-table';
import {
  Bar,
  BarChart,
  CartesianGrid,
  LabelList,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { DataTable } from '@/components/ui/data-table';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
export type BarRow = { label: string; value: number };
type Props = {
  title: string;
  description?: ReactNode;
  rows: BarRow[];
  /** Formats axis ticks, bar labels, the tooltip and the table. */
  format: (value: number) => string;
  categoryLabel: string;
  valueLabel: string;
  /** Fixed upper end of the value axis, for example 1 for a share. */
  max?: number;
  empty: ReactNode;
};
const BAR = 18;
const ROW = 40;
const AXIS = 32;
const helper = createColumnHelper<BarRow>();
function shorten(label: string) {
  return label.length > 18 ? `${label.slice(0, 17)}…` : label;
}
function reducedMotion() {
  return (
    typeof window !== 'undefined' &&
    !!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  );
}
/** Value first, label second; the colour key is a short stroke, not a box. */
function ChartTooltip({
  active,
  payload,
  format,
}: {
  active?: boolean;
  payload?: ReadonlyArray<{ payload?: unknown }>;
  format: (value: number) => string;
}) {
  const row = payload?.[0]?.payload as BarRow | undefined;
  if (!active || !row) return null;
  return (
    <div className="rounded-md border border-border bg-popover px-3 py-2 text-popover-foreground shadow-sm">
      <p className="m-0 flex items-center gap-2 text-sm font-semibold">
        <span
          aria-hidden="true"
          className="inline-block h-0.5 w-3 rounded-full bg-[var(--chart-1)]"
        />
        {format(row.value)}
      </p>
      <p className="m-0 mt-0.5 text-xs text-muted-foreground">{row.label}</p>
    </div>
  );
}
/**
 * One measure across named categories as horizontal bars, with the same numbers in a table tab.
 * A single series, so the title names it and there is no legend.
 */
export function BarChartCard({
  title,
  description,
  rows,
  format,
  categoryLabel,
  valueLabel,
  max,
  empty,
}: Props) {
  const columns = [
    helper.accessor('label', { header: categoryLabel }),
    helper.accessor('value', {
      header: valueLabel,
      cell: (cell) => <span className="tabular-nums">{format(cell.getValue())}</span>,
    }),
  ];
  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle>{title}</CardTitle>
        {description && <CardDescription>{description}</CardDescription>}
      </CardHeader>
      {rows.length === 0 ? (
        <div className="px-6 pb-6">{empty}</div>
      ) : (
        <Tabs defaultValue="chart" className="px-6 pb-4">
          <TabsList aria-label={`${title}: view`}>
            <TabsTrigger value="chart">Chart</TabsTrigger>
            <TabsTrigger value="table">Table</TabsTrigger>
          </TabsList>
          <TabsContent value="chart">
            <div style={{ height: rows.length * ROW + AXIS }}>
              <ResponsiveContainer width="100%" height="100%">
                <BarChart
                  data={rows}
                  layout="vertical"
                  margin={{ top: 0, right: 56, bottom: 0, left: 0 }}
                  barCategoryGap={ROW - BAR}
                >
                  <CartesianGrid horizontal={false} stroke="var(--border)" />
                  <XAxis
                    type="number"
                    domain={[0, max ?? 'auto']}
                    tickFormatter={(value: number) => format(value)}
                    tick={{ fill: 'var(--muted-foreground)', fontSize: 11 }}
                    axisLine={{ stroke: 'var(--border)' }}
                    tickLine={false}
                    allowDecimals={max !== undefined}
                  />
                  <YAxis
                    type="category"
                    dataKey="label"
                    width={132}
                    tickFormatter={shorten}
                    tick={{ fill: 'var(--foreground)', fontSize: 12 }}
                    axisLine={false}
                    tickLine={false}
                  />
                  <Tooltip
                    cursor={{ fill: 'var(--muted)' }}
                    content={({ active, payload }) => (
                      <ChartTooltip active={active} payload={payload} format={format} />
                    )}
                  />
                  <Bar
                    dataKey="value"
                    name={valueLabel}
                    fill="var(--chart-1)"
                    barSize={BAR}
                    radius={[0, 4, 4, 0]}
                    isAnimationActive={!reducedMotion()}
                  >
                    <LabelList
                      dataKey="value"
                      position="right"
                      formatter={(value) => format(Number(value))}
                      fill="var(--foreground)"
                      fontSize={12}
                    />
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </div>
          </TabsContent>
          <TabsContent value="table">
            <DataTable columns={columns} data={rows} caption={title} pageSize={rows.length} />
          </TabsContent>
        </Tabs>
      )}
    </Card>
  );
}
