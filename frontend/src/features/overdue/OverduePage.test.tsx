import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { App } from '@/app/App';
import { AuthProvider } from '@/app/AuthProvider';
import { currentUser, server } from '@/test/server';

function mount(roles: string[]) {
  server.use(http.get('/api/v1/auth/me', () => HttpResponse.json({ ...currentUser, roles })));
  return render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <MemoryRouter initialEntries={['/overdue']}><AuthProvider><App /></AuthProvider></MemoryRouter>
  </QueryClientProvider>);
}

describe('overdue view', () => {
  it('shows named loans and requests the selected duration order', async () => {
    const orders: string[] = [];
    server.use(http.get('/api/v1/overdue', ({ request }) => {
      orders.push(new URL(request.url).searchParams.get('order') ?? '');
      return HttpResponse.json({ content: [{ loanId: 'loan-1', reservationId: 'reservation-1',
        borrowerName: 'Alex', equipmentName: 'Camera', dueAt: '2026-10-14T01:00:00Z', overdueSeconds: 90000 }],
        totalElements: 1, page: 0, size: 20 });
    }));
    mount(['CUSTODIAN']);
    expect(await screen.findByText('Camera')).toBeVisible();
    expect(screen.getByText('Alex')).toBeVisible();
    expect(screen.getByText('1d 1h 0m')).toBeVisible();
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Sort by overdue duration' }), 'asc');
    await screen.findByText('1d 1h 0m');
    expect(orders).toContain('asc');
  });
  it('keeps borrowers out of the overdue workspace', async () => {
    mount(['BORROWER']);
    expect(await screen.findByText('Your account does not have access to this page.')).toBeVisible();
  });
});
