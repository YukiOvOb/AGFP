import { ErrorState, LoadingState } from '@/components/Feedback';
import { message } from '@/api/client';
import { useAssignedCases } from './queries';
import { statusLabel, statuses } from './format';
import { MaintenanceCaseCard } from './MaintenanceCaseCard';
export function AssignedCaseList() {
  const query = useAssignedCases();
  return (
    <section className="maintenance-panel">
      <h2>My assigned cases</h2>
      {query.isPending ? (
        <LoadingState />
      ) : query.isError ? (
        <ErrorState message={message(query.error)} onRetry={() => void query.refetch()} />
      ) : !query.data.some((item) => item.status !== 'OPEN') ? (
        <p>No assigned maintenance cases.</p>
      ) : (
        statuses
          .filter((status) => status !== 'OPEN')
          .map((status) => {
            const items = query.data.filter((item) => item.status === status);
            return (
              items.length > 0 && (
                <section key={status} aria-label={statusLabel(status)}>
                  <h3>
                    {statusLabel(status)} <small>({items.length})</small>
                  </h3>
                  <div className="maintenance-grid">
                    {items.map((item) => (
                      <MaintenanceCaseCard key={item.maintenanceCaseId} item={item} />
                    ))}
                  </div>
                </section>
              )
            );
          })
      )}
    </section>
  );
}
