import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { App } from '@/app/App';
import { AuthProvider } from '@/app/AuthProvider';
import { currentUser, server } from '@/test/server';
import type { ApprovalDetail, DecisionRequest } from './queries';

const ROOT = '/api/v1/approvals';
const ID = '12345678-1234-4234-8234-123456789001';
const OTHER = '12345678-1234-4234-8234-123456789002';
const NOW = '2026-10-07T01:00:00Z';
const item = (): ApprovalDetail => ({
  reservationId: ID,
  requesterId: OTHER,
  requesterName: 'Zhang Student',
  equipment: { equipmentId: OTHER, assetTag: 'CAM-007', name: 'Sony Camera', category: 'Camera' },
  startAt: '2026-10-07T02:00:00Z',
  endAt: '2026-10-07T04:00:00Z',
  createdAt: '2026-10-06T02:00:00Z',
  purpose: 'Course filming',
  status: 'PENDING_APPROVAL',
  version: 7,
  expired: false,
});
function mount(path = `/approvals/${ID}`, roles = ['APPROVER']) {
  server.use(http.get('/api/v1/auth/me', () => HttpResponse.json({ ...currentUser, roles })));
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <AuthProvider>
          <App />
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return client;
}
function backend(initial = item()) {
  let state = initial;
  let reads = 0;
  const writes: { body: DecisionRequest; csrf: string | null }[] = [];
  server.use(
    http.get(`${ROOT}/pending`, () =>
      HttpResponse.json({
        content: state.status === 'PENDING_APPROVAL' ? [state] : [],
        number: 0,
        size: 20,
        totalElements: state.status === 'PENDING_APPROVAL' ? 1 : 0,
        totalPages: 1,
      }),
    ),
    http.get(`${ROOT}/${ID}`, () => {
      reads++;
      return HttpResponse.json(state);
    }),
    http.post(`${ROOT}/${ID}/decision`, async ({ request }) => {
      const body = (await request.json()) as DecisionRequest;
      writes.push({ body, csrf: request.headers.get('X-XSRF-TOKEN') });
      state = {
        ...state,
        status: body.decision === 'APPROVED' ? 'CONFIRMED' : 'REJECTED',
        version: 8,
        decision: {
          approvalId: OTHER,
          reservationId: ID,
          approverId: currentUser.userId,
          decision: body.decision,
          comment: body.comment,
          decidedAt: NOW,
        },
      };
      return HttpResponse.json({ reservation: state, decision: state.decision });
    }),
  );
  return {
    writes,
    reads: () => reads,
    replace: (value: ApprovalDetail) => {
      state = value;
    },
  };
}
async function preview() {
  await userEvent.click(await screen.findByRole('button', { name: 'Review decision' }));
  return screen.findByRole('button', { name: 'Confirm approval' });
}
beforeEach(() => vi.spyOn(Date, 'now').mockReturnValue(Date.parse(NOW)));
afterEach(() => vi.restoreAllMocks());

