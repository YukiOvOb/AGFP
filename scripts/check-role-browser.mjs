import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { createRequire } from 'node:module';
import { repo, runtime, baseURL, demoPassword, state } from './lib/local-demo-client.mjs';
import { accounts, pages, unavailablePages, menuOrder, reads, resolveRoute, deniedWrites } from './lib/role-access.mjs';

// Real browser sessions and same-origin requests only. No interception, fake time or role changes.
assert.equal(state.data, path.join(runtime, 'pg18'), 'Use only this checkout’s local-demo cluster');
const require = createRequire(path.join(repo, 'frontend/package.json'));
const { chromium, expect } = require('@playwright/test');
const evidence = path.join(runtime, 'role-browser-' + Date.now());
fs.mkdirSync(evidence, { recursive: true });
const publicDir = path.join(repo, 'docs/evidence/role-access-admin-display-name-20261015');
const publish = process.argv.includes('--publishable-evidence');
if (publish) fs.mkdirSync(publicDir, { recursive: true });
const inputs = ['scripts/check-role-browser.mjs', 'scripts/lib/role-access.mjs',
  'scripts/lib/local-demo-client.mjs', 'scripts/lib/uc02-browser-guard.sql',
  'scripts/role-browser-fixture.sql', 'scripts/role-browser-snapshot.sql',
  'frontend/src/app/App.tsx', 'frontend/src/app/RequireRole.tsx', 'frontend/src/app/navigation.ts',
  'frontend/src/styles/shared.css',
  'frontend/src/features/overdue/OverduePage.tsx',
  'app/src/main/java/sg/edu/nus/serms/notification/api/OverdueController.java',
  'app/src/main/java/sg/edu/nus/serms/notification/service/OverdueLoanService.java',
  'frontend/src/features/admin/UsersPage.tsx', 'frontend/src/features/admin/AuditPage.tsx',
  'frontend/src/features/admin/EditDisplayNameDialog.tsx',
  'app/src/main/java/sg/edu/nus/serms/identity/service/AdminUserService.java',
  'app/src/main/java/sg/edu/nus/serms/identity/api/AdminUserController.java',
  'app/src/main/java/sg/edu/nus/serms/identity/api/AdminUserProblemHandler.java',
  'app/src/main/java/sg/edu/nus/serms/identity/repository/AdminUserRepository.java',
  'app/src/main/java/sg/edu/nus/serms/identity/service/AdminAccess.java',
  'app/src/main/java/sg/edu/nus/serms/audit/service/AuditQueryService.java'];
