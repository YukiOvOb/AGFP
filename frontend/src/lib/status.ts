import type { components } from '@/api/schema';
type ReservationStatus = components['schemas']['ReservationView']['status'];
type EquipmentStatus = components['schemas']['EquipmentView']['status'];
type MaintenanceStatus = components['schemas']['MaintenanceCaseResponse']['status'];
export type Tone = 'ok' | 'pending' | 'bad' | 'muted' | 'info';
export type StatusDisplay = { label: string; tone: Tone };
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
/** Loan codes are plain strings in the API; an active loan past its due time is shown as overdue. */
export const loanStatus: Record<'ACTIVE' | 'RETURNED' | 'OVERDUE', StatusDisplay> = {
  ACTIVE: { label: 'On loan', tone: 'info' },
  RETURNED: { label: 'Returned', tone: 'muted' },
  OVERDUE: { label: 'Overdue', tone: 'bad' },
};
export function loanDisplay(status: string | undefined, overdue?: boolean): StatusDisplay {
  if (status === 'ACTIVE' && overdue) return loanStatus.OVERDUE;
  return status === 'ACTIVE' || status === 'RETURNED'
    ? loanStatus[status]
    : { label: status ?? 'Unknown', tone: 'muted' };
}
/** Labels follow Proposal 5.6; the five stored states are unchanged (decision D-008). */
export const maintenanceStatus: Record<MaintenanceStatus, StatusDisplay> = {
  OPEN: { label: 'Reported', tone: 'pending' },
  ASSIGNED: { label: 'Assigned', tone: 'info' },
  IN_PROGRESS: { label: 'In progress', tone: 'info' },
  RESOLVED: { label: 'Resolved', tone: 'ok' },
  UNREPAIRABLE: { label: 'Not repairable', tone: 'bad' },
};
export const slotReason: Record<string, string> = {
  PENDING_APPROVAL: 'Requested (awaiting approval)',
  RESERVED: 'Reserved',
  ON_LOAN: 'On loan',
};
export function toneClass(tone: Tone) {
  return `badge badge-${tone}`;
}
