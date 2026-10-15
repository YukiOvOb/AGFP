import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { createRequire } from 'node:module';
import { repo, runtime, baseURL, demoPassword, state } from './lib/local-demo-client.mjs';

// Live browser acceptance only: no page.route(), fake clocks, mocked APIs or history rewrites.
assert.equal(state.data, path.join(runtime, 'pg18'), 'Use this checkout’s isolated demo cluster');
const require = createRequire(path.join(repo, 'frontend/package.json'));
const { chromium, expect } = require('@playwright/test');
const evidence = path.join(runtime, 'uc02-browser-' + Date.now());
fs.mkdirSync(evidence, { recursive: true });
const runId = randomUUID().replaceAll('-', '').slice(0, 10);
const summary = {
  status: 'running', mocked: false, runId, baseURL, checks: [], records: [],
  startedAt: new Date().toISOString(),
  remaining: ['Admin users/audit pages (#18)', 'Equipment management pages (#17)',
    'Remote CI and independent member/deployment acceptance'],
};
const pass = name => { summary.checks.push(name); console.log('PASS ' + name); };
const browser = await chromium.launch({ headless: true });
const errors = [];
let restoreApproverRole = false;
function fixtureSql(file, variables = {}) {
  const access = JSON.parse(fs.readFileSync(path.join(runtime, 'access.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.ok(Number.isInteger(state.databasePort) && state.databasePort > 0 && state.databasePort < 65536);
  return execFileSync(path.join(state.postgresBin, process.platform === 'win32' ? 'psql.exe' : 'psql'),
    ['-h', '127.0.0.1', '-p', String(state.databasePort), '-U', 'serms_local', '-d', 'serms_local_demo',
      '-w', '-qAt', '-v', 'ON_ERROR_STOP=1', ...Object.entries(variables).flatMap(([key, value]) => ['-v', key + '=' + value]),
      '-f', path.join(repo, 'scripts', file)],
    { env: { ...process.env, PGPASSWORD: access.databasePassword }, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim();
}
const roles = {
  borrower: ['Overview', 'Find equipment', 'Loans and returns', 'My reservations', 'Maintenance', 'Notifications'],
  approver: ['Overview', 'Find equipment', 'Approvals', 'Notifications'],
  custodian: ['Overview', 'Find equipment', 'Loans and returns', 'Maintenance', 'Notifications', 'Overdue'],
  maintainer: ['Overview', 'Find equipment', 'Maintenance', 'Notifications'],
  admin: ['Overview', 'Find equipment', 'Maintenance', 'Notifications', 'Overdue', 'Reports', 'Users and roles', 'Audit log'],
};
const localTime = ms => new Date(ms + 8 * 3600000).toISOString().slice(0, 16);
async function signIn(context, account) {
  const page = await context.newPage();
  page.setDefaultTimeout(15000);
  page.on('pageerror', error => errors.push(error.message));
  await page.goto(baseURL + '/login');
  await page.getByLabel('Email address').fill(account + '@example.test');
  await page.getByLabel('Password', { exact: true }).fill(demoPassword);
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page.getByRole('heading', { level: 1, name: /^Welcome/ })).toBeVisible();
  return page;
}
async function fit(page, name) {
  const width = await page.evaluate(() => ({ viewport: innerWidth, document: document.documentElement.scrollWidth }));
  assert.ok(width.document <= width.viewport + 2, name + ': ' + JSON.stringify(width));
}
async function cancelPriorFailedFixtures(page) {
  await page.request.get(baseURL + '/api/v1/auth/csrf');
  const token = (await page.context().cookies(baseURL)).find(x => x.name === 'XSRF-TOKEN');
  assert.ok(token, 'Real cancellation requires the server CSRF cookie');
  for (const status of ['PENDING_APPROVAL', 'CONFIRMED']) {
    const response = await page.request.get(baseURL + '/api/v1/reservations/mine?status=' + status + '&size=20');
    assert.equal(response.status(), 200);
    const own = await response.json();
    for (const row of own.content) {
      if (row.cancellable && /^(desktop|mobile) (approval-to-damaged-return|rejection|self-approval) [0-9]+$/.test(row.purpose ?? '')) {
        const cancelled = await page.request.post(baseURL + '/api/v1/reservations/' + row.reservationId + '/cancel',
          { headers: { 'X-XSRF-TOKEN': decodeURIComponent(token.value) } });
        assert.equal(cancelled.status(), 200, 'Cancel only this script’s own unissued fixture');
      }
    }
  }
}
async function book(page, item, start, purpose) {
  await page.getByRole('link', { name: 'Find equipment', exact: true }).first().click();
  await page.getByLabel('Name or asset tag').fill(item.assetTag);
  await page.getByRole('button', { name: 'Search', exact: true }).click();
  await page.getByRole('link', { name: 'Reserve ' + item.name, exact: true }).click();
  await page.getByLabel('Start (SGT)').fill(localTime(start));
  await page.getByLabel('End (SGT)').fill(localTime(start + 3600000));
  await page.getByLabel('Purpose (optional)').fill(purpose);
  const [saved] = await Promise.all([
    page.waitForResponse(r => new URL(r.url()).pathname === '/api/v1/reservations' && r.request().method() === 'POST'),
    page.getByRole('button', { name: 'Request reservation', exact: true }).click(),
  ]).catch(async error => {
    await page.screenshot({ path: path.join(evidence, 'reservation-failure.png'), fullPage: true });
    console.error('Reservation form feedback: ' + await page.locator('main').innerText());
    throw error;
  });
  const value = await saved.json();
  assert.equal(saved.status(), 201, 'Browser reservation must be persisted: ' + JSON.stringify(value));
  assert.equal(value.status, 'PENDING_APPROVAL');
  summary.records.push({ reservationId: value.reservationId, purpose });
  await page.waitForURL('**/reservations');
  await expect(page.getByRole('row').filter({ hasText: purpose })).toContainText('Waiting for approval');
  return value;
}
async function review(page, id) {
  await page.getByRole('link', { name: 'Approvals', exact: true }).first().click();
  await page.getByRole('button', { name: 'Refresh', exact: true }).click();
  await page.locator('a[href="/approvals/' + id + '"]').click();
  await expect(page.getByRole('heading', { name: 'Request details', exact: true })).toBeVisible();
}
async function readNotification(page, item) {
  await page.goto(baseURL + '/notifications');
  // UC05 now renders the equipment name, not an internal reservation UUID. Select the
  // decision message, not the separate submitted/collection messages for the same item.
  const row = page.getByRole('row').filter({ hasText: 'Your reservation for ' + item.name + ' has been confirmed.' });
  await expect(async () => {
    await page.getByRole('button', { name: 'Refresh', exact: true }).click();
    await expect(row).toHaveCount(1);
  }).toPass({ timeout: 90000, intervals: [500, 1000, 2000] });
  await row.getByRole('button', { name: 'Mark as read', exact: true }).click();
  await expect(row).toContainText('Read');
  await expect(row.getByRole('button', { name: 'Mark as read', exact: true })).toHaveCount(0);
}

async function selfDecisionScenario(device, viewport, independentApprover) {
  const context = await browser.newContext({ viewport });
  let page, traced = false, complete = false;
  try {
    page = await signIn(context, 'borrower2');
    await context.tracing.start({ screenshots: true, snapshots: true });
    traced = true;
    const meResponse = await page.request.get(baseURL + '/api/v1/auth/me');
    assert.equal(meResponse.status(), 200);
    const me = await meResponse.json();
    assert.deepEqual([...me.roles].sort(), ['APPROVER', 'BORROWER']);
    assert.equal(me.userId, '10000000-0000-0000-0000-000000000002');
    await expect(page.getByRole('navigation', { name: 'Main navigation' }).getByRole('link', { name: 'Approvals', exact: true })).toBeVisible();
    await cancelPriorFailedFixtures(page);
    const assetTag = 'DEMO-UC02-' + runId + '-self-' + device;
    const catalogue = await (await page.request.get(baseURL + '/api/v1/equipment?' + new URLSearchParams({ q: assetTag }))).json();
    const item = catalogue.content.find(x => x.assetTag === assetTag);
    assert.ok(item, 'Own isolated self-review item must exist');
    const purpose = device + ' self-approval ' + Date.now();
    const own = await book(page, item, Math.ceil(Date.now() / 60000) * 60000 + 86400000, purpose);
    pass(device + ': real dual-role login and own pending browser reservation');

    await page.getByRole('link', { name: 'Approvals', exact: true }).first().click();
    await page.getByRole('button', { name: 'Refresh', exact: true }).click();
    const ownLink = page.locator('a[href="/approvals/' + own.reservationId + '"]');
    await expect(page.getByRole('row').filter({ has: ownLink })).toContainText('Your request');
    await ownLink.click();
    await expect(page.getByText('You cannot approve or reject your own reservation.', { exact: true })).toBeVisible();
    await expect(page.getByRole('radio', { name: /Approve reservation|Reject reservation/ })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Review decision', exact: true })).toHaveCount(0);
    await fit(page, 'own approval blocked');
    await page.screenshot({ path: path.join(evidence, device + '-self-decision-blocked.png'), fullPage: true });
    if (process.argv.includes('--publishable-evidence')) {
      // Synthetic demo identities only; screenshots contain no cookies, login fields or traces.
      const publicEvidence = path.join(repo, 'docs/evidence/uc02-dual-role-20261015');
      fs.mkdirSync(publicEvidence, { recursive: true });
      await page.screenshot({ path: path.join(publicEvidence, device + '-self-decision-blocked.png'), fullPage: true });
    }
    pass(device + ': own-request marker and UI block both decision controls');

    const snapshot = () => JSON.parse(fixtureSql('uc02-browser-self-snapshot.sql',
      { run_id: runId, device, reservation_id: own.reservationId }));
    const before = snapshot();
    assert.equal(before.status, 'PENDING_APPROVAL');
    assert.equal(before.version, own.version);
    assert.equal(before.decisionCount, 0);
    assert.equal(before.successAuditCount, 0);
    for (const decision of ['APPROVED', 'REJECTED']) {
      // Bypass the hidden UI in the actual browser, with its real session and CSRF cookie.
      const result = await page.evaluate(async ({ id, version, decision }) => {
        const cookie = document.cookie.split(';').map(x => x.trim()).find(x => x.startsWith('XSRF-TOKEN='));
        if (!cookie) throw new Error('The actual CSRF cookie is required');
        const response = await fetch('/api/v1/approvals/' + id + '/decision', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': decodeURIComponent(cookie.slice('XSRF-TOKEN='.length)) },
          body: JSON.stringify({ decision, comment: 'Attempted self decision must be denied', expectedVersion: version }),
        });
        return { status: response.status, problem: await response.json() };
      }, { id: own.reservationId, version: own.version, decision });
      assert.equal(result.status, 403);
      assert.equal(result.problem.type, '/problems/self-approval');
      assert.deepEqual(snapshot(), before, 'No status/version/decision/success-audit/notification write on ' + decision);
      const fresh = await independentApprover.request.get(baseURL + '/api/v1/approvals/' + own.reservationId);
      assert.equal(fresh.status(), 200);
      const detail = await fresh.json();
      assert.equal(detail.status, 'PENDING_APPROVAL');
      assert.equal(detail.version, own.version);
      assert.equal(detail.decision ?? null, null);
      pass(device + ': browser-forged self ' + decision + ' is 403 with no persisted side effects');
    }
    await review(independentApprover, own.reservationId);
    await independentApprover.getByLabel('Reject reservation').check();
    await independentApprover.getByLabel('Comment (required)').fill('Independent reviewer resolves the self-review fixture.');
    await independentApprover.getByRole('button', { name: 'Review decision', exact: true }).click();
    await independentApprover.getByRole('button', { name: 'Confirm rejection', exact: true }).click();
    await expect(independentApprover.getByRole('heading', { name: 'Decision recorded', exact: true })).toBeVisible();
    const after = snapshot();
    assert.equal(after.status, 'REJECTED');
    assert.equal(after.version, own.version + 1);
    assert.equal(after.decisionCount, 1);
    assert.equal(after.successAuditCount, 1);
    assert.equal(after.notificationCount, before.notificationCount + 1);
    await page.goto(baseURL + '/reservations');
    await expect(page.getByRole('row').filter({ hasText: purpose })).toContainText('Rejected');
    pass(device + ': a different real approver can decide the same request exactly once');
    complete = true;
    await page.getByRole('button', { name: 'Sign out', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Sign in', exact: true })).toBeVisible();
  } finally {
    try {
      if (!complete && page) await cancelPriorFailedFixtures(page);
    } finally {
      if (traced) await context.tracing.stop({ path: path.join(evidence, device + '-dual-role-trace.zip') });
      await context.close();
    }
  }
}

try {
  // Fresh owned equipment for every run/viewport. Never reset old transactions, change clocks
  // or bypass availability just because a previous successful loan still holds its booked interval.
  const grant = fixtureSql('uc02-browser-fixtures.sql', { run_id: runId }).split(/\r?\n/).at(-1);
  assert.ok(['0', '1'].includes(grant), 'Fixture must report whether it added the role');
  restoreApproverRole = grant === '1';
  summary.dualRoleAddedByRun = restoreApproverRole;
  for (const [device, viewport] of [['desktop', { width: 1440, height: 1000 }], ['mobile', { width: 390, height: 844 }]]) {
    const contexts = [];
    const pages = {};
    const traced = new Set();
    const firstRecord = summary.records.length;
    try {
      for (const [account, links] of Object.entries(roles)) {
        const context = await browser.newContext({ viewport });
        contexts.push(context);
        const page = pages[account] = await signIn(context, account);
        // Trace starts AFTER login, so the generated demo password is not in its login request.
        // Traces can still contain session tokens: retain locally only and never commit them.
        await context.tracing.start({ screenshots: true, snapshots: true });
        traced.add(context);
        const actual = (await page.locator('.sidebar nav a').allTextContents())
          .map(text => text.trim().replace(/^Notifications\d+$/, 'Notifications'));
        assert.deepEqual(actual, links, device + ' ' + account + ' role menu');
        const allowed = await page.request.get(baseURL + '/api/v1/approvals/pending');
        assert.equal(allowed.status(), account === 'approver' ? 200 : 403);
        const report = await page.request.get(baseURL + '/api/v1/reports?from=' +
          encodeURIComponent(new Date(Date.now() - 86400000).toISOString()) + '&to=' + encodeURIComponent(new Date().toISOString()));
        assert.equal(report.status(), account === 'admin' ? 200 : 403);
        await fit(page, account);
        pass(device + ': ' + account + ' real UI login, menu and backend permissions');
      }
      const { borrower, approver, custodian, maintainer, admin } = pages;
      // Only cleanup uses the real API; the acceptance workflow below uses actual browser actions.
      // Preserve earlier successful handovers/history; only our unissued failed fixtures can cancel.
      await cancelPriorFailedFixtures(borrower);
      await approver.goto(baseURL + '/approvals');
      // Empty state is asserted only when the real queue is empty, never by hiding prior data.
      const initial = await (await approver.request.get(baseURL + '/api/v1/approvals/pending')).json();
      if (initial.totalElements === 0) {
        await expect(approver.getByText('No pending reservations', { exact: true })).toBeVisible();
        pass(device + ': real empty approval queue');
      }
      const start = Math.ceil(Date.now() / 60000) * 60000 + 60000;
      const assetTag = 'DEMO-UC02-' + runId + '-' + device;
      const query = new URLSearchParams({ q: assetTag, from: new Date(start).toISOString(), to: new Date(start + 3600000).toISOString(), availableOnly: 'true', size: '50' });
      const catalogue = await (await borrower.request.get(baseURL + '/api/v1/equipment?' + query)).json();
      const item = catalogue.content.find(x => x.requiresApproval && x.assetTag === assetTag && x.status === 'AVAILABLE');
      assert.ok(item, 'No free approval-required demo item; preserve history and choose another window');
      // Reject first so the two scenarios need only one spare reservation-quota slot.
      const rejected = await book(borrower, item, start + 86400000, device + ' rejection ' + Date.now());
      await review(approver, rejected.reservationId);
      await approver.getByLabel('Reject reservation').check();
      await approver.getByRole('button', { name: 'Review decision', exact: true }).click();
      await expect(approver.getByRole('alert')).toContainText('Enter a reason');
      await approver.getByLabel('Comment (required)').fill('Browser acceptance: unsuitable requested time.');
      await approver.getByRole('button', { name: 'Review decision', exact: true }).click();
      await approver.getByRole('button', { name: 'Confirm rejection', exact: true }).click();
      await expect(approver.getByRole('heading', { name: 'Decision recorded', exact: true })).toBeVisible();
      await borrower.goto(baseURL + '/reservations');
      await expect(borrower.getByRole('row').filter({ hasText: summary.records.at(-1).purpose })).toContainText('Rejected');
      pass(device + ': required rejection comment, persisted rejection and borrower status');
      const released = await (await borrower.request.get(baseURL + '/api/v1/equipment/' + item.equipmentId + '/availability?' +
        new URLSearchParams({ from: rejected.startAt, to: rejected.endAt }))).json();
      assert.equal(released.available, true);
      pass(device + ': rejection releases real allocation');

      const accepted = await book(borrower, item, start, device + ' approval-to-damaged-return ' + Date.now());
      await review(approver, accepted.reservationId);
      await approver.getByRole('button', { name: 'Review decision', exact: true }).click();
      await approver.getByRole('button', { name: 'Confirm approval', exact: true }).click();
      await expect(approver.getByRole('heading', { name: 'Decision recorded', exact: true })).toBeVisible();
      await fit(approver, 'approval confirmation');
      await approver.screenshot({ path: path.join(evidence, device + '-approved.png'), fullPage: true });
      pass(device + ': separate approver confirms actual borrower request');
      await readNotification(borrower, item);
      pass(device + ': committed approval notification delivered and marked read');

      // Real wall time, not a fake frontend or backend clock.
      const wait = Math.max(0, Date.parse(accepted.startAt) - Date.now() + 200);
      console.log(device + ': waiting ' + Math.ceil(wait / 1000) + 's for the real checkout window');
      await new Promise(resolve => setTimeout(resolve, wait));
      await custodian.goto(baseURL + '/loans');
      const checkout = custodian.getByRole('region', { name: 'Checkout queue' }).getByRole('article').filter({ hasText: item.name });
      await checkout.getByRole('button', { name: 'Check out', exact: true }).click();
      await custodian.getByLabel(/I checked the person's identity/).check();
      await custodian.getByRole('button', { name: 'Record handover', exact: true }).click();
      await expect(custodian.getByText('Handover recorded successfully.', { exact: true })).toBeVisible();
      const loan = custodian.getByRole('region', { name: 'Loan list' }).getByRole('article').filter({ hasText: item.name });
      await loan.getByRole('button', { name: 'Receive return', exact: true }).click();
      await custodian.getByLabel('Return condition').selectOption('DAMAGED');
      await expect(custodian.getByRole('button', { name: 'Record handover', exact: true })).toBeDisabled();
      const fault = device + ' browser damaged connector ' + Date.now();
      await custodian.getByLabel('Return note').fill(fault);
      await custodian.getByRole('button', { name: 'Record handover', exact: true }).click();
      await expect(custodian.getByText('Handover recorded successfully.', { exact: true })).toBeVisible();
      pass(device + ': custodian physical checkout and damaged return with required note');
      await custodian.goto(baseURL + '/maintenance');
      const card = custodian.getByRole('article').filter({ hasText: fault });
      await card.getByLabel('Assign technician').selectOption({ label: 'Sam Maintainer · maintainer@example.test' });
      await card.getByRole('button', { name: 'Assign technician', exact: true }).click();
      await expect(custodian.getByText('Technician assigned successfully.', { exact: true })).toBeVisible();
      await maintainer.goto(baseURL + '/maintenance');
      await maintainer.getByRole('link', { name: fault, exact: true }).click();
      await maintainer.getByRole('button', { name: 'Start Maintenance', exact: true }).click();
      await maintainer.getByLabel('Record Diagnosis — details').fill('Loose connector confirmed');
      await maintainer.getByRole('button', { name: 'Record Diagnosis', exact: true }).click();
      await expect(maintainer.getByText('Record Diagnosis completed successfully.', { exact: true })).toBeVisible();
      await maintainer.getByLabel('Record Repair Action — details').fill('Connector replaced and tested');
      await maintainer.getByRole('button', { name: 'Record Repair Action', exact: true }).click();
      await expect(maintainer.getByText('Record Repair Action completed successfully.', { exact: true })).toBeVisible();
      await maintainer.getByLabel('Resolve — resolution note').fill('Repair completed, equipment tested');
      await maintainer.getByRole('button', { name: 'Resolve', exact: true }).click();
      await expect(maintainer.getByText('This case is complete and read-only.', { exact: true })).toBeVisible();
      await fit(maintainer, 'maintenance resolution');
      await maintainer.screenshot({ path: path.join(evidence, device + '-resolved.png'), fullPage: true });
      pass(device + ': auto-created linked maintenance assigned, diagnosed, repaired and resolved');
      await borrower.goto(baseURL + '/equipment');
      await borrower.getByLabel('Name or asset tag').fill(item.assetTag);
      await borrower.getByRole('button', { name: 'Search', exact: true }).click();
      await expect(borrower.getByText('Available', { exact: true })).toBeVisible();
      await fit(borrower, 'equipment restored');
      await admin.getByRole('link', { name: 'Reports', exact: true }).first().click();
      await expect(admin.getByRole('heading', { level: 1, name: /Reports/ })).toBeVisible();
      await fit(admin, 'admin reports');
      pass(device + ': equipment restored and actual administrator report page loads');
      await selfDecisionScenario(device, viewport, approver);
      for (const page of Object.values(pages)) {
        await page.getByRole('button', { name: 'Sign out', exact: true }).click();
        await expect(page.getByRole('button', { name: 'Sign in', exact: true })).toBeVisible();
      }
    } catch (error) {
      // Clean up only our own not-yet-issued fixtures via ordinary cancellation, retaining history.
      // Never truncate/reset the database or change completed handovers/maintenance to force a pass.
      if (pages.borrower) {
        try {
          await pages.borrower.request.get(baseURL + '/api/v1/auth/csrf');
          const token = (await pages.borrower.context().cookies(baseURL)).find(x => x.name === 'XSRF-TOKEN');
          for (const record of summary.records.slice(firstRecord)) {
            const response = await pages.borrower.request.get(baseURL + '/api/v1/reservations/' + record.reservationId);
            if (response.ok() && (await response.json()).cancellable) {
              const cancelled = await pages.borrower.request.post(baseURL + '/api/v1/reservations/' + record.reservationId + '/cancel',
                { headers: { 'X-XSRF-TOKEN': decodeURIComponent(token.value) } });
              record.failureCleanupStatus = cancelled.status();
            }
          }
        } catch { summary.cleanupIncomplete = true; }
      }
      throw error;
    } finally {
      for (let i = 0; i < contexts.length; i++) {
        if (traced.has(contexts[i]))
          await contexts[i].tracing.stop({ path: path.join(evidence, device + '-' + Object.keys(roles)[i] + '-trace.zip') });
        await contexts[i].close();
      }
    }
  }
  assert.deepEqual(errors, [], 'No uncaught browser errors');
  summary.status = 'passed';
} catch (error) {
  summary.status = 'failed';
  summary.failure = error.message;
  throw error;
} finally {
  try {
    if (restoreApproverRole) {
      assert.equal(fixtureSql('uc02-browser-role-cleanup.sql'), '0', 'Temporary approver role must be removed');
      summary.dualRoleRestored = true;
      pass('temporary dual-role assignment restored; pre-existing roles untouched');
    }
  } catch (error) {
    summary.status = 'failed';
    summary.dualRoleRestored = false;
    summary.failure ??= error.message;
    throw error;
  } finally {
    summary.finishedAt = new Date().toISOString();
    summary.evidence = evidence;
    fs.writeFileSync(path.join(runtime, 'uc02-browser-summary.json'), JSON.stringify(summary, null, 2));
    await browser.close();
  }
}