const summary = {
  status: 'running', mocked: false, startedAt: new Date().toISOString(), checks: [],
  pages: [], apiReads: [], deniedWrites: [], ownership: [], overdueChecks: [],
  unavailable: unavailablePages.map(x => ({ ...x, status: 'not implemented at tested baseline' })),
  sourceHashes: inputs.map(file => ({ path: file,
    sha256: createHash('sha256').update(fs.readFileSync(path.join(repo, file))).digest('hex') })),
  startingHead: execFileSync('git', ['rev-parse', 'HEAD'], { cwd: repo, encoding: 'utf8' }).trim(),
  fetchedMain: execFileSync('git', ['rev-parse', 'origin/main'], { cwd: repo, encoding: 'utf8' }).trim(),
};
const pass = name => { summary.checks.push(name); console.log('PASS ' + name); };
function sql(file) {
  const access = JSON.parse(fs.readFileSync(path.join(runtime, 'access.json'), 'utf8').replace(/^\uFEFF/, ''));
  return JSON.parse(execFileSync(path.join(state.postgresBin, process.platform === 'win32' ? 'psql.exe' : 'psql'),
    ['-h', '127.0.0.1', '-p', String(state.databasePort), '-U', 'serms_local', '-d', 'serms_local_demo',
      '-w', '-qAt', '-v', 'ON_ERROR_STOP=1', '-f', path.join(repo, 'scripts', file)],
    { env: { ...process.env, PGPASSWORD: access.databasePassword }, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim());
}
// Fail when new routes appear rather than silently claiming that an old matrix covers them.
const app = fs.readFileSync(path.join(repo, 'frontend/src/app/App.tsx'), 'utf8');
const routes = [...app.matchAll(/<Route\s+path="([^"]+)"/g)].map(m => m[1]);
assert.deepEqual(routes.sort(), [...pages.map(p => p.route), '/login', '/dev/components', '*'].sort(),
  'Route inventory changed: review permissions and extend the matrix explicitly');
const fixture = sql('role-browser-fixture.sql');
assert.ok(fixture?.reservationId && fixture.loanId && fixture.caseId && fixture.notificationId,
  'Run check-uc02-browser.mjs first; completed real handover/repair/notification history is required');
const before = sql('role-browser-snapshot.sql');
const period = new URLSearchParams({ from: new Date(Date.now() - 86400000).toISOString(), to: new Date().toISOString() }).toString();
const errors = [];
const browser = await chromium.launch({ headless: true });
async function request(page, route, method = 'GET', body, csrf = true) {
  return page.evaluate(async ({ route, method, body, csrf }) => {
    const headers = { Accept: 'application/json' };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (csrf && method !== 'GET') {
      const cookie = document.cookie.split(';').map(s => s.trim()).find(s => s.startsWith('XSRF-TOKEN='));
      if (!cookie) throw new Error('The actual server CSRF cookie is required');
      headers['X-XSRF-TOKEN'] = decodeURIComponent(cookie.slice('XSRF-TOKEN='.length));
    }
    const response = await fetch(route, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
    const text = await response.text();
    return { status: response.status, body: text ? JSON.parse(text) : null, type: response.headers.get('content-type') };
  }, { route, method, body, csrf });
}
function problem(response, status, type, label) {
  assert.equal(response.status, status, label);
  assert.equal(response.body.type, '/problems/' + type, label);
  assert.equal(response.body.status, status, label);
  assert.match(response.type, /application\/problem\+json/);
}
async function fit(page) {
  const width = await page.evaluate(() => ({ viewport: innerWidth, document: document.documentElement.scrollWidth }));
  assert.ok(width.document <= width.viewport + 2, 'No document overflow: ' + JSON.stringify(width));
}
async function screenshot(page, name) {
  await page.screenshot({ path: path.join(evidence, name), fullPage: true });
  if (publish) await page.screenshot({ path: path.join(publicDir, name), fullPage: true });
}
try {
  pass('route inventory covers every implemented application page; future pages explicitly excluded');
  for (const [device, viewport] of [['desktop', { width: 1440, height: 1000 }], ['mobile', { width: 390, height: 844 }]]) {
    for (const account of ['anonymous', ...Object.keys(accounts)]) {
      const context = await browser.newContext({ viewport });
      const page = await context.newPage();
      page.setDefaultTimeout(15000);
      page.on('pageerror', e => errors.push(e.message));
      let tracing = false;
      try {
        await page.goto(baseURL + '/login');
        const role = accounts[account];
        if (role) {
          await page.getByLabel('Email address').fill(account + '@example.test');
          await page.getByLabel('Password', { exact: true }).fill(demoPassword);
          await page.getByRole('button', { name: 'Sign in', exact: true }).click();
          await expect(page.getByRole('heading', { level: 1, name: /^Welcome/ })).toBeVisible();
          const me = await request(page, '/api/v1/auth/me');
          assert.equal(me.status, 200);
          assert.deepEqual(me.body.roles, [role], 'Use actual single-role accounts, not elevated fixtures');
          // Start after login. Raw traces can still contain session/CSRF tokens and stay ignored.
          await context.tracing.start({ screenshots: true, snapshots: true });
          tracing = true;
          const expectedMenu = menuOrder.filter(route => pages.find(p => p.route === route).roles.includes(role));
          const menu = await page.locator('.sidebar nav a').evaluateAll(links => links.map(a => a.getAttribute('href')));
          assert.deepEqual(menu, expectedMenu, device + ' ' + account + ' exact menu');
          await fit(page);
          pass(device + ' ' + account + ': actual login and exact role menu');
        }
        for (const spec of reads) {
          const route = resolveRoute(spec.route, fixture, period);
          const expected = !role ? 401 : !spec.roles.includes(role) ? 403 :
            account === 'borrower2' && ['reservation detail', 'loan detail'].includes(spec.id) ? 404 : 200;
          const result = await request(page, route);
          assert.equal(result.status, expected, device + ' ' + account + ' ' + spec.id);
          if (expected === 401 || expected === 403) problem(result, expected, expected === 401 ? 'unauthenticated' : 'forbidden', spec.id);
          summary.apiReads.push({ device, account, endpoint: spec.id, expected, actual: result.status });
        }
        pass(device + ' ' + account + ': all ' + reads.length + ' real read APIs match permissions/ownership');
        for (const spec of pages) {
          await page.goto(baseURL + resolveRoute(spec.route, fixture, period));
          const allowed = role && spec.roles.includes(role);
          if (!role) {
            await expect(page).toHaveURL(baseURL + '/login');
            await expect(page.getByRole('button', { name: 'Sign in', exact: true })).toBeVisible();
          } else if (!allowed) {
            await expect(page.getByRole('alert')).toContainText(/Your account does not have access/);
            await expect(page.locator('main h1')).toHaveCount(0);
          } else {
            await expect(page.getByRole('heading', { level: 1,
              name: spec.fixtureHeading ? fixture[spec.fixtureHeading] : new RegExp(spec.title) })).toBeVisible();
            await expect(page.locator('main [role="alert"]')).toHaveCount(0);
            if (spec.route === '/overdue') {
              const response = await request(page, '/api/v1/overdue');
              assert.equal(response.status, 200);
              if (response.body.totalElements === 0)
                await expect(page.getByRole('heading', { name: 'No overdue loans', exact: true })).toBeVisible();
              const sorted = page.waitForResponse(r => new URL(r.url()).pathname === '/api/v1/overdue' && new URL(r.url()).searchParams.get('order') === 'asc');
              await page.getByLabel('Sort by overdue duration').selectOption('asc');
              const sortedStatus = (await sorted).status();
              assert.equal(sortedStatus, 200);
              summary.overdueChecks.push({ device, account, totalElements: response.body.totalElements,
                emptyState: response.body.totalElements === 0, sortStatus: sortedStatus });
              await expect(page.locator('main [role="alert"]')).toHaveCount(0);
              await screenshot(page, device + '-' + account + '-overdue.png');
            }
          }
          await fit(page);
          summary.pages.push({ device, account, route: spec.route, result: !role ? 'login redirect' : allowed ? 'allowed' : 'denied' });
          if (account === 'admin' && spec.route === '/approvals') await screenshot(page, device + '-admin-no-implicit-approval.png');
          if (account === 'maintainer' && spec.route === '/maintenance/cases/:caseId') await screenshot(page, device + '-assigned-maintenance.png');
        }
        pass(device + ' ' + account + ': all ' + pages.length + ' direct page routes, deny states and viewport fit');
        if (!role) continue;
        await page.goto(baseURL + '/');
        // Valid CSRF and syntactically valid bodies: a 403 must mean role denial, not bad input/token.
        for (const spec of deniedWrites(fixture).filter(x => !x.roles.includes(role))) {
          const result = await request(page, spec.route, spec.method ?? 'POST', spec.body);
          problem(result, 403, 'forbidden', account + ' ' + spec.id);
          summary.deniedWrites.push({ device, account, endpoint: spec.id, actual: result.status });
        }
        if (account === 'borrower2') {
          for (const [id, route, body] of [
            ['other reservation cancel', '/api/v1/reservations/' + fixture.reservationId + '/cancel'],
            ['other notification read', '/api/v1/notifications/' + fixture.notificationId + '/read'],
          ]) {
            const result = await request(page, route, 'POST', body);
            assert.equal(result.status, 404, id + ' must not reveal ownership');
            summary.ownership.push({ device, endpoint: id, actual: result.status });
          }
        }
        const csrfDenied = await request(page, '/api/v1/auth/logout', 'POST', undefined, false);
        problem(csrfDenied, 403, 'csrf', 'Missing CSRF must not log out');
        assert.equal((await request(page, '/api/v1/auth/me')).status, 200);
        pass(device + ' ' + account + ': valid-CSRF role-denied mutations and missing-CSRF protection');
        await page.getByRole('button', { name: 'Sign out', exact: true }).click();
        await expect(page.getByRole('button', { name: 'Sign in', exact: true })).toBeVisible();
        problem(await request(page, '/api/v1/auth/me'), 401, 'unauthenticated', 'Logged-out session');
        pass(device + ' ' + account + ': actual logout invalidates browser session');
      } finally {
        if (tracing) await context.tracing.stop({ path: path.join(evidence, device + '-' + account + '-private-trace.zip') });
        await context.close();
      }
    }
  }
  assert.deepEqual(errors, [], 'No uncaught browser errors');
  assert.deepEqual(sql('role-browser-snapshot.sql'), before, 'Read/denied probes must leave business data, audit, notifications and roles unchanged');
  pass('all business/audit/notification-read/role snapshots unchanged');
  summary.status = 'passed';
} catch (error) {
  summary.status = 'failed';
  summary.failure = error.message;
  throw error;
} finally {
  summary.finishedAt = new Date().toISOString();
  fs.writeFileSync(path.join(evidence, 'verification.json'), JSON.stringify(summary, null, 2) + '\n');
  if (publish && summary.status === 'passed') fs.writeFileSync(path.join(publicDir, 'verification.json'), JSON.stringify(summary, null, 2) + '\n');
  await browser.close();
}
