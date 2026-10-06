import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Button } from '@/components/ui/button';
import { ErrorState } from '@/components/Feedback';
import { message } from '@/api/client';
import { nonBlank } from './format';
import { useCaseAction, type Action, type MaintenanceCase } from './queries';
const labels: Record<Action, string> = {
  start: 'Start Maintenance',
  diagnosis: 'Record Diagnosis',
  'repair-actions': 'Record Repair Action',
  notes: 'Add Note',
  resolve: 'Resolve',
  unrepairable: 'Mark Unrepairable',
};
const textSchema = z.object({ text: nonBlank });
function WorkForm({
  caseId,
  action,
  onSuccess,
}: {
  caseId: string;
  action: Action;
  onSuccess: (text: string) => void;
}) {
  const mutation = useCaseAction(caseId);
  const [confirming, setConfirming] = useState(false);
  const form = useForm<{ text: string }>({
    resolver: zodResolver(textSchema),
    defaultValues: { text: '' },
  });
  const id = 'work-' + action;
  async function submit(text: string) {
    try {
      await mutation.mutateAsync({ action, text });
      form.reset();
      setConfirming(false);
      onSuccess(labels[action] + ' completed successfully.');
    } catch {
      setConfirming(false);
    }
  }
  return (
    <form
      className="maintenance-work"
      noValidate
      onSubmit={form.handleSubmit(({ text }) => {
        if (action === 'unrepairable') setConfirming(true);
        else void submit(text);
      })}
    >
      <label htmlFor={id}>
        {action === 'resolve' || action === 'unrepairable'
          ? labels[action] + ' — resolution note'
          : labels[action] + ' — details'}
      </label>
      <textarea
        id={id}
        className="input"
        rows={3}
        {...form.register('text')}
        disabled={mutation.isPending || confirming}
        aria-invalid={!!form.formState.errors.text}
        aria-describedby={id + '-error'}
      />
      <p id={id + '-error'} className="field-error">
        {form.formState.errors.text?.message}
      </p>
      {!confirming ? (
        <Button
          variant={action === 'unrepairable' ? 'outline' : 'default'}
          disabled={mutation.isPending}
        >
          {mutation.isPending ? 'Saving…' : labels[action]}
        </Button>
      ) : (
        <section className="maintenance-confirm" aria-labelledby="retire-title">
          <h3 id="retire-title">Retire this equipment?</h3>
          <p>
            Marking this case UNREPAIRABLE changes the equipment to RETIRED. Confirm that it cannot
            be repaired.
          </p>
          <div className="maintenance-buttons">
            <Button
              type="button"
              autoFocus
              disabled={mutation.isPending}
              onClick={() => void submit(form.getValues('text'))}
            >
              Confirm unrepairable
            </Button>
            <Button
              type="button"
              variant="outline"
              disabled={mutation.isPending}
              onClick={() => setConfirming(false)}
            >
              Cancel
            </Button>
          </div>
        </section>
      )}
      {mutation.isError && <ErrorState message={message(mutation.error)} />}
    </form>
  );
}
export function MaintenanceActions({ item }: { item: MaintenanceCase }) {
  const start = useCaseAction(item.maintenanceCaseId);
  const [success, setSuccess] = useState('');
  return (
    <section className="maintenance-panel">
      <h2>Maintenance actions</h2>
      {success && (
        <p className="maintenance-success" role="status">
          {success}
        </p>
      )}
      {item.status === 'ASSIGNED' && (
        <>
          <Button
            disabled={start.isPending}
            onClick={() =>
              start.mutate(
                { action: 'start', text: '' },
                { onSuccess: () => setSuccess('Maintenance started successfully.') },
              )
            }
          >
            {start.isPending ? 'Starting…' : 'Start Maintenance'}
          </Button>
          {start.isError && <ErrorState message={message(start.error)} />}
        </>
      )}
      {item.status === 'IN_PROGRESS' && (
        <div className="maintenance-grid">
          {(['diagnosis', 'repair-actions', 'notes', 'resolve', 'unrepairable'] as const).map(
            (action) => (
              <WorkForm
                key={action}
                action={action}
                caseId={item.maintenanceCaseId}
                onSuccess={setSuccess}
              />
            ),
          )}
        </div>
      )}
      {(item.status === 'RESOLVED' || item.status === 'UNREPAIRABLE') && (
        <p>This case is complete and read-only.</p>
      )}
    </section>
  );
}
