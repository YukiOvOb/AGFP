import { describe, expect, it } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { App } from '@/app/App';
import { AuthProvider } from '@/app/AuthProvider';
import { currentUser, server } from '@/test/server';

const item = {
  equipmentId: '00000000-0000-0000-0000-000000000017', assetTag: 'CAM-017', serialNumber: 'SN-017',
  name: 'Studio camera', description: 'A demo camera', category: 'Camera', location: 'Store',
  condition: 'GOOD', status: 'AVAILABLE', requiresApproval: false, maxBorrowDuration: 'PT72H', version: 3,
};
function mount(roles = ['CUSTODIAN']) {
  server.use(http.get('/api/v1/auth/me', () => HttpResponse.json({ ...currentUser, roles })));
  return render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <MemoryRouter initialEntries={['/manage/equipment']}><AuthProvider><App /></AuthProvider></MemoryRouter>
  </QueryClientProvider>);
}

describe('equipment management', () => {
  it('blocks roles outside CUSTODIAN and ADMIN before requesting management data', async () => {
    let reads = 0;
    server.use(http.get('/api/v1/equipment/manage', () => { reads++; return HttpResponse.json([item]); }));
    mount(['BORROWER']);
    expect(await screen.findByRole('alert')).toHaveTextContent('Your account does not have access to this page.');
    expect(reads).toBe(0);
  });

  it('registers and updates equipment and confirms retirement', async () => {
    const writes: { method: string; body: unknown }[] = [];
    let records = [item];
    server.use(
      http.get('/api/v1/equipment/manage', () => HttpResponse.json(records)),
      http.get('/api/v1/equipment/manage/categories', () => HttpResponse.json(['Camera'])),
      http.post('/api/v1/equipment/manage', async ({ request }) => {
        const body = await request.json(); writes.push({ method: 'POST', body });
        records = [...records, { ...item, equipmentId: 'new-item', assetTag: 'CAM-018', name: 'New camera', version: 0 }];
        return HttpResponse.json(records.at(-1), { status: 201 });
      }),
      http.put('/api/v1/equipment/manage/:id', async ({ request }) => {
        const body = await request.json(); writes.push({ method: 'PUT', body });
        records = records.map((record) => record.equipmentId === item.equipmentId
          ? { ...record, name: 'Updated camera', version: 4 } : record);
        return HttpResponse.json(records[0]);
      }),
      http.post('/api/v1/equipment/manage/:id/retire', async ({ request }) => {
        const body = await request.json(); writes.push({ method: 'RETIRE', body });
        records = records.map((record) => record.equipmentId === item.equipmentId
          ? { ...record, status: 'RETIRED', version: 5 } : record);
        return HttpResponse.json(records[0]);
      }),
    );
    mount();
    const user = userEvent.setup();
    expect(await screen.findByText('Studio camera (CAM-017)')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Register equipment' }));
    const form = screen.getByRole('form', { name: 'Equipment form' });
    await user.type(within(form).getByRole('textbox', { name: 'Asset tag' }), 'CAM-018');
    await user.type(within(form).getByRole('textbox', { name: 'Name' }), 'New camera');
    await user.selectOptions(within(form).getByRole('combobox', { name: 'Category' }), 'Camera');
    await user.type(within(form).getByRole('textbox', { name: 'Location' }), 'Store');
    await user.click(within(form).getByRole('button', { name: 'Save equipment' }));
    expect(await screen.findByText('Equipment registered.')).toBeInTheDocument();
    expect(writes[0]).toMatchObject({ method: 'POST', body: { assetTag: 'CAM-018', name: 'New camera', category: 'Camera' } });
    await user.click(screen.getByRole('button', { name: 'Edit Studio camera' }));
    const edit = screen.getByRole('form', { name: 'Equipment form' });
    await user.clear(within(edit).getByRole('textbox', { name: 'Name' }));
    await user.type(within(edit).getByRole('textbox', { name: 'Name' }), 'Updated camera');
    await user.click(within(edit).getByRole('button', { name: 'Save equipment' }));
    expect(await screen.findByText('Equipment updated.')).toBeInTheDocument();
    expect(writes[1]).toMatchObject({ method: 'PUT', body: { expectedVersion: 3, name: 'Updated camera' } });
    await user.click(screen.getByRole('button', { name: 'Retire Updated camera' }));
    const dialog = screen.getByRole('alertdialog', { name: 'Retire this equipment?' });
    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }));
    expect(writes).toHaveLength(2);
    await user.click(screen.getByRole('button', { name: 'Retire Updated camera' }));
    await user.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Retire equipment' }));
    expect(await screen.findByText('Equipment retired.')).toBeInTheDocument();
    expect(writes[2]).toEqual({ method: 'RETIRE', body: { expectedVersion: 4 } });
  });

  it('creates and renames a category', async () => {
    let categories = ['Camera'];
    const writes: string[] = [];
    server.use(
      http.get('/api/v1/equipment/manage', () => HttpResponse.json([])),
      http.get('/api/v1/equipment/manage/categories', () => HttpResponse.json(categories)),
      http.post('/api/v1/equipment/manage/categories', async ({ request }) => {
        const body = await request.json() as { name: string };
        writes.push(body.name); categories = [...categories, body.name];
        return HttpResponse.json(categories, { status: 201 });
      }),
      http.patch('/api/v1/equipment/manage/categories/:name', async ({ request }) => {
        const body = await request.json() as { name: string };
        writes.push(body.name); categories = ['Camera', body.name];
        return HttpResponse.json(categories);
      }),
    );
    mount(['ADMIN']);
    const user = userEvent.setup();
    await screen.findByRole('heading', { name: 'Categories' });
    await user.type(screen.getByRole('textbox', { name: 'New category' }), 'Tripod');
    await user.click(screen.getByRole('button', { name: 'Create category' }));
    expect(await screen.findByText('Category created.')).toBeInTheDocument();
    await user.selectOptions(screen.getByRole('combobox', { name: 'Category to rename' }), 'Tripod');
    await user.type(screen.getByRole('textbox', { name: 'New name' }), 'Support');
    await user.click(screen.getByRole('button', { name: 'Rename category' }));
    expect(await screen.findByText('Category renamed.')).toBeInTheDocument();
    expect(writes).toEqual(['Tripod', 'Support']);
  });
});
