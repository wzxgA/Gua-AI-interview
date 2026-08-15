import { useParams, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { GlassCard } from '@/components/ui/glass-card';
import { Skeleton } from '@/components/ui/skeleton';
import { SilverButton } from '@/components/ui/silver-button';
import { PageHeader, ErrorState } from '@/components/common/PageHeader';
import { ReportSummaryCard } from '@/components/report/ReportSummaryCard';
import { DimensionRadarChart } from '@/components/report/DimensionRadarChart';
import { DimensionScoreList } from '@/components/report/DimensionScoreList';
import { RoundEvaluationList } from '@/components/report/RoundEvaluationList';
import { ProctorFocusCard } from '@/components/report/ProctorFocusCard';
import { useInterview, useInterviewAccess, useProctorSummary } from '@/api/interview';
import { useInterviewReport, useInterviewEvaluations } from '@/api/report';
import { DIMENSION_CONFIG, type EvaluationDimension } from '@/types/report';

export function InterviewReportPage() {
  const { t } = useTranslation();
  const { id } = useParams();
  const navigate = useNavigate();
  const interviewId = id ? Number(id) : undefined;

  const { data: interview, isLoading: interviewLoading } =
    useInterview(interviewId);
  const { data: report, isLoading: reportLoading, isError: reportError } =
    useInterviewReport(interviewId);
  const { data: evaluations } = useInterviewEvaluations(interviewId);

  // 该面试是否开启防作弊（专注度摘要仅开启时展示）
  const { data: accessConfig } = useInterviewAccess(interviewId);
  const proctorActive =
    (accessConfig?.proctor?.tabSwitch || accessConfig?.proctor?.gaze) ?? false;
  const { data: proctorSummary } = useProctorSummary(interviewId, proctorActive);

  const isLoading = interviewLoading || reportLoading;

  // 评估中/报告生成中
  if (
    interview &&
    (interview.status === 'EVALUATING' || interview.status === 'REPORTING')
  ) {
    return (
      <div className="space-y-6">
        <PageHeader
          title={t('interviews.reportTitle')}
          subtitle={t('interviews.interviewIdSubtitle', { id: interviewId })}
          action={
            <SilverButton
              variant="ghost"
              onClick={() => navigate(`/interviews/${interviewId}`)}
            >
              {t('interviews.backToConsole')}
            </SilverButton>
          }
        />
        <GlassCard className="p-6">
          <div className="flex flex-col items-center gap-4 py-12">
            <div className="h-10 w-10 animate-spin rounded-full border-2 border-sky-400/20 border-t-sky-400" />
            <p className="text-sm text-text-secondary">
              {interview.status === 'EVALUATING'
                ? t('interviews.evaluatingText')
                : t('interviews.reportingText')}
            </p>
          </div>
        </GlassCard>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="space-y-6">
        <PageHeader title={t('interviews.reportTitle')} />
        <Skeleton className="h-40 w-full" />
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
          <Skeleton className="h-64 w-full" />
          <Skeleton className="h-64 w-full" />
        </div>
        <Skeleton className="h-80 w-full" />
      </div>
    );
  }

  if (reportError || !report) {
    return (
      <div className="space-y-6">
        <PageHeader title={t('interviews.reportTitle')} />
        <ErrorState
          message={t('interviews.reportNotGenerated')}
          onRetry={() => navigate(`/interviews/${interviewId}`)}
        />
      </div>
    );
  }

  // 解析 dimensionsJson 并计算各维度平均分
  const dimensionScores = parseDimensionScores(report.dimensionsJson, evaluations ?? []);

  return (
    <div className="space-y-6">
      <PageHeader
        title={t('interviews.reportTitle')}
        subtitle={t('interviews.interviewIdSubtitle', { id: interviewId })}
        action={
          <div className="flex gap-2">
            <SilverButton
              variant="ghost"
              onClick={() => navigate(`/interviews/${interviewId}`)}
            >
              {t('interviews.backToConsole')}
            </SilverButton>
          </div>
        }
      />

      <ReportSummaryCard report={report} />

      {/* 专注度摘要（开启防作弊时展示；与评估打分解耦，仅参考） */}
      {proctorActive && <ProctorFocusCard summary={proctorSummary} />}

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <DimensionRadarChart scores={dimensionScores} />
        <DimensionScoreList scores={dimensionScores} />
      </div>

      <RoundEvaluationList evaluations={evaluations ?? []} />
    </div>
  );
}

/** 从评分明细计算各维度平均分 */
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
