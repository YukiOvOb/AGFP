import { z } from 'zod';
import { localToIso } from '@/lib/time';
/**
 * Client-side mirror of the booking rules for instant feedback. The server applies the same rules
 * (and the overlap check) again; its answer is final.
 */
export const MIN_MINUTES = 30;
export const MAX_DAYS = 7;
/** Minutes in an ISO-8601 duration as the server writes it (`PT72H`, `PT2H30M`); null otherwise. */
export function durationMinutes(iso: string | null | undefined): number | null {
  const match = iso ? /^PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?$/.exec(iso) : null;
  if (!match || !match.slice(1).some(Boolean)) return null;
  return Number(match[1] ?? 0) * 60 + Number(match[2] ?? 0) + Number(match[3] ?? 0) / 60;
}
/** Same wording as the server's DurationPolicy: whole days, else whole hours, else minutes. */
export function durationText(minutes: number) {
  if (minutes % (24 * 60) === 0) return plural(minutes / (24 * 60), 'day');
  if (minutes % 60 === 0) return plural(minutes / 60, 'hour');
  return `${Math.floor(minutes)} minutes`;
}
const plural = (n: number, unit: string) => `${n} ${unit}${n === 1 ? '' : 's'}`;
/** `itemMaxMinutes` is the item's own limit (V005); null means the global maximum applies. */
export function reservationSchema(
  now: () => number = Date.now,
  itemMaxMinutes: number | null = null,
) {
  return z
    .object({
      startAt: z.string().min(1, 'Enter a start time.'),
      endAt: z.string().min(1, 'Enter an end time.'),
      purpose: z.string().max(500, 'Keep the purpose under 500 characters.'),
    })
    .superRefine((v, ctx) => {
      const start = localToIso(v.startAt);
      const end = localToIso(v.endAt);
      if (v.startAt && !start)
        ctx.addIssue({ code: 'custom', path: ['startAt'], message: 'Enter a valid start time.' });
      if (v.endAt && !end)
        ctx.addIssue({ code: 'custom', path: ['endAt'], message: 'Enter a valid end time.' });
      if (!start || !end) return;
      const s = Date.parse(start);
      const e = Date.parse(end);
      if (s < now())
        ctx.addIssue({
          code: 'custom',
          path: ['startAt'],
          message: 'The start time has already passed.',
        });
      if (e <= s) {
        ctx.addIssue({
          code: 'custom',
          path: ['endAt'],
          message: 'The end must be after the start.',
        });
        return;
      }
      if (e - s < MIN_MINUTES * 60_000)
        ctx.addIssue({
          code: 'custom',
          path: ['endAt'],
          message: `A reservation must last at least ${MIN_MINUTES} minutes.`,
        });
      const maxMinutes = itemMaxMinutes ?? MAX_DAYS * 24 * 60;
      if (e - s > maxMinutes * 60_000)
        ctx.addIssue({
          code: 'custom',
          path: ['endAt'],
          message:
            itemMaxMinutes === null
              ? `A reservation may last at most ${MAX_DAYS} days.`
              : `This equipment may be reserved for at most ${durationText(itemMaxMinutes)}.`,
        });
    });
}
export type ReservationForm = z.infer<ReturnType<typeof reservationSchema>>;
