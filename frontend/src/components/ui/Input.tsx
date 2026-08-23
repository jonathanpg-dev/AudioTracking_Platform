import { forwardRef, type InputHTMLAttributes, type SelectHTMLAttributes, type TextareaHTMLAttributes, type LabelHTMLAttributes, type ReactNode } from 'react'
import { cn } from '@/utils/cn'

const FIELD_CLASSES =
  'w-full rounded-md border border-border bg-surface px-3 py-2 text-sm text-ink placeholder:text-ink-subtle ' +
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:border-accent ' +
  'disabled:cursor-not-allowed disabled:bg-surface-muted'

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(
  ({ className, ...props }, ref) => (
    <input ref={ref} className={cn(FIELD_CLASSES, className)} {...props} />
  ),
)
Input.displayName = 'Input'

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement>>(
  ({ className, ...props }, ref) => (
    <textarea ref={ref} className={cn(FIELD_CLASSES, 'min-h-24 resize-y', className)} {...props} />
  ),
)
Textarea.displayName = 'Textarea'

export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement> & { children: ReactNode }>(
  ({ className, children, ...props }, ref) => (
    <select ref={ref} className={cn(FIELD_CLASSES, 'bg-surface', className)} {...props}>
      {children}
    </select>
  ),
)
Select.displayName = 'Select'

// Always paired with an input via htmlFor/id -- see the "labels for form inputs" accessibility
// requirement. A required field gets a visible, non-color-only marker.
export function Label({ className, children, required, ...props }: LabelHTMLAttributes<HTMLLabelElement> & { required?: boolean }) {
  return (
    <label className={cn('mb-1.5 block text-sm font-medium text-ink', className)} {...props}>
      {children}
      {required && (
        <span className="ml-0.5 text-danger" aria-hidden="true">
          *
        </span>
      )}
    </label>
  )
}

export function FieldError({ children }: { children?: string | null }) {
  if (!children) return null
  return (
    <p className="mt-1.5 text-sm text-danger" role="alert">
      {children}
    </p>
  )
}
