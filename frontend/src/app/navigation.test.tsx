import { describe, expect, it } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { App } from '@/app/App';
import { AuthProvider } from '@/app/AuthProvider';
import { navigation, visibleNavigation, type NavItem } from '@/app/navigation';
import { hasAnyRole, roleLabel, roleLabels } from '@/lib/roles';
import { currentUser, server } from '@/test/server';
function mount(path: string, roles: string[], displayName = 'Wang Yuanmeng') {
  server.use(
    http.get('/api/v1/auth/me', () => HttpResponse.json({ ...currentUser, displayName, roles })),
  );
  return render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <MemoryRouter initialEntries={[path]}>
        <AuthProvider>
          <App />
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
const menu = () =>
  within(screen.getByRole('navigation', { name: 'Main navigation' }))
    .getAllByRole('link')
    .map((link) => link.textContent);
describe('role names', () => {
  it('shows the Proposal wording for stored role codes', () => {
    expect(roleLabel('CUSTODIAN')).toBe('Storekeeper');
    expect(roleLabel('MAINTAINER')).toBe('Technician');
    expect(roleLabel('ADMIN')).toBe('Administrator');
    expect(roleLabel('AUDITOR')).toBe('AUDITOR');
  });
  it('lists roles in one fixed order whatever the API order', () => {
    expect(roleLabels(['ADMIN', 'BORROWER', 'AUDITOR'])).toEqual([
      'Borrower',
      'Administrator',
      'AUDITOR',
    ]);
    expect(roleLabels(undefined)).toEqual([]);
  });
  it('does not treat one role as another', () => {
    expect(hasAnyRole(['ADMIN'], 'APPROVER')).toBe(false);
    expect(hasAnyRole(['ADMIN', 'APPROVER'], 'APPROVER')).toBe(true);
    expect(hasAnyRole(undefined, 'BORROWER')).toBe(false);
  });
});
describe('navigation list', () => {
  it('hides reserved pages until their route exists', () => {
    const reserved = navigation.filter((item) => !item.available).map((item) => item.to);
    expect(reserved).toEqual(['/manage/equipment']);
    expect(visibleNavigation(['ADMIN', 'CUSTODIAN']).some((item) => !item.available)).toBe(false);
  });
  it('shows a reserved page to its roles once it is switched on', () => {
    const items: NavItem[] = navigation.map((item) => ({ ...item, available: true }));
    const paths = (roles: string[]) => visibleNavigation(roles, items).map((item) => item.to);
    expect(paths(['BORROWER'])).not.toContain('/admin/users');
    expect(paths(['ADMIN'])).toEqual(
      expect.arrayContaining(['/admin/users', '/admin/audit', '/reports', '/overdue']),
    );
    expect(paths(['CUSTODIAN'])).toEqual(expect.arrayContaining(['/overdue', '/manage/equipment']));
    expect(paths(['CUSTODIAN'])).not.toContain('/reports');
  });
});
describe('overview and menu', () => {
  it('lands a borrower on the overview with one card per page they can use', async () => {
    server.use(
      http.get('/api/v1/notifications/unread-count', () => HttpResponse.json({ count: 2 })),
    );
    mount('/', ['BORROWER']);
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Welcome, Wang Yuanmeng' }),
    ).toBeInTheDocument();
    const cards = within(screen.getByRole('navigation', { name: 'Your pages' }));
    expect(cards.getAllByRole('link')).toHaveLength(5);
    expect(cards.getByRole('link', { name: /My reservations/ })).toHaveAttribute(
      'href',
      '/reservations',
    );
    // The unread count arrives after the page; it then shows on the card and in the menu.
    expect(await cards.findByText('2 unread')).toBeInTheDocument();
    expect(menu()).toEqual([
      'Overview',
      'Find equipment',
      'Loans and returns',
      'My reservations',
      'Maintenance',
      'Notifications2',
    ]);
    expect(screen.getByLabelText('2 unread notifications')).toBeInTheDocument();
    expect(
      within(screen.getByRole('list', { name: 'Your roles' })).getByText('Borrower'),
    ).toBeInTheDocument();
  });
  it('gives an approver-administrator their own pages and role names', async () => {
    mount('/', ['ADMIN', 'APPROVER'], 'Robin Lee');
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Welcome, Robin Lee' }),
    ).toBeInTheDocument();
    expect(menu()).toEqual([
      'Overview',
      'Find equipment',
      'Maintenance',
      'Approvals',
      'Notifications',
    ]);
    expect(screen.getByText('Approver · Administrator')).toBeInTheDocument();
    // Both delivered management pages follow their order in the navigation list.
    expect(
      within(screen.getByRole('navigation', { name: 'Management' }))
        .getAllByRole('link')
        .map((link) => link.textContent),
    ).toEqual(['Overdue', 'Reports', 'Users and roles', 'Audit log']);
  });
  it('opens a page from its card and returns through the brand link', async () => {
    mount('/', ['BORROWER']);
    const cards = within(await screen.findByRole('navigation', { name: 'Your pages' }));
    await userEvent.click(cards.getByRole('link', { name: /Notifications/ }));
    expect(await screen.findByText('You’re all caught up')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('link', { name: /SERMS/ }));
    expect(await screen.findByRole('heading', { level: 1, name: /Welcome/ })).toBeInTheDocument();
  });
  it('offers a way back from an unknown address', async () => {
    mount('/no-such-page', ['BORROWER']);
    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('link', { name: 'Back to the overview' }));
    expect(await screen.findByRole('heading', { level: 1, name: /Welcome/ })).toBeInTheDocument();
  });
});
