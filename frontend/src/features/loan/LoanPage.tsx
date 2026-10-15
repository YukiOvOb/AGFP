import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useAuth } from '@/app/AuthProvider';
import { ApiError, message } from '@/api/client';
import { Button } from '@/components/ui/button';
import { formatDateTime } from '@/lib/time';
import {
  checkoutEquipment,
  receiveReturn,
  useCheckouts,
  useLoans,
  type Checkout,
  type Loan,
} from './queries';

const time = formatDateTime;
export function LoanPage() {
  const { user } = useAuth();
  const custodian = user?.roles.includes('CUSTODIAN') ?? false;
  const [page, setPage] = useState(0);
  const [queuePage, setQueuePage] = useState(0);
  const [activeOnly, setActiveOnly] = useState(true);
  const [searchInput, setSearchInput] = useState('');
  const [search, setSearch] = useState('');
  const loans = useLoans(activeOnly, page, custodian ? search : '');
  const checkouts = useCheckouts(custodian, queuePage, search);
  const client = useQueryClient();
  const [checkout, setCheckout] = useState<Checkout>();
  const [returning, setReturning] = useState<Loan>();
  const [confirmed, setConfirmed] = useState(false);
  const [condition, setCondition] = useState('GOOD');
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  async function submit() {
    setBusy(true);
    setError('');
    setSuccess('');
    try {
      if (checkout) await checkoutEquipment(checkout, confirmed);
      else if (returning) await receiveReturn(returning, condition, note);
      setCheckout(undefined);
      setReturning(undefined);
      setConfirmed(false);
      setSuccess('Handover recorded successfully.');
      await Promise.all(
        ['loans', 'checkouts', 'reservations', 'equipment', 'maintenance'].map((key) =>
          client.invalidateQueries({ queryKey: [key] }),
        ),
      );
    } catch (e) {
      setError(message(e));
      if (e instanceof ApiError && e.status === 409) {
        setCheckout(undefined);
        setReturning(undefined);
        setConfirmed(false);
      }
      await Promise.all(
        ['loans', 'checkouts'].map((key) => client.invalidateQueries({ queryKey: [key] })),
      );
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="loan-page">
      <h1>{custodian ? 'Checkout and returns' : 'My loans'}</h1>
      <p>Record physical handover and receipt. Times are shown in Singapore time.</p>
      {error && (
        <p role="alert" className="banner-error">
          {error}
        </p>
      )}
      {success && <p role="status">{success}</p>}
      {custodian && (
        <form onSubmit={(event) => {
          event.preventDefault();
          setSearch(searchInput.trim());
          setPage(0);
          setQueuePage(0);
        }}>
          <label htmlFor="loan-search">Find reservation, borrower or equipment code</label>
          <input id="loan-search" value={searchInput} maxLength={100}
            onChange={(event) => setSearchInput(event.target.value)} />
          <Button type="submit">Search</Button>
          {search && <Button type="button" variant="ghost" onClick={() => {
            setSearchInput(''); setSearch(''); setPage(0); setQueuePage(0);
          }}>Clear search</Button>}
        </form>
      )}
      {custodian && (
        <section aria-label="Checkout queue">
          <h2>Confirmed reservations</h2>
          {checkouts.isPending && <p>Loading reservations…</p>}
          {checkouts.error && <p role="alert">{message(checkouts.error)}</p>}
          {checkouts.data?.length === 0 && <p>{search ? 'No reservations match your search.' : 'No confirmed reservations awaiting checkout.'}</p>}
          {checkouts.data?.map((item) => (
            <article key={item.reservationId}>
              <strong>{item.equipmentName} ({item.assetTag})</strong>
              <p>Reservation {item.reservationId}</p>
              <p>
                {item.borrowerName} · {item.borrowerId}
              </p>
              <p>
                {time(item.startAt)} – {time(item.endAt)}
              </p>
              <Button
                disabled={busy || Date.now() < Date.parse(item.startAt)}
                onClick={() => {
                  setCheckout(item);
                  setReturning(undefined);
                  setConfirmed(false);
                  setError('');
                }}
              >
                Check out
              </Button>
            </article>
          ))}
          <Button disabled={queuePage === 0 || busy} onClick={() => setQueuePage(queuePage - 1)}>
            Previous reservations
          </Button>
          <Button
            disabled={(checkouts.data?.length ?? 0) < 20 || busy}
            onClick={() => setQueuePage(queuePage + 1)}
          >
            Next reservations
          </Button>
        </section>
      )}
      {(checkout || returning) && (
        <section aria-label="Handover confirmation">
          <h2>{checkout ? 'Confirm checkout' : 'Confirm physical return'}</h2>
          <p>
            {(checkout ?? returning)?.equipmentName} · {(checkout ?? returning)?.borrowerName}
          </p>
          {checkout ? (
            <label>
              <input
                type="checkbox"
                checked={confirmed}
                onChange={(e) => setConfirmed(e.target.checked)}
                disabled={busy}
              />
              I checked the person's identity against borrower ID {checkout.borrowerId}.
            </label>
          ) : (
            <>
              <label>
                Return condition{' '}
                <select
                  value={condition}
                  disabled={busy}
                  onChange={(e) => setCondition(e.target.value)}
                >
                  <option value="GOOD">Good</option>
                  <option value="DAMAGED">Damaged</option>
                </select>
              </label>
              <label>
                Return note{' '}
                <textarea
                  maxLength={2000}
                  value={note}
                  disabled={busy}
                  onChange={(e) => setNote(e.target.value)}
                />
              </label>
              {condition === 'DAMAGED' && (
                <p>A damage description is required. A linked maintenance case will be created.</p>
              )}
              {returning?.overdue && <p>This return is overdue and will be recorded as late.</p>}
            </>
          )}
          <Button
            disabled={busy || (checkout ? !confirmed : condition === 'DAMAGED' && !note.trim())}
            onClick={() => void submit()}
          >
            {busy ? 'Saving…' : 'Record handover'}
          </Button>
          <Button
            variant="ghost"
            disabled={busy}
            onClick={() => {
              setCheckout(undefined);
              setReturning(undefined);
            }}
          >
            Cancel
          </Button>
        </section>
      )}
      <section aria-label="Loan list">
        <h2>{activeOnly ? 'Active loans' : 'Loan history'}</h2>
        <label>
          <input
            type="checkbox"
            checked={activeOnly}
            onChange={(e) => {
              setActiveOnly(e.target.checked);
              setPage(0);
            }}
          />{' '}
          Show active loans only
        </label>
        {loans.isPending && <p>Loading loans…</p>}
        {loans.error && <p role="alert">{message(loans.error)}</p>}
        {loans.data?.length === 0 && <p>{search ? 'No loans match your search.' : 'No loans found.'}</p>}
        {loans.data?.map((item) => (
          <article key={item.loanId}>
            <strong>{item.equipmentName} ({item.assetTag})</strong>
            <p>Reservation {item.reservationId}</p>
            <p>
              {item.borrowerName} · {item.status}
              {item.overdue ? ' · Overdue / late return' : ''}
            </p>
            <p>
              Due {time(item.dueAt)}
              {item.returnedAt
                ? ` · Returned ${time(item.returnedAt)} · ${item.returnCondition}`
                : ''}
            </p>
            {item.returnNote && <p>{item.returnNote}</p>}
            {custodian && item.status === 'ACTIVE' && (
              <Button
                disabled={busy}
                onClick={() => {
                  setReturning(item);
                  setCheckout(undefined);
                  setCondition('GOOD');
                  setNote('');
                  setError('');
                }}
              >
                Receive return
              </Button>
            )}
          </article>
        ))}
        <Button disabled={page === 0 || busy} onClick={() => setPage(page - 1)}>
          Previous loans
        </Button>
        <Button disabled={(loans.data?.length ?? 0) < 20 || busy} onClick={() => setPage(page + 1)}>
          Next loans
        </Button>
      </section>
    </div>
  );
}
