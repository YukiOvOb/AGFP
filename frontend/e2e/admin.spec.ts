import { expect, test } from '@playwright/test';

// Browser-level contract test with simulated APIs. Real PostgreSQL/UI acceptance is separate.
test('administrator creates a borrower who can sign in', async ({ page, context }) => {
  const admin = {
    userId: '10000000-0000-4000-8000-000000000001',
    email: 'admin@example.test',
    displayName: 'Admin',
    roles: ['ADMIN'],
  };
  let signedIn: typeof admin | null = null;
  let created: (typeof admin & { accountStatus: string; version: number }) | null = null;
  const password = 'browser-only-password';
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path.endsWith('/auth/csrf')) {
      await context.addCookies([
        { name: 'XSRF-TOKEN', value: 'admin-browser-csrf', url: 'http://127.0.0.1:5173' },
      ]);
      return route.fulfill({ status: 204 });
    }
    if (['POST', 'PATCH'].includes(request.method()))
      expect(request.headers()['x-xsrf-token']).toBe('admin-browser-csrf');
    if (path.endsWith('/auth/login')) {
      const body = request.postDataJSON();
      signedIn =
        body.email === admin.email
          ? admin
          : created && body.email === created.email && body.password === password
            ? created
            : null;
      return route.fulfill({
        status: signedIn ? 200 : 401,
        json: signedIn ?? { detail: 'Invalid credentials' },
      });
    }
    if (path.endsWith('/auth/logout')) {
      signedIn = null;
      return route.fulfill({ status: 204 });
    }
    if (!signedIn) return route.fulfill({ status: 401, json: { detail: 'Sign in to continue.' } });
    if (path.endsWith('/auth/me')) return route.fulfill({ json: signedIn });
    if (path.endsWith('/unread-count')) return route.fulfill({ json: { count: 0 } });
    if (path === '/api/v1/admin/users' && request.method() === 'POST') {
      expect(signedIn.roles).toEqual(['ADMIN']);
      const body = request.postDataJSON();
      expect(body.roles).toEqual(['BORROWER']);
      expect(body.initialPassword).toBe(password);
      created = {
        userId: '10000000-0000-4000-8000-000000000099',
        email: body.email,
        displayName: body.displayName,
        roles: body.roles,
        accountStatus: 'ACTIVE',
        version: 0,
      };
      return route.fulfill({ status: 201, json: created });
    }
    if (path.endsWith('/display-name') && request.method() === 'PATCH') {
      expect(signedIn.roles).toEqual(['ADMIN']);
      const body = request.postDataJSON();
      expect(body.expectedDisplayName).toBe('New Borrower');
      expect(body.displayName).toBe('Renamed Borrower');
      expect(created).not.toBeNull();
      created = { ...created!, displayName: body.displayName };
      return route.fulfill({ json: created });
    }
    if (path === '/api/v1/admin/users')
      return route.fulfill({
        json: {
          content: created ? [created] : [],
          totalElements: created ? 1 : 0,
          page: 0,
          size: 20,
        },
      });
    throw new Error('Unhandled admin browser contract: ' + path);
  });
  await page.goto('/login');
  await page.getByLabel('Email').fill(admin.email);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await page.getByRole('link', { name: 'Users and roles', exact: true }).click();
  await page.getByLabel('Email').fill('new-borrower@example.test');
  await page.getByLabel('Display name', { exact: true }).fill('New Borrower');
  await page.getByLabel('Initial password').fill(password);
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.getByText('Account created. The user can now sign in.')).toBeVisible();
  await expect(page.getByLabel('Initial password')).toHaveValue('');
  await page
    .getByRole('button', { name: 'Edit display name for new-borrower@example.test' })
    .click();
  await page.getByLabel('New display name').fill('Renamed Borrower');
  await page.getByRole('button', { name: 'Save name' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.getByRole('cell', { name: 'Renamed Borrower', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page.getByRole('button', { name: 'Sign in', exact: true })).toBeVisible();
  // Start a fresh sign-in rather than returning the borrower to the previous ADMIN-only URL.
  await page.goto('/login');
  await page.getByLabel('Email').fill('new-borrower@example.test');
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible();
  await page.getByRole('link', { name: 'Overview', exact: true }).click();
  await expect(page.getByRole('heading', { level: 1, name: /^Welcome,/ })).toBeVisible();
  await expect(
    page.getByRole('heading', { level: 1, name: /^Welcome, Renamed Borrower/ }),
  ).toBeVisible();
  await expect(page.getByRole('link', { name: 'Users and roles', exact: true })).toHaveCount(0);
});
