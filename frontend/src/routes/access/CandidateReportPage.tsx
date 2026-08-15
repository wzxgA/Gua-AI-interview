import { useParams, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { GlassCard } from '@/components/ui/glass-card';
import { Skeleton } from '@/components/ui/skeleton';
import { SilverButton } from '@/components/ui/silver-button';
import { PageHeader, ErrorState } from '@/components/common/PageHeader';
import { ReportSummaryCard } from '@/components/report/ReportSummaryCard';
import { DimensionRadarChart } from '@/components/report/DimensionRadarChart';
import { DimensionScoreList } from '@/components/report/DimensionScoreList';
import { RoundEvaluationList } from '@/components/report/RoundEvaluationList';
import { getGuestReport, getGuestEvaluations } from '@/api/access';
import { DIMENSION_CONFIG, type EvaluationDimension } from '@/types/report';
import { useUrlLanguageInit } from '@/hooks/useUrlLanguageInit';
import { LanguageSwitcher } from '@/components/ui/LanguageSwitcher';

/** 候选人报告页：免登录，只读自己的评估报告。 */
export function CandidateReportPage() {
  const { t } = useTranslation();
  useUrlLanguageInit();
  const { accessToken } = useParams();
  const navigate = useNavigate();
  const sessionIdStr = sessionStorage.getItem('guestSessionId');
  const sessionId = sessionIdStr ? Number(sessionIdStr) : null;

  const {
    data: report,
    isLoading,
    isError,
  } = useQuery({
    queryKey: ['guest', 'report', sessionId],
    queryFn: () => getGuestReport(sessionId!),
    enabled: sessionId != null,
  });
  const { data: evaluations } = useQuery({
    queryKey: ['guest', 'evaluations', sessionId],
    queryFn: () => getGuestEvaluations(sessionId!),
    enabled: sessionId != null,
  });

  if (isLoading) {
    return (
      <div className="space-y-6">
        <PageHeader title={t('candidate.reportTitle')} />
        <Skeleton className="h-40 w-full" />
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
          <Skeleton className="h-64 w-full" />
          <Skeleton className="h-64 w-full" />
        </div>
        <Skeleton className="h-80 w-full" />
      </div>
    );
  }

  if (isError || !report) {
    return (
      <div className="space-y-6">
        <PageHeader title={t('candidate.reportTitle')} />
        <ErrorState
          message={t('candidate.reportNotFound')}
          onRetry={() => navigate(`/i/${accessToken}/room`)}
        />
      </div>
    );
  }

  const dimensionScores = parseDimensionScores(report.dimensionsJson, evaluations ?? []);

  return (
    <div className="space-y-6">
      <PageHeader
        title={t('candidate.reportTitle')}
        subtitle={t('candidate.reportSubtitle')}
        action={
          <div className="flex items-center gap-3">
            <LanguageSwitcher />
            <SilverButton variant="ghost" onClick={() => navigate(`/i/${accessToken}/room`)}>
              {t('candidate.backToRoom')}
            </SilverButton>
          </div>
        }
      />

      <ReportSummaryCard report={report} />

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <DimensionRadarChart scores={dimensionScores} />
        <DimensionScoreList scores={dimensionScores} />
      </div>

      <GlassCard className="p-6">
        <h3 className="mb-4 text-sm font-medium text-text-primary">{t('interviews.roundEvaluationTitle')}</h3>
        <RoundEvaluationList evaluations={evaluations ?? []} />
      </GlassCard>
    </div>
  );
}

/** 从评分明细计算各维度平均分。 */
function parseDimensionScores(
  _dimensionsJson: string,
  evaluations: { dimension: EvaluationDimension; score: number }[],
): Partial<Record<EvaluationDimension, number>> {
  const sums: Record<string, { total: number; count: number }> = {};
  for (const evalItem of evaluations) {
    const key = evalItem.dimension;
    if (!sums[key]) sums[key] = { total: 0, count: 0 };
    sums[key].total += evalItem.score;
    sums[key].count += 1;
  }
  const result: Partial<Record<EvaluationDimension, number>> = {};
  for (const dim of Object.keys(DIMENSION_CONFIG) as EvaluationDimension[]) {
    const s = sums[dim];
    if (s && s.count > 0) {
      result[dim] = s.total / s.count;
    }
  }
  return result;
}