describe('approval workspace', () => {
  it('shows pending equipment, Singapore times and an expired marker', async () => {
    backend({ ...item(), expired: true });
    mount('/approvals');
    expect(await screen.findByText('Sony Camera')).toBeVisible();
    expect(screen.getByText('07 Oct 2026, 10:00 SGT')).toBeVisible();
    expect(screen.getByText('Expired · rejection allowed')).toBeVisible();
    expect(screen.getByRole('link', { name: 'Review request for CAM-007' })).toHaveAttribute(
      'href',
      `/approvals/${ID}`,
    );
  });
  it('shows the empty queue', async () => {
    backend({ ...item(), status: 'REJECTED' });
    mount('/approvals');
    expect(await screen.findByText('No pending reservations')).toBeVisible();
  });
  it('uses server pagination, fixed sort and bounded page size', async () => {
    const pages: string[] = [];
    server.use(
      http.get(`${ROOT}/pending`, ({ request }) => {
        const params = new URL(request.url).searchParams;
        pages.push(params.get('page')!);
        expect(params.get('size')).toBe('20');
        expect(params.get('sort')).toBe('startAt,asc');
        return HttpResponse.json({
          content: [
            { ...item(), requesterName: params.get('page') === '1' ? 'Second page' : 'First page' },
          ],
          number: Number(params.get('page')),
          size: 20,
          totalElements: 21,
          totalPages: 2,
        });
      }),
    );
    mount('/approvals');
    await screen.findByText('First page');
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));
    expect(await screen.findByText('Second page')).toBeVisible();
    expect(pages).toEqual(['0', '1']);
  });
  it('retries a failed queue read only when requested', async () => {
    let calls = 0;
    server.use(
      http.get(`${ROOT}/pending`, () =>
        ++calls === 1
          ? HttpResponse.json({ detail: 'Try again later.' }, { status: 503 })
          : HttpResponse.json({ content: [], totalPages: 0 }),
      ),
    );
    mount('/approvals');
    await screen.findByText('Try again later.');
    expect(calls).toBe(1);
    await userEvent.click(screen.getByRole('button', { name: /try again/i }));
    expect(await screen.findByText('No pending reservations')).toBeVisible();
    expect(calls).toBe(2);
  });
  it.each([['BORROWER'], ['ADMIN'], ['TECHNICIAN']])(
    'does not grant approval access to %s alone',
    async (role) => {
      const api = backend();
      mount(`/approvals/${ID}`, [role]);
      expect(
        await screen.findByText('Your account does not have access to this page.'),
      ).toBeVisible();
      expect(screen.queryByRole('link', { name: 'Approvals' })).not.toBeInTheDocument();
      expect(api.reads()).toBe(0);
    },
  );
  it('rejects a malformed route without fetching a reservation', async () => {
    const api = backend();
    mount('/approvals/not-a-uuid');
    expect(await screen.findByText('This reservation link is invalid.')).toBeVisible();
    expect(api.reads()).toBe(0);
  });
  it('requires explicit confirmation and submits only command data with CSRF', async () => {
    const api = backend();
    mount();
    const confirm = await preview();
    expect(api.writes).toHaveLength(0);
    expect(confirm).toHaveFocus();
    await userEvent.click(confirm);
    await screen.findByText('Decision recorded');
    expect(api.writes).toEqual([
      { body: { decision: 'APPROVED', expectedVersion: 7 }, csrf: 'test-csrf' },
    ]);
    expect(screen.getByText('07 Oct 2026, 09:00 SGT')).toBeVisible();
    await userEvent.click(screen.getByRole('link', { name: /Pending reservations/ }));
    expect(await screen.findByText('No pending reservations')).toBeVisible();
  });
  it('allows cancelling confirmation without any write', async () => {
    const api = backend();
    mount();
    await preview();
    await userEvent.click(screen.getByRole('button', { name: 'Back to editing' }));
    expect(api.writes).toHaveLength(0);
    expect(screen.getByRole('button', { name: 'Review decision' })).toBeEnabled();
  });
  it('requires a nonblank rejection comment', async () => {
    const api = backend();
    mount();
    await userEvent.click(await screen.findByLabelText('Reject reservation'));
    await userEvent.type(screen.getByLabelText('Comment (required)'), '   ');
    await userEvent.click(screen.getByRole('button', { name: 'Review decision' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(/reason|required/i);
    expect(api.writes).toHaveLength(0);
  });
  it.each([true, false])(
    'permits rejecting an expired reservation (server flag %s)',
    async (expired) => {
      const api = backend({ ...item(), expired, startAt: NOW });
      mount();
      await screen.findByText(/This reservation has expired/);
      expect(screen.queryByLabelText('Approve reservation')).not.toBeInTheDocument();
      await userEvent.type(screen.getByLabelText('Comment (required)'), 'Start time has passed');
      await userEvent.click(screen.getByRole('button', { name: 'Review decision' }));
      await userEvent.click(await screen.findByRole('button', { name: 'Confirm rejection' }));
      await screen.findByText('Decision recorded');
      expect(api.writes[0].body.decision).toBe('REJECTED');
    },
  );
  it('prevents self-decisions', async () => {
    backend({ ...item(), requesterId: currentUser.userId });
    mount();
    await screen.findByText('You cannot approve or reject your own reservation.');
    expect(screen.queryByRole('button', { name: 'Review decision' })).not.toBeInTheDocument();
  });
  it.each([-1, Number.MAX_SAFE_INTEGER + 1])(
    'fails closed for unsafe version %s',
    async (version) => {
      backend({ ...item(), version });
      mount();
      await screen.findByText('Unable to safely load this request. Refresh before continuing.');
      expect(screen.queryByRole('button', { name: 'Review decision' })).not.toBeInTheDocument();
    },
  );
  it('shows nonpending reservations as read-only', async () => {
    backend({ ...item(), status: 'CANCELLED' });
    mount();
    await screen.findByText('This reservation is no longer waiting for approval.');
    expect(screen.queryByRole('button', { name: 'Review decision' })).not.toBeInTheDocument();
  });
  it('validates the 1000-character limit before confirmation', async () => {
    const api = backend();
    mount();
    await screen.findByRole('button', { name: 'Review decision' });
    fireEvent.change(screen.getByLabelText('Comment (optional)'), {
      target: { value: 'x'.repeat(1001) },
    });
    await userEvent.click(screen.getByRole('button', { name: 'Review decision' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(/1000/);
    expect(api.writes).toHaveLength(0);
  });
  it('renders applicant input as text, not HTML', async () => {
    backend({ ...item(), purpose: '<img src=x onerror=alert(1)>' });
    mount();
    expect(await screen.findByText('<img src=x onerror=alert(1)>')).toBeVisible();
    expect(document.querySelector('img[src=x]')).toBeNull();
  });
  it('blocks another write after 409 until the request is refreshed', async () => {
    const api = backend();
    let writes = 0;
    server.use(
      http.post(`${ROOT}/${ID}/decision`, () => {
        writes++;
        return HttpResponse.json({ detail: 'Stale version' }, { status: 409 });
      }),
    );
    mount();
    await userEvent.click(await preview());
    await screen.findByText(/This request changed/);
    expect(screen.getByRole('button', { name: 'Review decision' })).toBeDisabled();
    expect(writes).toBe(1);
    api.replace({ ...item(), status: 'CONFIRMED', version: 8 });
    await userEvent.click(screen.getByRole('button', { name: 'Refresh request' }));
    await screen.findByText('This reservation is no longer waiting for approval.');
    expect(writes).toBe(1);
  });
  it('maps server field errors while preserving the editable comment', async () => {
    backend();
    server.use(
      http.post(`${ROOT}/${ID}/decision`, () =>
        HttpResponse.json(
          { errors: [{ field: 'comment', message: 'A clearer reason is required.' }] },
          { status: 400 },
        ),
      ),
    );
    mount();
    await screen.findByRole('button', { name: 'Review decision' });
    await userEvent.type(screen.getByLabelText('Comment (optional)'), 'Please approve');
    await userEvent.click(screen.getByRole('button', { name: 'Review decision' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Confirm approval' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('A clearer reason is required.');
    expect(screen.getByLabelText('Comment (optional)')).toHaveValue('Please approve');
    expect(screen.getByRole('button', { name: 'Review decision' })).toBeEnabled();
  });
  it('does not automatically retry failed writes', async () => {
    backend();
    let writes = 0;
    server.use(
      http.post(`${ROOT}/${ID}/decision`, () => {
        writes++;
        return HttpResponse.json({ detail: 'Service unavailable.' }, { status: 503 });
      }),
    );
    mount();
    await userEvent.click(await preview());
    await screen.findByText('Service unavailable.');
    expect(screen.getByRole('button', { name: 'Review decision' })).toBeDisabled();
    expect(writes).toBe(1);
  });
  it('prevents double-clicking a pending confirmation from posting twice', async () => {
    backend();
    let writes = 0;
    let finish!: () => void;
    const pending = new Promise<void>((resolve) => {
      finish = resolve;
    });
    server.use(
      http.post(`${ROOT}/${ID}/decision`, async () => {
        writes++;
        await pending;
        return HttpResponse.json({ detail: 'Conflict' }, { status: 409 });
      }),
    );
    mount();
    const confirm = await preview();
    await userEvent.dblClick(confirm);
    await waitFor(() => expect(writes).toBe(1));
    expect(screen.getByRole('button', { name: 'Submitting…' })).toBeDisabled();
    finish();
    await screen.findByText(/This request changed/);
    expect(writes).toBe(1);
  });
  it('returns to sign-in when the session expires during submission', async () => {
    backend();
    server.use(
      http.post(`${ROOT}/${ID}/decision`, () =>
        HttpResponse.json({ detail: 'Sign in required.' }, { status: 401 }),
      ),
    );
    mount();
    await userEvent.click(await preview());
    expect(await screen.findByRole('button', { name: 'Sign in' })).toBeVisible();
  });
});
