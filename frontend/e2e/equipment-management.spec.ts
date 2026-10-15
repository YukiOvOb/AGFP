import { test, expect } from '@playwright/test';

// Browser path for the management UI. PostgreSQL behavior is covered by EquipmentManagementPostgresIT.
test('custodian registers and retires equipment with confirmation', async ({ page, context }) => {
  const user = {
    userId: '00000000-0000-0000-0000-000000000017',
    email: 'custodian@example.test',
    displayName: 'Demo Custodian',
    roles: ['CUSTODIAN'],
  };
  const item = {
    equipmentId: '00000000-0000-0000-0000-000000000018',
    assetTag: 'CAM-018',
    serialNumber: null,
    name: 'Field camera',
    description: null,
    category: 'Camera',
    location: 'Store',
    condition: 'GOOD',
    status: 'AVAILABLE',
    requiresApproval: false,
    maxBorrowDuration: null,
    version: 0,
  };
  let signedIn = false;
  let equipment: typeof item[] = [];
  let retireRequests = 0;
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path.endsWith('/auth/csrf')) {
      await context.addCookies([
        { name: 'XSRF-TOKEN', value: 'management-browser-token', url: 'http://127.0.0.1:5173' },
      ]);
      return route.fulfill({ status: 204 });
    }
    if (request.method() === 'POST')
      expect(request.headers()['x-xsrf-token']).toBe('management-browser-token');
    if (path.endsWith('/auth/login')) {
      signedIn = true;
      return route.fulfill({ json: user });
    }
    if (!signedIn) return route.fulfill({ status: 401, json: { detail: 'Sign in to continue.' } });
    if (path.endsWith('/auth/me')) return route.fulfill({ json: user });
    if (path.endsWith('/unread-count')) return route.fulfill({ json: { count: 0 } });
    if (path === '/api/v1/equipment/manage/categories') return route.fulfill({ json: ['Camera'] });
    if (path === '/api/v1/equipment/manage' && request.method() === 'GET')
      return route.fulfill({ json: equipment });
    if (path === '/api/v1/equipment/manage' && request.method() === 'POST') {
      expect(request.postDataJSON()).toMatchObject({
        assetTag: 'CAM-018', name: 'Field camera', category: 'Camera', location: 'Store',
      });
      equipment = [item];
      return route.fulfill({ status: 201, json: item });
    }
    if (path === `/api/v1/equipment/manage/${item.equipmentId}/retire`) {
      expect(request.postDataJSON()).toEqual({ expectedVersion: 0 });
      retireRequests++;
      equipment = [{ ...item, status: 'RETIRED', version: 1 }];
      return route.fulfill({ json: equipment[0] });
    }
    return route.fulfill({ status: 404 });
  });

  await page.goto('/manage/equipment');
  await page.getByLabel('Email address').fill(user.email);
  await page.getByLabel('Password').fill('test-password');
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Manage equipment' })).toBeVisible();
  await page.getByRole('button', { name: 'Register equipment' }).click();
  const form = page.getByRole('form', { name: 'Equipment form' });
  await form.getByRole('textbox', { name: 'Asset tag' }).fill('CAM-018');
  await form.getByRole('textbox', { name: 'Name' }).fill('Field camera');
  await form.getByRole('combobox', { name: 'Category' }).selectOption('Camera');
  await form.getByRole('textbox', { name: 'Location' }).fill('Store');
  await form.getByRole('button', { name: 'Save equipment' }).click();
  await expect(page.getByText('Equipment registered.')).toBeVisible();
  await expect(page.getByText('Field camera (CAM-018)')).toBeVisible();
  await page.getByRole('button', { name: 'Retire Field camera' }).click();
  const dialog = page.getByRole('alertdialog', { name: 'Retire this equipment?' });
  await dialog.getByRole('button', { name: 'Cancel' }).click();
  expect(retireRequests).toBe(0);
  await page.getByRole('button', { name: 'Retire Field camera' }).click();
  await page.getByRole('alertdialog').getByRole('button', { name: 'Retire equipment' }).click();
  await expect(page.getByText('Equipment retired.')).toBeVisible();
  expect(retireRequests).toBe(1);
  await expect(page.getByRole('button', { name: 'Retire Field camera' })).toHaveCount(0);
});
