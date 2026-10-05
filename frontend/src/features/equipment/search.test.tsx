import { describe, it, expect } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { App } from '@/app/App';
import { AuthProvider } from '@/app/AuthProvider';
import { server, currentUser } from '@/test/server';
const ITEM = '00000000-0000-0000-0000-0000000000e1';
const camera = {
  equipmentId: ITEM,
  assetTag: 'CAM-001',
  name: 'Canon EOS R6',
  category: 'Camera',
  location: 'COM1 Lab',
  status: 'AVAILABLE',
  requiresApproval: false,
  availableInWindow: null,
};
function page(content: object[]) {
  return {
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages: content.length ? 1 : 0,
  };
}
function LocationProbe() {
  const location = useLocation();
  return <output data-testid="location">{location.pathname + location.search}</output>;
}
function mount(path: string) {
  return render(
    <QueryClientProvider
      client={
        new QueryClient({
          defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
        })
      }
    >
      <MemoryRouter initialEntries={[path]}>
        <AuthProvider>
          <App />
          <Routes>
            <Route path="*" element={<LocationProbe />} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
function withSearch(handler: (url: URL) => object) {
  const seen: URL[] = [];
  server.use(
    http.get('/api/v1/equipment/filters', () =>
      HttpResponse.json({
        categories: ['Camera', 'Tripod'],
        locations: ['COM1 Lab', 'UTown Studio'],
      }),
    ),
    http.get('/api/v1/equipment', ({ request }) => {
      const url = new URL(request.url);
      seen.push(url);
      return HttpResponse.json(handler(url));
    }),
  );
  return seen;
}
describe('equipment search (UC01)', () => {
  it('lists equipment and links borrowers to the reserve page', async () => {
    withSearch(() => page([camera]));
    mount('/equipment');
    expect(await screen.findByText('Canon EOS R6')).toBeInTheDocument();
    expect(screen.getByText('CAM-001')).toBeInTheDocument();
    expect(screen.getByText('Pick a time')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Reserve Canon EOS R6' })).toHaveAttribute(
      'href',
      `/equipment/${ITEM}/reserve`,
    );
  });
  it('sends filters as UTC, keeps them in the URL and shows window availability', async () => {
    const seen = withSearch((url) =>
      page(url.searchParams.get('from') ? [{ ...camera, availableInWindow: false }] : [camera]),
    );
    mount('/equipment');
    await screen.findByText('Canon EOS R6');
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('Name or asset tag'), 'canon');
    await screen.findByRole('option', { name: 'Camera' });
    await user.selectOptions(screen.getByLabelText('Category'), 'Camera');
    await user.type(screen.getByLabelText('From (SGT)'), '2030-01-10T09:00');
    await user.type(screen.getByLabelText('Until (SGT)'), '2030-01-10T11:00');
    await user.click(screen.getByRole('button', { name: 'Search' }));
    expect(await screen.findByText('Not free')).toBeInTheDocument();
    const last = seen.at(-1)!;
    expect(last.searchParams.get('q')).toBe('canon');
    expect(last.searchParams.get('category')).toBe('Camera');
    expect(last.searchParams.get('from')).toBe('2030-01-10T01:00:00Z');
    expect(last.searchParams.get('to')).toBe('2030-01-10T03:00:00Z');
    expect(screen.getByTestId('location')).toHaveTextContent(
      '/equipment?q=canon&category=Camera&from=2030-01-10T09%3A00&to=2030-01-10T11%3A00',
    );
    // Not free in the chosen window: the action becomes "Availability".
    expect(screen.getByRole('link', { name: 'View availability of Canon EOS R6' })).toHaveAttribute(
      'href',
      `/equipment/${ITEM}/reserve?from=2030-01-10T09%3A00&to=2030-01-10T11%3A00`,
    );
  });
  it('restores a shared search link and can show only free equipment', async () => {
    const seen = withSearch(() => page([{ ...camera, availableInWindow: true }]));
    mount('/equipment?location=COM1+Lab&from=2030-01-10T09:00&to=2030-01-10T11:00&available=1');
    expect(await screen.findByText('Free')).toBeInTheDocument();
    expect(screen.getByLabelText('From (SGT)')).toHaveValue('2030-01-10T09:00');
    expect(screen.getByLabelText('Only show free equipment')).toBeChecked();
    const last = seen.at(-1)!;
    expect(last.searchParams.get('availableOnly')).toBe('true');
    expect(last.searchParams.get('location')).toBe('COM1 Lab');
  });
  it('validates the time window before searching', async () => {
    const seen = withSearch(() => page([camera]));
    mount('/equipment');
    await screen.findByText('Canon EOS R6');
    const before = seen.length;
    const user = userEvent.setup();
    expect(screen.getByLabelText('Only show free equipment')).toBeDisabled();
    await user.type(screen.getByLabelText('From (SGT)'), '2030-01-10T11:00');
    await user.click(screen.getByRole('button', { name: 'Search' }));
    expect(await screen.findByText('Enter an end time too.')).toBeInTheDocument();
    await user.type(screen.getByLabelText('Until (SGT)'), '2030-01-10T09:00');
    await user.click(screen.getByRole('button', { name: 'Search' }));
    expect(await screen.findByText('The end must be after the start.')).toBeInTheDocument();
    expect(seen.length).toBe(before);
  });
  it('shows an empty state and a retry on failure', async () => {
    let fail = true;
    withSearch(() => page([]));
    server.use(
      http.get('/api/v1/equipment', () =>
        fail
          ? HttpResponse.json({ detail: 'Search is temporarily unavailable' }, { status: 503 })
          : HttpResponse.json(page([])),
      ),
    );
    mount('/equipment');
    expect(await screen.findByText('Search is temporarily unavailable')).toBeInTheDocument();
    fail = false;
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByText('No equipment matches')).toBeInTheDocument();
  });
  it('approvers can search but are offered availability instead of reserving', async () => {
    server.use(
      http.get('/api/v1/auth/me', () => HttpResponse.json({ ...currentUser, roles: ['APPROVER'] })),
    );
    withSearch(() => page([camera]));
    mount('/equipment');
    const row = (await screen.findByText('Canon EOS R6')).closest('tr')!;
    expect(
      within(row).getByRole('link', { name: 'View availability of Canon EOS R6' }),
    ).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /My reservations/ })).not.toBeInTheDocument();
    await waitFor(() =>
      expect(screen.getByRole('link', { name: /Find equipment/ })).toBeInTheDocument(),
    );
  });
});
