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
export function EmptyState() {
  return (
    <div className="state empty">
      <span className="state-icon">
        <Bell size={26} />
      </span>
      <h2>You’re all caught up</h2>
      <p>Equipment reminders and reservation updates will appear here.</p>
    </div>
  );
}
