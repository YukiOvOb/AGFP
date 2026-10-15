import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { EmptyState } from '@/components/Feedback';
import { loanDisplay, maintenanceStatus, reservationStatus } from '@/lib/status';
import { Badge, StatusBadge } from './badge';
import { Button } from './button';
import { ConfirmDialog } from './confirm-dialog';
import { Dialog, DialogContent, DialogDescription, DialogTitle, DialogTrigger } from './dialog';
import { Field } from './field';
import { Input } from './input';
import { PageHeader } from './page-header';
import { Select } from './select';
import { Tabs, TabsContent, TabsList, TabsTrigger } from './tabs';
import { Textarea } from './textarea';
describe('Field', () => {
  it('binds the label, hint and error to the control', () => {
    render(
      <Field label="Serial number" hint="Printed under the device." error="Already in use.">
        <Input />
      </Field>,
    );
    const input = screen.getByLabelText('Serial number');
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(input).toHaveAccessibleDescription('Printed under the device. Already in use.');
  });
  it('leaves a valid control unmarked and works for selects and text areas', () => {
    render(
      <>
        <Field label="Category">
          <Select defaultValue="audio">
            <option value="audio">Audio</option>
          </Select>
        </Field>
        <Field label="Notes" id="notes">
          <Textarea />
        </Field>
      </>,
    );
    expect(screen.getByLabelText('Category')).not.toHaveAttribute('aria-invalid');
    expect(screen.getByLabelText('Category')).toHaveValue('audio');
    expect(screen.getByLabelText('Notes')).toHaveAttribute('id', 'notes');
  });
  it('lets a control keep its own attributes outside a field', () => {
    render(<Input aria-label="Search" id="q" aria-invalid />);
    expect(screen.getByLabelText('Search')).toHaveAttribute('id', 'q');
    expect(screen.getByLabelText('Search')).toHaveAttribute('aria-invalid', 'true');
  });
});
describe('ConfirmDialog', () => {
  function Retire({ onConfirm }: { onConfirm: () => void }) {
    const [open, setOpen] = useState(false);
    return (
      <>
        <Button onClick={() => setOpen(true)}>Retire</Button>
        <ConfirmDialog
          open={open}
          onOpenChange={setOpen}
          title="Retire this camera?"
          description="It stays in history and can no longer be reserved."
          confirmLabel="Retire"
          destructive
          onConfirm={onConfirm}
        />
      </>
    );
  }
  it('asks first, starts on Cancel and only acts on confirmation', async () => {
    const onConfirm = vi.fn();
    render(<Retire onConfirm={onConfirm} />);
    await userEvent.click(screen.getByRole('button', { name: 'Retire' }));
    const dialog = screen.getByRole('alertdialog', { name: 'Retire this camera?' });
    expect(dialog).toHaveAccessibleDescription(
      'It stays in history and can no longer be reserved.',
    );
    expect(screen.getByRole('button', { name: 'Cancel' })).toHaveFocus();
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(onConfirm).not.toHaveBeenCalled();
    // Opened from state, without a Radix trigger: focus still returns to the opener.
    expect(screen.getByRole('button', { name: 'Retire' })).toHaveFocus();
    await userEvent.click(screen.getByRole('button', { name: 'Retire' }));
    await userEvent.click(screen.getAllByRole('button', { name: 'Retire' }).at(-1)!);
    expect(onConfirm).toHaveBeenCalledTimes(1);
    // The caller closes it after the request succeeds.
    expect(screen.getByRole('alertdialog')).toBeInTheDocument();
  });
  it('blocks both buttons while the action is running', () => {
    render(
      <ConfirmDialog
        open
        onOpenChange={() => {}}
        title="Deactivate account?"
        description="The user can no longer sign in."
        pending
        onConfirm={() => {}}
      />,
    );
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Confirm' })).toBeDisabled();
  });
});
describe('Dialog and Tabs', () => {
  it('opens a titled dialog and closes it with Escape', async () => {
    render(
      <Dialog>
        <DialogTrigger asChild>
          <Button>Add equipment</Button>
        </DialogTrigger>
        <DialogContent>
          <DialogTitle>Register equipment</DialogTitle>
          <DialogDescription>Enter the details printed on the device.</DialogDescription>
        </DialogContent>
      </Dialog>,
    );
    await userEvent.click(screen.getByRole('button', { name: 'Add equipment' }));
    expect(screen.getByRole('dialog', { name: 'Register equipment' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Close' })).toBeInTheDocument();
    await userEvent.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Add equipment' })).toHaveFocus();
  });
  it('shows one tab panel at a time', async () => {
    render(
      <Tabs defaultValue="open">
        <TabsList aria-label="Cases">
          <TabsTrigger value="open">Open</TabsTrigger>
          <TabsTrigger value="closed">Closed</TabsTrigger>
        </TabsList>
        <TabsContent value="open">Open cases</TabsContent>
        <TabsContent value="closed">Closed cases</TabsContent>
      </Tabs>,
    );
    expect(screen.getByText('Open cases')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('tab', { name: 'Closed' }));
    expect(screen.getByText('Closed cases')).toBeInTheDocument();
    expect(screen.queryByText('Open cases')).not.toBeInTheDocument();
  });
});
describe('labels, headers and empty states', () => {
  it('shows a status from the central maps and falls back for an unknown one', () => {
    render(
      <>
        <StatusBadge status={reservationStatus.PENDING_APPROVAL} />
        <StatusBadge status={maintenanceStatus.UNREPAIRABLE} />
        <StatusBadge status={loanDisplay('ACTIVE', true)} />
        <StatusBadge status={undefined} />
        <Badge>Borrower</Badge>
      </>,
    );
    expect(screen.getByText('Waiting for approval')).toHaveClass('badge', 'badge-pending');
    expect(screen.getByText('Not repairable')).toHaveClass('badge-bad');
    expect(screen.getByText('Overdue')).toHaveClass('badge-bad');
    expect(screen.getByText('Unknown')).toHaveClass('badge-muted');
    expect(screen.getByText('Borrower').className).toBe('badge');
  });
  it('gives a page one heading, its description and its action', () => {
    render(
      <PageHeader
        eyebrow="ADMINISTRATION"
        title="Users and roles"
        description="Create accounts and assign roles."
        actions={<Button>Add user</Button>}
      />,
    );
    expect(screen.getByRole('heading', { level: 1, name: 'Users and roles' })).toBeInTheDocument();
    expect(screen.getByText('Create accounts and assign roles.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Add user' })).toBeInTheDocument();
  });
  it('keeps the inbox wording by default and accepts another list', () => {
    const { rerender } = render(<EmptyState />);
    expect(screen.getByRole('heading', { name: 'You’re all caught up' })).toBeInTheDocument();
    rerender(
      <EmptyState
        title="No overdue loans"
        description="Everything is back on time."
        action={<Button>Refresh</Button>}
      />,
    );
    expect(screen.getByRole('heading', { name: 'No overdue loans' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Refresh' })).toBeInTheDocument();
  });
});
