import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { accounts, pages, unavailablePages, reads, deniedWrites, resolveRoute, menuOrder } from './lib/role-access.mjs';

test('the explicit matrix covers every current application route', () => {
  const app = fs.readFileSync(new URL('../frontend/src/app/App.tsx', import.meta.url), 'utf8');
  const actual = [...app.matchAll(/<Route\s+path="([^"]+)"/g)].map(m => m[1]);
  assert.deepEqual(actual.sort(), [...pages.map(p => p.route), '/login', '/dev/components', '*'].sort());
});
test('all page/API expectations use real role names and no duplicate paths', () => {
  for (const specs of [pages, reads]) {
    assert.equal(new Set(specs.map(s => s.route)).size, specs.length);
    for (const spec of specs) {
      assert.ok(spec.roles.length > 0);
      assert.ok(spec.roles.every(role => Object.values(accounts).includes(role)));
    }
  }
  assert.deepEqual(menuOrder.filter(route => !pages.some(p => p.route === route && p.menu)), []);
});
test('ADMIN never implicitly approves, reserves or checks out', () => {
  for (const route of ['/approvals', '/reservations', '/loans'])
    assert.ok(!pages.find(p => p.route === route).roles.includes('ADMIN'));
});
test('overdue is implemented for operators only, not a future or implicitly inherited page', () => {
  assert.deepEqual(pages.find(p => p.route === '/overdue').roles, ['CUSTODIAN', 'ADMIN']);
  assert.deepEqual(reads.find(p => p.id === 'overdue projection').roles, ['CUSTODIAN', 'ADMIN']);
  assert.ok(!unavailablePages.some(p => p.route === '/overdue'));
});

test('equipment management is implemented for custodians and admins only', () => {
  assert.deepEqual(pages.find(p => p.route === '/manage/equipment').roles, ['CUSTODIAN', 'ADMIN']);
  assert.deepEqual(reads.find(p => p.id === 'managed equipment').roles, ['CUSTODIAN', 'ADMIN']);
  assert.deepEqual(reads.find(p => p.id === 'equipment categories').roles, ['CUSTODIAN', 'ADMIN']);
  assert.ok(!unavailablePages.some(p => p.route === '/manage/equipment'));
});
test('all dynamic API/page paths resolve with explicit real-fixture inputs', () => {
  const fixture = { equipmentId: 'equipment', reservationId: 'reservation', loanId: 'loan', caseId: 'case' };
  for (const spec of [...pages, ...reads]) assert.ok(!resolveRoute(spec.route, fixture, 'from=a&to=b').includes(':'));
  assert.throws(() => resolveRoute('/api/:loanId', {}, ''), /Missing real fixture/);
});
test('denied-write matrix includes every existing business mutation family', () => {
  const specs = deniedWrites({ equipmentId: 'e', reservationId: 'r', borrowerId: 'b', loanId: 'l', caseId: 'c', reservationVersion: 1, loanVersion: 1 });
  assert.equal(specs.length, 19);
  assert.equal(new Set(specs.map(s => s.route)).size, 19);
  assert.equal(specs.find(s => s.id === 'admin display name update').method, 'PATCH');
  for (const spec of specs) assert.ok(Object.values(accounts).some(role => !spec.roles.includes(role)));
  assert.equal(specs.find(s => s.id === 'approval decision').body.decision, 'REJECTED');
});
test('admin pages and APIs are explicit ADMIN-only entries; unimplemented dependencies stay visible', () => {
  for (const route of ['/admin/users', '/admin/audit']) {
    assert.deepEqual(pages.find(p => p.route === route).roles, ['ADMIN']);
    assert.deepEqual(reads.find(p => p.route === '/api/v1' + route).roles, ['ADMIN']);
  }
  assert.ok(!unavailablePages.some(p => p.issue === 18));
  for (const pending of unavailablePages) assert.ok(!pages.some(p => p.route === pending.route));
});
