import { test, expect } from '@playwright/test';
// Browser acceptance of the UI contract. PostgreSQL end-to-end acceptance follows the shared migration.
test('sign in, read a notification and sign out', async ({ page, context }) => {
  let signedIn = false;
  let read = false;
  const user = {
    userId: '00000000-0000-0000-0000-000000000001',
    email: 'wang@example.com',
    displayName: 'Wang Yuanmeng',
    roles: ['BORROWER'],
  };
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path.endsWith('/auth/csrf')) {
      await context.addCookies([
        { name: 'XSRF-TOKEN', value: 'browser-test-token', url: 'http://127.0.0.1:5173' },
      ]);
      return route.fulfill({ status: 204 });
    }
    if (request.method() === 'POST')
      expect(request.headers()['x-xsrf-token']).toBe('browser-test-token');
    if (path.endsWith('/auth/login')) {
      expect(request.postDataJSON()).toEqual({ email: user.email, password: 'test-password' });
      signedIn = true;
      return route.fulfill({ json: user });
    }
    if (path.endsWith('/auth/logout')) {
      signedIn = false;
      return route.fulfill({ status: 204 });
    }
    if (!signedIn) return route.fulfill({ status: 401, json: { detail: 'Sign in to continue.' } });
    if (path.endsWith('/auth/me')) return route.fulfill({ json: user });
    if (path.endsWith('/unread-count')) return route.fulfill({ json: { count: read ? 0 : 1 } });
    if (path.endsWith('/read')) {
      read = true;
      return route.fulfill({ status: 204 });
    }
    if (path === '/api/v1/notifications')
      return route.fulfill({
        json: {
          content: [
            {
              id: '00000000-0000-0000-0000-000000000002',
              type: 'OVERDUE',
              content: 'Please return the camera.',
              deliveredAt: '2026-09-24T10:00:00Z',
              readAt: read ? '2026-09-24T10:01:00Z' : null,
            },
          ],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
        },
      });
    return route.fulfill({ status: 404 });
  });
  await page.goto('/notifications');
  await page.getByLabel('Email address').fill(user.email);
  await page.getByLabel('Password').fill('test-password');
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page.getByText('Please return the camera.')).toBeVisible();
  await expect(page.getByText('18:00')).toBeVisible();
  await page.getByRole('button', { name: 'Mark as read' }).click();
  await expect(page.getByText('Reviewed')).toBeVisible();
  await expect(page.getByText('0 unread notifications')).toBeVisible();
  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page.getByRole('button', { name: 'Sign in', exact: true })).toBeVisible();
});
