import { test, expect, type Page, type BrowserContext } from '@playwright/test';
import type { components } from '../src/api/schema';

// Browser/UI contract tests only: mocked API responses are not PostgreSQL integration evidence.
const ID = '12345678-1234-4234-8234-123456789001';
const OTHER = '12345678-1234-4234-8234-123456789002';
const NOW = '2026-10-07T01:00:00Z';
type Detail = components['schemas']['DetailResponse'];
type Command = components['schemas']['DecisionRequest'];
async function workspace(page: Page, context: BrowserContext, conflict = false) {
  await page.clock.install({ time: new Date(NOW) });
  const user = {
    userId: OTHER,
    email: 'approver@example.com',
    displayName: 'Zhang Hanming',
    roles: ['APPROVER'],
  };
  let signedIn = false;
  let item: Detail = {
    reservationId: ID,
    requesterId: '12345678-1234-4234-8234-123456789003',
    requesterName: 'Equipment Applicant',
    equipment: { equipmentId: OTHER, assetTag: 'CAM-007', name: 'Sony Camera', category: 'Camera' },
    startAt: '2026-10-07T02:00:00Z',
    endAt: '2026-10-07T04:00:00Z',
    createdAt: '2026-10-06T08:00:00Z',
    status: 'PENDING_APPROVAL',
    version: 7,
    purpose: 'Course project filming',
    expired: false,
  };
  const writes: Command[] = [];
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path.endsWith('/auth/csrf')) {
      await context.addCookies([
        { name: 'XSRF-TOKEN', value: 'approval-browser-token', url: 'http://127.0.0.1:5173' },
      ]);
      return route.fulfill({ status: 204 });
    }
    if (request.method() === 'POST')
      expect(request.headers()['x-xsrf-token']).toBe('approval-browser-token');
    if (path.endsWith('/auth/login')) {
      signedIn = true;
      return route.fulfill({ json: user });
    }
    if (path.endsWith('/auth/logout')) {
      signedIn = false;
      return route.fulfill({ status: 204 });
    }
    if (!signedIn) return route.fulfill({ status: 401, json: { detail: 'Sign in to continue.' } });
    if (path.endsWith('/auth/me')) return route.fulfill({ json: user });
    if (path.endsWith('/unread-count')) return route.fulfill({ json: { count: 0 } });
    if (path.endsWith('/approvals/pending'))
      return route.fulfill({
        json: {
          content: item.status === 'PENDING_APPROVAL' ? [item] : [],
          number: 0,
          size: 20,
          totalElements: item.status === 'PENDING_APPROVAL' ? 1 : 0,
          totalPages: 1,
        },
      });
    if (path.endsWith(`/approvals/${ID}`)) return route.fulfill({ json: item });
    if (path.endsWith(`/approvals/${ID}/decision`)) {
      const command = request.postDataJSON() as Command;
      writes.push(command);
      if (conflict) {
        item = { ...item, status: 'CONFIRMED', version: 8 };
        return route.fulfill({
          status: 409,
          json: { type: '/problems/version-conflict', detail: 'Request changed.' },
        });
      }
      expect(command.expectedVersion).toBe(7);
      item = {
        ...item,
        status: command.decision === 'APPROVED' ? 'CONFIRMED' : 'REJECTED',
        version: 8,
        decision: {
          approvalId: OTHER,
          reservationId: ID,
          approverId: user.userId,
          decision: command.decision,
          comment: command.comment,
          decidedAt: NOW,
        },
      };
      return route.fulfill({ json: { reservation: item, decision: item.decision } });
    }
    return route.fulfill({ status: 404 });
  });
  await page.goto('/approvals');
  await page.getByLabel('Email address').fill(user.email);
  await page.getByLabel('Password').fill('test-password');
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page.getByText('Sony Camera')).toBeVisible();
  return writes;
}

test('approver reviews and rejects a request, then sees an empty queue', async ({
  page,
  context,
}, info) => {
  const writes = await workspace(page, context);
  await page.screenshot({ path: info.outputPath('approval-queue.png'), fullPage: true });
  await page.getByRole('link', { name: 'Review request for CAM-007' }).click();
  await expect(page.getByText('07 Oct 2026, 10:00 SGT')).toBeVisible();
  await page.getByLabel('Reject reservation').check();
  await page.getByRole('button', { name: 'Review decision' }).click();
  await expect(page.getByRole('alert')).toContainText('Enter a reason');
  await page.getByLabel('Comment (required)').fill('Equipment needed for maintenance preparation.');
  await page.getByRole('button', { name: 'Review decision' }).click();
  expect(writes).toHaveLength(0);
  await page.screenshot({ path: info.outputPath('approval-confirmation.png'), fullPage: true });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.getByRole('button', { name: 'Confirm rejection' }).click();
  await expect(page.getByText('Decision recorded')).toBeVisible();
  expect(writes).toEqual([
    {
      decision: 'REJECTED',
      comment: 'Equipment needed for maintenance preparation.',
      expectedVersion: 7,
    },
  ]);
  await page.getByRole('link', { name: /Pending reservations/ }).click();
  await expect(page.getByText('No pending reservations')).toBeVisible();
  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page.getByRole('button', { name: 'Sign in', exact: true })).toBeVisible();
});

test('a stale approval stops further writes until manual refresh', async ({ page, context }) => {
  const writes = await workspace(page, context, true);
  await page.getByRole('link', { name: 'Review request for CAM-007' }).click();
  await page.getByRole('button', { name: 'Review decision' }).click();
  await page.getByRole('button', { name: 'Confirm approval' }).click();
  await expect(page.getByText(/This request changed/)).toBeVisible();
  await expect(page.getByRole('button', { name: 'Review decision' })).toBeDisabled();
  expect(writes).toHaveLength(1);
  await page.getByRole('button', { name: 'Refresh request' }).click();
  await expect(page.getByText('This reservation is no longer waiting for approval.')).toBeVisible();
  expect(writes).toHaveLength(1);
});

test('a reservation expiring while confirmation is open cannot be approved', async ({
  page,
  context,
}) => {
  const writes = await workspace(page, context);
  await page.getByRole('link', { name: 'Review request for CAM-007' }).click();
  await page.getByRole('button', { name: 'Review decision' }).click();
  await page.clock.fastForward(60 * 60 * 1000 + 1000);
  await expect(page.getByRole('button', { name: 'Confirm approval' })).toBeDisabled();
  await page.getByRole('button', { name: 'Back to editing' }).click();
  await expect(page.getByLabel('Approve reservation')).toHaveCount(0);
  expect(writes).toHaveLength(0);
});
