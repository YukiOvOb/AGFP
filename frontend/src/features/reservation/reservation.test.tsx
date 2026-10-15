import { describe, it, expect } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { App } from '@/app/App';
import { AuthProvider } from '@/app/AuthProvider';
import { server, currentUser } from '@/test/server';
import { durationMinutes, durationText, reservationSchema } from './rules';
const ITEM = '00000000-0000-0000-0000-0000000000e1';
const RES = '00000000-0000-0000-0000-0000000000c1';
const equipment = {
  equipmentId: ITEM,
  assetTag: 'CAM-001',
  name: 'Canon EOS R6',
  category: 'Camera',
  location: 'COM1 Lab',
  status: 'AVAILABLE',
  requiresApproval: false,
  availableInWindow: true,
  description: null,
  maxBorrowDuration: null,
};
const reservation = {
  reservationId: RES,
  equipmentId: ITEM,
  assetTag: 'CAM-001',
  equipmentName: 'Canon EOS R6',
  location: 'COM1 Lab',
  startAt: '2030-01-10T01:00:00Z',
  endAt: '2030-01-10T03:00:00Z',
  status: 'CONFIRMED',
  purpose: 'Project shoot',
  createdAt: '2029-12-01T00:00:00Z',
  version: 0,
  cancellable: true,
};
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
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
function withAvailability(overrides: object = {}, busySlots: object[] = []) {
  server.use(
    http.get(`/api/v1/equipment/${ITEM}/availability`, ({ request }) => {
      const url = new URL(request.url);
      return HttpResponse.json({
        equipment: { ...equipment, ...overrides },
        from: url.searchParams.get('from'),
        to: url.searchParams.get('to'),
        available: true,
        openMaintenance: false,
        busySlots,
      });
    }),
  );
}
function withMine(content: object[]) {
  server.use(
    http.get('/api/v1/reservations/mine', () =>
      HttpResponse.json({
        content,
        page: 0,
        size: 20,
        totalElements: content.length,
        totalPages: 1,
      }),
    ),
  );
}
describe('reserve equipment (UC01)', () => {
  it('prefills the chosen window, posts UTC times with CSRF and lands on my reservations', async () => {
    withAvailability({}, [
      { startAt: '2030-01-11T01:00:00Z', endAt: '2030-01-11T02:00:00Z', reason: 'RESERVED' },
    ]);
    let posted: unknown;
    server.use(
      http.post('/api/v1/reservations', async ({ request }) => {
        expect(request.headers.get('X-XSRF-TOKEN')).toBe('test-csrf');
        posted = await request.json();
        return HttpResponse.json(reservation, { status: 201 });
      }),
    );
    withMine([reservation]);
    mount(`/equipment/${ITEM}/reserve?from=2030-01-10T09:00&to=2030-01-10T11:00`);
    expect(await screen.findByRole('heading', { name: /Canon EOS R6/ })).toBeInTheDocument();
    expect(screen.getByText('11 Jan 2030, 09:00 – 10:00')).toBeInTheDocument();
    expect(screen.getByLabelText('Start (SGT)')).toHaveValue('2030-01-10T09:00');
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('Purpose (optional)'), 'Project shoot');
    await user.click(screen.getByRole('button', { name: 'Reserve' }));
    expect(
      await screen.findByText('Canon EOS R6 is reserved for 10 Jan 2030, 09:00 – 11:00.'),
    ).toBeInTheDocument();
    expect(posted).toEqual({
      equipmentId: ITEM,
      startAt: '2030-01-10T01:00:00Z',
      endAt: '2030-01-10T03:00:00Z',
      purpose: 'Project shoot',
    });
    expect(screen.getByRole('heading', { name: /My reservations/ })).toBeInTheDocument();
  });
  it('explains a slot conflict from the server', async () => {
    withAvailability();
    server.use(
      http.post('/api/v1/reservations', () =>
        HttpResponse.json(
          {
            type: '/problems/reservation-conflict',
            status: 409,
            detail:
              'This equipment is already reserved for part of that time. Choose another time slot.',
            violations: [],
          },
          { status: 409 },
        ),
      ),
    );
    mount(`/equipment/${ITEM}/reserve?from=2030-01-10T09:00&to=2030-01-10T11:00`);
    await userEvent.click(await screen.findByRole('button', { name: 'Reserve' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Choose another time slot.');
  });
  it('lists every broken booking rule', async () => {
    withAvailability();
    server.use(
      http.post('/api/v1/reservations', () =>
        HttpResponse.json(
          {
            status: 422,
            detail: 'A reservation may last at most 7 days.',
            violations: [
              {
                code: 'too-far-ahead',
                message: 'Reservations can start at most 90 days from now.',
              },
              { code: 'quota-exceeded', message: 'You already have 3 open reservations.' },
            ],
          },
          { status: 422 },
        ),
      ),
    );
    mount(`/equipment/${ITEM}/reserve?from=2030-01-10T09:00&to=2030-01-10T11:00`);
    await userEvent.click(await screen.findByRole('button', { name: 'Reserve' }));
    const alert = await screen.findByRole('alert');
    expect(within(alert).getByText('You already have 3 open reservations.')).toBeInTheDocument();
  });
  it('validates times in the browser before calling the server', async () => {
    withAvailability();
    let calls = 0;
    server.use(
      http.post('/api/v1/reservations', () => {
        calls++;
        return HttpResponse.json(reservation, { status: 201 });
      }),
    );
    mount(`/equipment/${ITEM}/reserve`);
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Reserve' }));
    expect(await screen.findByText('Enter a start time.')).toBeInTheDocument();
    await user.type(screen.getByLabelText('Start (SGT)'), '2030-01-10T11:00');
    await user.type(screen.getByLabelText('End (SGT)'), '2030-01-10T10:00');
    await user.click(screen.getByRole('button', { name: 'Reserve' }));
    expect(await screen.findByText('The end must be after the start.')).toBeInTheDocument();
    expect(calls).toBe(0);
  });
  it("applies an item's own maximum and states it before submitting", async () => {
    withAvailability({ maxBorrowDuration: 'PT2H' });
    let calls = 0;
    server.use(
      http.post('/api/v1/reservations', () => {
        calls++;
        return HttpResponse.json(reservation, { status: 201 });
      }),
    );
    mount(`/equipment/${ITEM}/reserve`);
    const user = userEvent.setup();
    expect(await screen.findByText('This item can be reserved for at most 2 hours.')).toBeVisible();
    await user.type(screen.getByLabelText('Start (SGT)'), '2030-01-10T09:00');
    await user.type(screen.getByLabelText('End (SGT)'), '2030-01-10T12:00');
    await user.click(screen.getByRole('button', { name: 'Reserve' }));
    expect(
      await screen.findByText('This equipment may be reserved for at most 2 hours.'),
    ).toBeInTheDocument();
    expect(calls).toBe(0);
  });
  it('reads server durations and words them like the server', () => {
    expect(durationMinutes('PT72H')).toBe(72 * 60);
    expect(durationMinutes('PT2H30M')).toBe(150);
    expect(durationMinutes('PT45M')).toBe(45);
    expect(durationMinutes(null)).toBeNull();
    expect(durationMinutes('P2D')).toBeNull();
    expect(durationText(72 * 60)).toBe('3 days');
    expect(durationText(24 * 60)).toBe('1 day');
    expect(durationText(51 * 60)).toBe('51 hours');
    expect(durationText(150)).toBe('150 minutes');
    const now = Date.parse('2030-01-10T00:00:00Z');
    const issues = (max: number | null, endAt: string) =>
      reservationSchema(() => now, max)
        .safeParse({ startAt: '2030-01-10T09:00', endAt, purpose: '' })
        .error?.issues.map((i) => i.message) ?? [];
    expect(issues(120, '2030-01-10T11:00')).toEqual([]);
    expect(issues(120, '2030-01-10T11:01')).toEqual([
      'This equipment may be reserved for at most 2 hours.',
    ]);
    // A longer item limit replaces the global seven days.
    expect(issues(10 * 24 * 60, '2030-01-18T09:00')).toEqual([]);
  });
  it('mirrors the server rules for duration and past start', () => {
    const now = Date.parse('2030-01-10T00:00:00Z');
    const schema = reservationSchema(() => now);
    const issues = (startAt: string, endAt: string) =>
      schema.safeParse({ startAt, endAt, purpose: '' }).error?.issues.map((i) => i.message) ?? [];
    expect(issues('2030-01-10T09:00', '2030-01-10T11:00')).toEqual([]);
    expect(issues('2030-01-10T07:00', '2030-01-10T11:00')).toContain(
      'The start time has already passed.',
    );
    expect(issues('2030-01-10T09:00', '2030-01-10T09:20')).toContain(
      'A reservation must last at least 30 minutes.',
    );
    expect(issues('2030-01-10T09:00', '2030-01-17T09:01')).toContain(
      'A reservation may last at most 7 days.',
    );
    expect(
      schema.safeParse({
        startAt: '2030-01-10T09:00',
        endAt: '2030-01-10T11:00',
        purpose: 'x'.repeat(501),
      }).success,
    ).toBe(false);
  });
  it('says when approval is needed and blocks equipment with an open fault', async () => {
    withAvailability({ requiresApproval: true });
    const view = mount(`/equipment/${ITEM}/reserve`);
    expect(await screen.findByRole('button', { name: 'Request reservation' })).toBeInTheDocument();
    expect(screen.getByText(/needs approval/)).toBeInTheDocument();
    view.unmount();
    server.use(
      http.get(`/api/v1/equipment/${ITEM}/availability`, () =>
        HttpResponse.json({
          equipment: { ...equipment, status: 'UNDER_MAINTENANCE' },
          from: '2030-01-10T00:00:00Z',
          to: '2030-01-11T00:00:00Z',
          available: false,
          openMaintenance: true,
          busySlots: [],
        }),
      ),
    );
    mount(`/equipment/${ITEM}/reserve`);
    expect(await screen.findByText(/cannot be reserved/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Reserve/ })).not.toBeInTheDocument();
  });
  it('only borrowers can open the reserve and my-reservations pages', async () => {
    server.use(
      http.get('/api/v1/auth/me', () =>
        HttpResponse.json({ ...currentUser, roles: ['TECHNICIAN'] }),
      ),
    );
    mount('/reservations');
    expect(
      await screen.findByText('Your account does not have access to this page.'),
    ).toBeInTheDocument();
  });
});
describe('my reservations (UC01)', () => {
  it('cancels after confirmation and refreshes the list', async () => {
    let cancelled = false;
    server.use(
      http.get('/api/v1/reservations/mine', () =>
        HttpResponse.json({
          content: [
            cancelled ? { ...reservation, status: 'CANCELLED', cancellable: false } : reservation,
            {
              ...reservation,
              reservationId: 'x2',
              status: 'FULFILLED',
              cancellable: false,
              equipmentName: 'Tripod',
            },
          ],
          page: 0,
          size: 20,
          totalElements: 2,
          totalPages: 1,
        }),
      ),
      http.post(`/api/v1/reservations/${RES}/cancel`, ({ request }) => {
        expect(request.headers.get('X-XSRF-TOKEN')).toBe('test-csrf');
        cancelled = true;
        return HttpResponse.json({ ...reservation, status: 'CANCELLED', cancellable: false });
      }),
    );
    mount('/reservations');
    const row = (await screen.findByText('Tripod')).closest('tr')!;
    expect(within(row).getByText('Checked out')).toBeInTheDocument();
    // A checked-out reservation offers no cancel button (rule 4).
    expect(within(row).queryByRole('button')).not.toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: /Cancel reservation/ })).toHaveLength(1);
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Cancel reservation for Canon EOS R6' }));
    await user.click(screen.getByRole('button', { name: 'Yes, cancel' }));
    await screen.findByText((_, el) => el?.classList.contains('badge-muted') === true);
    expect(screen.queryByRole('button', { name: /Cancel reservation/ })).not.toBeInTheDocument();
  });
  it('filters by status and shows an empty state', async () => {
    const statuses: (string | null)[] = [];
    server.use(
      http.get('/api/v1/reservations/mine', ({ request }) => {
        statuses.push(new URL(request.url).searchParams.get('status'));
        return HttpResponse.json({
          content: [],
          page: 0,
          size: 20,
          totalElements: 0,
          totalPages: 0,
        });
      }),
    );
    mount('/reservations');
    expect(await screen.findByText('No reservations yet')).toBeInTheDocument();
    await userEvent.selectOptions(screen.getByLabelText('Show'), 'Waiting for approval');
    await screen.findByText('No reservations yet');
    expect(statuses).toContain('PENDING_APPROVAL');
  });
});
