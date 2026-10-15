import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { api, ApiError, message } from '@/api/client';
import type { components } from '@/api/schema';
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

const schema = z.object({
  displayName: z.string().trim().min(1, 'Enter a display name.').max(200),
});
type Values = z.infer<typeof schema>;
export function EditDisplayNameDialog({ user }: { user: components['schemas']['AdminUser'] }) {
  const [open, setOpen] = useState(false);
  const [expectedName, setExpectedName] = useState(user.displayName);
  const [blocked, setBlocked] = useState(false);
  const query = useQueryClient();
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { displayName: user.displayName },
  });
  function changeOpen(next: boolean) {
    if (form.formState.isSubmitting) return;
    if (next) {
      form.reset({ displayName: user.displayName });
      setExpectedName(user.displayName);
      setBlocked(false);
    }
    setOpen(next);
  }
  async function save(values: Values) {
    if (blocked) return;
    form.clearErrors('root');
    try {
      await api.PATCH('/api/v1/admin/users/{userId}/display-name', {
        params: { path: { userId: user.userId } },
        body: { displayName: values.displayName, expectedDisplayName: expectedName },
      });
      await Promise.all([
        query.invalidateQueries({ queryKey: ['admin-users'] }),
        query.invalidateQueries({ queryKey: ['admin-audit'] }),
      ]);
      setOpen(false);
      toast.success('Display name updated.');
    } catch (error) {
      if (error instanceof ApiError && (error.status === 409 || error.status === 404)) {
        setBlocked(true);
        form.setError('root', {
          message:
            error.status === 409
              ? 'This name was changed by another administrator. Close this editor and reopen it from the refreshed list.'
              : 'This account is unavailable. Close this editor and refresh the user list.',
        });
        await query.invalidateQueries({ queryKey: ['admin-users'] });
      } else {
        const field =
          error instanceof ApiError
            ? error.problem.errors?.find((entry) => entry.field === 'displayName')
            : undefined;
        form.setError(field ? 'displayName' : 'root', {
          message: field?.message ?? message(error),
        });
      }
    }
  }
  return (
    <Dialog open={open} onOpenChange={changeOpen}>
      <DialogTrigger asChild>
        <Button variant="outline" size="sm" aria-label={`Edit display name for ${user.email}`}>
          Edit name
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Edit display name</DialogTitle>
          <DialogDescription>
            Only the name changes. Email, roles, account status, password and existing sign-in
            sessions stay unchanged.
          </DialogDescription>
        </DialogHeader>
        <p className="m-0 break-all text-sm text-muted-foreground">{user.email}</p>
        <form className="grid gap-4" onSubmit={(event) => void form.handleSubmit(save)(event)}>
          <Field
            label="New display name"
            hint="Existing sessions keep their current name until the user signs in again."
            error={form.formState.errors.displayName?.message}
          >
            <Input
              maxLength={200}
              disabled={blocked || form.formState.isSubmitting}
              {...form.register('displayName')}
            />
          </Field>
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
            <Button type="submit" disabled={blocked || form.formState.isSubmitting}>
              {form.formState.isSubmitting ? 'Saving…' : 'Save name'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
