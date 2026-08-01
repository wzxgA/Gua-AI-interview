import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { GlassCard } from '@/components/ui/glass-card';
import { SilverButton } from '@/components/ui/silver-button';
import { Skeleton } from '@/components/ui/skeleton';
import { PageHeader, ErrorState } from '@/components/common/PageHeader';
import { StatusCard } from '@/components/interview/StatusCard';
import { PlanViewer } from '@/components/interview/PlanViewer';
import { RoundTimeline } from '@/components/interview/RoundTimeline';
import { ActionButtons } from '@/components/interview/ActionButtons';
import { PlanConfigDialog } from '@/components/interview/PlanConfigDialog';
import { EvaluationProgress } from '@/components/report/EvaluationProgress';
import {
  useInterview,
  useStartInterview,
  useCancelInterview,
  useFinishInterview,
  usePauseInterview,
  useResumeInterview,
} from '@/api/interview';
import type { StartPlanBody } from '@/api/interview';

export function InterviewConsolePage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const interviewId = id ? Number(id) : undefined;

  const { data: interview, isLoading, isError, error } = useInterview(interviewId);
  const startMutation = useStartInterview();
  const cancelMutation = useCancelInterview();
  const finishMutation = useFinishInterview();
  const pauseMutation = usePauseInterview();
  const resumeMutation = useResumeInterview();

  const [planDialogOpen, setPlanDialogOpen] = useState(false);

  const handleStart = () => {
    setPlanDialogOpen(true);
  };

  const handlePlanConfirm = (params: StartPlanBody) => {
    if (!interviewId) return;
    setPlanDialogOpen(false);
    startMutation.mutate(
      { id: interviewId, body: params },
      {
        onSuccess: () => toast.success('面试已开始，正在生成计划'),
        onError: (err: Error) => toast.error(err.message || '启动失败'),
      },
    );
  };

  const handleCancel = () => {
    if (!interviewId) return;
    cancelMutation.mutate(interviewId, {
      onSuccess: () => toast.success('面试已取消'),
      onError: (err: Error) => toast.error(err.message || '取消失败'),
    });
  };

  const handlePause = () => {
    if (!interviewId) return;
    pauseMutation.mutate(interviewId, {
      onSuccess: () => toast.success('面试已暂停'),
      onError: (err: Error) => toast.error(err.message || '暂停失败'),
    });
  };

  const handleFinish = () => {
    if (!interviewId) return;
    finishMutation.mutate(interviewId, {
      onSuccess: () => toast.success('面试已结束'),
      onError: (err: Error) => toast.error(err.message || '结束失败'),
    });
  };

  const handleResume = () => {
    if (!interviewId) return;
    resumeMutation.mutate(interviewId, {
      onSuccess: () => toast.success('面试已恢复'),
      onError: (err: Error) => toast.error(err.message || '恢复失败'),
    });
  };

  // 全屏加载态（生成计划时）
  if (startMutation.isPending) {
    return (
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-modal-scrim backdrop-blur-sm">
        <div className="flex flex-col items-center gap-4">
          <div className="h-10 w-10 animate-spin rounded-full border-2 border-silver-300/30 border-t-silver-300" />
          <p className="text-sm text-text-secondary">正在生成面试计划...</p>
        </div>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="space-y-6">
        <PageHeader title="面试控制台" />
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
          <div className="space-y-4 lg:col-span-1">
            <Skeleton className="h-40 w-full" />
            <Skeleton className="h-20 w-full" />
          </div>
          <div className="space-y-4 lg:col-span-2">
            <Skeleton className="h-60 w-full" />
            <Skeleton className="h-40 w-full" />
          </div>
        </div>
      </div>
    );
  }

  if (isError || !interview) {
    return (
      <div className="space-y-6">
        <PageHeader title="面试控制台" />
        <ErrorState
          message={error?.message || '面试不存在'}
          onRetry={() => navigate('/interviews')}
        />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="面试控制台"
        subtitle={`面试 #${interview.id}`}
        action={
          <SilverButton variant="ghost" onClick={() => navigate('/interviews')}>
            返回列表
          </SilverButton>
        }
      />

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        {/* 左侧：状态卡片 + 操作按钮 */}
        <div className="space-y-4 lg:col-span-1">
          <StatusCard interview={interview} />
          <GlassCard className="p-5">
            <h3 className="mb-3 text-sm font-medium text-text-muted">操作</h3>
            <ActionButtons
              status={interview.status}
              onStart={handleStart}
              onCancel={handleCancel}
              onEnterRoom={() => navigate(`/interviews/${interview.id}/room`)}
              onPause={handlePause}
              onFinish={handleFinish}
              onResume={handleResume}
              onViewReport={() => navigate(`/interviews/${interview.id}/report`)}
              onBack={() => navigate('/interviews')}
            />
          </GlassCard>
          {(interview.status === 'EVALUATING' ||
            interview.status === 'REPORTING') && (
            <EvaluationProgress
              status={interview.status}
              evaluatedRounds={interview.evaluatedRounds}
              totalRoundsToEvaluate={interview.totalRoundsToEvaluate}
            />
          )}
        </div>

        {/* 右侧：计划查看 + 轮次时间线 */}
        <div className="space-y-4 lg:col-span-2">
          <PlanViewer planJson={interview.planJson} />
          <RoundTimeline
            planJson={interview.planJson}
            currentRoundId={null}
          />
        </div>
      </div>

      <PlanConfigDialog
        open={planDialogOpen}
        onClose={() => setPlanDialogOpen(false)}
        onConfirm={handlePlanConfirm}
      />
    </div>
  );
}
