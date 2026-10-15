import { formatInTimeZone } from 'date-fns-tz';
import { z } from 'zod';

export const statusLabels: Record<string, string> = {
  PENDING_APPROVAL: 'Pending approval',
  CONFIRMED: 'Confirmed',
  REJECTED: 'Rejected',
  CANCELLED: 'Cancelled',
  FULFILLED: 'Fulfilled',
};
export const statusLabel = (status: string | undefined) =>
  statusLabels[status ?? ''] ?? 'Unavailable';
export const isUuid = (id: string | undefined): id is string =>
  !!id && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id);
export const safeVersion = (version: number | undefined): version is number =>
  typeof version === 'number' && Number.isSafeInteger(version) && version >= 0;
export function singaporeTime(value: string | undefined) {
  if (!value) return '—';
  try {
    return formatInTimeZone(value, 'Asia/Singapore', 'dd MMM yyyy, HH:mm') + ' SGT';
  } catch {
    return 'Time unavailable';
  }
}
export const decisionSchema = z
  .object({
    decision: z.enum(['APPROVED', 'REJECTED']),
    comment: z.string().max(1000, 'Comment must not exceed 1000 characters.'),
  })
  .superRefine((value, context) => {
    if (value.decision === 'REJECTED' && !value.comment.trim())
      context.addIssue({
        code: 'custom',
        path: ['comment'],
        message: 'Enter a reason for rejecting this reservation.',
      });
  });
export type DecisionFormValues = z.infer<typeof decisionSchema>;
