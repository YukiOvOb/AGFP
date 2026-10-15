import { beforeAll, describe, expect, it } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import ReportsPage from './ReportsPage';
import { customPeriod, formatHours, formatPercent, lastDays, presetOf } from './period';
import type { Report } from './queries';
// Recharts measures its container; jsdom has no layout, so the chart tab renders no bars here.
// The numbers are checked through the summary and the table tab, which carry the same data.
beforeAll(() => {
  globalThis.ResizeObserver ??= class {
    observe() {}
    unobserve() {}
    disconnect() {}
  };
});
const report = (overrides: Partial<Report> = {}): Report => ({
  from: '2026-09-14T16:00:00Z',
  to: '2026-10-14T16:00:00Z',
  measuredUntil: '2026-10-14T04:00:00Z',
  generatedAt: '2026-10-14T04:00:00Z',
  reservationsByCategory: [
    {
      category: 'Photography',
      total: 12,
      pendingApproval: 2,
      confirmed: 5,
      fulfilled: 3,
      rejected: 1,
      cancelled: 1,
    },
    {
      category: 'Audio',
      total: 4,
      pendingApproval: 0,
      confirmed: 2,
      fulfilled: 2,
      rejected: 0,
      cancelled: 0,
    },
  ],
  equipmentUtilisation: [
    {
      equipmentId: 'a',
      assetTag: 'CAM-01',
      name: 'Camera',
      category: 'Photography',
      status: 'ON_LOAN',
      loans: 3,
      loanedHours: 300,
      utilisation: 0.42,
    },
    {
      equipmentId: 'b',
      assetTag: 'MIC-01',
      name: 'Microphone',
      category: 'Audio',
      status: 'AVAILABLE',
      loans: 1,
      loanedHours: 24,
      utilisation: 0.03,
    },
  ],
  utilisationByCategory: [
    { category: 'Photography', equipment: 1, loanedHours: 300, utilisation: 0.42 },
    { category: 'Audio', equipment: 1, loanedHours: 24, utilisation: 0.03 },
  ],
  overdueNow: [
    {
      loanId: 'l1',
      equipmentId: 'a',
      assetTag: 'CAM-01',
      equipmentName: 'Camera',
      category: 'Photography',
      borrowerName: 'Alex Borrower',
      dueAt: '2026-10-12T04:00:00Z',
      overdueHours: 48,
    },
    {
      loanId: 'l2',
      equipmentId: 'b',
      assetTag: 'MIC-01',
      equipmentName: 'Microphone',
      category: 'Audio',
      borrowerName: 'Jamie Lee',
      dueAt: '2026-10-14T01:00:00Z',
      overdueHours: 3,
    },
  ],
  overdueByCategory: [{ category: 'Photography', loansDue: 4, returnedLate: 1, stillOverdue: 1 }],
  maintenance: {
    casesByStatus: { OPEN: 2, ASSIGNED: 1, IN_PROGRESS: 0, RESOLVED: 5, UNREPAIRABLE: 1 },
    reportedInPeriod: 3,
    completedInPeriod: 2,
    averageCompletionHours: 30,
  },
  maintenanceByEquipment: [
    { equipmentId: 'a', assetTag: 'CAM-01', name: 'Camera', category: 'Photography', cases: 2 },
  ],
  ...overrides,
});
function Address() {
  const location = useLocation();
  return <output data-testid="address">{location.search}</output>;
}
function mount(path = '/reports') {
  return render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route
            path="/reports"
            element={
              <>
                <ReportsPage />
                <Address />
              </>
            }
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
function serve(body: Report | ((url: URL) => Report)) {
  const seen: URL[] = [];
  server.use(
    http.get('/api/v1/reports', ({ request }) => {
      const url = new URL(request.url);
      seen.push(url);
      return HttpResponse.json(typeof body === 'function' ? body(url) : body);
    }),
  );
  return seen;
}
describe('reports page', () => {
  it('summarises the period in headline figures', async () => {
    serve(report());
    mount();
    const summary = within(await screen.findByRole('region', { name: 'Summary' }));
    expect(summary.getByText('16')).toBeInTheDocument(); // reservations
    // 324 h on loan over 2 items for 30 days less 12 h of the period still ahead.
    expect(summary.getByText('23%')).toBeInTheDocument();
    expect(summary.getByText('Needs follow-up')).toBeInTheDocument();
    expect(summary.getByText('50%')).toBeInTheDocument(); // 2 of 4 loans due were late
    expect(summary.getByText('2 of 4 loans due')).toBeInTheDocument();
    expect(summary.getByText('30 h')).toBeInTheDocument(); // average repair time
  });
  it('lists overdue loans longest first and offers every chart as a table', async () => {
    serve(report());
    mount();
    const overdue = await screen.findByRole('table', { name: 'Loans that are overdue now' });
    const rows = within(overdue).getAllByRole('row').slice(1);
    expect(rows.map((r) => within(r).getAllByRole('cell')[2].textContent)).toEqual([
      'Alex Borrower',
      'Jamie Lee',
    ]);
    expect(within(rows[0]).getByText('2 d')).toBeInTheDocument();
    const reservations = screen.getByRole('tablist', { name: 'Reservations by category: view' });
    await userEvent.click(within(reservations).getByRole('tab', { name: 'Table' }));
    const table = screen.getByRole('table', { name: 'Reservations by category' });
    expect(within(table).getByRole('row', { name: /Photography 12/ })).toBeInTheDocument();
  });
  it('requests the chosen period and keeps it in the address', async () => {
    const seen = serve((url) =>
      report({ from: url.searchParams.get('from')!, to: url.searchParams.get('to')! }),
    );
    mount();
    expect(await screen.findByRole('button', { name: 'Last 30 days' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    await userEvent.click(screen.getByRole('button', { name: 'Last 7 days' }));
    expect(screen.getByRole('button', { name: 'Last 7 days' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    const expected = lastDays(7);
    await screen.findByText(
      (_, el) =>
        el?.textContent ===
        `?from=${encodeURIComponent(expected.from)}&to=${encodeURIComponent(expected.to)}`,
    );
    expect(seen.at(-1)!.searchParams.get('from')).toBe(expected.from);
    expect(seen.at(-1)!.searchParams.get('to')).toBe(expected.to);
  });
  it('rejects a custom range that ends before it starts', async () => {
    const seen = serve(report());
    mount();
    await screen.findByRole('region', { name: 'Summary' });
    const before = seen.length;
    await userEvent.clear(screen.getByLabelText('First day'));
    await userEvent.type(screen.getByLabelText('First day'), '2026-10-10');
    await userEvent.clear(screen.getByLabelText('Last day'));
    await userEvent.type(screen.getByLabelText('Last day'), '2026-10-01');
    await userEvent.click(screen.getByRole('button', { name: 'Show period' }));
    expect(screen.getByRole('alert')).toHaveTextContent('first day on or before the last day');
    expect(seen.length).toBe(before);
  });
  it('explains empty sections instead of drawing empty charts', async () => {
    serve(
      report({
        reservationsByCategory: [],
        overdueNow: [],
        overdueByCategory: [],
        maintenanceByEquipment: [],
        maintenance: { casesByStatus: {}, reportedInPeriod: 0, completedInPeriod: 0 },
      }),
    );
    mount();
    expect(await screen.findByText('No reservations')).toBeInTheDocument();
    expect(screen.getByText('Nothing is overdue')).toBeInTheDocument();
    expect(screen.getByText('No faults reported')).toBeInTheDocument();
    expect(screen.getByText('None outstanding')).toBeInTheDocument();
    expect(screen.getByText('No case completed in the period')).toBeInTheDocument();
  });
  it('shows the problem and retries', async () => {
    server.use(
      http.get('/api/v1/reports', () =>
        HttpResponse.json({ detail: 'Reports are unavailable' }, { status: 503 }),
      ),
    );
    mount();
    expect(await screen.findByText('Reports are unavailable')).toBeInTheDocument();
    serve(report());
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByRole('region', { name: 'Summary' })).toBeInTheDocument();
  });
});
describe('report periods', () => {
  const now = new Date('2026-10-14T04:00:00Z'); // 12:00 in Singapore
  it('counts whole Singapore days including today', () => {
    expect(lastDays(7, now)).toEqual({
      from: '2026-10-07T16:00:00.000Z',
      to: '2026-10-14T16:00:00.000Z',
    });
    expect(presetOf(lastDays(30, now), now)).toBe(30);
    expect(
      presetOf({ from: '2026-10-01T00:00:00Z', to: '2026-10-02T00:00:00Z' }, now),
    ).toBeUndefined();
  });
  it('turns a custom range into whole days and refuses invalid ones', () => {
    expect(customPeriod('2026-10-01', '2026-10-01')).toEqual({
      from: '2026-09-30T16:00:00.000Z',
      to: '2026-10-01T16:00:00.000Z',
    });
    expect(customPeriod('2026-10-02', '2026-10-01')).toBeNull();
    expect(customPeriod('2025-01-01', '2026-10-01')).toBeNull();
    expect(customPeriod('', '2026-10-01')).toBeNull();
  });
  it('shows hours under two days and days after', () => {
    expect(formatHours(3)).toBe('3 h');
    expect(formatHours(47.5)).toBe('47.5 h');
    expect(formatHours(72)).toBe('3 d');
    expect(formatHours(0.01)).toBe('< 0.1 h');
    expect(formatHours(0)).toBe('0 h');
    expect(formatPercent(0.001)).toBe('<1%');
    expect(formatPercent(0)).toBe('0%');
    expect(formatPercent(0.425)).toBe('43%');
  });
});
