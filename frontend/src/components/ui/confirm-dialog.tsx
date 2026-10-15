import type { ReactNode } from 'react';
import * as AlertDialog from '@radix-ui/react-alert-dialog';
import { cn } from '@/lib/utils';
import { Button } from './button';
import { contentClass, overlayClass, useReturnFocus } from './dialog';
type Props = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  /** Say what will happen and whether it can be undone. */
  description: ReactNode;
  confirmLabel?: string;
  cancelLabel?: string;
  /** Red confirm button for actions that remove or disable something. */
  destructive?: boolean;
  /** Disables both buttons while the request runs. */
  pending?: boolean;
  /** The dialog stays open; close it from the caller once the action has succeeded. */
  onConfirm: () => void | Promise<void>;
};
/**
 * Yes/no question before an action that is hard to undo (retire equipment, deactivate a user).
 * Focus starts on Cancel, returns to what opened it, and clicking outside does not dismiss it.
 */
export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  description,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  destructive = false,
  pending = false,
  onConfirm,
}: Props) {
  const focus = useReturnFocus();
  return (
    <AlertDialog.Root open={open} onOpenChange={onOpenChange}>
      <AlertDialog.Portal>
        <AlertDialog.Overlay className={overlayClass} />
        <AlertDialog.Content
          className={cn(contentClass, 'max-w-md')}
          onOpenAutoFocus={focus.onOpenAutoFocus}
          onCloseAutoFocus={focus.onCloseAutoFocus}
        >
          <div className="flex flex-col gap-1.5">
            <AlertDialog.Title className="m-0 text-base font-semibold">{title}</AlertDialog.Title>
            <AlertDialog.Description className="m-0 text-sm leading-relaxed text-muted-foreground">
              {description}
            </AlertDialog.Description>
          </div>
          <div className="flex flex-wrap justify-end gap-2">
            <AlertDialog.Cancel asChild>
              <Button variant="outline" disabled={pending}>
                {cancelLabel}
              </Button>
            </AlertDialog.Cancel>
            <Button
              variant={destructive ? 'destructive' : 'default'}
              disabled={pending}
              onClick={() => void onConfirm()}
            >
              {confirmLabel}
            </Button>
          </div>
        </AlertDialog.Content>
      </AlertDialog.Portal>
    </AlertDialog.Root>
  );
}
