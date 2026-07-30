import { DIMENSION_CONFIG, type EvaluationDimension } from '@/types/report';
import { GlassCard } from '@/components/ui/glass-card';

interface DimensionScoreListProps {
  scores: Partial<Record<EvaluationDimension, number>>;
}

export function DimensionScoreList({ scores }: DimensionScoreListProps) {
  const dimensions = Object.keys(DIMENSION_CONFIG) as EvaluationDimension[];

  return (
    <GlassCard className="p-6">
      <h3 className="mb-4 text-sm font-medium text-text-muted">维度评分明细</h3>
      <div className="space-y-3">
        {dimensions.map((dim) => {
          const config = DIMENSION_CONFIG[dim];
          const score = scores[dim] ?? 0;
          const percent = (score / 5) * 100;
          return (
            <div key={dim} className="flex items-center gap-4">
              <div className="w-24 shrink-0">
                <span className="text-sm text-text-secondary">
                  {config.label}
                </span>
              </div>
              <div className="flex-1">
                <div className="h-2 overflow-hidden rounded-full bg-white/5">
                  <div
                    className="h-full rounded-full bg-gradient-to-r from-sky-500/60 to-sky-400/80 transition-all duration-500"
                    style={{ width: `${percent}%` }}
                  />
                </div>
              </div>
              <div className="w-12 shrink-0 text-right">
                <span className="text-sm font-medium text-text-primary">
                  {score.toFixed(1)}
                </span>
                <span className="text-xs text-text-muted">/5</span>
              </div>
              <div className="w-12 shrink-0 text-right">
                <span className="text-xs text-text-muted">
                  {(config.weight * 100).toFixed(0)}%
                </span>
              </div>
            </div>
          );
        })}
      </div>
    </GlassCard>
  );
}
