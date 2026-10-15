import { describe, expect, it } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { App } from '@/app/App';
import { AuthProvider } from '@/app/AuthProvider';
import { currentUser, server } from '@/test/server';

const account = {
  userId: '10000000-0000-4000-8000-000000000099',
  email: 'person@example.test',
  displayName: 'Person',
  accountStatus: 'ACTIVE',
  roles: ['BORROWER'],
  version: 3,
};
function setup(row = account) {
  server.use(
    http.get('/api/v1/auth/me', () => HttpResponse.json({ ...currentUser, roles: ['ADMIN'] })),
    http.get('/api/v1/admin/users', () =>
      HttpResponse.json({ content: [row], totalElements: 1, page: 0, size: 20 }),
    ),
  );
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/admin/users']}>
        <AuthProvider>
          <App />
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return client;
}
async function open(operation: string) {
  await userEvent.click(
    await screen.findByRole('button', { name: `Manage ${operation} for ${account.email}` }),
  );
  return within(screen.getByRole('dialog'));
}

describe('administrator account security', () => {
  it('sends only explicit selected roles and the opened version with CSRF', async () => {
    setup();
    let sent: Record<string, unknown> | undefined;
    server.use(
      http.patch('/api/v1/admin/users/:id/roles', async ({ request }) => {
        expect(request.headers.get('X-XSRF-TOKEN')).toBe('test-csrf');
        sent = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ ...account, roles: ['ADMIN'], version: 4 });
      }),
    );
    const dialog = await open('roles');
    await userEvent.click(dialog.getByLabelText('Assigned Borrower'));
    await userEvent.click(dialog.getByLabelText('Assigned Administrator'));
    await userEvent.click(dialog.getByRole('button', { name: 'Save roles' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(sent).toEqual({ roles: ['ADMIN'], expectedVersion: 3 });
  });

  it('requires a nonempty role selection', async () => {
    setup();
    const dialog = await open('roles');
    await userEvent.click(dialog.getByLabelText('Assigned Borrower'));
    await userEvent.click(dialog.getByRole('button', { name: 'Save roles' }));
    expect(await dialog.findByText('Select at least one role.')).toBeVisible();
  });

  it.each(['roles', 'status', 'password'])(
    'allows cancel without a %s request and restores focus',
    async (operation) => {
      setup();
      const dialog = await open(operation);
      await userEvent.click(dialog.getByRole('button', { name: 'Cancel' }));
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
      expect(
        screen.getByRole('button', { name: `Manage ${operation} for ${account.email}` }),
      ).toHaveFocus();
    },
  );

  it('requires explicit disable confirmation and sends the version', async () => {
    setup();
    let sent: unknown;
    server.use(
      http.patch('/api/v1/admin/users/:id/status', async ({ request }) => {
        sent = await request.json();
        return HttpResponse.json({ ...account, accountStatus: 'DISABLED', version: 4 });
      }),
    );
    const dialog = await open('status');
    expect(dialog.getByText(/unable to sign in/)).toBeVisible();
    expect(sent).toBeUndefined();
    await userEvent.click(dialog.getByRole('button', { name: 'Confirm disable' }));
    await waitFor(() => expect(sent).toEqual({ active: false, expectedVersion: 3 }));
  });

  it('activates a disabled account without restoring revoked sessions', async () => {
    setup({ ...account, accountStatus: 'DISABLED' });
    let sent: unknown;
    server.use(
      http.patch('/api/v1/admin/users/:id/status', async ({ request }) => {
        sent = await request.json();
        return HttpResponse.json({ ...account, version: 4 });
      }),
    );
    const dialog = await open('status');
    expect(dialog.getByText(/Previously revoked sessions will not be restored/)).toBeVisible();
    await userEvent.click(dialog.getByRole('button', { name: 'Confirm activation' }));
    await waitFor(() => expect(sent).toEqual({ active: true, expectedVersion: 3 }));
  });

  it('blocks self-deactivation in the UI as well as on the server', async () => {
    setup({ ...account, userId: currentUser.userId });
    expect(
      await screen.findByRole('button', { name: `Manage status for ${account.email}` }),
    ).toBeDisabled();
  });

  it('shows stale/last-admin errors and blocks overwrite until reopening', async () => {
    setup();
    let calls = 0;
    server.use(
      http.patch('/api/v1/admin/users/:id/roles', () => {
        calls++;
        return HttpResponse.json(
          {
            type: '/problems/last-active-admin',
            detail: 'The last active administrator must be retained.',
          },
          { status: 409 },
        );
      }),
    );
    const dialog = await open('roles');
    await userEvent.click(dialog.getByRole('button', { name: 'Save roles' }));
    expect(await dialog.findByRole('alert')).toHaveTextContent('last active administrator');
    expect(dialog.getByRole('button', { name: 'Save roles' })).toBeDisabled();
    expect(calls).toBe(1);
  });

  it('validates reset password and clears secrets on cancellation and errors', async () => {
    const client = setup();
    server.use(
      http.post('/api/v1/admin/users/:id/password-reset', () =>
        HttpResponse.json({ detail: 'Try again later.' }, { status: 500 }),
      ),
    );
    let dialog = await open('password');
    await userEvent.click(dialog.getByRole('button', { name: 'Save new password' }));
    expect(await dialog.findByText('Use at least 12 non-padding characters.')).toBeVisible();
    await userEvent.type(dialog.getByLabelText('New password'), 'replacement-test-password');
    await userEvent.click(dialog.getByRole('button', { name: 'Save new password' }));
    expect(await dialog.findByRole('alert')).toHaveTextContent('Try again later.');
    expect(dialog.getByLabelText('New password')).toHaveValue('');
    await userEvent.type(dialog.getByLabelText('New password'), 'cancelled-test-password');
    await userEvent.click(dialog.getByRole('button', { name: 'Cancel' }));
    dialog = await open('password');
    expect(dialog.getByLabelText('New password')).toHaveValue('');
    expect(
      JSON.stringify(
        client
          .getQueryCache()
          .getAll()
          .map((q) => q.state.data),
      ),
    ).not.toContain('test-password');
    expect(client.getMutationCache().getAll()).toHaveLength(0);
  });

  it('resets with the opened version and closes without putting the password in a cache', async () => {
    const client = setup();
    let sent: unknown;
    server.use(
      http.post('/api/v1/admin/users/:id/password-reset', async ({ request }) => {
        sent = await request.json();
        return HttpResponse.json({ ...account, version: 4 });
      }),
    );
    const dialog = await open('password');
    await userEvent.type(dialog.getByLabelText('New password'), 'replacement-test-password');
    await userEvent.click(dialog.getByRole('button', { name: 'Save new password' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(sent).toEqual({ newPassword: 'replacement-test-password', expectedVersion: 3 });
    expect(client.getMutationCache().getAll()).toHaveLength(0);
  });
});
