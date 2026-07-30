import { RECOMMENDATION_COLORS, type Recommendation } from '@/types/report';
import { cn } from '@/lib/utils';

interface RecommendationBadgeProps {
  recommendation: Recommendation;
  label?: string;
}

export function RecommendationBadge({
  recommendation,
  label,
}: RecommendationBadgeProps) {
  const colorClass = RECOMMENDATION_COLORS[recommendation] ?? '';
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full border px-3 py-1 text-xs font-medium',
        colorClass,
      )}
    >
      {label ?? recommendation}
    </span>
  );
}
