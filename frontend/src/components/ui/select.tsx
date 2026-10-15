import type { SelectHTMLAttributes } from 'react';
import { cn } from '@/lib/utils';
import { useFieldControl } from './field';
/**
 * The platform's own select, styled like `Input`: it works with touch, keyboards, screen readers
 * and `register()` from React Hook Form without extra code. Pass `<option>` elements as children.
 */
export function Select({ className, ...props }: SelectHTMLAttributes<HTMLSelectElement>) {
  return <select className={cn('input', className)} {...useFieldControl()} {...props} />;
}
