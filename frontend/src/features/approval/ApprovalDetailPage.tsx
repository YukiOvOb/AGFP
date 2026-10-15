import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import { RefreshCw } from 'lucide-react';
import { useAuth } from '@/app/AuthProvider';
import { Button } from '@/components/ui/button';
import { ErrorState, LoadingState } from '@/components/Feedback';
import { message } from '@/api/client';
import { isUuid, safeVersion, singaporeTime, statusLabel } from './format';
import { useApprovalDetail } from './queries';
import { DecisionForm } from './DecisionForm';

function Detail({ id }: { id: string }) {
  const query = useApprovalDetail(id);
  const { user } = useAuth();
  const [tick, setTick] = useState(0);
  const starts = Date.parse(query.data?.startAt ?? '');
  const pending = query.data?.status === 'PENDING_APPROVAL';
  const serverExpired = query.data?.expired;
  useEffect(() => {
    if (!pending || serverExpired || !Number.isFinite(starts) || starts <= Date.now()) return;
    const timer = window.setTimeout(
      () => setTick((value) => value + 1),
      Math.min(starts - Date.now() + 1, 2_147_483_647),
    );
    return () => window.clearTimeout(timer);
  }, [starts, pending, serverExpired, tick]);

  if (query.isPending) return <LoadingState />;
  if (!query.data)
    return <ErrorState message={message(query.error)} onRetry={() => void query.refetch()} />;
  const item = query.data;
  const expired = pending && (!!item.expired || (Number.isFinite(starts) && starts <= Date.now()));
  const own =
    !!item.requesterId &&
    !!user?.userId &&
    item.requesterId.toLowerCase() === user.userId.toLowerCase();
  const safe =
    isUuid(item.reservationId) &&
    item.reservationId.toLowerCase() === id.toLowerCase() &&
    isUuid(item.requesterId) &&
    isUuid(user?.userId) &&
    safeVersion(item.version) &&
    Number.isFinite(starts);
  async function refresh() {
    const result = await query.refetch();
    if (result.isError) throw result.error;
  }

  return (
    <div className="approval">
      <Link to="/approvals" className="approval-back">
        ← Pending reservations
      </Link>
      <div className="page-title">
        <div>
          <p className="eyebrow">RESERVATION REVIEW</p>
          <h1>Request details</h1>
          <p className="muted">Check the equipment, applicant and reserved time before deciding.</p>
        </div>
        <span className="badge">{statusLabel(item.status)}</span>
      </div>
      {query.isError && (
        <ErrorState message={message(query.error)} onRetry={() => void query.refetch()} />
      )}
      {expired && (
        <p className="approval-notice" role="status">
          This reservation has expired. You can still reject it to release the reserved time slot.
        </p>
      )}
      <div className="approval-grid">
        <section className="approval-panel" aria-labelledby="reservation-heading">
          <h2 id="reservation-heading">{item.equipment?.name ?? 'Reserved equipment'}</h2>
          <dl className="approval-meta">
            <dt>Asset tag</dt>
            <dd>{item.equipment?.assetTag ?? '—'}</dd>
            <dt>Category</dt>
            <dd>{item.equipment?.category ?? '—'}</dd>
            <dt>Applicant</dt>
            <dd>{item.requesterName ?? '—'}</dd>
            <dt>Starts</dt>
            <dd>{singaporeTime(item.startAt)}</dd>
            <dt>Ends</dt>
            <dd>{singaporeTime(item.endAt)}</dd>
            <dt>Requested</dt>
            <dd>{singaporeTime(item.createdAt)}</dd>
            <dt>Purpose</dt>
            <dd className="approval-text">{item.purpose || 'Not provided'}</dd>
          </dl>
        </section>
        <section className="approval-panel" aria-label="Approval decision">
          {item.decision ? (
            <>
              <h2>Decision recorded</h2>
              <dl className="approval-meta">
                <dt>Result</dt>
                <dd>
                  {item.decision.decision === 'APPROVED'
                    ? 'Approved'
                    : item.decision.decision === 'REJECTED'
                      ? 'Rejected'
                      : 'Unavailable'}
                </dd>
                <dt>Approver</dt>
                <dd>{item.decision.approverId ?? '—'}</dd>
                <dt>Recorded</dt>
                <dd>{singaporeTime(item.decision.decidedAt)}</dd>
                <dt>Comment</dt>
                <dd className="approval-text">{item.decision.comment || 'No comment'}</dd>
              </dl>
            </>
          ) : !pending ? (
            <>
              <h2>Reservation status</h2>
              <p>This reservation is no longer waiting for approval.</p>
            </>
          ) : own ? (
            <p className="approval-notice">You cannot approve or reject your own reservation.</p>
          ) : !safe ? (
            <ErrorState
              message="Unable to safely load this request. Refresh before continuing."
              onRetry={() => void query.refetch()}
            />
          ) : (
            <DecisionForm
              item={item}
              expired={expired}
              busy={query.isFetching || query.isError}
              refresh={refresh}
            />
          )}
        </section>
      </div>
      <Button variant="outline" disabled={query.isFetching} onClick={() => void query.refetch()}>
        <RefreshCw size={16} />
        Refresh details
      </Button>
    </div>
  );
}

export function ApprovalDetailPage() {
  const { reservationId = '' } = useParams();
  if (!isUuid(reservationId)) return <ErrorState message="This reservation link is invalid." />;
  return <Detail key={reservationId} id={reservationId} />;
}
