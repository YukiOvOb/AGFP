import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { ErrorState, LoadingState } from '@/components/Feedback';
import { message } from '@/api/client';
import {
  useAssignment,
  useOpenCases,
  useTechnicians,
  type MaintenanceCase,
  type Priority,
} from './queries';
import { enumLabel, priorities } from './format';
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
const rank = (priority: Priority) => priorities.indexOf(priority);
export function OpenCaseQueue() {
  const cases = useOpenCases();
  const technicians = useTechnicians();
  const [assigned, setAssigned] = useState(false);
  const [priorityFilter, setPriorityFilter] = useState<Priority | ''>('');
  const [sort, setSort] = useState<'oldest' | 'priority'>('oldest');
  // The server lists oldest first; filtering and the priority sort are view choices. The sort is
  // stable, so equal priorities keep the oldest-first order.
  const shown = (cases.data ?? []).filter(
    (item) => !priorityFilter || item.priority === priorityFilter,
  );
  if (sort === 'priority') shown.sort((a, b) => rank(a.priority) - rank(b.priority));
  // A successful assignment removes the OPEN card; feedback belongs outside that card.
  return (
    <section className="maintenance-panel" onSubmitCapture={() => setAssigned(false)}>
      <h2>Open maintenance queue</h2>
      <p className="muted">Review faults and assign an active maintainer.</p>
      <div className="maintenance-queue-controls">
        <label htmlFor="queue-priority">Priority</label>
        <select
          id="queue-priority"
          className="input"
          value={priorityFilter}
          onChange={(event) => setPriorityFilter(event.target.value as Priority | '')}
        >
          <option value="">All priorities</option>
          {priorities.map((priority) => (
            <option key={priority} value={priority}>
              {enumLabel(priority)}
            </option>
          ))}
        </select>
        <label htmlFor="queue-sort">Sort</label>
        <select
          id="queue-sort"
          className="input"
          value={sort}
          onChange={(event) => setSort(event.target.value as 'oldest' | 'priority')}
        >
          <option value="oldest">Oldest report first</option>
          <option value="priority">Highest priority first</option>
        </select>
      </div>
      {cases.isPending ? (
        <LoadingState />
      ) : cases.isError ? (
        <ErrorState message={message(cases.error)} onRetry={() => void cases.refetch()} />
      ) : !cases.data.length ? (
        <p>No open maintenance cases.</p>
      ) : !shown.length ? (
        <p>No open cases with this priority.</p>
      ) : (
        <div className="maintenance-grid">
          {shown.map((item) => (
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
