import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { App } from '@/app/App';
import { AuthProvider } from '@/app/AuthProvider';
import { server, currentUser } from '@/test/server';

const item = {
  reservationId: 'reservation-1',
  equipmentId: 'equipment-1',
  equipmentName: 'UC03 camera',
  assetTag: 'CAM-001',
  borrowerId: 'borrower-1',
  borrowerName: 'Borrower',
  startAt: '2020-01-01T00:00:00Z',
  endAt: '2030-01-01T00:00:00Z',
  version: 4,
};
const loan = {
  ...item,
  loanId: 'loan-1',
  checkedOutAt: '2026-10-01T00:00:00Z',
  dueAt: '2026-10-02T00:00:00Z',
  returnedAt: null,
  status: 'ACTIVE',
  returnCondition: null,
  returnNote: null,
  overdue: true,
  version: 2,
};
function mount(roles = ['CUSTODIAN']) {
  currentUser.roles = roles;
  server.use(
    http.get('/api/v1/loans/checkouts', () => HttpResponse.json([item])),
    http.get('/api/v1/loans', () => HttpResponse.json([loan])),
  );
  return render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <MemoryRouter initialEntries={['/loans']}>
        <AuthProvider>
          <App />
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
describe('UC03 handover', () => {
  it.each(['APPROVER', 'MAINTAINER', 'ADMIN'])('blocks direct loan navigation for %s without requesting loan data', async (role) => {
    let reads = 0;
    mount([role]);
    server.use(http.get('/api/v1/loans', () => {
      reads++;
      return HttpResponse.json([loan]);
    }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Your account does not have access to this page.');
    expect(screen.queryByRole('heading', { name: 'My loans' })).not.toBeInTheDocument();
    expect(reads).toBe(0);
  });
  it('allows an explicitly assigned borrower role alongside ADMIN', async () => {
    mount(['ADMIN', 'BORROWER']);
    expect(await screen.findByRole('heading', { name: 'My loans' })).toBeInTheDocument();
    expect(await screen.findByText('UC03 camera (CAM-001)')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Receive return' })).not.toBeInTheDocument();
  });
  it('requires physical identity confirmation and sends server version', async () => {
    let submitted: unknown;
    server.use(
      http.post('/api/v1/loans', async ({ request }) => {
        submitted = await request.json();
        return HttpResponse.json(loan, { status: 201 });
      }),
    );
    mount();
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Check out' }));
    expect(screen.getByRole('button', { name: 'Record handover' })).toBeDisabled();
    await user.click(screen.getByRole('checkbox', { name: /I checked the person's identity/ }));
    await user.click(screen.getByRole('button', { name: 'Record handover' }));
    await screen.findByText('Handover recorded successfully.');
    expect(submitted).toEqual({
      reservationId: item.reservationId,
      borrowerId: item.borrowerId,
      expectedVersion: 4,
      identityConfirmed: true,
    });
  });
  it('searches checkout candidates and active loans and shows the equipment code', async () => {
    const queries: string[] = [];
    mount();
    server.use(
      http.get('/api/v1/loans/checkouts', ({ request }) => {
        const q = new URL(request.url).searchParams.get('q');
        if (q) queries.push(q);
        return HttpResponse.json(q ? [] : [item]);
      }),
      http.get('/api/v1/loans', ({ request }) => {
        const q = new URL(request.url).searchParams.get('q');
        if (q) queries.push(q);
        return HttpResponse.json(q ? [] : [loan]);
      }),
    );
    expect(await screen.findAllByText(/CAM-001/)).toHaveLength(2);
    const user = userEvent.setup();
    await user.type(screen.getByRole('textbox', { name: 'Find reservation, borrower or equipment code' }), ' CAM-001 ');
    await user.click(screen.getByRole('button', { name: 'Search' }));
    expect(await screen.findByText('No reservations match your search.')).toBeInTheDocument();
    expect(await screen.findByText('No loans match your search.')).toBeInTheDocument();
    expect(queries).toContain('CAM-001');
    await user.click(screen.getByRole('button', { name: 'Clear search' }));
    expect(await screen.findAllByText(/CAM-001/)).toHaveLength(2);
  });
  it('requires damage details and preserves late-return notice', async () => {
    let submitted: unknown;
    server.use(
      http.post('/api/v1/loans/loan-1/return', async ({ request }) => {
        submitted = await request.json();
        return HttpResponse.json({ ...loan, status: 'RETURNED' });
      }),
    );
    mount();
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Receive return' }));
    expect(
      screen.getByText('This return is overdue and will be recorded as late.'),
    ).toBeInTheDocument();
    await user.selectOptions(screen.getByRole('combobox'), 'DAMAGED');
    expect(screen.getByRole('button', { name: 'Record handover' })).toBeDisabled();
    await user.type(screen.getByRole('textbox', { name: 'Return note' }), 'Lens cracked');
    await user.click(screen.getByRole('button', { name: 'Record handover' }));
    await screen.findByText('Handover recorded successfully.');
    expect(submitted).toEqual({ expectedVersion: 2, condition: 'DAMAGED', note: 'Lens cracked' });
  });
  it('shows conflicts without claiming success', async () => {
    server.use(
      http.post('/api/v1/loans/loan-1/return', () =>
        HttpResponse.json({ detail: 'This loan was returned or changed.' }, { status: 409 }),
      ),
    );
    mount();
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Receive return' }));
    await user.click(screen.getByRole('button', { name: 'Record handover' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'This loan was returned or changed.',
    );
    expect(screen.queryByText('Handover recorded successfully.')).not.toBeInTheDocument();
  });
  it('borrowers have a read-only own-loan view', async () => {
    mount(['BORROWER']);
    await screen.findByRole('heading', { name: 'My loans' });
    await screen.findByText('UC03 camera (CAM-001)');
    expect(screen.queryByRole('button', { name: 'Receive return' })).not.toBeInTheDocument();
    expect(
      screen.queryByRole('heading', { name: 'Confirmed reservations' }),
    ).not.toBeInTheDocument();
  });
});
