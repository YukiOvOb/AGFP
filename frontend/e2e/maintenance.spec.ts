import { test, expect } from '@playwright/test';
import type { components } from '../src/api/schema';
// Mocked browser acceptance. This does not exercise the shared PostgreSQL application runtime.
test('maintainer signs in, starts own case, diagnoses and resolves it', async ({
  page,
  context,
}, testInfo) => {
  const user = {
    userId: '12345678-1234-4234-8234-123456789001',
    email: 'maintainer@example.com',
    displayName: 'Liu Tongyao',
    roles: ['MAINTAINER'],
  };
  const caseId = '12345678-1234-4234-8234-123456789002';
  let signedIn = false;
  let item: components['schemas']['MaintenanceCaseResponse'] = {
    maintenanceCaseId: caseId,
    equipmentId: '12345678-1234-4234-8234-123456789003',
    reportedBy: '12345678-1234-4234-8234-123456789004',
    assignedTo: user.userId,
    loanId: null,
    status: 'ASSIGNED',
    faultDescription: 'Camera lens jammed',
    reportedAt: '2026-10-06T00:00:00Z',
    resolvedAt: null,
    resolutionNote: null,
    version: 1,
  };
  const history: components['schemas']['MaintenanceHistoryResponse'][] = [];
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path.endsWith('/auth/csrf')) {
      await context.addCookies([
        { name: 'XSRF-TOKEN', value: 'maintenance-browser-token', url: 'http://127.0.0.1:5173' },
      ]);
      return route.fulfill({ status: 204 });
    }
    if (request.method() === 'POST')
      expect(request.headers()['x-xsrf-token']).toBe('maintenance-browser-token');
    if (path.endsWith('/auth/login')) {
      expect(request.postDataJSON()).toEqual({ email: user.email, password: 'test-password' });
      signedIn = true;
      return route.fulfill({ json: user });
    }
    if (!signedIn) return route.fulfill({ status: 401, json: { detail: 'Sign in to continue.' } });
    if (path.endsWith('/auth/me')) return route.fulfill({ json: user });
    if (path.endsWith('/unread-count')) return route.fulfill({ json: { count: 0 } });
    if (path.endsWith('/maintenance/cases/assigned')) return route.fulfill({ json: [item] });
    if (path.endsWith('/history')) return route.fulfill({ json: history });
    if (path.endsWith('/cases/' + caseId)) return route.fulfill({ json: item });
    if (request.method() === 'POST') {
      const action = path.split('/').at(-1);
      const before = item.status;
      let detail: string | null = null;
      if (action === 'start') item = { ...item, status: 'IN_PROGRESS', version: 2 };
      else if (action === 'diagnosis') {
        expect(request.postDataJSON()).toEqual({ detail: 'Lens motor failure' });
        detail = 'Lens motor failure';
      } else if (action === 'resolve') {
        expect(request.postDataJSON()).toEqual({ resolutionNote: 'Motor replaced and tested' });
        detail = 'Motor replaced and tested';
        item = {
          ...item,
          status: 'RESOLVED',
          resolutionNote: detail,
          resolvedAt: '2026-10-06T02:30:00Z',
          version: 3,
        };
      } else return route.fulfill({ status: 404 });
      history.push({
        maintenanceCaseId: caseId,
        equipmentId: item.equipmentId,
        actorId: user.userId,
        action:
          action === 'start' ? 'START' : action === 'diagnosis' ? 'RECORD_DIAGNOSIS' : 'RESOLVE',
        fromStatus: before,
        toStatus: item.status,
        occurredAt: '2026-10-06T02:30:00Z',
        detail,
      });
      return route.fulfill({ json: item });
    }
    return route.fulfill({ status: 404 });
  });
  await page.goto('/maintenance');
  await page.getByLabel('Email address').fill(user.email);
  await page.getByLabel('Password').fill('test-password');
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'My assigned cases' })).toBeVisible();
  await page.getByRole('link', { name: 'Camera lens jammed' }).click();
  await expect(page.getByText('06 Oct 2026, 08:00 SGT')).toBeVisible();
  await page.getByRole('button', { name: 'Start Maintenance', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Record Diagnosis', exact: true })).toBeVisible();
  await page.getByLabel('Record Diagnosis — details').fill('Lens motor failure');
  await page.getByRole('button', { name: 'Record Diagnosis', exact: true }).click();
  await expect(page.getByText('Record Diagnosis completed successfully.')).toBeVisible();
  await expect(page.getByText('Lens motor failure', { exact: true })).toBeVisible();
  // Capture desktop/mobile action layout and assert the page has no horizontal overflow.
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.screenshot({ path: testInfo.outputPath('maintenance-actions.png'), fullPage: true });
  await page.getByLabel('Resolve — resolution note').fill('Motor replaced and tested');
  await page.getByRole('button', { name: 'Resolve', exact: true }).click();
  await expect(page.getByText('This case is complete and read-only.')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Record Diagnosis', exact: true })).toHaveCount(0);
  await expect(page.getByText('06 Oct 2026, 10:30 SGT').first()).toBeVisible();
  await page.getByRole('link', { name: '← Maintenance workspace' }).click();
  await expect(page.getByRole('region', { name: 'RESOLVED', exact: true })).toBeVisible();
  await expect(page.getByText('Motor replaced and tested')).toBeVisible();
});
