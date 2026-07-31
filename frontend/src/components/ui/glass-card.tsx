import { forwardRef, type HTMLAttributes } from 'react';
import { cn } from '@/lib/utils';

export interface GlassCardProps extends HTMLAttributes<HTMLDivElement> {
  hover?: boolean;
}

export const GlassCard = forwardRef<HTMLDivElement, GlassCardProps>(
  ({ className, hover = false, ...props }, ref) => (
    <div
      ref={ref}
      className={cn(
        'rounded-lg border border-border-subtle bg-surface-overlay backdrop-blur-xl',
        'shadow-[0_0_20px_var(--shadow-glow)]',
        hover &&
          'transition-all duration-300 hover:border-border-default hover:bg-surface-hover hover:shadow-[0_0_28px_var(--shadow-glow)]',
        className,
      )}
      {...props}
    />
  ),
);
GlassCard.displayName = 'GlassCard';
