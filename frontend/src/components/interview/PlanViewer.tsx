import { useTranslation } from 'react-i18next';
import { GlassCard } from '@/components/ui/glass-card';
import { Badge } from '@/components/ui/badge';
import type { InterviewPlan } from '@/types/interview';
import { useEnumLabel } from '@/hooks/useEnumLabel';

interface PlanViewerProps {
  planJson: string | null;
}

/** 面试计划查看器：解析 planJson 并展示结构化信息 */
export function PlanViewer({ planJson }: PlanViewerProps) {
  const { t } = useTranslation();
  const enumLabel = useEnumLabel();

  if (!planJson) {
    return (
      <GlassCard className="p-5">
        <h3 className="mb-3 text-sm font-medium text-text-muted">{t('interviews.planTitle')}</h3>
        <p className="text-sm text-text-muted">{t('interviews.planNotGenerated')}</p>
      </GlassCard>
    );
  }

  let plan: InterviewPlan;
  try {
    plan = JSON.parse(planJson) as InterviewPlan;
  } catch {
    return (
      <GlassCard className="p-5">
        <h3 className="mb-3 text-sm font-medium text-text-muted">{t('interviews.planTitle')}</h3>
        <p className="text-sm text-danger">{t('interviews.planParseFailed')}</p>
      </GlassCard>
    );
  }

  return (
    <GlassCard className="p-5">
      <h3 className="mb-4 text-sm font-medium text-text-muted">{t('interviews.planTitle')}</h3>

      {/* 基本信息 */}
      <div className="grid grid-cols-3 gap-4">
        <div>
          <p className="text-xs text-text-muted">{t('interviews.planCandidate')}</p>
          <p className="mt-1 text-sm text-text-primary">{plan.candidateName}</p>
        </div>
        <div>
          <p className="text-xs text-text-muted">{t('interviews.planPosition')}</p>
          <p className="mt-1 text-sm text-text-primary">{plan.position}</p>
        </div>
        <div>
          <p className="text-xs text-text-muted">{t('interviews.estimatedDuration')}</p>
          <p className="mt-1 text-sm text-text-primary">{t('interviews.minutesValue', { minutes: plan.estimatedMinutes })}</p>
        </div>
      </div>

      {/* 板块列表 */}
      {plan.sections?.length > 0 && (
        <div className="mt-5">
          <p className="mb-2 text-xs text-text-muted">{t('interviews.planSections')}</p>
          <div className="space-y-2">
            {plan.sections.map((section, i) => (
              <div
                key={i}
                className="flex items-start gap-3 rounded-md bg-surface-overlay p-3"
              >
                <span className="mt-0.5 text-xs text-silver-300">Q{i + 1}</span>
                <div className="flex-1">
                  <div className="flex items-center gap-2">
                    <span className="text-sm text-text-primary">{section.name}</span>
                    <span className="text-xs text-text-muted">
                      {t('interviews.questionCountValue', { count: section.questionCount })}
                    </span>
                  </div>
                  <p className="mt-0.5 text-xs text-text-muted">{section.objective}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* 问题列表 */}
      {plan.questions?.length > 0 && (
        <div className="mt-5">
          <p className="mb-2 text-xs text-text-muted">{t('interviews.presetQuestions')}</p>
          <div className="space-y-2">
            {plan.questions.map((q, i) => (
              <div
                key={q.questionId}
                className="flex items-start gap-3 rounded-md bg-surface-overlay p-3"
              >
                <span className="mt-0.5 text-xs text-silver-300">{i + 1}</span>
                <div className="flex-1">
                  <div className="flex items-center gap-2">
                    <span className="text-sm text-text-primary">{q.topic}</span>
                    <Badge variant="difficulty">
                      {enumLabel('difficulty', q.difficulty, q.difficulty)}
                    </Badge>
                  </div>
                  <p className="mt-0.5 text-xs text-text-muted">
                    {t('interviews.evaluationFocus', { focus: q.evaluationFocus })}
                  </p>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </GlassCard>
  );
}
