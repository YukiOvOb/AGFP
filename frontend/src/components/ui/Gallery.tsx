import { useState } from 'react';
import { createColumnHelper } from '@tanstack/react-table';
import { PackageSearch } from 'lucide-react';
import { EmptyState } from '@/components/Feedback';
import { ROLES, roleLabel } from '@/lib/roles';
import { equipmentStatus, loanStatus, maintenanceStatus, reservationStatus } from '@/lib/status';
import { Badge, StatusBadge } from './badge';
import { Button } from './button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from './card';
import { ConfirmDialog } from './confirm-dialog';
import { DataTable } from './data-table';
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from './dialog';
import { Field } from './field';
import { Input } from './input';
import { PageHeader } from './page-header';
import { Select } from './select';
import { Skeleton } from './skeleton';
import { Tabs, TabsContent, TabsList, TabsTrigger } from './tabs';
import { Textarea } from './textarea';
import { toast } from './toast';
type Row = { tag: string; name: string; category: string; status: keyof typeof equipmentStatus };
const rows: Row[] = [
  { tag: 'CAM-01', name: 'Mirrorless camera', category: 'Photography', status: 'AVAILABLE' },
  { tag: 'TRI-01', name: 'Tripod', category: 'Photography', status: 'ON_LOAN' },
  {
    tag: 'MIC-01',
    name: 'Wireless microphone kit',
    category: 'Audio',
    status: 'UNDER_MAINTENANCE',
  },
  { tag: 'LAP-01', name: 'Engineering laptop', category: 'Computing', status: 'AVAILABLE' },
  { tag: 'PRO-01', name: 'Portable projector', category: 'Presentation', status: 'RETIRED' },
];
const helper = createColumnHelper<Row>();
const columns = [
  helper.accessor('name', {
    header: 'Equipment',
    cell: (cell) => (
      <>
        <strong>{cell.getValue()}</strong>
        <span className="cell-sub">{cell.row.original.tag}</span>
      </>
    ),
  }),
  helper.accessor('category', { header: 'Category' }),
  helper.accessor('status', {
    header: 'Status',
    cell: (cell) => <StatusBadge status={equipmentStatus[cell.getValue()]} />,
  }),
];
const statusGroups = {
  Equipment: equipmentStatus,
  Reservation: reservationStatus,
  Loan: loanStatus,
  Maintenance: maintenanceStatus,
};
/**
 * Every shared component with sample data. Development builds only, at `/dev/components`;
 * copy from here when building a page. See ../README.md for the rules.
 */
export default function Gallery() {
  const [confirming, setConfirming] = useState(false);
  return (
    <>
      <PageHeader
        eyebrow="DEVELOPMENT ONLY"
        title="Shared components"
        description="Sample data. This page is not part of the production build."
        actions={<Button onClick={() => toast.success('Saved')}>Show a toast</Button>}
      />
      <div className="grid gap-6">
        <Card>
          <CardHeader>
            <CardTitle>Buttons and badges</CardTitle>
            <CardDescription>One primary action per view; red only for removal.</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-5">
            <div className="flex flex-wrap items-center gap-2">
              <Button>Primary</Button>
              <Button variant="outline">Outline</Button>
              <Button variant="ghost">Ghost</Button>
              <Button variant="destructive">Destructive</Button>
              <Button size="sm">Small</Button>
              <Button disabled>Disabled</Button>
            </div>
            <div className="flex flex-wrap gap-2">
              {ROLES.map((role) => (
                <Badge key={role}>{roleLabel(role)}</Badge>
              ))}
            </div>
            {Object.entries(statusGroups).map(([group, map]) => (
              <div key={group} className="flex flex-wrap items-center gap-2">
                <span className="w-24 text-xs text-muted-foreground">{group}</span>
                {Object.entries(map).map(([code, status]) => (
                  <StatusBadge key={code} status={status} />
                ))}
              </div>
            ))}
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Form fields</CardTitle>
            <CardDescription>
              Field binds the label, hint and validation message to the control.
            </CardDescription>
          </CardHeader>
          <CardContent className="grid gap-4 sm:grid-cols-2">
            <Field label="Equipment name" hint="As printed on the asset label.">
              <Input placeholder="e.g. Mirrorless camera" />
            </Field>
            <Field label="Serial number" error="This serial number is already registered.">
              <Input defaultValue="SN-0001" />
            </Field>
            <Field label="Category">
              <Select defaultValue="photo">
                <option value="photo">Photography</option>
                <option value="audio">Audio</option>
              </Select>
            </Field>
            <Field label="Notes">
              <Textarea placeholder="Condition, accessories, anything a borrower should know." />
            </Field>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Table</CardTitle>
            <CardDescription>Sort from the headers; two rows per page here.</CardDescription>
          </CardHeader>
          <DataTable columns={columns} data={rows} caption="Sample equipment" pageSize={2} />
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Tabs, loading and empty states</CardTitle>
          </CardHeader>
          <CardContent>
            <Tabs defaultValue="loading">
              <TabsList aria-label="States">
                <TabsTrigger value="loading">Loading</TabsTrigger>
                <TabsTrigger value="empty">Empty</TabsTrigger>
              </TabsList>
              <TabsContent value="loading" className="grid gap-3">
                <Skeleton className="h-4 w-2/3" />
                <Skeleton className="h-4 w-1/2" />
                <Skeleton className="h-24 w-full" />
              </TabsContent>
              <TabsContent value="empty">
                <EmptyState
                  icon={<PackageSearch size={26} />}
                  title="No equipment matches"
                  description="Try fewer filters or a different time."
                  action={<Button variant="outline">Clear filters</Button>}
                />
              </TabsContent>
            </Tabs>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Dialogs and toasts</CardTitle>
            <CardDescription>
              A dialog for a form, a confirmation before something hard to undo, a toast after it.
            </CardDescription>
          </CardHeader>
          <CardContent className="flex flex-wrap gap-2">
            <Dialog>
              <DialogTrigger asChild>
                <Button variant="outline">Open a dialog</Button>
              </DialogTrigger>
              <DialogContent>
                <DialogHeader>
                  <DialogTitle>Register equipment</DialogTitle>
                  <DialogDescription>Enter the details printed on the device.</DialogDescription>
                </DialogHeader>
                <Field label="Asset tag">
                  <Input placeholder="CAM-002" />
                </Field>
                <DialogFooter>
                  <DialogClose asChild>
                    <Button variant="outline">Cancel</Button>
                  </DialogClose>
                  <DialogClose asChild>
                    <Button>Save</Button>
                  </DialogClose>
                </DialogFooter>
              </DialogContent>
            </Dialog>
            <Button variant="outline" onClick={() => setConfirming(true)}>
              Ask for confirmation
            </Button>
            <Button
              variant="outline"
              onClick={() => toast.error('The request could not be saved.')}
            >
              Show an error toast
            </Button>
            <ConfirmDialog
              open={confirming}
              onOpenChange={setConfirming}
              title="Retire this equipment?"
              description="It stays in history and can no longer be reserved."
              confirmLabel="Retire"
              destructive
              onConfirm={() => {
                setConfirming(false);
                toast.success('Equipment retired');
              }}
            />
          </CardContent>
        </Card>
      </div>
    </>
  );
}
