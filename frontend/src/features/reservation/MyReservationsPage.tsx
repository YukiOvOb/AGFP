import { useState } from 'react';
import { Link, useLocation } from 'react-router';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { CalendarCheck, ChevronLeft, ChevronRight, X } from 'lucide-react';
import { api, message } from '@/api/client';
import type { components } from '@/api/schema';
import { Button } from '@/components/ui/button';
import { ErrorState, LoadingState } from '@/components/Feedback';
import { reservationStatus, toneClass } from '@/lib/status';
import { formatRange } from '@/lib/time';
import { equipmentKeys } from '@/features/equipment/queries';
import { reservationKeys, useMyReservations, type ReservationStatus } from './queries';
type Reservation = components['schemas']['ReservationView'];
const FILTERS: { value: ReservationStatus | ''; label: string }[] = [
  { value: '', label: 'All' },
  { value: 'PENDING_APPROVAL', label: reservationStatus.PENDING_APPROVAL.label },
  { value: 'CONFIRMED', label: reservationStatus.CONFIRMED.label },
  { value: 'FULFILLED', label: reservationStatus.FULFILLED.label },
  { value: 'REJECTED', label: reservationStatus.REJECTED.label },
  { value: 'CANCELLED', label: reservationStatus.CANCELLED.label },
];
/** UC01: the borrower's own reservations, with cancel until checkout. */
export function MyReservationsPage() {
  const location = useLocation();
  const created = (location.state as { created?: Reservation } | null)?.created;
  const [status, setStatus] = useState<ReservationStatus | ''>('');
  const [page, setPage] = useState(0);
  const [confirming, setConfirming] = useState<string | null>(null);
  const client = useQueryClient();
  const list = useMyReservations(status || undefined, page);
  const cancel = useMutation({
    mutationFn: async (id: string) => {
      const { data } = await api.POST('/api/v1/reservations/{id}/cancel', {
        params: { path: { id } },
      });
      return data!;
    },
    onSettled: () => {
      setConfirming(null);
      void client.invalidateQueries({ queryKey: reservationKeys.all });
      void client.invalidateQueries({ queryKey: equipmentKeys.all });
    },
  });
  return (
    <>
      <div className="page-title">
        <div>
          <p className="eyebrow">YOUR BOOKINGS</p>
          <h1>
            My reservations<span className="title-dot">.</span>
          </h1>
          <p className="muted">Cancel any reservation until the equipment is checked out.</p>
        </div>
        <Link className="button button-primary" to="/equipment">
          <CalendarCheck size={16} />
          New reservation
        </Link>
      </div>
      {created && (
        <div className="notice notice-success" role="status">
          {created.status === 'PENDING_APPROVAL'
            ? `Request sent for ${created.equipmentName}. You will be notified when an approver decides.`
            : `${created.equipmentName} is reserved for ${formatRange(created.startAt, created.endAt)}.`}
        </div>
      )}
      {cancel.isError && <ErrorState message={message(cancel.error)} />}
      <section className="inbox-panel">
        <div className="panel-toolbar">
          <h2>Reservations{list.data && <span>{list.data.totalElements}</span>}</h2>
          <label className="sort-label">
            Show
            <select
              value={status}
              onChange={(e) => {
                setStatus(e.target.value as ReservationStatus | '');
                setPage(0);
              }}
            >
              {FILTERS.map((f) => (
                <option key={f.value} value={f.value}>
                  {f.label}
                </option>
              ))}
            </select>
          </label>
        </div>
        {list.isPending ? (
          <LoadingState />
        ) : list.isError ? (
          <ErrorState message={message(list.error)} onRetry={() => void list.refetch()} />
        ) : !list.data.content.length ? (
          <div className="state empty">
            <span className="state-icon">
              <CalendarCheck size={26} />
            </span>
            <h2>No reservations yet</h2>
            <p>Find equipment and reserve a time slot to see it here.</p>
          </div>
        ) : (
          <div className="table-scroll">
            <table className="data-table">
              <caption className="sr-only">Your reservations</caption>
              <thead>
                <tr>
                  <th scope="col">Equipment</th>
                  <th scope="col">When (SGT)</th>
                  <th scope="col">Status</th>
                  <th scope="col">
                    <span className="sr-only">Action</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {list.data.content.map((r) => (
                  <tr key={r.reservationId}>
                    <td>
                      <strong>{r.equipmentName}</strong>
                      <small className="cell-sub">
                        {r.assetTag} · {r.location}
                      </small>
                      {r.purpose && <small className="cell-sub">{r.purpose}</small>}
                    </td>
                    <td>{formatRange(r.startAt, r.endAt)}</td>
                    <td>
                      <span className={toneClass(reservationStatus[r.status].tone)}>
                        {reservationStatus[r.status].label}
                      </span>
                    </td>
                    <td className="row-actions">
                      {r.cancellable &&
                        (confirming === r.reservationId ? (
                          <>
                            <Button
                              variant="outline"
                              disabled={cancel.isPending}
                              onClick={() => cancel.mutate(r.reservationId)}
                            >
                              Yes, cancel
                            </Button>
                            <Button variant="ghost" onClick={() => setConfirming(null)}>
                              Keep
                            </Button>
                          </>
                        ) : (
                          <Button
                            variant="ghost"
                            aria-label={`Cancel reservation for ${r.equipmentName}`}
                            onClick={() => setConfirming(r.reservationId)}
                          >
                            <X size={15} />
                            Cancel
                          </Button>
                        ))}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <div className="pagination">
          <span>
            Page {page + 1}
            {list.data && ` of ${Math.max(1, list.data.totalPages)}`}
          </span>
          <div>
            <Button
              variant="outline"
              aria-label="Previous page"
              disabled={page === 0 || list.isFetching}
              onClick={() => setPage((p) => p - 1)}
            >
              <ChevronLeft size={16} />
            </Button>
            <Button
              variant="outline"
              aria-label="Next page"
              disabled={!list.data || page + 1 >= list.data.totalPages || list.isFetching}
              onClick={() => setPage((p) => p + 1)}
            >
              <ChevronRight size={16} />
            </Button>
          </div>
        </div>
      </section>
    </>
  );
}
