import { useState } from 'react';
import { Link } from 'react-router';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import type { z } from 'zod';
import { useAuth } from '@/app/AuthProvider';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { ErrorState } from '@/components/Feedback';
import { message } from '@/api/client';
import { faultSchema, hasAnyRole } from './format';
import { useReportFault } from './queries';
export function ReportFaultForm() {
  const { user } = useAuth();
  const mutation = useReportFault();
  const [created, setCreated] = useState('');
  const form = useForm<z.infer<typeof faultSchema>>({
    resolver: zodResolver(faultSchema),
    defaultValues: { equipmentId: '', loanId: '', faultDescription: '' },
  });
  return (
    <section className="maintenance-panel">
      <h2>Report a fault</h2>
      <p className="muted">Tell us what happened so the equipment can be assessed.</p>
      <form
        noValidate
        onSubmit={form.handleSubmit(async (values) => {
          setCreated('');
          try {
            const item = await mutation.mutateAsync({
              equipmentId: values.equipmentId,
              ...(values.loanId ? { loanId: values.loanId } : {}),
              faultDescription: values.faultDescription,
            });
            setCreated(item.maintenanceCaseId);
            form.reset();
          } catch {
            /* ProblemDetail is rendered below. */
          }
        })}
      >
        <label htmlFor="fault-equipment">Equipment ID</label>
        <Input
          id="fault-equipment"
          {...form.register('equipmentId')}
          aria-invalid={!!form.formState.errors.equipmentId}
          aria-describedby="equipment-help fault-equipment-error"
          placeholder="Equipment UUID"
        />
        <p id="equipment-help" className="muted">
          Equipment lookup will be connected when the shared equipment catalogue API is available.
        </p>
        <p id="fault-equipment-error" className="field-error">
          {form.formState.errors.equipmentId?.message}
        </p>
        <label htmlFor="fault-loan">Loan ID (optional)</label>
        <Input
          id="fault-loan"
          {...form.register('loanId')}
          aria-invalid={!!form.formState.errors.loanId}
          aria-describedby="fault-loan-error"
        />
        <p id="fault-loan-error" className="field-error">
          {form.formState.errors.loanId?.message}
        </p>
        <label htmlFor="fault-description">Fault description</label>
        <textarea
          id="fault-description"
          className="input"
          rows={4}
          {...form.register('faultDescription')}
          aria-invalid={!!form.formState.errors.faultDescription}
          aria-describedby="fault-description-error"
        />
        <p id="fault-description-error" className="field-error">
          {form.formState.errors.faultDescription?.message}
        </p>
        <Button disabled={mutation.isPending}>
          {mutation.isPending ? 'Reporting…' : 'Submit fault report'}
        </Button>
      </form>
      {mutation.isError && <ErrorState message={message(mutation.error)} />}
      {created && (
        <p role="status" className="maintenance-success">
          Fault reported successfully. Case ID: {created}
          {hasAnyRole(user?.roles, 'CUSTODIAN', 'ADMIN') && (
            <>
              {' '}
              · <Link to={'/maintenance/cases/' + created}>View case</Link>
            </>
          )}
        </p>
      )}
    </section>
  );
}
