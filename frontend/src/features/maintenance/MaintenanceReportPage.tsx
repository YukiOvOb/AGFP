import { useState } from 'react';
import { Link } from 'react-router';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useAuth } from '@/app/AuthProvider';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { ErrorState, LoadingState } from '@/components/Feedback';
import { message } from '@/api/client';
import {
  durationLabel,
  hasAnyRole,
  periodSchema,
  singaporeDate,
  singaporeInstant,
  statusLabel,
  statuses,
} from './format';
import { useReport } from './queries';
function Report() {
  const [period, setPeriod] = useState({ from: '', to: '' });
  const form = useForm<{ from: string; to: string }>({
    resolver: zodResolver(periodSchema),
    defaultValues: { from: '', to: '' },
  });
  const query = useReport(period.from, period.to);
  return (
    <div className="maintenance">
      <Link to="/maintenance" className="maintenance-back">
        ← Maintenance workspace
      </Link>
      <div className="page-title">
        <div>
          <span className="eyebrow">ADMIN INSIGHTS</span>
          <h1>Maintenance report</h1>
          <p className="muted">
            Choose a reporting period in Singapore time (SGT). End time is exclusive.
          </p>
        </div>
      </div>
      <section className="maintenance-panel">
        <form
          noValidate
          onSubmit={form.handleSubmit(({ from, to }) =>
            setPeriod({ from: singaporeInstant(from), to: singaporeInstant(to) }),
          )}
        >
          <div className="maintenance-grid">
            {(['from', 'to'] as const).map((field) => (
              <div key={field}>
                <label htmlFor={'report-' + field}>
                  {field === 'from' ? 'From (SGT, inclusive)' : 'To (SGT, exclusive)'}
                </label>
                <Input
                  id={'report-' + field}
                  type="datetime-local"
                  {...form.register(field)}
                  aria-invalid={!!form.formState.errors[field]}
                  aria-describedby={'report-' + field + '-error'}
                />
                <p id={'report-' + field + '-error'} className="field-error">
                  {form.formState.errors[field]?.message}
                </p>
              </div>
            ))}
          </div>
          <Button disabled={query.isFetching}>Generate report</Button>
        </form>
      </section>
      {!period.from ? (
        <p className="muted">
          Select both dates to view the report. No reporting period is selected.
        </p>
      ) : query.isPending ? (
        <LoadingState />
      ) : query.isError ? (
        <ErrorState message={message(query.error)} onRetry={() => void query.refetch()} />
      ) : (
        <section className="maintenance-panel" aria-label="Report results">
          <p className="muted">
            {singaporeDate(query.data.fromInclusive)} → {singaporeDate(query.data.toExclusive)}{' '}
            (exclusive)
          </p>
          <div className="maintenance-summary">
            <div>
              <span>Reported cases in selected period</span>
              <strong>{query.data.reportedCaseCount}</strong>
            </div>
            <div>
              <span>Completed cases in selected period</span>
              <strong>{query.data.completedCaseCount}</strong>
            </div>
            <div>
              <span>Average completion duration</span>
              <strong>{durationLabel(query.data.averageCompletionDuration)}</strong>
            </div>
          </div>
          <h2>Current cases by status</h2>
          <p className="muted">
            Current status totals; these counts are not limited to the selected reporting period.
          </p>
          <dl className="maintenance-status-counts">
            {statuses.map((status) => (
              <div key={status}>
                <dt>{statusLabel(status)}</dt>
                <dd>{query.data.casesByStatus[status] ?? 0}</dd>
              </div>
            ))}
          </dl>
        </section>
      )}
    </div>
  );
}
export function MaintenanceReportPage() {
  const { user } = useAuth();
  return hasAnyRole(user?.roles, 'ADMIN') ? (
    <Report />
  ) : (
    <ErrorState message="Your account does not have access to maintenance reports." />
  );
}
