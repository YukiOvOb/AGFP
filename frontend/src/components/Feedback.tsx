import type { ReactNode } from 'react';
import { AlertCircle, Bell, LoaderCircle } from 'lucide-react';
import { Button } from './ui/button';
export function LoadingState() {
  return (
    <div className="state" role="status">
      <LoaderCircle className="spin" />
      Loading your workspace…
    </div>
  );
}
export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="error-state" role="alert">
      <AlertCircle size={20} />
      <div>
        <strong>Something needs your attention</strong>
        <p>{message}</p>
        {onRetry && (
          <Button variant="outline" onClick={onRetry}>
            Try again
          </Button>
        )}
      </div>
    </div>
  );
}
type EmptyProps = {
  icon?: ReactNode;
  title?: string;
  /** Say what will appear here, or what to do next. */
  description?: string;
  /** For example a button that creates the first item. */
  action?: ReactNode;
};
/** Without props it is the inbox message; pass a title and description for any other list. */
export function EmptyState({
  icon = <Bell size={26} />,
  title = 'You’re all caught up',
  description = 'Equipment reminders and reservation updates will appear here.',
  action,
}: EmptyProps = {}) {
  return (
    <div className="state empty">
      <span className="state-icon">{icon}</span>
      <h2>{title}</h2>
      {description && <p>{description}</p>}
      {action}
    </div>
  );
}
