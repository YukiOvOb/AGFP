import { describe, expect, it } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
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
  roles: ['BORROWER', 'APPROVER'],
};
const auditEntry = {
  auditId: '10000000-0000-4000-8000-000000000098',
  actorId: currentUser.userId,
  actorName: 'Admin Person',
  action: 'USER_CREATED',
  entityType: 'User',
  entityId: account.userId,
  outcome: 'SUCCESS',
  changeSummary: '<script>not executed</script>',
  occurredAt: '2026-10-15T02:00:00Z',
};
function setup(route: string, roles = ['ADMIN']) {
  server.use(
    http.get('/api/v1/auth/me', () => HttpResponse.json({ ...currentUser, roles })),
    http.get('/api/v1/admin/users', () =>
      HttpResponse.json({ content: [account], totalElements: 1, page: 0, size: 20 }),
    ),
    http.get('/api/v1/admin/audit', () =>
      HttpResponse.json({ content: [auditEntry], totalElements: 1, page: 0, size: 20 }),
    ),
  );
  return () =>
    render(
      <QueryClientProvider
        client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
      >
        <MemoryRouter initialEntries={[route]}>
          <AuthProvider>
            <App />
          </AuthProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    );
}
async function completeForm() {
  await userEvent.type(screen.getByLabelText('Email'), account.email);
  await userEvent.type(screen.getByLabelText('Display name'), account.displayName);
  await userEvent.type(screen.getByLabelText('Initial password'), 'correct horse battery');
}

describe('admin account foundation', () => {
  it('shows the directory with shared role labels and no password', async () => {
    setup('/admin/users')();
    expect(await screen.findByText(account.email)).toBeVisible();
    expect(screen.getByText('Borrower · Approver')).toBeVisible();
    expect(screen.queryByText('passwordHash')).not.toBeInTheDocument();
  });

  it('creates with explicit initial roles and clears the password after success', async () => {
    setup('/admin/users')();
    let body: Record<string, unknown> | undefined;
    server.use(
      http.post('/api/v1/admin/users', async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>;
        expect(request.headers.get('X-XSRF-TOKEN')).toBe('test-csrf');
        return HttpResponse.json(account, { status: 201 });
      }),
    );
    await screen.findByText(account.email);
    await completeForm();
    await userEvent.click(screen.getByLabelText('Approver'));
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));
    expect(await screen.findByText('Account created. The user can now sign in.')).toBeVisible();
    expect(body?.roles).toEqual(['BORROWER', 'APPROVER']);
    expect(screen.getByLabelText('Initial password')).toHaveValue('');
    expect(screen.getByLabelText('Administrator')).not.toBeChecked();
  });

  it('validates name, password length and initial role selection before submitting', async () => {
    setup('/admin/users')();
    await screen.findByText(account.email);
    await userEvent.type(screen.getByLabelText('Email'), account.email);
    await userEvent.click(screen.getByLabelText('Borrower'));
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));
    expect(await screen.findByText('Enter a display name.')).toBeVisible();
    expect(screen.getByText('Use at least 12 non-padding characters.')).toBeVisible();
    expect(screen.getByText('Select at least one role.')).toBeVisible();
  });

  it('puts duplicate-email errors next to the email field', async () => {
    setup('/admin/users')();
    server.use(
      http.post('/api/v1/admin/users', () =>
        HttpResponse.json({ detail: 'Duplicate' }, { status: 409 }),
      ),
    );
    await screen.findByText(account.email);
    await completeForm();
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));
    expect(await screen.findByText('This email address is already in use.')).toBeVisible();
  });

  it('shows create failures safely without echoing the password', async () => {
    setup('/admin/users')();
    server.use(
      http.post('/api/v1/admin/users', () =>
        HttpResponse.json({ detail: 'Service unavailable.' }, { status: 503 }),
      ),
    );
    await screen.findByText(account.email);
    await completeForm();
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));
    expect(await screen.findByText('Service unavailable.')).toBeVisible();
    expect(screen.queryByText('correct horse battery')).not.toBeInTheDocument();
  });

  it('searches on submit and uses server pagination', async () => {
    const mount = setup('/admin/users');
    const queries: URL[] = [];
    server.use(
      http.get('/api/v1/admin/users', ({ request }) => {
        queries.push(new URL(request.url));
        return HttpResponse.json({
          content: [account],
          totalElements: 21,
          page: Number(queries.at(-1)!.searchParams.get('page')),
          size: 20,
        });
      }),
    );
    mount();
    await screen.findByText(account.email);
    await userEvent.click(screen.getByRole('button', { name: 'Next page' }));
    await waitFor(() => expect(queries.at(-1)!.searchParams.get('page')).toBe('1'));
    await userEvent.type(screen.getByLabelText('Search users'), 'literal%');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));
    await waitFor(() => expect(queries.at(-1)!.searchParams.get('search')).toBe('literal%'));
    expect(queries.at(-1)!.searchParams.get('page')).toBe('0');
  });

  it('shows empty and failed directory states', async () => {
    const mount = setup('/admin/users');
    server.use(
      http.get('/api/v1/admin/users', () =>
        HttpResponse.json({ content: [], totalElements: 0, page: 0, size: 20 }),
      ),
    );
    mount();
    expect(await screen.findByText('No users found')).toBeVisible();
  });

  it.each(['BORROWER', 'APPROVER', 'CUSTODIAN', 'MAINTAINER'])(
    'keeps %s out of both administrator pages',
    async (role) => {
      setup('/admin/users', [role])();
      expect(
        await screen.findByText('Your account does not have access to this page.'),
      ).toBeVisible();
      expect(
        screen.queryByRole('link', { name: 'Audit log' }),
      ).not.toBeInTheDocument();
    },
  );
});

