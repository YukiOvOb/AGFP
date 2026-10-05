import type { components } from '@/api/schema';
type ReservationStatus = components['schemas']['ReservationView']['status'];
type EquipmentStatus = components['schemas']['EquipmentView']['status'];
type Tone = 'ok' | 'pending' | 'bad' | 'muted' | 'info';
/** The one place where status names and colours are defined (Proposal 7.4). */
export const reservationStatus: Record<ReservationStatus, { label: string; tone: Tone }> = {
  PENDING_APPROVAL: { label: 'Waiting for approval', tone: 'pending' },
  CONFIRMED: { label: 'Confirmed', tone: 'ok' },
  REJECTED: { label: 'Rejected', tone: 'bad' },
  CANCELLED: { label: 'Cancelled', tone: 'muted' },
  FULFILLED: { label: 'Checked out', tone: 'info' },
};
export const equipmentStatus: Record<EquipmentStatus, { label: string; tone: Tone }> = {
  AVAILABLE: { label: 'Available', tone: 'ok' },
  ON_LOAN: { label: 'On loan', tone: 'info' },
  UNDER_MAINTENANCE: { label: 'Under maintenance', tone: 'bad' },
  RETIRED: { label: 'Retired', tone: 'muted' },
};
export const slotReason: Record<string, string> = {
  PENDING_APPROVAL: 'Requested (awaiting approval)',
  RESERVED: 'Reserved',
  ON_LOAN: 'On loan',
};
export function toneClass(tone: Tone) {
  return `badge badge-${tone}`;
}
