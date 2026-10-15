import type { HTMLAttributes } from 'react';
import { cn } from '@/lib/utils';
/** Placeholder block while content loads; size it with `h-*` and `w-*`. */
export function Skeleton({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      aria-hidden="true"
      className={cn('animate-pulse rounded-md bg-muted motion-reduce:animate-none', className)}
      {...props}
    />
  );
}
