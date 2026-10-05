import { z } from 'zod';
import { localToIso } from '@/lib/time';
/**
 * Client-side mirror of the booking rules for instant feedback. The server applies the same rules
 * (and the overlap check) again; its answer is final.
 */
export const MIN_MINUTES = 30;
export const MAX_DAYS = 7;
export function reservationSchema(now: () => number = Date.now) {
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
      if (e - s > MAX_DAYS * 24 * 3_600_000)
        ctx.addIssue({
          code: 'custom',
          path: ['endAt'],
          message: `A reservation may last at most ${MAX_DAYS} days.`,
        });
    });
}
export type ReservationForm = z.infer<ReturnType<typeof reservationSchema>>;
