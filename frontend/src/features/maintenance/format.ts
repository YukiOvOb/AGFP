import { formatInTimeZone, fromZonedTime } from 'date-fns-tz';
import { z } from 'zod';
import type { Status } from './queries';
export const statuses: Status[] = ['OPEN', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'UNREPAIRABLE'];
export const statusLabel = (status: Status) => status.replaceAll('_', ' ');
export { hasAnyRole } from '@/lib/roles';
export const workspaceRoles = ['BORROWER', 'CUSTODIAN', 'MAINTAINER', 'ADMIN'];
export function singaporeDate(value: string | null) {
  if (!value) return '—';
  try {
    return formatInTimeZone(value, 'Asia/Singapore', 'dd MMM yyyy, HH:mm') + ' SGT';
  } catch {
    return value;
  }
}
export function durationLabel(value: string | null) {
  if (value === null) return 'No completed cases';
  const match = /^PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?$/.exec(value);
  if (!match || !match.slice(1).some(Boolean)) return value;
  return match
    .slice(1)
    .map((part, index) => (part ? part + ['h', 'm', 's'][index] : ''))
    .filter(Boolean)
    .join(' ');
}
// Match PostgreSQL/Java UUID syntax, including existing database identifiers whose
// version/variant bits are not RFC-generated. The backend owns existence checks.
export const uuid = z
  .string()
  .regex(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i, 'Enter a valid UUID.');
export const nonBlank = z.string().trim().min(1, 'This field is required.');
export const faultSchema = z.object({
  equipmentId: uuid,
  loanId: z.union([uuid, z.literal('')]),
  faultDescription: nonBlank,
});
export const periodSchema = z
  .object({ from: nonBlank, to: nonBlank })
  .superRefine((value, context) => {
    const from = fromZonedTime(value.from, 'Asia/Singapore').getTime();
    const to = fromZonedTime(value.to, 'Asia/Singapore').getTime();
    if (!Number.isFinite(from) || !Number.isFinite(to) || from >= to) {
      context.addIssue({ code: 'custom', path: ['to'], message: 'Choose an end after the start.' });
    }
  });
export const singaporeInstant = (value: string) =>
  fromZonedTime(value, 'Asia/Singapore').toISOString();
