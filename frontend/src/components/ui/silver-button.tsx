import { forwardRef, type ButtonHTMLAttributes } from 'react';
import { cn } from '@/lib/utils';

type Variant = 'primary' | 'ghost' | 'danger';

export interface SilverButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
}

const variants: Record<Variant, string> = {
  primary: cn(
    'relative overflow-hidden text-space-900',
    'bg-gradient-to-r from-silver-300 via-silver-100 to-silver-300',
    'shadow-[0_0_16px_var(--silver-glow)] hover:shadow-[0_0_28px_var(--silver-glow)]',
    'hover:scale-[1.02] active:scale-[0.98] transition-all',
    'before:absolute before:inset-0 before:-translate-x-full',
    'before:bg-[linear-gradient(110deg,transparent,rgba(255,255,255,0.65),transparent)]',
    'before:animate-stream',
  ),
  ghost: cn(
    'border border-white/10 bg-transparent text-text-secondary',
    'hover:bg-white/5 hover:text-text-primary hover:border-white/20',
    'transition-all',
  ),
  danger: cn(
    'border border-danger/30 bg-danger/10 text-danger',
    'hover:bg-danger/20 hover:border-danger/50',
    'transition-all',
  ),
};

export const SilverButton = forwardRef<HTMLButtonElement, SilverButtonProps>(
  ({ className, variant = 'primary', ...props }, ref) => (
    <button
      ref={ref}
      className={cn(
        'inline-flex items-center justify-center gap-2 rounded-md px-5 py-2.5',
        'font-medium text-sm disabled:opacity-40 disabled:pointer-events-none',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-silver-glow/50',
        variants[variant],
        className,
      )}
      {...props}
    />
  ),
);
SilverButton.displayName = 'SilverButton';