describe('read-only audit log', () => {
  it('shows safe text, actor, target and Singapore time without mutation controls', async () => {
    setup('/admin/audit')();
    expect(await screen.findByText('Admin Person')).toBeVisible();
    expect(screen.getByText('15 Oct 2026, 10:00')).toBeVisible();
    expect(screen.getByText('<script>not executed</script>')).toBeVisible();
    expect(document.querySelector('main script')).toBeNull();
    expect(screen.queryByRole('button', { name: /delete|edit/i })).not.toBeInTheDocument();
  });

  it('submits every filter with Singapore-to-UTC conversion', async () => {
    const mount = setup('/admin/audit');
    let url: URL | undefined;
    server.use(
      http.get('/api/v1/admin/audit', ({ request }) => {
        url = new URL(request.url);
        return HttpResponse.json({ content: [auditEntry], totalElements: 1, page: 0, size: 20 });
      }),
    );
    mount();
    await screen.findByText('Admin Person');
    await userEvent.type(screen.getByLabelText('Actor UUID'), account.userId);
    await userEvent.type(screen.getByLabelText('Action'), 'USER_CREATED');
    await userEvent.type(screen.getByLabelText('Entity type'), 'User');
    await userEvent.type(screen.getByLabelText('From · SGT'), '2026-10-15T09:00');
    await userEvent.type(screen.getByLabelText('Until · SGT'), '2026-10-15T11:00');
    await userEvent.click(screen.getByRole('button', { name: 'Apply filters' }));
    await waitFor(() => expect(url?.searchParams.get('from')).toBe('2026-10-15T01:00:00Z'));
    expect(url?.searchParams.get('to')).toBe('2026-10-15T03:00:00Z');
    expect(url?.searchParams.get('actorId')).toBe(account.userId);
    expect(url?.searchParams.get('action')).toBe('USER_CREATED');
    expect(url?.searchParams.get('entityType')).toBe('User');
    await userEvent.click(screen.getByRole('button', { name: 'Clear' }));
    await waitFor(() => expect(screen.getByLabelText('Action')).toHaveValue(''));
  });

  it('validates actor identity rather than sending invalid queries', async () => {
    setup('/admin/audit')();
    await screen.findByText('Admin Person');
    await userEvent.type(screen.getByLabelText('Actor UUID'), 'invalid');
    await userEvent.click(screen.getByRole('button', { name: 'Apply filters' }));
    expect(await screen.findByText('Enter a valid actor UUID.')).toBeVisible();
  });
});
