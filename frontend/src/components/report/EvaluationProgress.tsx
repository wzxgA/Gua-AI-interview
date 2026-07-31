import { GlassCard } from '@/components/ui/glass-card';

interface EvaluationProgressProps {
  status: string;
  evaluatedRounds: number | null;
  totalRoundsToEvaluate: number | null;
}

export function EvaluationProgress({
  status,
  evaluatedRounds,
  totalRoundsToEvaluate,
}: EvaluationProgressProps) {
  const isEvaluating = status === 'EVALUATING';
  const isReporting = status === 'REPORTING';

  if (!isEvaluating && !isReporting) return null;

  const current = evaluatedRounds ?? 0;
  const total = totalRoundsToEvaluate ?? 0;
  const percent = total > 0 ? (current / total) * 100 : 0;

  return (
    <GlassCard className="p-6">
      <div className="flex flex-col items-center gap-4 py-4">
        <div className="h-8 w-8 animate-spin rounded-full border-2 border-sky-400/20 border-t-sky-400" />
        <div className="text-center">
          {isEvaluating ? (
            <>
              <p className="text-sm font-medium text-text-primary">
                正在评估...
              </p>
              {total > 0 && (
                <p className="mt-1 text-xs text-text-muted">
                  已完成 {current} / {total} 轮
                </p>
              )}
            </>
          ) : (
            <p className="text-sm font-medium text-text-primary">
              报告生成中...
            </p>
          )}
        </div>
        {isEvaluating && total > 0 && (
          <div className="h-1.5 w-48 overflow-hidden rounded-full bg-surface-hover">
            <div
              className="h-full rounded-full bg-sky-400/60 transition-all duration-500"
              style={{ width: `${percent}%` }}
            />
          </div>
        )}
      </div>
    </GlassCard>
  );
}
