import { GlassCard } from '@/components/ui/glass-card';
import { cn } from '@/lib/utils';
import type { InterviewPlan } from '@/types/interview';

interface RoundTimelineProps {
  planJson: string | null;
  currentRoundId: number | null;
}

/** 轮次时间线：展示预期轮次进度 */
export function RoundTimeline({ planJson, currentRoundId }: RoundTimelineProps) {
  let questions: { questionId: string; topic: string }[] = [];

  if (planJson) {
    try {
      const plan = JSON.parse(planJson) as InterviewPlan;
      questions = plan.questions ?? [];
    } catch {
      // 解析失败时使用空数组
    }
  }

  if (questions.length === 0) {
    return (
      <GlassCard className="p-5">
        <h3 className="mb-3 text-sm font-medium text-text-muted">轮次进度</h3>
        <p className="text-sm text-text-muted">暂无轮次数据</p>
      </GlassCard>
    );
  }

  return (
    <GlassCard className="p-5">
      <h3 className="mb-4 text-sm font-medium text-text-muted">轮次进度</h3>
      <div className="space-y-0">
        {questions.map((q, i) => {
          const roundId = i + 1;
          const isAnswered =
            currentRoundId != null && roundId < currentRoundId;
          const isInProgress =
            currentRoundId != null && roundId === currentRoundId;

          return (
            <div key={q.questionId} className="relative pl-6 pb-4 last:pb-0">
              {/* 连线 */}
              {i < questions.length - 1 && (
                <div className="absolute left-[3px] top-3 h-full w-px bg-surface-hover" />
              )}
              {/* 节点：实心点(已回答) / 脉动点(进行中) / 空心点(未开始) */}
              <div
                className={cn(
                  'absolute left-0 top-1.5 h-2 w-2 rounded-full border',
                  isAnswered && 'border-silver-300 bg-silver-300',
                  isInProgress &&
                    'border-silver-200 bg-silver-200 animate-pulse-slow',
                  !isAnswered &&
                    !isInProgress &&
                    'border-border-strong bg-transparent',
                )}
              />
              <div className="flex items-center gap-2">
                <span className="text-xs text-text-muted">Q{roundId}</span>
                <span
                  className={cn(
                    'text-sm',
                    isAnswered
                      ? 'text-text-primary'
                      : isInProgress
                        ? 'text-silver-200'
                        : 'text-text-muted',
                  )}
                >
                  {q.topic}
                </span>
              </div>
            </div>
          );
        })}
      </div>
    </GlassCard>
  );
}
