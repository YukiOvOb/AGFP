import { describe, it, expect } from 'vitest';
import { render, screen, waitFor, within, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { App } from '@/app/App';
import { AuthProvider } from '@/app/AuthProvider';
import { server, currentUser } from '@/test/server';
import { durationLabel, singaporeDate, singaporeInstant } from './format';
import { maintenanceKeys, type MaintenanceCase, type MaintenanceHistoryEntry } from './queries';
const ROOT = '/api/v1/maintenance';
const ME = currentUser.userId;
const CASE = '12345678-1234-4234-8234-123456789001';
const EQUIPMENT = '12345678-1234-4234-8234-123456789002';
const OTHER = '12345678-1234-4234-8234-123456789003';
const item = (
  status: MaintenanceCase['status'] = 'ASSIGNED',
  assignedTo: string | null = ME,
): MaintenanceCase => ({
  maintenanceCaseId: CASE,
  equipmentId: EQUIPMENT,
  equipmentName: 'Field Camera',
  equipmentAssetTag: 'CAM-001',
  reportedBy: OTHER,
  reportedByName: 'Omar Reporter',
  assignedTo,
  assignedToName: assignedTo === null ? null : assignedTo === ME ? 'Mia Tech' : 'Other Tech',
  loanId: null,
  status,
  faultDescription: 'Camera lens jammed',
  faultCategory: 'OTHER',
  priority: 'MEDIUM',
  reportedAt: '2026-10-06T00:00:00Z',
  resolvedAt: status === 'RESOLVED' || status === 'UNREPAIRABLE' ? '2026-10-06T02:30:00Z' : null,
  resolutionNote: status === 'RESOLVED' || status === 'UNREPAIRABLE' ? 'Work completed' : null,
  version: 2,
});
function mount(roles: string[], path = '/maintenance') {
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
function lists(open: MaintenanceCase[] = [], assigned: MaintenanceCase[] = []) {
  server.use(
    http.get(ROOT + '/cases/open', () => HttpResponse.json(open)),
    http.get(ROOT + '/cases/assigned', () => HttpResponse.json(assigned)),
    http.get(ROOT + '/technicians', () =>
      HttpResponse.json([
        { userId: ME, displayName: 'Active Technician', email: 'tech@example.com' },
      ]),
    ),
  );
}
function detail(initial = item()) {
  let state = initial;
  let histories: MaintenanceHistoryEntry[] = [];
  let caseReads = 0;
  let historyReads = 0;
  const requests: { action: string; body: unknown }[] = [];
  server.use(
    http.get(ROOT + '/cases/' + CASE, () => {
      caseReads++;
      return HttpResponse.json(state);
    }),
    http.get(ROOT + '/cases/' + CASE + '/history', () => {
      historyReads++;
      return HttpResponse.json(histories);
    }),
    http.post(ROOT + '/cases/' + CASE + '/:action', async ({ request, params }) => {
      const action = String(params.action);
      const body =
        action === 'start'
          ? undefined
          : ((await request.json()) as { detail?: string; resolutionNote?: string });
      requests.push({ action, body });
      expect(request.headers.get('X-XSRF-TOKEN')).toBe('test-csrf');
      state = {
        ...state,
        status:
          action === 'start'
            ? 'IN_PROGRESS'
            : action === 'resolve'
              ? 'RESOLVED'
              : action === 'unrepairable'
                ? 'UNREPAIRABLE'
                : state.status,
        resolutionNote: body?.resolutionNote ?? null,
        version: state.version + 1,
      };
      histories = [
        ...histories,
        {
          maintenanceCaseId: CASE,
          equipmentId: EQUIPMENT,
          action:
            action === 'start'
              ? 'START'
              : action === 'resolve'
                ? 'RESOLVE'
                : action === 'unrepairable'
                  ? 'MARK_UNREPAIRABLE'
                  : action === 'diagnosis'
                    ? 'RECORD_DIAGNOSIS'
                    : action === 'notes'
                      ? 'ADD_NOTE'
                      : 'RECORD_REPAIR_ACTION',
          fromStatus: initial.status,
          toStatus: state.status,
          actorId: ME,
          actorName: 'Mia Tech',
          occurredAt: '2026-10-06T01:00:00Z',
          detail: body?.detail ?? body?.resolutionNote ?? null,
        },
      ];
      return HttpResponse.json(state);
    }),
  );
  return { requests, reads: () => ({ caseReads, historyReads }) };
}
describe('UC04 maintenance workspace', () => {
  it('reports equipment selected in the catalogue and clears the selection after success', async () => {
    let body: unknown;
    server.use(
      http.post(ROOT + '/cases', async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(item('OPEN', null), { status: 201 });
      }),
    );
    mount(['BORROWER'], '/maintenance?equipmentId=' + EQUIPMENT);
    expect(await screen.findByLabelText('Equipment ID', { exact: true })).toHaveValue(EQUIPMENT);
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('Fault description'), 'Selected camera fault');
    await user.click(screen.getByRole('button', { name: 'Submit fault report' }));
    await screen.findByText(/Fault reported successfully/);
    expect(body).toEqual({
      equipmentId: EQUIPMENT,
      faultDescription: 'Selected camera fault',
      faultCategory: 'OTHER',
      priority: 'MEDIUM',
    });
    expect(screen.getByLabelText('Equipment ID', { exact: true })).toHaveValue('');
  });
  it('ignores an invalid catalogue equipment parameter', async () => {
    mount(['BORROWER'], '/maintenance?equipmentId=invalid');
    expect(await screen.findByLabelText('Equipment ID', { exact: true })).toHaveValue('');
  });
  it('accepts the existing PostgreSQL demo UUID format', async () => {
    mount(['BORROWER'], '/maintenance?equipmentId=20000000-0000-0000-0000-000000000008');
    expect(await screen.findByLabelText('Equipment ID', { exact: true })).toHaveValue(
      '20000000-0000-0000-0000-000000000008',
    );
  });
  it.each(['BORROWER', 'CUSTODIAN', 'MAINTAINER', 'ADMIN'])(
    'shows Maintenance navigation to %s',
    async (role) => {
      lists();
      mount([role], '/notifications');
      expect(await screen.findByRole('link', { name: 'Maintenance' })).toBeVisible();
      expect(screen.getByRole('link', { name: 'Notifications' })).toBeVisible();
    },
  );
  it('hides Maintenance and denies direct workspace to APPROVER-only', async () => {
    mount(['APPROVER']);
    expect(
      await screen.findByText('Your account does not have access to the maintenance workspace.'),
    ).toBeVisible();
    expect(screen.queryByRole('link', { name: 'Maintenance' })).not.toBeInTheDocument();
  });
  it('borrower reports only supported fields, resets the form, and has no unauthorized case link', async () => {
    let body: unknown;
    server.use(
      http.post(ROOT + '/cases', async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(item('OPEN', null), { status: 201 });
      }),
    );
    mount(['BORROWER']);
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText('Equipment ID', { exact: true }), EQUIPMENT);
    await user.type(screen.getByLabelText('Fault description'), ' Lens jammed ');
    await user.click(screen.getByRole('button', { name: 'Submit fault report' }));
    expect(await screen.findByText(/Fault reported successfully/)).toBeVisible();
    expect(body).toEqual({
      equipmentId: EQUIPMENT,
      faultDescription: 'Lens jammed',
      faultCategory: 'OTHER',
      priority: 'MEDIUM',
    });
    expect(screen.getByLabelText('Equipment ID', { exact: true })).toHaveValue('');
    expect(screen.queryByText('Open maintenance queue')).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'View case' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Maintenance report' })).not.toBeInTheDocument();
    expect(screen.queryByText('Maintenance actions')).not.toBeInTheDocument();
  });
  it('reports the chosen fault category and priority and resets them after success', async () => {
    let body: unknown;
    server.use(
      http.post(ROOT + '/cases', async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(
          { ...item('OPEN', null), faultCategory: 'ELECTRICAL', priority: 'URGENT' },
          { status: 201 },
        );
      }),
    );
    mount(['BORROWER'], '/maintenance?equipmentId=' + EQUIPMENT);
    const user = userEvent.setup();
    await user.selectOptions(await screen.findByLabelText('Fault category'), 'ELECTRICAL');
    await user.selectOptions(screen.getByLabelText('Priority'), 'URGENT');
    await user.type(screen.getByLabelText('Fault description'), 'Charger sparks');
    await user.click(screen.getByRole('button', { name: 'Submit fault report' }));
    await screen.findByText(/Fault reported successfully/);
    expect(body).toMatchObject({ faultCategory: 'ELECTRICAL', priority: 'URGENT' });
    expect(screen.getByLabelText('Fault category')).toHaveValue('OTHER');
    expect(screen.getByLabelText('Priority')).toHaveValue('MEDIUM');
  });
  it('filters the open queue by priority and sorts it highest first', async () => {
    lists([
      {
        ...item('OPEN', null),
        maintenanceCaseId: CASE,
        faultDescription: 'Old low',
        priority: 'LOW',
      },
      {
        ...item('OPEN', null),
        maintenanceCaseId: OTHER,
        faultDescription: 'Newer urgent',
        priority: 'URGENT',
        faultCategory: 'ELECTRICAL',
      },
    ]);
    mount(['CUSTODIAN']);
    const user = userEvent.setup();
    const titles = () =>
      screen.getAllByRole('link', { name: /Old low|Newer urgent/ }).map((a) => a.textContent);
    expect(await screen.findByRole('link', { name: 'Old low' })).toBeVisible();
    expect(titles()).toEqual(['Old low', 'Newer urgent']);
    expect(screen.getByText('Electrical', { selector: 'dd' })).toBeVisible();
    await user.selectOptions(screen.getByLabelText('Sort'), 'priority');
    expect(titles()).toEqual(['Newer urgent', 'Old low']);
    await user.selectOptions(
      screen.getByLabelText('Priority', { selector: '#queue-priority' }),
      'HIGH',
    );
    expect(screen.getByText('No open cases with this priority.')).toBeVisible();
    await user.selectOptions(
      screen.getByLabelText('Priority', { selector: '#queue-priority' }),
      'LOW',
    );
    expect(titles()).toEqual(['Old low']);
  });
  it('validates UUID, optional loan UUID and required description without submitting', async () => {
    mount(['BORROWER']);
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText('Equipment ID', { exact: true }), 'invalid');
    await user.type(screen.getByLabelText('Loan ID (optional)'), 'bad');
    await user.click(screen.getByRole('button', { name: 'Submit fault report' }));
    expect(await screen.findAllByText('Enter a valid UUID.')).toHaveLength(2);
    expect(screen.getByText('This field is required.')).toBeVisible();
    expect(screen.getByLabelText('Fault description')).toHaveAttribute('aria-invalid', 'true');
  });
  it('shows a reporting ProblemDetail and preserves entered values', async () => {
    server.use(
      http.post(ROOT + '/cases', () =>
        HttpResponse.json({ detail: 'Equipment has been retired.' }, { status: 409 }),
      ),
    );
    mount(['BORROWER']);
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText('Equipment ID', { exact: true }), EQUIPMENT);
    await user.type(screen.getByLabelText('Fault description'), 'Fault');
    await user.click(screen.getByRole('button', { name: 'Submit fault report' }));
    expect(await screen.findByText('Equipment has been retired.')).toBeVisible();
    expect(screen.getByLabelText('Fault description')).toHaveValue('Fault');
  });
  it('custodian reports, selects a real technician and refreshes the queue with success feedback', async () => {
    let open = [item('OPEN', null)];
    let body: unknown;
    let reads = 0;
    lists();
    server.use(
      http.get(ROOT + '/cases/open', () => {
        reads++;
        return HttpResponse.json(open);
      }),
      http.post(ROOT + '/cases/' + CASE + '/assignment', async ({ request }) => {
        body = await request.json();
        open = [];
        return HttpResponse.json(item());
      }),
    );
    mount(['CUSTODIAN']);
    const user = userEvent.setup();
    expect(await screen.findByRole('heading', { name: 'Report a fault' })).toBeVisible();
    await screen.findByRole('option', { name: 'Active Technician · tech@example.com' });
    await user.selectOptions(screen.getByLabelText('Assign technician'), ME);
    await user.click(screen.getByRole('button', { name: 'Assign technician' }));
    expect(await screen.findByText('No open maintenance cases.')).toBeVisible();
    expect(await screen.findByText('Technician assigned successfully.')).toBeVisible();
    expect(body).toEqual({ technicianId: ME });
    expect(reads).toBeGreaterThan(1);
  });
  it('shows explicit empty technician and queue states', async () => {
    lists([item('OPEN', null)]);
    server.use(http.get(ROOT + '/technicians', () => HttpResponse.json([])));
    mount(['ADMIN']);
    expect(await screen.findByText('No active technicians available.')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Assign technician' })).toBeDisabled();
    expect(screen.getByText('Field Camera (CAM-001)')).toBeVisible();
    expect(screen.getByText('Omar Reporter')).toBeVisible();
    expect(screen.queryByText('Technician', { selector: 'dt' })).not.toBeInTheDocument();
  });
  it('admin sees queue and report, but does not inherit fault reporting', async () => {
    lists();
    mount(['ADMIN']);
    expect(await screen.findByText('No open maintenance cases.')).toBeVisible();
    expect(screen.getByRole('link', { name: 'Maintenance report' })).toBeVisible();
    expect(screen.queryByRole('heading', { name: 'Report a fault' })).not.toBeInTheDocument();
  });
  it('multi-role user sees the union without duplicate sections', async () => {
    lists();
    mount(['ADMIN', 'BORROWER', 'MAINTAINER']);
    expect(await screen.findByRole('heading', { name: 'Report a fault' })).toBeVisible();
    expect(await screen.findByText('No assigned maintenance cases.')).toBeVisible();
    expect(screen.getAllByRole('heading', { name: 'Open maintenance queue' })).toHaveLength(1);
    expect(screen.getByRole('link', { name: 'Maintenance report' })).toBeVisible();
  });
  it('maintainer sees assigned status groups, terminal notes and no OPEN items', async () => {
    lists(
      [],
      [
        item(),
        { ...item('IN_PROGRESS'), maintenanceCaseId: OTHER },
        { ...item('RESOLVED'), maintenanceCaseId: EQUIPMENT },
        { ...item('UNREPAIRABLE'), maintenanceCaseId: ME },
        { ...item('OPEN'), faultDescription: 'Hidden open item' },
      ],
    );
    mount(['MAINTAINER']);
    expect(await screen.findByRole('region', { name: 'ASSIGNED' })).toBeVisible();
    for (const name of ['IN PROGRESS', 'RESOLVED', 'UNREPAIRABLE'])
      expect(screen.getByRole('region', { name })).toBeVisible();
    expect(screen.queryByText('Hidden open item')).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Report a fault' })).not.toBeInTheDocument();
    expect(
      screen.queryByRole('heading', { name: 'Open maintenance queue' }),
    ).not.toBeInTheDocument();
  });
  it('shows explicit empty assigned state', async () => {
    lists();
    mount(['MAINTAINER']);
    expect(await screen.findByText('No assigned maintenance cases.')).toBeVisible();
  });
  it('starts own assigned case and refreshes case/history', async () => {
    const state = detail();
    mount(['MAINTAINER'], '/maintenance/cases/' + CASE);
    const client = userEvent.setup();
    await client.click(await screen.findByRole('button', { name: 'Start Maintenance' }));
    expect(await screen.findByRole('button', { name: 'Record Diagnosis' })).toBeVisible();
    expect(await screen.findByText('Maintenance started successfully.')).toBeVisible();
    expect(state.requests).toEqual([{ action: 'start', body: undefined }]);
    expect(state.reads().caseReads).toBeGreaterThan(1);
    expect(state.reads().historyReads).toBeGreaterThan(1);
    expect(screen.getByText('06 Oct 2026, 08:00 SGT')).toBeVisible();
    expect(screen.getByText('Field Camera (CAM-001)')).toBeVisible();
    expect(screen.getByText('Omar Reporter')).toBeVisible();
    expect(screen.getByText('Mia Tech')).toBeVisible();
    expect(await screen.findByText(/· By Mia Tech$/)).toBeVisible();
    expect(screen.queryByText(EQUIPMENT)).not.toBeInTheDocument();
    expect(screen.queryByText(OTHER)).not.toBeInTheDocument();
    expect(screen.queryByText(/· By 12345678-/)).not.toBeInTheDocument();
  });
  it('falls back to identifiers when the server cannot read a name', async () => {
    detail({
      ...item('ASSIGNED', OTHER),
      equipmentName: null,
      equipmentAssetTag: null,
      reportedByName: null,
      assignedToName: null,
    });
    mount(['CUSTODIAN'], '/maintenance/cases/' + CASE);
    expect(await screen.findByText(EQUIPMENT)).toBeVisible();
    expect(screen.getAllByText(OTHER)).toHaveLength(2);
  });
  it.each([
    ['diagnosis', 'Record Diagnosis', 'Record Diagnosis — details'],
    ['repair-actions', 'Record Repair Action', 'Record Repair Action — details'],
    ['notes', 'Add Note', 'Add Note — details'],
    ['resolve', 'Resolve', 'Resolve — resolution note'],
  ])('submits %s and invalidates all maintenance views', async (action, button, label) => {
    const state = detail(item('IN_PROGRESS'));
    const query = mount(['MAINTAINER'], '/maintenance/cases/' + CASE);
    query.setQueryData(maintenanceKeys.open(), []);
    query.setQueryData(maintenanceKeys.assigned(), []);
    query.setQueryData(maintenanceKeys.report('a', 'b'), {});
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText(label), 'Work details');
    await user.click(screen.getByRole('button', { name: button }));
    expect(await screen.findByText(button + ' completed successfully.')).toBeVisible();
    expect(state.requests).toEqual([
      {
        action,
        body:
          action === 'resolve' ? { resolutionNote: 'Work details' } : { detail: 'Work details' },
      },
    ]);
    expect(query.getQueryState(maintenanceKeys.open())?.isInvalidated).toBe(true);
    expect(query.getQueryState(maintenanceKeys.assigned())?.isInvalidated).toBe(true);
    expect(query.getQueryState(maintenanceKeys.report('a', 'b'))?.isInvalidated).toBe(true);
    expect(state.reads().historyReads).toBeGreaterThan(1);
    if (action === 'resolve')
      expect(screen.queryByRole('button', { name: 'Resolve' })).not.toBeInTheDocument();
  });
  it('unrepairable requires a keyboard-usable confirmation; cancellation does not submit', async () => {
    const state = detail(item('IN_PROGRESS'));
    mount(['MAINTAINER'], '/maintenance/cases/' + CASE);
    const user = userEvent.setup();
    await user.type(
      await screen.findByLabelText('Mark Unrepairable — resolution note'),
      'Beyond repair',
    );
    await user.click(screen.getByRole('button', { name: 'Mark Unrepairable' }));
    expect(screen.getByRole('button', { name: 'Confirm unrepairable' })).toHaveFocus();
    expect(state.requests).toHaveLength(0);
    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(state.requests).toHaveLength(0);
    await user.click(screen.getByRole('button', { name: 'Mark Unrepairable' }));
    await user.keyboard('{Enter}');
    expect(await screen.findByText('Mark Unrepairable completed successfully.')).toBeVisible();
    expect(state.requests).toEqual([
      { action: 'unrepairable', body: { resolutionNote: 'Beyond repair' } },
    ]);
    expect(screen.getByText('This case is complete and read-only.')).toBeVisible();
  });
  it('requires work detail and displays mutation errors without losing input', async () => {
    detail(item('IN_PROGRESS'));
    server.use(
      http.post(ROOT + '/cases/' + CASE + '/notes', () =>
        HttpResponse.json({ detail: 'Case changed. Refresh and try again.' }, { status: 409 }),
      ),
    );
    mount(['MAINTAINER'], '/maintenance/cases/' + CASE);
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Add Note' }));
    expect(await screen.findByText('This field is required.')).toBeVisible();
    await user.type(screen.getByLabelText('Add Note — details'), 'Note');
    await user.click(screen.getByRole('button', { name: 'Add Note' }));
    expect(await screen.findByText('Case changed. Refresh and try again.')).toBeVisible();
    expect(screen.getByLabelText('Add Note — details')).toHaveValue('Note');
  });
  it('another assigned maintainer has no action controls even when the server returns a case', async () => {
    detail(item('IN_PROGRESS', OTHER));
    mount(['MAINTAINER'], '/maintenance/cases/' + CASE);
    expect(await screen.findByRole('heading', { name: 'Case details' })).toBeVisible();
    expect(screen.queryByRole('heading', { name: 'Maintenance actions' })).not.toBeInTheDocument();
  });
  it.each(['RESOLVED', 'UNREPAIRABLE'] as const)(
    '%s case is terminal and read-only',
    async (status) => {
      detail(item(status));
      mount(['MAINTAINER'], '/maintenance/cases/' + CASE);
      expect(await screen.findByText('This case is complete and read-only.')).toBeVisible();
      expect(screen.queryByRole('button', { name: 'Start Maintenance' })).not.toBeInTheDocument();
      expect(screen.getByText('Work completed')).toBeVisible();
    },
  );
  it('shows server 403 ProblemDetail cleanly on case reads', async () => {
    detail();
    server.use(
      http.get(ROOT + '/cases/' + CASE, () =>
        HttpResponse.json(
          { detail: 'Only the assigned technician may view this case.' },
          { status: 403 },
        ),
      ),
    );
    mount(['MAINTAINER'], '/maintenance/cases/' + CASE);
    expect(
      await screen.findByText('Only the assigned technician may view this case.'),
    ).toBeVisible();
  });
  it('operator equipment lookup validates UUID and displays cases and history', async () => {
    lists();
    server.use(
      http.get(ROOT + '/equipment/' + EQUIPMENT + '/cases', () => HttpResponse.json([item()])),
      http.get(ROOT + '/equipment/' + EQUIPMENT + '/history', () => HttpResponse.json([])),
    );
    mount(['CUSTODIAN']);
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Look up maintenance' }));
    expect(await screen.findAllByText('Enter a valid UUID.')).toHaveLength(1);
    await user.type(screen.getByLabelText('Equipment ID for history'), EQUIPMENT);
    await user.click(screen.getByRole('button', { name: 'Look up maintenance' }));
    expect(await screen.findByRole('link', { name: 'Camera lens jammed' })).toBeVisible();
    expect(screen.getByRole('heading', { name: 'Field Camera (CAM-001)' })).toBeVisible();
    expect(await screen.findByText('No maintenance history yet.')).toBeVisible();
  });
  it('operator opens equipment history from a case and the lookup covers that item', async () => {
    lists();
    detail(item('IN_PROGRESS'));
    server.use(
      http.get(ROOT + '/equipment/' + EQUIPMENT + '/cases', () =>
        HttpResponse.json([item('IN_PROGRESS')]),
      ),
      http.get(ROOT + '/equipment/' + EQUIPMENT + '/history', () => HttpResponse.json([])),
    );
    mount(['CUSTODIAN'], '/maintenance/cases/' + CASE);
    await userEvent
      .setup()
      .click(
        await screen.findByRole('link', { name: 'Maintenance history for Field Camera (CAM-001)' }),
      );
    const lookup = await screen.findByRole('region', { name: 'Equipment maintenance lookup' });
    expect(within(lookup).getByLabelText('Equipment ID for history')).toHaveValue(EQUIPMENT);
    expect(
      await within(lookup).findByRole('heading', { name: 'Field Camera (CAM-001)' }),
    ).toBeVisible();
    expect(within(lookup).getByRole('link', { name: 'Camera lens jammed' })).toBeVisible();
    // Cards in the lookup already show this item, so their equipment is not linked again.
    expect(
      within(lookup).queryByRole('link', { name: /^Maintenance history for/ }),
    ).not.toBeInTheDocument();
  });
  it('links equipment history from the operator queue but not from maintainer views', async () => {
    lists([item('OPEN', null)]);
    mount(['CUSTODIAN']);
    expect(
      await screen.findByRole('link', { name: 'Maintenance history for Field Camera (CAM-001)' }),
    ).toHaveAttribute('href', '/maintenance?history=' + EQUIPMENT);
  });
  it('maintainer sees equipment names without history links', async () => {
    lists([], [item()]);
    detail(item());
    mount(['MAINTAINER']);
    expect(await screen.findByText('Field Camera (CAM-001)')).toBeVisible();
    expect(
      screen.queryByRole('link', { name: /^Maintenance history for/ }),
    ).not.toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole('link', { name: 'Camera lens jammed' }));
    expect(await screen.findByRole('heading', { name: 'Case details' })).toBeVisible();
    expect(screen.getByText('Field Camera (CAM-001)')).toBeVisible();
    expect(
      screen.queryByRole('link', { name: /^Maintenance history for/ }),
    ).not.toBeInTheDocument();
  });
  it('ignores an invalid history parameter without looking anything up', async () => {
    let lookups = 0;
    lists();
    server.use(
      http.get(ROOT + '/equipment/:id/cases', () => {
        lookups++;
        return HttpResponse.json([]);
      }),
    );
    mount(['CUSTODIAN'], '/maintenance?history=not-a-uuid');
    expect(await screen.findByLabelText('Equipment ID for history')).toHaveValue('');
    expect(await screen.findByText('No open maintenance cases.')).toBeVisible();
    expect(lookups).toBe(0);
  });
  it('admin report has no default period, converts SGT bounds and renders five status counts', async () => {
    let requestUrl = '';
    server.use(
      http.get(ROOT + '/report', ({ request }) => {
        requestUrl = request.url;
        return HttpResponse.json({
          fromInclusive: '2026-10-05T16:00:00Z',
          toExclusive: '2026-10-06T16:00:00Z',
          casesByStatus: { OPEN: 2, ASSIGNED: 3, IN_PROGRESS: 4, RESOLVED: 5, UNREPAIRABLE: 1 },
          reportedCaseCount: 8,
          completedCaseCount: 6,
          averageCompletionDuration: 'PT2H30M',
        });
      }),
    );
    mount(['ADMIN'], '/maintenance/report');
    const from = await screen.findByLabelText('From (SGT, inclusive)');
    expect(from).toHaveValue('');
    expect(requestUrl).toBe('');
    fireEvent.change(from, { target: { value: '2026-10-06T00:00' } });
    fireEvent.change(screen.getByLabelText('To (SGT, exclusive)'), {
      target: { value: '2026-10-07T00:00' },
    });
    await userEvent.setup().click(screen.getByRole('button', { name: 'Generate report' }));
    const results = await screen.findByRole('region', { name: 'Report results' });
    for (const status of ['OPEN', 'ASSIGNED', 'IN PROGRESS', 'RESOLVED', 'UNREPAIRABLE'])
      expect(within(results).getByText(status)).toBeVisible();
    expect(within(results).getByText('2h 30m')).toBeVisible();
    expect(within(results).getByText('Reported cases in selected period')).toBeVisible();
    expect(within(results).getByText('8')).toBeVisible();
    expect(new URL(requestUrl).searchParams.get('from')).toBe('2026-10-05T16:00:00.000Z');
    expect(new URL(requestUrl).searchParams.get('to')).toBe('2026-10-06T16:00:00.000Z');
  });
  it('report validates ordered period and denies non-admin direct navigation', async () => {
    mount(['ADMIN'], '/maintenance/report');
    fireEvent.change(await screen.findByLabelText('From (SGT, inclusive)'), {
      target: { value: '2026-10-07T00:00' },
    });
    fireEvent.change(screen.getByLabelText('To (SGT, exclusive)'), {
      target: { value: '2026-10-06T00:00' },
    });
    await userEvent.setup().click(screen.getByRole('button', { name: 'Generate report' }));
    expect(await screen.findByText('Choose an end after the start.')).toBeVisible();
  });
  it('custodian cannot enter admin report', async () => {
    mount(['CUSTODIAN'], '/maintenance/report');
    expect(
      await screen.findByText('Your account does not have access to maintenance reports.'),
    ).toBeVisible();
  });
  it('queue displays a server policy error', async () => {
    lists();
    server.use(
      http.get(ROOT + '/cases/open', () =>
        HttpResponse.json({ detail: 'Operator access is unavailable.' }, { status: 403 }),
      ),
    );
    mount(['CUSTODIAN']);
    expect(await screen.findByText('Operator access is unavailable.')).toBeVisible();
    await waitFor(() => expect(screen.queryByText('Loading technicians…')).not.toBeInTheDocument());
  });
});
describe('Singapore and duration formatting', () => {
  it('handles ISO durations, nulls, fractional seconds and safe unknown fallbacks', () => {
    expect(durationLabel('PT2H30M')).toBe('2h 30m');
    expect(durationLabel('PT0S')).toBe('0s');
    expect(durationLabel('PT3.5617285S')).toBe('3.5617285s');
    expect(durationLabel('P2D')).toBe('P2D');
    expect(durationLabel(null)).toBe('No completed cases');
    expect(singaporeDate('invalid')).toBe('invalid');
    expect(singaporeInstant('2026-10-06T08:00')).toBe('2026-10-06T00:00:00.000Z');
  });
});
