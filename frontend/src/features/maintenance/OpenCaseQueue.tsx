import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { ErrorState, LoadingState } from '@/components/Feedback';
import { message } from '@/api/client';
import { useAssignment, useOpenCases, useTechnicians, type MaintenanceCase } from './queries';
import { MaintenanceCaseCard } from './MaintenanceCaseCard';
function Assignment({
  item,
  technicians,
  onSuccess,
}: {
  item: MaintenanceCase;
  technicians: NonNullable<ReturnType<typeof useTechnicians>['data']>;
  onSuccess: () => void;
}) {
  const [technician, setTechnician] = useState('');
  const mutation = useAssignment(item.maintenanceCaseId, onSuccess);
  const id = 'assign-' + item.maintenanceCaseId;
  return (
    <form
      className="maintenance-assignment"
      onSubmit={(event) => {
        event.preventDefault();
        if (technician) mutation.mutate(technician);
      }}
    >
      <label htmlFor={id}>Assign technician</label>
      <select
        id={id}
        className="input"
        required
        value={technician}
        onChange={(event) => setTechnician(event.target.value)}
        disabled={mutation.isPending || !technicians.length}
      >
        <option value="">Select an active technician</option>
        {technicians.map((tech) => (
          <option key={tech.userId} value={tech.userId}>
            {tech.displayName} · {tech.email}
          </option>
        ))}
      </select>
      <Button disabled={!technician || mutation.isPending}>
        {mutation.isPending ? 'Assigning…' : 'Assign technician'}
      </Button>
      {mutation.isError && <ErrorState message={message(mutation.error)} />}
    </form>
  );
}
export function OpenCaseQueue() {
  const cases = useOpenCases();
  const technicians = useTechnicians();
  const [assigned, setAssigned] = useState(false);
  // A successful assignment removes the OPEN card; feedback belongs outside that card.
  return (
    <section className="maintenance-panel" onSubmitCapture={() => setAssigned(false)}>
      <h2>Open maintenance queue</h2>
      <p className="muted">Review faults and assign an active maintainer.</p>
      {cases.isPending ? (
        <LoadingState />
      ) : cases.isError ? (
        <ErrorState message={message(cases.error)} onRetry={() => void cases.refetch()} />
      ) : !cases.data.length ? (
        <p>No open maintenance cases.</p>
      ) : (
        <div className="maintenance-grid">
          {cases.data.map((item) => (
            <div key={item.maintenanceCaseId}>
              <MaintenanceCaseCard item={item} historyLink>
                {technicians.isPending ? (
                  <p role="status">Loading technicians…</p>
                ) : technicians.isError ? (
                  <ErrorState
                    message={message(technicians.error)}
                    onRetry={() => void technicians.refetch()}
                  />
                ) : (
                  <>
                    <Assignment
                      item={item}
                      technicians={technicians.data}
                      onSuccess={() => setAssigned(true)}
                    />
                    {!technicians.data.length && <p>No active technicians available.</p>}
                  </>
                )}
              </MaintenanceCaseCard>
            </div>
          ))}
        </div>
      )}
      {assigned && <p role="status">Technician assigned successfully.</p>}
    </section>
  );
}
