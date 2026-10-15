/**
 * Role codes come from the API and never change here. Labels follow the Proposal's wording
 * (chapter 3): the stored CUSTODIAN and MAINTAINER codes are shown as Storekeeper and Technician.
 */
export const ROLES = ['BORROWER', 'APPROVER', 'CUSTODIAN', 'MAINTAINER', 'ADMIN'] as const;
export type Role = (typeof ROLES)[number];
const labels: Record<Role, string> = {
  BORROWER: 'Borrower',
  APPROVER: 'Approver',
  CUSTODIAN: 'Storekeeper',
  MAINTAINER: 'Technician',
  ADMIN: 'Administrator',
};
/** Display name for one role code; an unknown code is shown as received. */
export function roleLabel(code: string) {
  return labels[code as Role] ?? code;
}
/** Display names in a fixed order, so the same account always reads the same way. */
export function roleLabels(codes: readonly string[] | undefined) {
  if (!codes?.length) return [];
  const known = ROLES.filter((role) => codes.includes(role)).map(roleLabel);
  const unknown = codes.filter((code) => !(ROLES as readonly string[]).includes(code));
  return [...known, ...unknown];
}
/** Menus and buttons only; the backend performs the real authorization check. */
export const hasAnyRole = (roles: readonly string[] | undefined, ...allowed: string[]) =>
  allowed.some((role) => roles?.includes(role));
