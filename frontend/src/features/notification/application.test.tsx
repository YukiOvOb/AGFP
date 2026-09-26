import { describe, it, expect } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { App } from '@/app/App';
import { AuthProvider } from '@/app/AuthProvider';
import { RequireRole } from '@/app/RequireRole';
import { server, currentUser } from '@/test/server';
function mount(path = '/notifications', children = <App />) {
  return render(
    <QueryClientProvider
      client={
        new QueryClient({
          defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
        })
      }
    >
      <MemoryRouter initialEntries={[path]}>
        <AuthProvider>{children}</AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
describe('session and inbox', () => {
  it('shows an empty inbox for an authenticated user', async () => {
    mount();
    expect(await screen.findByText('You’re all caught up')).toBeInTheDocument();
    expect(screen.getByText('Wang Yuanmeng')).toBeInTheDocument();
  });
  it('validates login and sends a CSRF header without storing credentials', async () => {
    server.use(http.get('/api/v1/auth/me', () => new HttpResponse(null, { status: 401 })));
    let submitted = false;
    server.use(
      http.post('/api/v1/auth/login', async ({ request }) => {
        expect(request.headers.get('X-XSRF-TOKEN')).toBe('test-csrf');
        expect(await request.json()).toEqual({
          email: 'wang@example.com',
          password: 'test-password',
        });
        submitted = true;
        return HttpResponse.json(currentUser);
      }),
    );
    mount('/login');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText('Enter a valid email address.')).toBeInTheDocument();
    await user.type(screen.getByLabelText('Email address'), 'wang@example.com');
    await user.type(screen.getByLabelText('Password'), 'test-password');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText('You’re all caught up')).toBeInTheDocument();
    expect(submitted).toBe(true);
    expect(localStorage.length).toBe(0);
  });
  it('renders notification text safely, uses SGT and marks read through the authenticated endpoint', async () => {
    let read = false;
    server.use(
      http.get('/api/v1/notifications', () =>
        HttpResponse.json({
          content: [
            {
              id: 'a',
              type: 'OVERDUE',
              content: '<script>alert(1)</script>',
              deliveredAt: '2026-09-24T10:00:00Z',
              readAt: read ? '2026-09-24T10:01:00Z' : null,
            },
          ],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
        }),
      ),
      http.post('/api/v1/notifications/a/read', ({ request }) => {
        expect(request.headers.get('X-XSRF-TOKEN')).toBe('test-csrf');
        read = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    mount();
    expect(await screen.findByText('<script>alert(1)</script>')).toBeInTheDocument();
    expect(document.querySelector('script')).toBeNull();
    expect(screen.getByText('18:00')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Mark as read' }));
    expect(await screen.findByText('Reviewed')).toBeInTheDocument();
  });
  it('offers retry on a failed inbox request', async () => {
    server.use(
      http.get('/api/v1/notifications', () =>
        HttpResponse.json({ detail: 'Temporarily unavailable' }, { status: 503 }),
      ),
    );
    mount();
    expect(await screen.findByText('Temporarily unavailable')).toBeInTheDocument();
    server.use(
      http.get('/api/v1/notifications', () =>
        HttpResponse.json({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
      ),
    );
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByText('You’re all caught up')).toBeInTheDocument();
  });
  it('does not grant an ADMIN the APPROVER role', async () => {
    server.use(
      http.get('/api/v1/auth/me', () => HttpResponse.json({ ...currentUser, roles: ['ADMIN'] })),
    );
    mount(
      '/approval',
      <Routes>
        <Route element={<RequireRole role="APPROVER" />}>
          <Route path="/approval" element={<p>Protected approval</p>} />
        </Route>
      </Routes>,
    );
    await waitFor(() => expect(screen.queryByRole('status')).not.toBeInTheDocument());
    expect(screen.queryByText('Protected approval')).not.toBeInTheDocument();
    expect(
      await screen.findByText('Your account does not have access to this page.'),
    ).toBeInTheDocument();
  });
});
