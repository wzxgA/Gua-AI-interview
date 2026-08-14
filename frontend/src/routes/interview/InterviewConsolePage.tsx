import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { useTranslation } from 'react-i18next';
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
  const { t } = useTranslation();
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
        onSuccess: () => toast.success(t('interviews.planGenerated')),
        onError: (err: Error) => toast.error(err.message || t('interviews.planFailed')),
      },
    );
  };

  const handleBeginInterview = () => {
    if (!interviewId) return;
    startMutation.mutate(interviewId, {
      onSuccess: () => toast.success(t('interviews.started')),
      onError: (err: Error) => toast.error(err.message || t('interviews.startFailed')),
    });
  };

  const handleCancel = () => {
    if (!interviewId) return;
    cancelMutation.mutate(interviewId, {
      onSuccess: () => toast.success(t('interviews.cancelSuccess')),
      onError: (err: Error) => toast.error(err.message || t('interviews.cancelFailed')),
    });
  };

  const handlePause = () => {
    if (!interviewId) return;
    pauseMutation.mutate(interviewId, {
      onSuccess: () => toast.success(t('interviews.paused')),
      onError: (err: Error) => toast.error(err.message || t('interviews.pauseFailed')),
    });
  };

  const handleFinish = () => {
    if (!interviewId) return;
    finishMutation.mutate(interviewId, {
      onSuccess: () => toast.success(t('interviews.finished')),
      onError: (err: Error) => toast.error(err.message || t('interviews.finishFailed')),
    });
  };

  const handleResume = () => {
    if (!interviewId) return;
    resumeMutation.mutate(interviewId, {
      onSuccess: () => toast.success(t('interviews.resumed')),
      onError: (err: Error) => toast.error(err.message || t('interviews.resumeFailed')),
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
      .then(() => toast.success(t('interviews.linkCopied')))
      .catch(() => toast.error(t('interviews.copyFailed')));
  };

  const handleResetPassword = () => {
    if (!interviewId) return;
    resetPasswordMutation.mutate(
      { id: interviewId, password: passwordInput.trim() || undefined },
      {
        onSuccess: (data) => {
          toast.success(t('interviews.passwordReset'));
          setShowResetPassword(false);
          setPasswordInput('');
          if (data.accessPassword) {
            toast.info(t('interviews.newPasswordInfo', { password: data.accessPassword }));
          }
        },
        onError: (err: Error) => toast.error(err.message || t('interviews.resetFailed')),
      },
    );
  };

  const handleDisableAccess = () => {
    if (!interviewId) return;
    disableMutation.mutate(interviewId, {
      onSuccess: () => toast.success(t('interviews.accessDisabled')),
      onError: (err: Error) => toast.error(err.message || t('interviews.operationFailed')),
    });
  };

  const handleGenerate = () => {
    if (!interviewId) return;
    generateMutation.mutate(
      { id: interviewId, password: generatePassword.trim() || undefined },
      {
        onSuccess: (data) => {
          toast.success(t('interviews.linkGenerated'));
          setShowGenerate(false);
          setGeneratePassword('');
          if (data.accessPassword) {
            toast.info(t('interviews.accessPasswordInfo', { password: data.accessPassword }));
          }
        },
        onError: (err: Error) => toast.error(err.message || t('interviews.generateFailed')),
      },
    );
  };

  // 全屏加载态（生成计划时）
  if (planMutation.isPending) {
    return (
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-modal-scrim backdrop-blur-sm">
        <div className="flex flex-col items-center gap-4">
          <div className="h-10 w-10 animate-spin rounded-full border-2 border-silver-300/30 border-t-silver-300" />
          <p className="text-sm text-text-secondary">{t('interviews.generatingPlan')}</p>
        </div>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="space-y-6">
        <PageHeader title={t('interviews.consoleTitle')} />
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
        <PageHeader title={t('interviews.consoleTitle')} />
        <ErrorState
          message={error?.message || t('interviews.notFound')}
          onRetry={() => navigate('/interviews')}
        />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title={t('interviews.consoleTitle')}
        subtitle={t('interviews.interviewIdSubtitle', { id: interview.id })}
        action={
          <SilverButton variant="ghost" onClick={() => navigate('/interviews')}>
            {t('interviews.backToList')}
          </SilverButton>
        }
      />

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        {/* 左侧：状态卡片 + 操作按钮 */}
        <div className="space-y-4 lg:col-span-1">
          <StatusCard interview={interview} />
          <GlassCard className="p-5">
            <h3 className="mb-3 text-sm font-medium text-text-muted">{t('interviews.actionsTitle')}</h3>
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
            <h3 className="mb-3 text-sm font-medium text-text-primary">{t('interviews.candidateAccess')}</h3>
            {accessLoading ? (
              <Skeleton className="h-20 w-full" />
            ) : (
              <div className="space-y-3 text-sm">
                {/* NONE：未生成链接 */}
                {(!accessConfig?.accessMode || accessConfig.accessMode === 'NONE') && (
                  <>
                    <p className="text-xs text-text-muted">
                      {t('interviews.accessNoneHint')}
                    </p>
                    <SilverButton
                      onClick={() => setShowGenerate((v) => !v)}
                      disabled={!canGenerate}
                    >
                      {t('interviews.generateLink')}
                    </SilverButton>
                    {showGenerate && (
                      <div className="flex items-center gap-2 pt-1">
                        <input
                          type="text"
                          value={generatePassword}
                          onChange={(e) => setGeneratePassword(e.target.value)}
                          placeholder={t('interviews.accessPasswordPlaceholder')}
                          className="min-w-0 flex-1 rounded-lg border border-border-default bg-surface-overlay px-3 py-1.5 text-sm text-text-primary placeholder:text-text-muted focus:border-accent-primary focus:outline-none"
                        />
                        <SilverButton onClick={handleGenerate} disabled={generateMutation.isPending}>
                          {t('common.confirm')}
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
                        <p className="mb-1 text-xs text-text-muted">{t('interviews.candidateLinkHint')}</p>
                        <div className="flex items-center gap-2">
                          <code className="min-w-0 flex-1 truncate text-xs text-silver-300">
                            {candidateLink}
                          </code>
                          <SilverButton variant="ghost" onClick={handleCopyLink}>
                            {t('interviews.copy')}
                          </SilverButton>
                        </div>
                      </div>
                    )}
                    <p className="text-xs text-amber-400">
                      {t('interviews.candidateOnlyHint')}
                    </p>
                    <div className="flex flex-wrap items-center gap-2">
                      <SilverButton
                        variant="ghost"
                        onClick={() => setShowResetPassword((v) => !v)}
                      >
                        {t('interviews.resetPassword')}
                      </SilverButton>
                      <SilverButton variant="danger" onClick={handleDisableAccess}>
                        {t('interviews.disableAccess')}
                      </SilverButton>
                    </div>
                    {showResetPassword && (
                      <div className="flex items-center gap-2 pt-1">
                        <input
                          type="text"
                          value={passwordInput}
                          onChange={(e) => setPasswordInput(e.target.value)}
                          placeholder={t('interviews.newPasswordPlaceholder')}
                          className="min-w-0 flex-1 rounded-lg border border-border-default bg-surface-overlay px-3 py-1.5 text-sm text-text-primary placeholder:text-text-muted focus:border-accent-primary focus:outline-none"
                        />
                        <SilverButton onClick={handleResetPassword}>{t('common.confirm')}</SilverButton>
                      </div>
                    )}
                  </>
                )}

                {/* DISABLED：已作废 */}
                {accessConfig?.accessMode === 'DISABLED' && (
                  <>
                    <p className="text-xs text-text-muted">{t('interviews.accessDisabledHint')}</p>
                    <SilverButton
                      onClick={() => setShowGenerate((v) => !v)}
                      disabled={!canGenerate}
                    >
                      {t('interviews.regenerateLink')}
                    </SilverButton>
                    {showGenerate && (
                      <div className="flex items-center gap-2 pt-1">
                        <input
                          type="text"
                          value={generatePassword}
                          onChange={(e) => setGeneratePassword(e.target.value)}
                          placeholder={t('interviews.accessPasswordPlaceholder')}
                          className="min-w-0 flex-1 rounded-lg border border-border-default bg-surface-overlay px-3 py-1.5 text-sm text-text-primary placeholder:text-text-muted focus:border-accent-primary focus:outline-none"
                        />
                        <SilverButton onClick={handleGenerate} disabled={generateMutation.isPending}>
                          {t('common.confirm')}
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
