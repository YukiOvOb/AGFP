import type { TextareaHTMLAttributes } from 'react';
import { cn } from '@/lib/utils';
import { useFieldControl } from './field';
export function Textarea({ className, ...props }: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return (
    <textarea
      className={cn('input min-h-24 resize-y leading-relaxed', className)}
      {...useFieldControl()}
      {...props}
    />
  );
}
