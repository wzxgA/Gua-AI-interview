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
  usePlanInterview,
  useStartInterview,
  useCancelInterview,
  useFinishInterview,
  usePauseInterview,
  useResumeInterview,
  useInterviewAccess,
  useResetAccessPassword,
  useDisableAccess,
  useGenerateAccess,
} from '@/api/interview';
import type { StartPlanBody } from '@/api/interview';

export function InterviewConsolePage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const interviewId = id ? Number(id) : undefined;

  const { data: interview, isLoading, isError, error } = useInterview(interviewId);
  const planMutation = usePlanInterview();
  const startMutation = useStartInterview();
  const cancelMutation = useCancelInterview();
  const finishMutation = useFinishInterview();
  const pauseMutation = usePauseInterview();
  const resumeMutation = useResumeInterview();
  const { data: accessConfig, isLoading: accessLoading } = useInterviewAccess(interviewId);
  const resetPasswordMutation = useResetAccessPassword();
  const disableMutation = useDisableAccess();
  const generateMutation = useGenerateAccess();

  const [planDialogOpen, setPlanDialogOpen] = useState(false);
  const [showResetPassword, setShowResetPassword] = useState(false);
  const [passwordInput, setPasswordInput] = useState('');
  const [showGenerate, setShowGenerate] = useState(false);
  const [generatePassword, setGeneratePassword] = useState('');

  const handleStart = () => {
    setPlanDialogOpen(true);
  };

  const handlePlanConfirm = (params: StartPlanBody) => {
    if (!interviewId) return;
    setPlanDialogOpen(false);
    planMutation.mutate(
      { id: interviewId, body: params },
      {
        onSuccess: () => toast.success('面试计划已生成'),
        onError: (err: Error) => toast.error(err.message || '生成计划失败'),
      },
    );
  };

  const handleBeginInterview = () => {
    if (!interviewId) return;
    startMutation.mutate(interviewId, {
      onSuccess: () => toast.success('面试已开始'),
      onError: (err: Error) => toast.error(err.message || '开始失败'),
    });
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

  const candidateLink = accessConfig?.accessToken
    ? `${window.location.origin}/i/${accessConfig.accessToken}`
    : null;

  const canGenerate = interview?.status === 'PLANNING' || interview?.status === 'PAUSED';

  const handleCopyLink = () => {
    if (!candidateLink) return;
    navigator.clipboard
      .writeText(candidateLink)
      .then(() => toast.success('候选人面试链接已复制'))
      .catch(() => toast.error('复制失败，请手动复制'));
  };

  const handleResetPassword = () => {
    if (!interviewId) return;
    resetPasswordMutation.mutate(
      { id: interviewId, password: passwordInput.trim() || undefined },
      {
        onSuccess: (data) => {
          toast.success('访问密码已重置');
          setShowResetPassword(false);
          setPasswordInput('');
          if (data.accessPassword) {
            toast.info(`候选人新密码：${data.accessPassword}`);
          }
        },
        onError: (err: Error) => toast.error(err.message || '重置失败'),
      },
    );
  };

  const handleDisableAccess = () => {
    if (!interviewId) return;
    disableMutation.mutate(interviewId, {
      onSuccess: () => toast.success('候选人入口已作废'),
      onError: (err: Error) => toast.error(err.message || '操作失败'),
    });
  };

  const handleGenerate = () => {
    if (!interviewId) return;
    generateMutation.mutate(
      { id: interviewId, password: generatePassword.trim() || undefined },
      {
        onSuccess: (data) => {
          toast.success('候选人面试链接已生成');
          setShowGenerate(false);
          setGeneratePassword('');
          if (data.accessPassword) {
            toast.info(`候选人访问密码：${data.accessPassword}`);
          }
        },
        onError: (err: Error) => toast.error(err.message || '生成失败'),
      },
    );
  };

  // 全屏加载态（生成计划时）
  if (planMutation.isPending) {
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
              onBeginInterview={handleBeginInterview}
              onCancel={handleCancel}
              onEnterRoom={
                interview.accessMode === 'CANDIDATE_ONLY'
                  ? undefined
                  : () => navigate(`/interviews/${interview.id}/room`)
              }
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

        {/* 右侧：计划查看 + 轮次时间线 + 候选人入口 */}
        <div className="space-y-4 lg:col-span-2">
          <PlanViewer planJson={interview.planJson} />
          <RoundTimeline
            planJson={interview.planJson}
            currentRoundId={null}
          />

          {/* 候选人入口 */}
          <GlassCard className="p-5">
            <h3 className="mb-3 text-sm font-medium text-text-primary">候选人入口</h3>
            {accessLoading ? (
              <Skeleton className="h-20 w-full" />
            ) : (
              <div className="space-y-3 text-sm">
                {/* NONE：未生成链接 */}
                {(!accessConfig?.accessMode || accessConfig.accessMode === 'NONE') && (
                  <>
                    <p className="text-xs text-text-muted">
                      尚未生成候选人面试链接。生成后面试将切换为候选端模式，管理端无法进入面试间。
                    </p>
                    <SilverButton
                      onClick={() => setShowGenerate((v) => !v)}
                      disabled={!canGenerate}
                    >
                      生成候选人面试链接
                    </SilverButton>
                    {showGenerate && (
                      <div className="flex items-center gap-2 pt-1">
                        <input
                          type="text"
                          value={generatePassword}
                          onChange={(e) => setGeneratePassword(e.target.value)}
                          placeholder="访问密码（留空则自动生成）"
                          className="min-w-0 flex-1 rounded-lg border border-border-default bg-surface-overlay px-3 py-1.5 text-sm text-text-primary placeholder:text-text-muted focus:border-accent-primary focus:outline-none"
                        />
                        <SilverButton onClick={handleGenerate} disabled={generateMutation.isPending}>
                          确认
                        </SilverButton>
                      </div>
                    )}
                  </>
                )}

                {/* CANDIDATE_ONLY：已生成，仅候选端 */}
                {accessConfig?.accessMode === 'CANDIDATE_ONLY' && (
                  <>
                    {candidateLink && (
                      <div className="rounded-lg border border-border-subtle bg-surface-overlay px-3 py-2.5">
                        <p className="mb-1 text-xs text-text-muted">候选人面试链接（免登录，需密码）</p>
                        <div className="flex items-center gap-2">
                          <code className="min-w-0 flex-1 truncate text-xs text-silver-300">
                            {candidateLink}
                          </code>
                          <SilverButton variant="ghost" onClick={handleCopyLink}>
                            复制
                          </SilverButton>
                        </div>
                      </div>
                    )}
                    <p className="text-xs text-amber-400">
                      已设为候选端面试，管理端无法进入面试间
                    </p>
                    <div className="flex flex-wrap items-center gap-2">
                      <SilverButton
                        variant="ghost"
                        onClick={() => setShowResetPassword((v) => !v)}
                      >
                        重置密码
                      </SilverButton>
                      <SilverButton variant="danger" onClick={handleDisableAccess}>
                        作废入口
                      </SilverButton>
                    </div>
                    {showResetPassword && (
                      <div className="flex items-center gap-2 pt-1">
                        <input
                          type="text"
                          value={passwordInput}
                          onChange={(e) => setPasswordInput(e.target.value)}
                          placeholder="输入新密码（留空则自动生成）"
                          className="min-w-0 flex-1 rounded-lg border border-border-default bg-surface-overlay px-3 py-1.5 text-sm text-text-primary placeholder:text-text-muted focus:border-accent-primary focus:outline-none"
                        />
                        <SilverButton onClick={handleResetPassword}>确认</SilverButton>
                      </div>
                    )}
                  </>
                )}

                {/* DISABLED：已作废 */}
                {accessConfig?.accessMode === 'DISABLED' && (
                  <>
                    <p className="text-xs text-text-muted">候选人入口已作废，管理端可正常使用面试间</p>
                    <SilverButton
                      onClick={() => setShowGenerate((v) => !v)}
                      disabled={!canGenerate}
                    >
                      重新生成链接
                    </SilverButton>
                    {showGenerate && (
                      <div className="flex items-center gap-2 pt-1">
                        <input
                          type="text"
                          value={generatePassword}
                          onChange={(e) => setGeneratePassword(e.target.value)}
                          placeholder="访问密码（留空则自动生成）"
                          className="min-w-0 flex-1 rounded-lg border border-border-default bg-surface-overlay px-3 py-1.5 text-sm text-text-primary placeholder:text-text-muted focus:border-accent-primary focus:outline-none"
                        />
                        <SilverButton onClick={handleGenerate} disabled={generateMutation.isPending}>
                          确认
                        </SilverButton>
                      </div>
                    )}
                  </>
                )}
              </div>
            )}
          </GlassCard>
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
