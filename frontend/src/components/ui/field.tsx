import { createContext, useContext, useId, type LabelHTMLAttributes, type ReactNode } from 'react';
import { cn } from '@/lib/utils';
type FieldControl = { id?: string; 'aria-invalid'?: true; 'aria-describedby'?: string };
const FieldContext = createContext<FieldControl>({});
/**
 * Attributes for the control inside a `Field`. `Input`, `Select` and `Textarea` spread them
 * already; props written on the control itself win.
 */
export function useFieldControl() {
  return useContext(FieldContext);
}
export function Label({ className, ...props }: LabelHTMLAttributes<HTMLLabelElement>) {
  return <label className={cn('text-xs font-medium text-foreground', className)} {...props} />;
}
type Props = {
  label: ReactNode;
  /** Help text shown under the control. */
  hint?: ReactNode;
  /** Validation message, for example `errors.email?.message` from React Hook Form. */
  error?: ReactNode;
  /** Only needed when something else must reference the control. */
  id?: string;
  className?: string;
  children: ReactNode;
};
/**
 * Label, one control, optional hint and validation message, wired for assistive technology:
 * the label is bound to the control and the hint and error are announced with it.
 */
export function Field({ label, hint, error, id, className, children }: Props) {
  const generated = useId();
  const controlId = id ?? generated;
  const hintId = hint ? `${controlId}-hint` : undefined;
  const errorId = error ? `${controlId}-error` : undefined;
  return (
    <div className={cn('grid content-start gap-1.5', className)}>
      <Label htmlFor={controlId}>{label}</Label>
      <FieldContext.Provider
        value={{
          id: controlId,
          'aria-invalid': error ? true : undefined,
          'aria-describedby': [hintId, errorId].filter(Boolean).join(' ') || undefined,
        }}
      >
        {children}
      </FieldContext.Provider>
      {hint && (
        <p id={hintId} className="m-0 text-xs leading-relaxed text-muted-foreground">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="m-0 text-xs text-destructive">
          {error}
        </p>
      )}
    </div>
  );
}
