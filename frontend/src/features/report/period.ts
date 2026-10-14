import { formatInTimeZone, fromZonedTime } from 'date-fns-tz';
import { ZONE } from '@/lib/time';
/** Days counted back from the start of tomorrow in Singapore, so "today" is always included. */
export const presets = [
  { days: 7, label: 'Last 7 days' },
  { days: 30, label: 'Last 30 days' },
  { days: 90, label: 'Last 90 days' },
  { days: 365, label: 'Last 12 months' },
] as const;
export const DEFAULT_DAYS = 30;
const DAY = 24 * 3600 * 1000;
/** Whole Singapore days: from the start of the first to the start of the day after the last. */
export type Period = { from: string; to: string };
export function startOfDaySgt(date: string) {
  return fromZonedTime(`${date}T00:00:00`, ZONE).toISOString();
}
export function sgtDate(iso: string) {
  return formatInTimeZone(iso, ZONE, 'yyyy-MM-dd');
}
export function lastDays(days: number, now = new Date()): Period {
  const tomorrow = startOfDaySgt(
    formatInTimeZone(new Date(now.getTime() + DAY), ZONE, 'yyyy-MM-dd'),
  );
  return { from: new Date(Date.parse(tomorrow) - days * DAY).toISOString(), to: tomorrow };
}
/** `first` and `last` are `yyyy-MM-dd` dates, both included. Null when not a usable range. */
export function customPeriod(first: string, last: string): Period | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(first) || !/^\d{4}-\d{2}-\d{2}$/.test(last)) return null;
  const from = startOfDaySgt(first);
  const to = new Date(Date.parse(startOfDaySgt(last)) + DAY).toISOString();
  if (Number.isNaN(Date.parse(from)) || Date.parse(from) >= Date.parse(to)) return null;
  if (Date.parse(to) - Date.parse(from) > 366 * DAY) return null;
  return { from, to };
}
/** Which preset a period matches, if any, for the selected state of the buttons. */
export function presetOf(period: Period, now = new Date()) {
  return presets.find((p) => {
    const candidate = lastDays(p.days, now);
    return candidate.from === period.from && candidate.to === period.to;
  })?.days;
}
const percent = new Intl.NumberFormat('en-SG', { style: 'percent', maximumFractionDigits: 0 });
const decimal = new Intl.NumberFormat('en-SG', { maximumFractionDigits: 1 });
const whole = new Intl.NumberFormat('en-SG');
export const formatPercent = (ratio: number) => percent.format(ratio);
export const formatCount = (value: number) => whole.format(value);
/** Hours under two days, otherwise days. */
export function formatHours(hours: number) {
  return hours < 48 ? `${decimal.format(hours)} h` : `${decimal.format(hours / 24)} d`;
}
