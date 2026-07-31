import type { HTMLAttributes } from 'react';
import { cn } from '@/lib/utils';

type Variant = 'default' | 'category' | 'difficulty' | 'score';

const variants: Record<Variant, string> = {
  default: 'border-border-default bg-surface-hover text-text-secondary',
  category: 'border-silver-300/30 bg-silver-300/10 text-silver-200',
  difficulty: 'border-sky-400/30 bg-sky-400/10 text-sky-300',
  score: 'border-silver-glow/30 bg-silver-glow/10 text-silver-100',
};

export function Badge({
  variant = 'default',
  className,
  ...props
}: HTMLAttributes<HTMLSpanElement> & { variant?: Variant }) {
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium',
        'border',
        variants[variant],
        className,
      )}
      {...props}
    />
  );
}
