import type { CSSProperties } from 'react';
import { Toaster as Sonner, toast } from 'sonner';
/**
 * Mounted once in `main.tsx`. Call `toast.success('Saved')` or `toast.error(message(error))`
 * after an action. Keep validation messages next to their fields, not in a toast.
 */
export function Toaster() {
  return (
    <Sonner
      position="bottom-right"
      closeButton
      style={
        {
          '--normal-bg': 'var(--popover)',
          '--normal-text': 'var(--popover-foreground)',
          '--normal-border': 'var(--border)',
        } as CSSProperties
      }
    />
  );
}
export { toast };
