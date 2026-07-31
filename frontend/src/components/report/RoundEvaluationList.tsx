import type { EvaluationResponse } from '@/types/report';
import { GlassCard } from '@/components/ui/glass-card';

interface RoundEvaluationListProps {
  evaluations: EvaluationResponse[];
}

export function RoundEvaluationList({ evaluations }: RoundEvaluationListProps) {
  // 按 roundId 分组
  const grouped = evaluations.reduce<
    Record<number, EvaluationResponse[]>
  >((acc, evalItem) => {
    const key = evalItem.roundId;
    if (!acc[key]) acc[key] = [];
    acc[key].push(evalItem);
    return acc;
  }, {});

  const roundIds = Object.keys(grouped).map(Number).sort((a, b) => a - b);

  return (
    <GlassCard className="p-6">
      <h3 className="mb-4 text-sm font-medium text-text-muted">轮次评估明细</h3>
      <div className="space-y-6">
        {roundIds.map((roundId, idx) => (
          <div key={roundId}>
            <div className="mb-3 flex items-center gap-2">
              <span className="flex h-6 w-6 items-center justify-center rounded-full bg-sky-400/10 text-xs font-medium text-sky-400">
                {idx + 1}
              </span>
              <span className="text-sm font-medium text-text-secondary">
                第 {idx + 1} 轮
              </span>
            </div>
            <div className="space-y-3 pl-8">
              {grouped[roundId].map((evalItem) => (
                <div
                  key={evalItem.id}
                  className="rounded-lg border border-border-subtle bg-surface-overlay p-4"
                >
                  <div className="mb-2 flex items-center justify-between">
                    <span className="text-sm text-text-secondary">
                      {evalItem.dimensionLabel}
                    </span>
                    <span className="flex items-center gap-1">
                      <span className="text-base font-semibold text-sky-400">
                        {evalItem.score}
                      </span>
                      <span className="text-xs text-text-muted">/5</span>
                    </span>
                  </div>
                  <p className="mb-2 text-sm text-text-primary">
                    {evalItem.comment}
                  </p>
                  {evalItem.evidenceQuote && (
                    <blockquote className="border-l-2 border-sky-400/30 pl-3 text-sm italic text-text-muted">
                      "{evalItem.evidenceQuote}"
                    </blockquote>
                  )}
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>
    </GlassCard>
  );
}
