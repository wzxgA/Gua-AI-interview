import { GlassCard } from '@/components/ui/glass-card';
import { Badge } from '@/components/ui/badge';
import type { InterviewPlan } from '@/types/interview';
import { DIFFICULTY_LABELS } from '@/lib/constants';

interface PlanViewerProps {
  planJson: string | null;
}

/** 面试计划查看器：解析 planJson 并展示结构化信息 */
export function PlanViewer({ planJson }: PlanViewerProps) {
  if (!planJson) {
    return (
      <GlassCard className="p-5">
        <h3 className="mb-3 text-sm font-medium text-text-muted">面试计划</h3>
        <p className="text-sm text-text-muted">计划尚未生成</p>
      </GlassCard>
    );
  }

  let plan: InterviewPlan;
  try {
    plan = JSON.parse(planJson) as InterviewPlan;
  } catch {
    return (
      <GlassCard className="p-5">
        <h3 className="mb-3 text-sm font-medium text-text-muted">面试计划</h3>
        <p className="text-sm text-danger">计划数据解析失败</p>
      </GlassCard>
    );
  }

  return (
    <GlassCard className="p-5">
      <h3 className="mb-4 text-sm font-medium text-text-muted">面试计划</h3>

      {/* 基本信息 */}
      <div className="grid grid-cols-3 gap-4">
        <div>
          <p className="text-xs text-text-muted">候选人</p>
          <p className="mt-1 text-sm text-text-primary">{plan.candidateName}</p>
        </div>
        <div>
          <p className="text-xs text-text-muted">岗位</p>
          <p className="mt-1 text-sm text-text-primary">{plan.position}</p>
        </div>
        <div>
          <p className="text-xs text-text-muted">预计时长</p>
          <p className="mt-1 text-sm text-text-primary">{plan.estimatedMinutes} 分钟</p>
        </div>
      </div>

      {/* 板块列表 */}
      {plan.sections?.length > 0 && (
        <div className="mt-5">
          <p className="mb-2 text-xs text-text-muted">面试板块</p>
          <div className="space-y-2">
            {plan.sections.map((section, i) => (
              <div
                key={i}
                className="flex items-start gap-3 rounded-md bg-white/[0.02] p-3"
              >
                <span className="mt-0.5 text-xs text-silver-300">Q{i + 1}</span>
                <div className="flex-1">
                  <div className="flex items-center gap-2">
                    <span className="text-sm text-text-primary">{section.name}</span>
                    <span className="text-xs text-text-muted">
                      {section.questionCount} 题
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
          <p className="mb-2 text-xs text-text-muted">预设问题</p>
          <div className="space-y-2">
            {plan.questions.map((q, i) => (
              <div
                key={q.questionId}
                className="flex items-start gap-3 rounded-md bg-white/[0.02] p-3"
              >
                <span className="mt-0.5 text-xs text-silver-300">{i + 1}</span>
                <div className="flex-1">
                  <div className="flex items-center gap-2">
                    <span className="text-sm text-text-primary">{q.topic}</span>
                    <Badge variant="difficulty">
                      {DIFFICULTY_LABELS[q.difficulty] ?? q.difficulty}
                    </Badge>
                  </div>
                  <p className="mt-0.5 text-xs text-text-muted">
                    评价重点：{q.evaluationFocus}
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
