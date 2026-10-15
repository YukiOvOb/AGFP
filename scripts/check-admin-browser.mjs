import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import { randomUUID, createHash } from 'node:crypto';
import { createRequire } from 'node:module';
import { execFileSync } from 'node:child_process';
import { repo, runtime, baseURL, demoPassword, state } from './lib/local-demo-client.mjs';

// Real React -> Spring Security/CSRF -> PostgreSQL. No route interception, direct DB writes or fake time.
assert.equal(state.data, path.join(runtime, 'pg18'), 'Only this checkout’s disposable demo is allowed');
const require = createRequire(path.join(repo, 'frontend/package.json'));
const { chromium, expect } = require('@playwright/test');
const output = path.join(runtime, 'admin-browser-' + Date.now());
fs.mkdirSync(output, { recursive: true });
const summary = { status: 'running', mocked: false, startedAt: new Date().toISOString(), checks: [],
  startingHead: execFileSync('git', ['rev-parse', 'HEAD'], { cwd: repo, encoding: 'utf8' }).trim(),
  fetchedMain: execFileSync('git', ['rev-parse', 'origin/main'], { cwd: repo, encoding: 'utf8' }).trim(),
  sourceHashes: ['scripts/check-admin-browser.mjs', 'frontend/src/features/admin/UsersPage.tsx',
    'frontend/src/features/admin/EditDisplayNameDialog.tsx',
    'frontend/src/features/admin/AuditPage.tsx', 'app/src/main/java/sg/edu/nus/serms/identity/service/AdminUserService.java',
    'app/src/main/java/sg/edu/nus/serms/identity/api/AdminUserController.java',
    'app/src/main/java/sg/edu/nus/serms/identity/api/AdminUserProblemHandler.java',
    'app/src/main/java/sg/edu/nus/serms/identity/repository/AdminUserRepository.java',
    'app/src/main/java/sg/edu/nus/serms/audit/repository/AuditQueryRepository.java'].map(file => ({ path: file,
      sha256: createHash('sha256').update(fs.readFileSync(path.join(repo, file))).digest('hex') })),
};
const browser = await chromium.launch({ headless: true });
async function signIn(page, email, password) {
  await page.goto(baseURL + '/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible();
  await page.getByRole('link', { name: 'Overview', exact: true }).click();
  await expect(page.getByRole('heading', { level: 1, name: /^Welcome,/ })).toBeVisible();
}
async function fits(page) {
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 2), 'No page-level overflow');
}
try {
  for (const [name, viewport] of Object.entries({ desktop: { width: 1440, height: 1000 }, mobile: { width: 390, height: 844 } })) {
    const context = await browser.newContext({ viewport }); const page = await context.newPage();
    try {
      await signIn(page, 'admin@example.test', demoPassword);
      await page.getByRole('link', { name: 'Users and roles', exact: true }).click();
      await expect(page.getByRole('heading', { name: 'Users and roles', exact: true })).toBeVisible();
      const email = `admin-browser-${randomUUID()}@example.test`, password = randomUUID();
      await page.getByLabel('Email').fill(email);
      await page.getByLabel('Display name', { exact: true }).fill('Browser-created Borrower');
      await page.getByLabel('Initial password').fill(password);
      await page.getByRole('button', { name: 'Create account' }).click();
      await expect(page.getByText('Account created. The user can now sign in.')).toBeVisible();
      await expect(page.getByLabel('Initial password')).toHaveValue('');
      await page.getByLabel('Search users').fill(email);
      await page.getByRole('button', { name: 'Search', exact: true }).click();
      await expect(page.getByText(email, { exact: true })).toBeVisible(); await fits(page);
      await page.getByRole('button', { name: 'Edit display name for ' + email, exact: true }).click();
      await page.getByLabel('New display name').fill('Renamed Browser Borrower');
      await fits(page);
      await page.screenshot({ path: path.join(output, `${name}-edit-name.png`), fullPage: true });
      await page.getByRole('button', { name: 'Save name', exact: true }).click();
      await expect(page.getByRole('dialog')).toHaveCount(0);
      await expect(page.getByRole('cell', { name: 'Renamed Browser Borrower', exact: true })).toBeVisible();
      // Publish only synthetic UI after password clearing, never a browser trace or credentials.
      await page.screenshot({ path: path.join(output, `${name}-users.png`), fullPage: true });
      await page.getByRole('link', { name: 'Audit log', exact: true }).click();
      await page.getByLabel('Action', { exact: true }).fill('USER_DISPLAY_NAME_CHANGED');
      await page.getByLabel('Entity type').fill('User');
      await page.getByRole('button', { name: 'Apply filters' }).click();
      await expect(page.getByRole('cell', { name: 'USER_DISPLAY_NAME_CHANGED', exact: true }).first()).toBeVisible();
      await fits(page);
      await page.screenshot({ path: path.join(output, `${name}-audit.png`), fullPage: true });
      await page.getByRole('button', { name: 'Sign out' }).click();
      await expect(page.getByRole('button', { name: 'Sign in', exact: true })).toBeVisible();
      await signIn(page, email, password);
      await expect(page.getByRole('heading', { level: 1, name: /^Welcome, Renamed Browser Borrower/ })).toBeVisible();
      await expect(page.getByRole('link', { name: 'Users and roles', exact: true })).toHaveCount(0);
      await page.goto(baseURL + '/admin/audit');
      await expect(page.getByText('Your account does not have access to this page.')).toBeVisible();
      const denied = await page.evaluate(async () => (await fetch('/api/v1/admin/audit')).status);
      assert.equal(denied, 403);
      summary.checks.push(`${name}: administrator creates and renames a borrower; password cleared; literal search; rename audit visible; same password still signs in with new name; administrator UI/API denied; no overflow`);
      console.log('PASS administrator real browser workflow: ' + name);
    } finally { await context.close(); }
  }
  summary.status = 'passed';
} catch (failure) {
  summary.status = 'failed'; summary.failure = failure.message;
  throw failure;
} finally {
  summary.finishedAt = new Date().toISOString();
  fs.writeFileSync(path.join(output, 'verification.json'), JSON.stringify(summary, null, 2) + '\n');
  await browser.close();
  console.log('Admin browser evidence: ' + output);
}
