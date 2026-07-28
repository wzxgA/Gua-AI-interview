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
        'rounded-lg border border-white/5 bg-white/[0.03] backdrop-blur-xl',
        'shadow-[0_0_20px_rgba(200,212,232,0.04)]',
        hover && 'transition-all duration-300 hover:border-white/10 hover:bg-white/[0.05] hover:shadow-[0_0_28px_rgba(200,212,232,0.08)]',
        className,
      )}
      {...props}
    />
  ),
);
GlassCard.displayName = 'GlassCard';
