import type { InputHTMLAttributes } from 'react';
import { cn } from '@/lib/utils';
import { useFieldControl } from './field';
export function Input({ className, ...props }: InputHTMLAttributes<HTMLInputElement>) {
  return <input className={cn('input', className)} {...useFieldControl()} {...props} />;
}
