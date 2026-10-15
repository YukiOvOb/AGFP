// Explicit expected permissions, reviewed against contracts and service policies.
// Never derive expected access from whichever menu the running UI happens to render.
export const accounts = {
  borrower: 'BORROWER', approver: 'APPROVER', custodian: 'CUSTODIAN',
  maintainer: 'MAINTAINER', admin: 'ADMIN', borrower2: 'BORROWER',
};
const all = Object.values(accounts).filter((r, i, values) => values.indexOf(r) === i);
export const pages = [
  { route: '/', title: '^Welcome', roles: all, menu: 'Overview' },
  { route: '/equipment', title: '^Find equipment', roles: all, menu: 'Find equipment' },
  { route: '/equipment/:equipmentId/reserve', fixtureHeading: 'equipmentName', roles: ['BORROWER'] },
  { route: '/reservations', title: '^My reservations', roles: ['BORROWER'], menu: 'My reservations' },
  { route: '/loans', title: '^(My loans|Checkout and returns)$', roles: ['BORROWER', 'CUSTODIAN'], menu: 'Loans and returns' },
  { route: '/approvals', title: '^Approvals', roles: ['APPROVER'], menu: 'Approvals' },
  { route: '/approvals/:reservationId', title: '^Request details$', roles: ['APPROVER'] },
  { route: '/notifications', title: '^Notifications', roles: all, menu: 'Notifications' },
  { route: '/maintenance', title: '^Maintenance$', roles: ['BORROWER', 'CUSTODIAN', 'MAINTAINER', 'ADMIN'], menu: 'Maintenance' },
  { route: '/maintenance/cases/:caseId', title: '^Case details$', roles: ['CUSTODIAN', 'MAINTAINER', 'ADMIN'] },
  { route: '/maintenance/report', title: '^Maintenance report$', roles: ['ADMIN'] },
  { route: '/reports', title: '^Reports$', roles: ['ADMIN'], menu: 'Reports' },
  { route: '/overdue', title: '^Overdue loans$', roles: ['CUSTODIAN', 'ADMIN'], menu: 'Overdue' },
  { route: '/manage/equipment', title: '^Manage equipment$', roles: ['CUSTODIAN', 'ADMIN'], menu: 'Manage equipment' },
  { route: '/admin/users', title: '^Users and roles$', roles: ['ADMIN'], menu: 'Users and roles' },
  { route: '/admin/audit', title: '^Audit log$', roles: ['ADMIN'], menu: 'Audit log' },
];
export const unavailablePages = [];
export const menuOrder = ['/', '/equipment', '/loans', '/reservations', '/maintenance', '/approvals', '/notifications', '/overdue', '/manage/equipment', '/reports', '/admin/users', '/admin/audit'];
const read = (id, route, roles) => ({ id, route, roles });
export const reads = [
  read('identity', '/api/v1/auth/me', all),
  read('catalogue', '/api/v1/equipment', all),
  read('catalogue filters', '/api/v1/equipment/filters', all),
  read('availability', '/api/v1/equipment/:equipmentId/availability?:period', all),
  read('managed equipment', '/api/v1/equipment/manage', ['CUSTODIAN', 'ADMIN']),
  read('equipment categories', '/api/v1/equipment/manage/categories', ['CUSTODIAN', 'ADMIN']),
  read('own reservations', '/api/v1/reservations/mine', ['BORROWER']),
  read('reservation detail', '/api/v1/reservations/:reservationId', ['BORROWER']),
  read('pending approvals', '/api/v1/approvals/pending', ['APPROVER']),
  read('approval detail', '/api/v1/approvals/:reservationId', ['APPROVER']),
  read('loan list', '/api/v1/loans?activeOnly=false', ['BORROWER', 'CUSTODIAN']),
  read('loan detail', '/api/v1/loans/:loanId', ['BORROWER', 'CUSTODIAN']),
  read('checkout queue', '/api/v1/loans/checkouts', ['CUSTODIAN']),
  read('notification inbox', '/api/v1/notifications', all),
  read('notification unread count', '/api/v1/notifications/unread-count', all),
  read('maintenance queue', '/api/v1/maintenance/cases/open', ['CUSTODIAN', 'ADMIN']),
  read('technician directory', '/api/v1/maintenance/technicians', ['CUSTODIAN', 'ADMIN']),
  read('assigned maintenance', '/api/v1/maintenance/cases/assigned', ['MAINTAINER']),
  read('maintenance detail', '/api/v1/maintenance/cases/:caseId', ['CUSTODIAN', 'MAINTAINER', 'ADMIN']),
  read('maintenance history', '/api/v1/maintenance/cases/:caseId/history', ['CUSTODIAN', 'MAINTAINER', 'ADMIN']),
  read('equipment cases', '/api/v1/maintenance/equipment/:equipmentId/cases', ['CUSTODIAN', 'ADMIN']),
  read('equipment maintenance history', '/api/v1/maintenance/equipment/:equipmentId/history', ['CUSTODIAN', 'ADMIN']),
  read('maintenance report', '/api/v1/maintenance/report?:period', ['ADMIN']),
  read('combined report', '/api/v1/reports?:period', ['ADMIN']),
  read('overdue projection', '/api/v1/overdue', ['CUSTODIAN', 'ADMIN']),
  read('admin user directory', '/api/v1/admin/users', ['ADMIN']),
  read('admin audit reader', '/api/v1/admin/audit', ['ADMIN']),
];
export function resolveRoute(route, fixture, period) {
  return route.replace(/:([A-Za-z]+)/g, (_, key) => {
    const value = key === 'period' ? period : fixture[key];
    if (!value) throw new Error('Missing real fixture field: ' + key);
    return value;
  });
}
export function deniedWrites(f) {
  return [
    { id: 'admin account creation', route: '/api/v1/admin/users', roles: ['ADMIN'],
      body: { email: 'rbac-denial-probe@example.test', displayName: 'Must not be created', initialPassword: 'browser-denial-test-only', roles: ['BORROWER'] } },
    { id: 'admin display name update', route: '/api/v1/admin/users/' + f.borrowerId + '/display-name', method: 'PATCH', roles: ['ADMIN'],
      body: { displayName: 'Must not be renamed', expectedDisplayName: 'Borrower' } },
    { id: 'admin role update', route: '/api/v1/admin/users/' + f.borrowerId + '/roles', method: 'PATCH', roles: ['ADMIN'],
      body: { roles: ['APPROVER'], expectedVersion: 0 } },
    { id: 'admin status update', route: '/api/v1/admin/users/' + f.borrowerId + '/status', method: 'PATCH', roles: ['ADMIN'],
      body: { active: false, expectedVersion: 0 } },
    { id: 'admin password reset', route: '/api/v1/admin/users/' + f.borrowerId + '/password-reset', roles: ['ADMIN'],
      body: { newPassword: 'denial-probe-test-only', expectedVersion: 0 } },
    { id: 'reservation submission', route: '/api/v1/reservations', roles: ['BORROWER'],
      body: { equipmentId: f.equipmentId, startAt: new Date(Date.now() + 86400000).toISOString(), endAt: new Date(Date.now() + 90000000).toISOString(), purpose: 'RBAC denial probe; must not persist' } },
    { id: 'reservation cancellation', route: '/api/v1/reservations/' + f.reservationId + '/cancel', roles: ['BORROWER'] },
    { id: 'approval decision', route: '/api/v1/approvals/' + f.reservationId + '/decision', roles: ['APPROVER'],
      body: { decision: 'REJECTED', comment: 'RBAC denial probe', expectedVersion: f.reservationVersion } },
    { id: 'checkout', route: '/api/v1/loans', roles: ['CUSTODIAN'],
      body: { reservationId: f.reservationId, borrowerId: f.borrowerId, expectedVersion: f.reservationVersion, identityConfirmed: true } },
    { id: 'return', route: '/api/v1/loans/' + f.loanId + '/return', roles: ['CUSTODIAN'],
      body: { expectedVersion: f.loanVersion, condition: 'GOOD', note: 'RBAC denial probe' } },
    { id: 'equipment registration', route: '/api/v1/equipment/manage', roles: ['CUSTODIAN', 'ADMIN'],
      body: { assetTag: 'RBAC-DENIED-' + f.equipmentId, name: 'RBAC denial probe', category: 'Photography', location: 'Store', condition: 'GOOD' } },
    { id: 'fault report', route: '/api/v1/maintenance/cases', roles: ['BORROWER', 'CUSTODIAN'],
      body: { equipmentId: f.equipmentId, faultDescription: 'RBAC denial probe; must not persist', faultCategory: 'OTHER', priority: 'MEDIUM' } },
    { id: 'technician assignment', route: '/api/v1/maintenance/cases/' + f.caseId + '/assignment', roles: ['CUSTODIAN', 'ADMIN'],
      body: { technicianId: '10000000-0000-0000-0000-000000000005' } },
    ...['start', 'diagnosis', 'repair-actions', 'notes', 'resolve', 'unrepairable'].map(action => ({
      id: 'maintenance ' + action, route: '/api/v1/maintenance/cases/' + f.caseId + '/' + action,
      roles: ['MAINTAINER'], body: action === 'start' ? undefined :
        ['resolve', 'unrepairable'].includes(action) ? { resolutionNote: 'RBAC denial probe' } : { detail: 'RBAC denial probe' },
    })),
  ];
}
