import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { api, ApiError, message } from '@/api/client';
import type { components } from '@/api/schema';
import { useAuth } from '@/app/AuthProvider';
import { Button } from '@/components/ui/button';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';
import { toast } from '@/components/ui/toast';
import { ROLES, roleLabel } from '@/lib/roles';

type User = components['schemas']['AdminUser'];
type Operation = 'roles' | 'status' | 'password';

export function AccountSecurityDialog({ user, operation }: { user: User; operation: Operation }) {
  const [open, setOpen] = useState(false);
  const [snapshot, setSnapshot] = useState(user);
  const [blocked, setBlocked] = useState(false);
  const query = useQueryClient();
  const auth = useAuth();
  const disabling = snapshot.accountStatus === 'ACTIVE';
  const title =
    operation === 'roles'
      ? 'Edit assigned roles'
      : operation === 'password'
        ? 'Reset password'
        : disabling
          ? 'Disable account'
          : 'Activate account';
  const schema = z
    .object({ roles: z.array(z.enum(ROLES)), newPassword: z.string() })
    .superRefine((values, context) => {
      if (operation === 'roles' && values.roles.length === 0)
        context.addIssue({ code: 'custom', path: ['roles'], message: 'Select at least one role.' });
      if (operation === 'password') {
        if (values.newPassword.trim().length < 12)
          context.addIssue({
            code: 'custom',
            path: ['newPassword'],
            message: 'Use at least 12 non-padding characters.',
          });
        if (new TextEncoder().encode(values.newPassword).length > 72)
          context.addIssue({
            code: 'custom',
            path: ['newPassword'],
            message: 'Use at most 72 UTF-8 bytes.',
          });
      }
    });
  const form = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: { roles: [...user.roles], newPassword: '' },
  });
  function changeOpen(next: boolean) {
    if (form.formState.isSubmitting) return;
    form.reset({ roles: [...user.roles], newPassword: '' });
    setBlocked(false);
    if (next) setSnapshot(user); // Keep the opened version, even when the directory refetches.
    setOpen(next);
  }
  async function save(values: z.infer<typeof schema>) {
    if (blocked) return;
    form.clearErrors('root');
    const params = { path: { userId: snapshot.userId } };
    const expectedVersion = snapshot.version;
    try {
      // Passwords never enter the QueryClient/mutation cache, toast text or audit payload.
      if (operation === 'roles')
        await api.PATCH('/api/v1/admin/users/{userId}/roles', {
          params,
          body: { roles: values.roles, expectedVersion },
        });
      else if (operation === 'status')
        await api.PATCH('/api/v1/admin/users/{userId}/status', {
          params,
          body: { active: !disabling, expectedVersion },
        });
      else
        await api.POST('/api/v1/admin/users/{userId}/password-reset', {
          params,
          body: { newPassword: values.newPassword, expectedVersion },
        });
      form.reset({ roles: [...snapshot.roles], newPassword: '' });
      setOpen(false);
      toast.success('Account security settings saved.');
      // Self role/password changes intentionally expire this session on its next protected request.
      if (snapshot.userId === auth.user?.userId) await auth.reload();
      else
        await Promise.all([
          query.invalidateQueries({ queryKey: ['admin-users'] }),
          query.invalidateQueries({ queryKey: ['admin-audit'] }),
        ]);
    } catch (error) {
      if (error instanceof ApiError && (error.status === 409 || error.status === 404)) {
        setBlocked(true);
        form.setError('root', {
          message: `${message(error)} Close this editor and reopen it from the refreshed list.`,
        });
        await query.invalidateQueries({ queryKey: ['admin-users'] });
      } else form.setError('root', { message: message(error) });
      // Do not retain a replacement password after any attempted submission.
      form.setValue('newPassword', '');
    }
  }
  return (
    <Dialog open={open} onOpenChange={changeOpen}>
      <DialogTrigger asChild>
        <Button
          variant="outline"
          size="sm"
          disabled={
            operation === 'status' &&
            user.accountStatus === 'ACTIVE' &&
            user.userId === auth.user?.userId
          }
          aria-label={`Manage ${operation} for ${user.email}`}
        >
          {operation === 'roles'
            ? 'Edit roles'
            : operation === 'password'
              ? 'Reset password'
              : user.accountStatus === 'ACTIVE'
                ? 'Disable'
                : 'Activate'}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>
            {operation === 'password'
              ? 'Set a replacement password and share it securely outside this system. All old sessions will expire on their next protected request.'
              : operation === 'roles'
                ? 'Choose explicit roles. Administrator does not inherit other roles. An actual change expires all old sessions on their next protected request.'
                : disabling
                  ? 'This user will be unable to sign in. All old sessions will expire on their next protected request. You can activate this account later.'
                  : 'Allow this user to sign in again. Previously revoked sessions will not be restored.'}
          </DialogDescription>
        </DialogHeader>
        <p className="m-0 break-all text-sm text-muted-foreground">{snapshot.email}</p>
        <form className="grid gap-4" onSubmit={(event) => void form.handleSubmit(save)(event)}>
          {operation === 'roles' && (
            <fieldset
              disabled={blocked || form.formState.isSubmitting}
              className="m-0 border-0 p-0"
            >
              <legend className="text-sm font-medium">Assigned roles</legend>
              <div className="flex flex-wrap gap-4 mt-2">
                {ROLES.map((role) => (
                  <label key={role} className="inline-flex items-center gap-2">
                    <input type="checkbox" value={role} {...form.register('roles')} />
                    Assigned {roleLabel(role)}
                  </label>
                ))}
              </div>
              {form.formState.errors.roles && (
                <p role="alert" className="text-sm text-destructive">
                  {form.formState.errors.roles.message}
                </p>
              )}
            </fieldset>
          )}
          {operation === 'password' && (
            <Field
              label="New password"
              hint="At least 12 non-padding characters, at most 72 UTF-8 bytes."
              error={form.formState.errors.newPassword?.message}
            >
              <Input
                type="password"
                autoComplete="new-password"
                disabled={blocked || form.formState.isSubmitting}
                {...form.register('newPassword')}
              />
            </Field>
          )}
          {form.formState.errors.root && (
            <p role="alert" className="text-sm text-destructive">
              {form.formState.errors.root.message}
            </p>
          )}
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              disabled={form.formState.isSubmitting}
              onClick={() => changeOpen(false)}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              variant={operation === 'status' && disabling ? 'destructive' : 'default'}
              disabled={blocked || form.formState.isSubmitting}
            >
              {form.formState.isSubmitting
                ? 'Saving…'
                : operation === 'status'
                  ? disabling
                    ? 'Confirm disable'
                    : 'Confirm activation'
                  : operation === 'password'
                    ? 'Save new password'
                    : 'Save roles'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
