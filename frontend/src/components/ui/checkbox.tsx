import { forwardRef, type InputHTMLAttributes } from 'react';
import { cn } from '@/lib/utils';

export const Checkbox = forwardRef<HTMLInputElement, Omit<InputHTMLAttributes<HTMLInputElement>, 'type'>>(
  ({ className, ...props }, ref) => (
    <input
      ref={ref}
      type="checkbox"
      className={cn(
        'h-4 w-4 shrink-0 cursor-pointer appearance-none rounded-[4px] border border-border-strong bg-surface-overlay',
        'transition-colors duration-150',
        'checked:border-silver-400 checked:bg-silver-400',
        'focus:border-silver-300/50 focus:outline-none focus:ring-1 focus:ring-silver-300/30',
        'disabled:cursor-not-allowed disabled:opacity-50',
        className,
      )}
      {...props}
    />
  ),
);
Checkbox.displayName = 'Checkbox';
