import { formatInTimeZone, fromZonedTime } from 'date-fns-tz';
/** All times are stored and sent as UTC instants and shown in Singapore time (tech-stack §3.2). */
export const ZONE = 'Asia/Singapore';
const LOCAL_INPUT = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/;
/** `<input type="datetime-local">` value (Singapore wall time) → ISO-8601 UTC, or null. */
export function localToIso(value: string | null | undefined): string | null {
  if (!value || !LOCAL_INPUT.test(value)) return null;
  const date = fromZonedTime(value, ZONE);
  return Number.isNaN(date.getTime()) ? null : date.toISOString().replace('.000Z', 'Z');
}
/** ISO-8601 instant → Singapore wall time for a `datetime-local` input. */
export function isoToLocal(iso: string): string {
  return formatInTimeZone(iso, ZONE, "yyyy-MM-dd'T'HH:mm");
}
export function formatDateTime(iso: string) {
  return formatInTimeZone(iso, ZONE, 'd MMM yyyy, HH:mm');
}
/** "10 Oct 2026, 09:00 – 11:00" on one day, otherwise both dates. */
export function formatRange(startIso: string, endIso: string) {
  const sameDay =
    formatInTimeZone(startIso, ZONE, 'yyyy-MM-dd') === formatInTimeZone(endIso, ZONE, 'yyyy-MM-dd');
  return sameDay
    ? `${formatDateTime(startIso)} – ${formatInTimeZone(endIso, ZONE, 'HH:mm')}`
    : `${formatDateTime(startIso)} – ${formatDateTime(endIso)}`;
}
