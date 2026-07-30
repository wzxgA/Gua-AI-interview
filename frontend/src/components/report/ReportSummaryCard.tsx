import type { ReportResponse } from '@/types/report';
import { GlassCard } from '@/components/ui/glass-card';
import { RecommendationBadge } from './RecommendationBadge';

interface ReportSummaryCardProps {
  report: ReportResponse;
}

export function ReportSummaryCard({ report }: ReportSummaryCardProps) {
  const score = report.totalScore ?? 0;
  const scorePercent = (score / 5) * 100;

  return (
    <GlassCard className="p-6">
      <div className="flex items-start justify-between gap-6">
        <div className="flex-1">
          <h3 className="mb-2 text-sm font-medium text-text-muted">综合概览</h3>
          <div className="mb-4 flex items-center gap-4">
            <div className="flex items-baseline gap-1">
              <span className="text-3xl font-bold text-text-primary">
                {score.toFixed(2)}
              </span>
              <span className="text-sm text-text-muted">/ 5.0</span>
            </div>
            <RecommendationBadge
              recommendation={report.recommendation}
              label={report.recommendationLabel}
            />
          </div>
          <div className="mb-4 h-2 overflow-hidden rounded-full bg-white/5">
            <div
              className="h-full rounded-full bg-gradient-to-r from-emerald-500/60 via-sky-400/70 to-sky-400/80 transition-all duration-700"
              style={{ width: `${scorePercent}%` }}
            />
          </div>
          <p className="text-sm leading-relaxed text-text-secondary">
            {report.summary}
          </p>
        </div>
      </div>
    </GlassCard>
  );
}
