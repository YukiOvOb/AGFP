import { useEffect, useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { ApiError, message } from '@/api/client';
import { Button } from '@/components/ui/button';
import { ErrorState } from '@/components/Feedback';
import { decisionSchema, safeVersion, type DecisionFormValues } from './format';
import { useDecision, type ApprovalDetail } from './queries';

type Preview = { values: DecisionFormValues; version: number };
export function DecisionForm({
  item,
  expired,
  busy,
  refresh,
}: {
  item: ApprovalDetail;
  expired: boolean;
  busy: boolean;
  refresh: () => Promise<void>;
}) {
  const mutation = useDecision(item.reservationId!);
  const [preview, setPreview] = useState<Preview | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const submitting = useRef(false);
  const confirmation = useRef<HTMLElement>(null);
  const {
    register,
    handleSubmit,
    watch,
    setError,
    clearErrors,
    formState: { errors },
  } = useForm<DecisionFormValues>({
    resolver: zodResolver(decisionSchema),
    defaultValues: { decision: expired ? 'REJECTED' : 'APPROVED', comment: '' },
  });
  const comment = watch('comment');
  const choice = watch('decision');
  const needsRefresh =
    mutation.isError && !(mutation.error instanceof ApiError && mutation.error.status === 400);
  const changed = !!preview && preview.version !== item.version;
  const expiredApproval = !!preview && expired && preview.values.decision === 'APPROVED';
  const blocked = busy || refreshing || mutation.isPending || needsRefresh;

  useEffect(() => {
    if (preview) confirmation.current?.querySelector<HTMLButtonElement>('button')?.focus();
  }, [preview]);

  function review(values: DecisionFormValues) {
    if (!safeVersion(item.version)) {
      setError('root.server', {
        message: 'Unable to safely submit this request. Refresh before continuing.',
      });
      return;
    }
    if (expired && values.decision === 'APPROVED') {
      setError('decision', { message: 'This reservation has expired. Choose Reject instead.' });
      return;
    }
    mutation.reset();
    clearErrors();
    setPreview({ values, version: item.version });
  }

  async function confirm() {
    if (!preview || blocked || changed || expiredApproval || submitting.current) return;
    submitting.current = true;
    try {
      await mutation.mutateAsync({
        decision: preview.values.decision,
        comment: preview.values.comment || undefined,
        expectedVersion: preview.version,
      });
    } catch (error) {
      setPreview(null);
      if (error instanceof ApiError && error.status === 400) {
        const fieldErrors = error.problem.errors ?? [];
        let mapped = false;
        for (const field of fieldErrors) {
          if (field.field === 'comment' || field.field === 'decision') {
            setError(field.field, { type: 'server', message: field.message });
            mapped = true;
          }
        }
        if (!mapped) setError('root.server', { type: 'server', message: message(error) });
      }
    } finally {
      submitting.current = false;
    }
  }

  async function reload() {
    setRefreshing(true);
    try {
      await refresh();
      mutation.reset();
      setPreview(null);
      clearErrors();
    } catch (error) {
      setError('root.server', { message: message(error) });
    } finally {
      setRefreshing(false);
    }
  }

  return (
    <>
      <h2>Make a decision</h2>
      <p className="muted">
        Approval confirms the reservation. Equipment issue is a separate step.
      </p>
      {needsRefresh && (
        <ErrorState
          message={
            mutation.error instanceof ApiError && mutation.error.status === 409
              ? 'This request changed or can no longer be approved. Refresh it before making another decision.'
              : message(mutation.error)
          }
        />
      )}
      {(needsRefresh || changed) && (
        <Button variant="outline" disabled={refreshing || busy} onClick={() => void reload()}>
          {refreshing ? 'Refreshing…' : 'Refresh request'}
        </Button>
      )}
      {errors.root?.server?.message && (
        <p className="field-error" role="alert">
          {errors.root.server.message}
        </p>
      )}
      <form onSubmit={handleSubmit(review)} noValidate>
        <fieldset disabled={blocked || !!preview}>
          <legend>Decision</legend>
          {!expired && (
            <label className="approval-choice">
              <input type="radio" value="APPROVED" {...register('decision')} />
              Approve reservation
            </label>
          )}
          <label className="approval-choice">
            <input type="radio" value="REJECTED" {...register('decision')} />
            Reject reservation
          </label>
          {errors.decision && (
            <p className="field-error" role="alert">
              {errors.decision.message}
            </p>
          )}
          <label className="approval-comment-label" htmlFor="approval-comment">
            Comment {choice === 'REJECTED' ? '(required)' : '(optional)'}
          </label>
          <textarea
            className="input"
            id="approval-comment"
            rows={4}
            maxLength={1000}
            aria-invalid={!!errors.comment}
            aria-describedby="approval-comment-help approval-comment-error"
            {...register('comment')}
          />
          <p className="approval-help" id="approval-comment-help">
            {comment.length}/1000 characters. Explain the reason when rejecting.
          </p>
          <p
            className="field-error"
            id="approval-comment-error"
            role={errors.comment ? 'alert' : undefined}
          >
            {errors.comment?.message}
          </p>
          <Button type="submit">Review decision</Button>
        </fieldset>
      </form>
      {preview && (
        <section
          ref={confirmation}
          className="approval-confirmation"
          aria-label="Confirm approval decision"
        >
          <h3>
            {preview.values.decision === 'APPROVED'
              ? 'Approve this reservation?'
              : 'Reject this reservation?'}
          </h3>
          <p>
            {preview.values.decision === 'APPROVED'
              ? 'The applicant will be notified that their reservation is confirmed.'
              : 'The reserved time slot will be released and the applicant will be notified.'}
          </p>
          {preview.values.comment && <p className="approval-text">{preview.values.comment}</p>}
          {(changed || expiredApproval) && (
            <p className="field-error" role="alert">
              {changed
                ? 'The request was updated. Refresh it before confirming.'
                : 'This reservation has expired. Return to editing and choose Reject.'}
            </p>
          )}
          <div className="approval-actions">
            <Button disabled={blocked || changed || expiredApproval} onClick={() => void confirm()}>
              {mutation.isPending
                ? 'Submitting…'
                : preview.values.decision === 'APPROVED'
                  ? 'Confirm approval'
                  : 'Confirm rejection'}
            </Button>
            <Button
              variant="outline"
              disabled={mutation.isPending || refreshing}
              onClick={() => setPreview(null)}
            >
              Back to editing
            </Button>
          </div>
        </section>
      )}
    </>
  );
}
