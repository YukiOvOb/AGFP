import { test, expect } from '@playwright/test';
// UC01 main flow in a real browser: sign in → search → reserve → see it → cancel.
// The API is simulated here; PostgreSQL behaviour is covered by ReservationPostgresTest.
test('search for equipment, reserve a slot and cancel it', async ({ page, context }) => {
  const user = {
    userId: '00000000-0000-0000-0000-0000000000b1',
    email: 'borrower@example.com',
    displayName: 'Demo Borrower',
    roles: ['BORROWER'],
  };
  const camera = {
    equipmentId: '00000000-0000-0000-0000-0000000000e1',
    assetTag: 'CAM-001',
    name: 'Canon EOS R6',
    category: 'Camera',
    location: 'COM1 Lab',
    status: 'AVAILABLE',
    requiresApproval: false,
    availableInWindow: null as boolean | null,
  };
  let signedIn = false;
  let reservation: Record<string, unknown> | null = null;
  let searchedWindow = '';
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    if (path.endsWith('/auth/csrf')) {
      await context.addCookies([
        { name: 'XSRF-TOKEN', value: 'browser-test-token', url: 'http://127.0.0.1:5173' },
      ]);
      return route.fulfill({ status: 204 });
    }
    if (request.method() === 'POST')
      expect(request.headers()['x-xsrf-token']).toBe('browser-test-token');
    if (path.endsWith('/auth/login')) {
      signedIn = true;
      return route.fulfill({ json: user });
    }
    if (!signedIn) return route.fulfill({ status: 401, json: { detail: 'Sign in to continue.' } });
    if (path.endsWith('/auth/me')) return route.fulfill({ json: user });
    if (path.endsWith('/unread-count')) return route.fulfill({ json: { count: 0 } });
    if (path === '/api/v1/notifications')
      return route.fulfill({
        json: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 },
      });
    if (path === '/api/v1/equipment/filters')
      return route.fulfill({ json: { categories: ['Camera'], locations: ['COM1 Lab'] } });
    if (path === '/api/v1/equipment') {
      const from = url.searchParams.get('from');
      if (from) searchedWindow = `${from}/${url.searchParams.get('to')}`;
      return route.fulfill({
        json: {
          content: [{ ...camera, availableInWindow: from ? true : null }],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
        },
      });
    }
    if (path.endsWith('/availability'))
      return route.fulfill({
        json: {
          equipment: camera,
          from: url.searchParams.get('from'),
          to: url.searchParams.get('to'),
          available: true,
          openMaintenance: false,
          busySlots: [],
        },
      });
    if (path === '/api/v1/reservations' && request.method() === 'POST') {
      const body = request.postDataJSON();
      reservation = {
        reservationId: '00000000-0000-0000-0000-0000000000c1',
        equipmentId: camera.equipmentId,
        assetTag: camera.assetTag,
        equipmentName: camera.name,
        location: camera.location,
        startAt: body.startAt,
        endAt: body.endAt,
        status: 'CONFIRMED',
        purpose: body.purpose ?? null,
        createdAt: '2029-12-01T00:00:00Z',
        version: 0,
        cancellable: true,
      };
      return route.fulfill({ status: 201, json: reservation });
    }
    if (path === '/api/v1/reservations/mine')
      return route.fulfill({
        json: {
          content: reservation ? [reservation] : [],
          page: 0,
          size: 20,
          totalElements: reservation ? 1 : 0,
          totalPages: 1,
        },
      });
    if (path.endsWith('/cancel') && reservation) {
      reservation = { ...reservation, status: 'CANCELLED', cancellable: false };
      return route.fulfill({ json: reservation });
    }
    return route.fulfill({ status: 404 });
  });

  await page.goto('/equipment');
  await page.getByLabel('Email address').fill(user.email);
  await page.getByLabel('Password').fill('test-password');
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();

  await page.goto('/equipment');
  await expect(page.getByRole('heading', { name: /Find equipment/ })).toBeVisible();
  await page.getByLabel('Category').selectOption('Camera');
  await page.getByLabel('From (SGT)').fill('2030-01-10T09:00');
  await page.getByLabel('Until (SGT)').fill('2030-01-10T11:00');
  await page.getByRole('button', { name: 'Search', exact: true }).click();
  await expect(page.getByText('Free', { exact: true })).toBeVisible();
  expect(searchedWindow).toBe('2030-01-10T01:00:00Z/2030-01-10T03:00:00Z');

  await page.getByRole('link', { name: 'Reserve Canon EOS R6' }).click();
  await expect(page.getByLabel('Start (SGT)')).toHaveValue('2030-01-10T09:00');
  await page.getByLabel('Purpose (optional)').fill('Project shoot');
  await page.getByRole('button', { name: 'Reserve', exact: true }).click();

  await expect(
    page.getByText('Canon EOS R6 is reserved for 10 Jan 2030, 09:00 – 11:00.'),
  ).toBeVisible();
  await page.getByRole('button', { name: 'Cancel reservation for Canon EOS R6' }).click();
  await page.getByRole('button', { name: 'Yes, cancel' }).click();
  await expect(page.locator('.badge-muted', { hasText: 'Cancelled' })).toBeVisible();
});
