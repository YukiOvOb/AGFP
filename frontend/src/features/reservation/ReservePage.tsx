import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, CalendarPlus, Wrench } from 'lucide-react';
import { api, ApiError, message } from '@/api/client';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { ErrorState, LoadingState } from '@/components/Feedback';
import { equipmentStatus, slotReason, toneClass } from '@/lib/status';
import { formatRange, localToIso } from '@/lib/time';
import { equipmentKeys, useAvailability } from '@/features/equipment/queries';
import { reservationKeys } from './queries';
import { reservationSchema, type ReservationForm } from './rules';
const LOOKAHEAD_DAYS = 14;
type Violation = { code: string; message: string };
/** UC01 step 2: check an item's held slots and submit a reservation. */
export function ReservePage() {
  const { equipmentId = '' } = useParams();
  const [query] = useSearchParams();
  const navigate = useNavigate();
  const client = useQueryClient();
  const [view] = useState(() => {
    const from = new Date();
    from.setSeconds(0, 0);
    const to = new Date(from.getTime() + LOOKAHEAD_DAYS * 24 * 3_600_000);
    return { from: from.toISOString(), to: to.toISOString() };
  });
  const availability = useAvailability(equipmentId, view.from, view.to);
  const [failure, setFailure] = useState<{ detail: string; violations: Violation[] } | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<ReservationForm>({
    resolver: zodResolver(reservationSchema()),
    defaultValues: { startAt: query.get('from') ?? '', endAt: query.get('to') ?? '', purpose: '' },
  });
  const create = useMutation({
    mutationFn: async (values: ReservationForm) => {
      const { data } = await api.POST('/api/v1/reservations', {
        body: {
          equipmentId,
          startAt: localToIso(values.startAt)!,
          endAt: localToIso(values.endAt)!,
          purpose: values.purpose.trim() || undefined,
        },
      });
      return data!;
    },
    onSuccess: async (created) => {
      await client.invalidateQueries({ queryKey: reservationKeys.all });
      await client.invalidateQueries({ queryKey: equipmentKeys.all });
      navigate('/reservations', { state: { created } });
    },
    onError: (e) => {
      if (e instanceof ApiError) {
        const p = e.problem as typeof e.problem & { violations?: Violation[] };
        if (e.status === 400 && p.errors?.length) {
          for (const f of p.errors)
            if (f.field === 'startAt' || f.field === 'endAt' || f.field === 'purpose')
              setError(f.field, { message: f.message });
        }
        if (e.status === 409) void availability.refetch();
        setFailure({ detail: message(e), violations: p.violations ?? [] });
      } else setFailure({ detail: message(e), violations: [] });
    },
  });
  if (availability.isPending) return <LoadingState />;
  if (availability.isError)
    return (
      <ErrorState
        message={message(availability.error)}
        onRetry={() => void availability.refetch()}
      />
    );
  const a = availability.data;
  const blocked = a.equipment.status === 'UNDER_MAINTENANCE' || a.openMaintenance;
  return (
    <>
      <Link className="back-link" to="/equipment">
        <ArrowLeft size={15} />
        Back to search
      </Link>
      <div className="page-title">
        <div>
          <p className="eyebrow">RESERVE · {a.equipment.assetTag}</p>
          <h1>
            {a.equipment.name}
            <span className="title-dot">.</span>
          </h1>
          <p className="muted">
            {a.equipment.category} · {a.equipment.location} ·{' '}
            <span className={toneClass(equipmentStatus[a.equipment.status].tone)}>
              {equipmentStatus[a.equipment.status].label}
            </span>
          </p>
        </div>
      </div>
      <div className="reserve-grid">
        <section className="inbox-panel form-panel">
          <div className="panel-toolbar">
            <h2>Your reservation</h2>
          </div>
          {blocked ? (
            <div className="notice notice-warning" role="status">
              <Wrench size={18} />
              This equipment has a reported fault or is being repaired, so it cannot be reserved
              right now.
            </div>
          ) : (
            <form
              className="reserve-form"
              onSubmit={handleSubmit((v) => {
                setFailure(null);
                create.mutate(v);
              })}
              noValidate
            >
              <label htmlFor="startAt">Start (SGT)</label>
              <Input
                id="startAt"
                type="datetime-local"
                aria-invalid={!!errors.startAt}
                aria-describedby={errors.startAt ? 'startAt-error' : undefined}
                {...register('startAt')}
              />
              {errors.startAt && (
                <p id="startAt-error" className="field-error">
                  {errors.startAt.message}
                </p>
              )}
              <label htmlFor="endAt">End (SGT)</label>
              <Input
                id="endAt"
                type="datetime-local"
                aria-invalid={!!errors.endAt}
                aria-describedby={errors.endAt ? 'endAt-error' : undefined}
                {...register('endAt')}
              />
              {errors.endAt && (
                <p id="endAt-error" className="field-error">
                  {errors.endAt.message}
                </p>
              )}
              <label htmlFor="purpose">Purpose (optional)</label>
              <textarea
                id="purpose"
                className="input"
                rows={3}
                aria-invalid={!!errors.purpose}
                {...register('purpose')}
              />
              {errors.purpose && <p className="field-error">{errors.purpose.message}</p>}
              {a.equipment.requiresApproval && (
                <p className="muted small">
                  This equipment needs approval. Your request waits for an approver before it is
                  confirmed.
                </p>
              )}
              {failure && (
                <div role="alert" className="field-error">
                  <strong>{failure.detail}</strong>
                  {failure.violations.length > 1 && (
                    <ul>
                      {failure.violations.map((v) => (
                        <li key={v.code}>{v.message}</li>
                      ))}
                    </ul>
                  )}
                </div>
              )}
              <Button type="submit" disabled={create.isPending}>
                <CalendarPlus size={16} />
                {create.isPending
                  ? 'Reserving…'
                  : a.equipment.requiresApproval
                    ? 'Request reservation'
                    : 'Reserve'}
              </Button>
            </form>
          )}
        </section>
        <section className="inbox-panel">
          <div className="panel-toolbar">
            <h2>
              Taken in the next {LOOKAHEAD_DAYS} days<span>{a.busySlots.length}</span>
            </h2>
          </div>
          {a.busySlots.length === 0 ? (
            <p className="slot-empty">Nothing booked yet — every slot is open.</p>
          ) : (
            <ul className="slot-list" aria-label="Taken time slots">
              {a.busySlots.map((s) => (
                <li key={s.startAt}>
                  <span>{formatRange(s.startAt, s.endAt)}</span>
                  <small>{slotReason[s.reason] ?? s.reason}</small>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </>
  );
}
