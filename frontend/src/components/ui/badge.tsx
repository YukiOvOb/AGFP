import type { HTMLAttributes } from 'react';
import { cn } from '@/lib/utils';
import type { StatusDisplay, Tone } from '@/lib/status';
type Props = HTMLAttributes<HTMLSpanElement> & { tone?: Tone | 'neutral' };
/** Small label. Colours come from the tone tokens in styles.css. */
export function Badge({ tone = 'neutral', className, ...props }: Props) {
  return (
    <span className={cn('badge', tone !== 'neutral' && `badge-${tone}`, className)} {...props} />
  );
}
/**
 * A status from the central maps in `@/lib/status`, for example
 * `<StatusBadge status={reservationStatus[item.status]} />`.
 */
export function StatusBadge({
  status,
  className,
}: {
  status: StatusDisplay | undefined;
  className?: string;
}) {
  return (
    <Badge tone={status?.tone ?? 'muted'} className={className}>
      {status?.label ?? 'Unknown'}
    </Badge>
  );
}
