import { forwardRef, type InputHTMLAttributes, type TextareaHTMLAttributes } from 'react';
import { cn } from '@/lib/utils';

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(
  ({ className, ...props }, ref) => (
    <input
      ref={ref}
      className={cn(
        'w-full rounded-md border border-border-default bg-surface-overlay px-3 py-2 text-sm',
        'text-text-primary placeholder:text-text-muted',
        'focus:border-silver-300/50 focus:outline-none focus:ring-1 focus:ring-silver-300/30',
        'transition-colors',
        className,
      )}
      {...props}
    />
  ),
);
Input.displayName = 'Input';

export const Textarea = forwardRef<
  HTMLTextAreaElement,
  TextareaHTMLAttributes<HTMLTextAreaElement>
>(({ className, ...props }, ref) => (
  <textarea
    ref={ref}
    className={cn(
      'w-full rounded-md border border-border-default bg-surface-overlay px-3 py-2 text-sm',
      'text-text-primary placeholder:text-text-muted',
      'focus:border-silver-300/50 focus:outline-none focus:ring-1 focus:ring-silver-300/30',
      'transition-colors resize-none',
      className,
    )}
    {...props}
  />
));
Textarea.displayName = 'Textarea';

export const Select = forwardRef<
  HTMLSelectElement,
  React.SelectHTMLAttributes<HTMLSelectElement>
>(({ className, children, ...props }, ref) => (
  <select
    ref={ref}
    className={cn(
      'w-full rounded-md border border-border-default bg-space-700 px-3 py-2 text-sm',
      'text-text-primary',
      'focus:border-silver-300/50 focus:outline-none focus:ring-1 focus:ring-silver-300/30',
      'transition-colors',
      className,
    )}
    {...props}
  >
    {children}
  </select>
));
Select.displayName = 'Select';

export function Label({ children, htmlFor }: { children: React.ReactNode; htmlFor?: string }) {
  return (
    <label htmlFor={htmlFor} className="mb-1.5 block text-sm text-text-secondary">
      {children}
    </label>
  );
}
