import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { createColumnHelper } from '@tanstack/react-table';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { api, ApiError, message } from '@/api/client';
import type { components } from '@/api/schema';
import { PageHeader } from '@/components/ui/page-header';
import { Card, CardContent } from '@/components/ui/card';
import { DataTable } from '@/components/ui/data-table';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Field } from '@/components/ui/field';
import { EmptyState, ErrorState } from '@/components/Feedback';
import { roleLabels, roleLabel, ROLES } from '@/lib/roles';
import { EditDisplayNameDialog } from './EditDisplayNameDialog';
import { AccountSecurityDialog } from './AccountSecurityDialog';

type User = components['schemas']['AdminUser'];
const column = createColumnHelper<User>();
const columns = [
  column.accessor('displayName', { header: 'Name' }),
  column.accessor('email', { header: 'Email' }),
  column.accessor('roles', {
    header: 'Roles',
    cell: (info) => roleLabels(info.getValue()).join(' · ') || 'None',
  }),
  column.accessor('accountStatus', {
    header: 'Account',
    cell: (info) => (info.getValue() === 'ACTIVE' ? 'Active' : 'Disabled'),
  }),
  column.display({
    id: 'actions',
    header: 'Actions',
    cell: (info) => (
      <div className="flex flex-wrap gap-2 min-w-56">
        <EditDisplayNameDialog user={info.row.original} />
        {(['roles', 'status', 'password'] as const).map((operation) => (
          <AccountSecurityDialog key={operation} user={info.row.original} operation={operation} />
        ))}
      </div>
    ),
  }),
];
const schema = z.object({
  email: z.email('Enter a valid email address.').max(254),
  displayName: z.string().trim().min(1, 'Enter a display name.').max(200),
  initialPassword: z
    .string()
    .refine((value) => value.trim().length >= 12, 'Use at least 12 non-padding characters.')
    .refine((value) => new TextEncoder().encode(value).length <= 72, 'Use at most 72 UTF-8 bytes.'),
  roles: z.array(z.enum(ROLES)).min(1, 'Select at least one role.'),
});
type Values = z.infer<typeof schema>;
const defaults: Values = { email: '', displayName: '', initialPassword: '', roles: ['BORROWER'] };

export function UsersPage() {
  const query = useQueryClient();
  const [search, setSearch] = useState('');
  const [draftSearch, setDraftSearch] = useState('');
  const [page, setPage] = useState(0);
  const [failure, setFailure] = useState('');
  const [success, setSuccess] = useState('');
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: defaults });
  const users = useQuery({
    queryKey: ['admin-users', search, page],
    queryFn: async () => {
      const { data } = await api.GET('/api/v1/admin/users', {
        params: { query: { search, page, size: 20 } },
      });
      return data!;
    },
  });
  async function create(values: Values) {
    setFailure('');
    setSuccess('');
    try {
      // Do not put a password into a TanStack mutation/query cache or into notifications.
      await api.POST('/api/v1/admin/users', { body: values });
      form.reset(defaults);
      setSuccess('Account created. The user can now sign in.');
      await query.invalidateQueries({ queryKey: ['admin-users'] });
    } catch (error) {
      if (error instanceof ApiError && error.status === 409)
        form.setError('email', { message: 'This email address is already in use.' });
      else if (error instanceof ApiError && error.problem.errors?.length) {
        for (const field of error.problem.errors) {
          if (['email', 'displayName', 'initialPassword', 'roles'].includes(field.field))
            form.setError(field.field as keyof Values, { message: field.message });
        }
      } else setFailure(message(error));
    }
  }
  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="ADMINISTRATION"
        title="Users and roles"
        description="Manage accounts and explicit roles. Administrator does not imply any other role."
      />
      <Card>
        <CardContent className="pt-6 space-y-4">
          <form
            className="flex flex-wrap items-end gap-3"
            onSubmit={(event) => {
              event.preventDefault();
              setSearch(draftSearch.trim());
              setPage(0);
            }}
          >
            <Field label="Search users" hint="Name or email; literal substring search.">
              <Input
                maxLength={200}
                value={draftSearch}
                onChange={(event) => setDraftSearch(event.target.value)}
              />
            </Field>
            <Button type="submit">Search</Button>
          </form>
          {users.isError ? (
            <ErrorState message={message(users.error)} onRetry={() => void users.refetch()} />
          ) : (
            <DataTable
              columns={columns}
              data={users.data?.content ?? []}
              caption="User accounts"
              loading={users.isPending}
              empty={
                <EmptyState title="No users found" description="Try a different name or email." />
              }
              paging={{
                page,
                pageCount: Math.max(1, Math.ceil((users.data?.totalElements ?? 0) / 20)),
                onPageChange: setPage,
                busy: users.isFetching,
              }}
            />
          )}
        </CardContent>
      </Card>
      <Card>
        <CardContent className="pt-6 space-y-4">
          <h2>Create account</h2>
          <p className="text-sm text-muted-foreground">
            Security changes expire old sessions on their next protected request. You cannot disable
            yourself or remove the last active administrator.
          </p>
          <form
            onSubmit={(event) => void form.handleSubmit(create)(event)}
            className="grid gap-4 max-w-2xl"
          >
            <Field label="Email" error={form.formState.errors.email?.message}>
              <Input type="email" autoComplete="off" {...form.register('email')} />
            </Field>
            <Field label="Display name" error={form.formState.errors.displayName?.message}>
              <Input maxLength={200} {...form.register('displayName')} />
            </Field>
            <Field
              label="Initial password"
              hint="At least 12 non-padding characters, at most 72 UTF-8 bytes. Share securely outside this system."
              error={form.formState.errors.initialPassword?.message}
            >
              <Input
                type="password"
                autoComplete="new-password"
                {...form.register('initialPassword')}
              />
            </Field>
            <fieldset className="m-0 border-0 p-0">
              <legend className="text-sm font-medium">Initial roles</legend>
              <div className="flex flex-wrap gap-4 mt-2">
                {ROLES.map((role) => (
                  <label key={role} className="inline-flex items-center gap-2">
                    <input type="checkbox" value={role} {...form.register('roles')} />
                    {roleLabel(role)}
                  </label>
                ))}
              </div>
              {form.formState.errors.roles && (
                <p role="alert" className="text-sm text-destructive">
                  {form.formState.errors.roles.message}
                </p>
              )}
            </fieldset>
            {failure && (
              <p role="alert" className="text-destructive">
                {failure}
              </p>
            )}
            {success && <p role="status">{success}</p>}
            <Button type="submit" disabled={form.formState.isSubmitting}>
              {form.formState.isSubmitting ? 'Creating…' : 'Create account'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
