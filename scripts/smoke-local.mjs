import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const runtime = path.join(root, '.cache/local-demo');
const state = JSON.parse(fs.readFileSync(path.join(runtime, 'state.json'), 'utf8').replace(/^\uFEFF/, ''));
const access = JSON.parse(fs.readFileSync(path.join(runtime, 'access.json'), 'utf8').replace(/^\uFEFF/, ''));
assert.equal(path.resolve(state.repo), root, 'Only this checkout\'s explicit local demo may be tested');
const base = `http://127.0.0.1:${state.webPort}`;
const checks = [];
const wait = ms => new Promise(resolve => setTimeout(resolve, ms));
function check(name, fn) { fn(); checks.push(name); console.log(`PASS ${name}`); }
class Client {
  cookies = new Map();
  async request(method, route, body, expected = 200, csrf = true) {
    const headers = { Accept: 'application/json', Cookie: [...this.cookies].map(([k,v]) => `${k}=${v}`).join('; ') };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (!['GET','HEAD'].includes(method) && csrf && this.cookies.has('XSRF-TOKEN')) headers['X-XSRF-TOKEN'] = decodeURIComponent(this.cookies.get('XSRF-TOKEN'));
    const response = await fetch(base + route, { method, headers, body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(10000) });
    for (const item of response.headers.getSetCookie()) {
      const pair = item.split(';')[0]; const split = pair.indexOf('=');
      const name = pair.slice(0, split), value = pair.slice(split + 1);
      if (/max-age=0/i.test(item) || !value) this.cookies.delete(name); else this.cookies.set(name, value);
    }
    const text = await response.text();
    let value; try { value = text ? JSON.parse(text) : undefined; } catch { value = text; }
    assert.equal(response.status, expected, `${method} ${route}: ${response.status} ${typeof value === 'object' ? JSON.stringify(value) : value}`);
    return value;
  }
  async login(email, role) {
    await this.request('GET', '/api/v1/auth/csrf', undefined, 204);
    const user = await this.request('POST', '/api/v1/auth/login', {email, password: access.demoPassword});
    assert.ok(user.roles.includes(role), `Expected ${role}`);
    await this.request('GET', '/api/v1/auth/csrf', undefined, 204);
    check(`real PostgreSQL login: ${role} (${email})`, () => assert.equal(user.email, email));
    return user;
  }
}
const result = { startedAt: new Date().toISOString(), baseURL: base, checks, status: 'running', inputs: ['scripts/start-local.ps1','scripts/stop-local.ps1','scripts/demo-data.sql','scripts/smoke-local.mjs','frontend/vite.config.ts'], remaining: ['Overdue timer/concurrency/rollback regression suites','Independent team-member acceptance','Remote CI','Cloud publication'] };
try {
  const anonymous = new Client();
  const health = await anonymous.request('GET', '/api/v1/health');
  check('real backend health through React proxy', () => assert.equal(health.status, 'UP'));
  for (const route of ['/', '/equipment', '/reservations', '/approvals', '/loans', '/maintenance']) {
    const response = await fetch(base + route, {signal: AbortSignal.timeout(10000)});
    check(`React direct route ${route}`, () => { assert.equal(response.status, 200); assert.match(response.headers.get('content-type'), /text\/html/); });
  }
  await anonymous.request('GET', '/api/v1/equipment', undefined, 401);
  check('anonymous requests are rejected', () => {});
  const borrower = new Client(), borrower2 = new Client(), approver = new Client(), custodian = new Client(), maintainer = new Client(), admin = new Client();
  const borrowerUser = await borrower.login('borrower@example.test', 'BORROWER');
  await borrower2.login('borrower2@example.test', 'BORROWER');
  await approver.login('approver@example.test', 'APPROVER');
  await custodian.login('custodian@example.test', 'CUSTODIAN');
  const technician = await maintainer.login('maintainer@example.test', 'MAINTAINER');
  await admin.login('admin@example.test', 'ADMIN');
  await borrower.request('GET', '/api/v1/approvals/pending', undefined, 403);
  await admin.request('GET', '/api/v1/approvals/pending', undefined, 403);
  check('borrower/admin cannot act as approver', () => {});
  const startAt = new Date(Date.now() + 4000).toISOString(), endAt = new Date(Date.now() + 3604000).toISOString();
  const listing = await borrower.request('GET', '/api/v1/equipment?' + new URLSearchParams({from:startAt,to:endAt,availableOnly:'true',size:'20'}));
  const equipment = listing.content.find(x => x.requiresApproval && x.assetTag.startsWith('DEMO-'));
  assert.ok(equipment, 'No free approval-required demo equipment; do not delete existing booking history to rerun a smoke test');
  result.equipmentName = equipment.name;
  check('real equipment search and interval availability', () => assert.equal(equipment.status, 'AVAILABLE'));
  const request = { equipmentId: equipment.equipmentId, startAt, endAt, purpose: 'Local integration smoke: reservation, approval and physical return' };
  await borrower.request('POST', '/api/v1/reservations', request, 403, false);
  check('write without CSRF header is rejected', () => {});
  const reservation = await borrower.request('POST', '/api/v1/reservations', request, 201);
  result.reservationId = reservation.reservationId;
  check('UC01 submits real pending reservation', () => assert.equal(reservation.status, 'PENDING_APPROVAL'));
  await borrower2.request('POST', '/api/v1/reservations', request, 409);
  check('overlapping reservation rejected on PostgreSQL', () => {});
  const detail = await approver.request('GET', `/api/v1/approvals/${reservation.reservationId}`);
  const approved = await approver.request('POST', `/api/v1/approvals/${reservation.reservationId}/decision`, {decision:'APPROVED',comment:'Local smoke approval',expectedVersion:detail.version});
  check('UC02 separate approver confirms reservation', () => assert.equal(approved.reservation.status, 'CONFIRMED'));
  await wait(Math.max(0, Date.parse(startAt) - Date.now() + 150));
  const checkout = {reservationId:reservation.reservationId,borrowerId:borrowerUser.userId,expectedVersion:approved.reservation.version,identityConfirmed:true};
  await borrower.request('POST', '/api/v1/loans', checkout, 403);
  const loan = await custodian.request('POST', '/api/v1/loans', checkout, 201);
  result.loanId = loan.loanId;
  check('UC03 custodian performs physical checkout', () => assert.equal(loan.status, 'ACTIVE'));
  const returned = await custodian.request('POST', `/api/v1/loans/${loan.loanId}/return`, {expectedVersion:loan.version,condition:'GOOD',note:'Local smoke physical return'});
  check('UC03 return persists', () => assert.equal(returned.status, 'RETURNED'));
  const faultEquipment = '20000000-0000-0000-0000-000000000006';
  const item = await borrower.request('POST', '/api/v1/maintenance/cases', {equipmentId:faultEquipment,faultDescription:'Local smoke: intermittent display on the multimeter',loanId:null}, 201);
  const caseId = item.maintenanceCaseId; result.maintenanceCaseId = caseId;
  check('UC04 fault creates real case', () => assert.equal(item.status, 'OPEN'));
  await custodian.request('POST', `/api/v1/maintenance/cases/${caseId}/assignment`, {technicianId:technician.userId});
  await borrower.request('POST', `/api/v1/maintenance/cases/${caseId}/start`, undefined, 403);
  const working = await maintainer.request('POST', `/api/v1/maintenance/cases/${caseId}/start`);
  check('UC04 assigned technician starts work', () => assert.equal(working.status, 'IN_PROGRESS'));
  await maintainer.request('POST', `/api/v1/maintenance/cases/${caseId}/diagnosis`, {detail:'Display connector is loose.'});
  await maintainer.request('POST', `/api/v1/maintenance/cases/${caseId}/repair-actions`, {detail:'Secured connector and checked measurement accuracy.'});
  const resolved = await maintainer.request('POST', `/api/v1/maintenance/cases/${caseId}/resolve`, {resolutionNote:'Connector secured; function and safety checks passed.'});
  check('UC04 repair resolves with persisted history', () => assert.equal(resolved.status, 'RESOLVED'));
  const items = await borrower.request('GET', '/api/v1/equipment?size=20');
  check('returned/repaired equipment availability recomputed', () => {
    assert.equal(items.content.find(x=>x.equipmentId===equipment.equipmentId).status, 'AVAILABLE');
    assert.equal(items.content.find(x=>x.equipmentId===faultEquipment).status, 'AVAILABLE');
  });
  let inbox;
  for (let i=0; i<80; i++) {
    inbox = await borrower.request('GET', '/api/v1/notifications');
    if (inbox.totalElements > 0) break;
    await wait(500);
  }
  check('UC05 real borrower notifications delivered', () => assert.ok(inbox.totalElements > 0));
  await borrower.request('POST', '/api/v1/auth/logout', undefined, 204);
  await borrower.request('GET', '/api/v1/auth/me', undefined, 401);
  check('logout invalidates session', () => {});
  result.status = 'passed';
} catch (error) { result.status = 'failed'; result.failure = error.message; throw error; }
finally { result.finishedAt = new Date().toISOString(); fs.writeFileSync(path.join(runtime,'smoke-summary.json'), JSON.stringify(result,null,2)); console.log(`Evidence: ${path.join(runtime,'smoke-summary.json')}`); }
