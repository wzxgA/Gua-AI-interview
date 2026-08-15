import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
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
  useProctorEvents,
  useProctorSummary,
} from '@/api/interview';
import type { StartPlanBody } from '@/api/interview';
import { useLanguage } from '@/contexts/LanguageContext';
import { SUPPORTED_LANGUAGES, type LanguageCode } from '@/i18n';
import { ProctorSummaryCard } from '@/components/interview/ProctorSummaryCard';
import { ProctorLivePanel } from '@/components/interview/ProctorLivePanel';

export function InterviewConsolePage() {
  const { t } = useTranslation();
  const { language } = useLanguage();
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
  const [generateLang, setGenerateLang] = useState<LanguageCode>(language);
  const [linkLang, setLinkLang] = useState<LanguageCode | null>(null);
  const [proctorEnabled, setProctorEnabled] = useState(false);
  const [proctorTabSwitch, setProctorTabSwitch] = useState(true);
  const [proctorGaze, setProctorGaze] = useState(true);
  const [revealedPassword, setRevealedPassword] = useState<string | null>(null);

  // 面试开启切屏/眼神检测时，控制台轮询防作弊事件/摘要
  const proctorActive = (accessConfig?.proctor?.tabSwitch || accessConfig?.proctor?.gaze) ?? false;
  const { data: proctorEvents } = useProctorEvents(interviewId, proctorActive);
  const { data: proctorSummary } = useProctorSummary(interviewId, proctorActive);

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
    ? `${window.location.origin}/i/${accessConfig.accessToken}${linkLang ? `?lang=${linkLang}` : ''}`
    : null;

  // 生成中 = 本地请求进行中 ∪ 服务端仍处于生成窗口（切页/刷新后 mutation 状态丢失，靠 2s 轮询兜底）
  const isGenerating =
    planMutation.isPending ||
    (interview?.status === 'PLANNING' && !interview?.planJson);

  // 生成中不可生成/重新生成候选人链接（计划未生成完成）
  const canGenerate =
    !isGenerating && (interview?.status === 'PLANNING' || interview?.status === 'PAUSED');

  const handleCopyLink = () => {
    if (!candidateLink) return;
    navigator.clipboard
      .writeText(candidateLink)
      .then(() => toast.success(t('interviews.linkCopied')))
      .catch(() => toast.error(t('interviews.copyFailed')));
  };

  const handleCopyPassword = () => {
    if (!revealedPassword) return;
    navigator.clipboard
      .writeText(revealedPassword)
      .then(() => toast.success(t('interviews.passwordCopied')))
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
            // 密码仅弹窗展示一次（明文不持久存储/显示）
            setRevealedPassword(data.accessPassword);
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
      {
        id: interviewId,
        password: generatePassword.trim() || undefined,
        // 防作弊为可选：勾选后随生成请求保存（含切屏/眼神子项）
        proctor: proctorEnabled ? { tabSwitch: proctorTabSwitch, gaze: proctorGaze } : undefined,
      },
      {
        onSuccess: (data) => {
          toast.success(t('interviews.linkGenerated'));
          setShowGenerate(false);
          setGeneratePassword('');
          setLinkLang(generateLang); // 记录生成时选择的候选人语言，链接带 ?lang=
          if (data.accessPassword) {
            // 密码仅弹窗展示一次（明文不持久存储/显示）
            setRevealedPassword(data.accessPassword);
          }
        },
        onError: (err: Error) => toast.error(err.message || t('interviews.generateFailed')),
      },
    );
  };

  // 生成计划不再全屏遮罩：页面保持可交互，进度以按钮态/骨架屏/角落提示条展示
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
              planning={isGenerating}
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
          {isGenerating ? (
            <Skeleton className="h-60 w-full" />
          ) : (
            <PlanViewer planJson={interview.planJson} />
          )}
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
                      <div className="pt-1">
                        <div className="mb-2 flex items-center gap-4">
                          <label className="flex cursor-pointer items-center gap-1.5 text-xs text-text-secondary">
                            <input
                              type="checkbox"
                              checked={proctorEnabled}
                              onChange={(e) => setProctorEnabled(e.target.checked)}
                              className="accent-silver-400"
                            />
                            {t('proctor.enableLabel')}
                          </label>
                          {proctorEnabled && (
                            <>
                              <label className="flex cursor-pointer items-center gap-1.5 text-xs text-text-secondary">
                                <input
                                  type="checkbox"
                                  checked={proctorTabSwitch}
                                  onChange={(e) => setProctorTabSwitch(e.target.checked)}
                                  className="accent-silver-400"
                                />
                                {t('proctor.tabSwitchLabel')}
                              </label>
                              <label className="flex cursor-pointer items-center gap-1.5 text-xs text-text-secondary">
                                <input
                                  type="checkbox"
                                  checked={proctorGaze}
                                  onChange={(e) => setProctorGaze(e.target.checked)}
                                  className="accent-silver-400"
                                />
                                {t('proctor.gazeLabel')}
                              </label>
                            </>
                          )}
                        </div>
                        <div className="flex items-center gap-2">
                        <select
                          value={generateLang}
                          onChange={(e) => setGenerateLang(e.target.value as LanguageCode)}
                          title={t('interviews.candidateLanguage')}
                          className="shrink-0 rounded-lg border border-border-default bg-surface-overlay px-2 py-1.5 text-sm text-text-primary outline-none focus:border-accent-primary"
                        >
                          {SUPPORTED_LANGUAGES.map((l) => (
                            <option key={l.code} value={l.code}>{l.label}</option>
                          ))}
                        </select>
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
                      <div className="pt-1">
                        <div className="mb-2 flex items-center gap-4">
                          <label className="flex cursor-pointer items-center gap-1.5 text-xs text-text-secondary">
                            <input
                              type="checkbox"
                              checked={proctorEnabled}
                              onChange={(e) => setProctorEnabled(e.target.checked)}
                              className="accent-silver-400"
                            />
                            {t('proctor.enableLabel')}
                          </label>
                          {proctorEnabled && (
                            <>
                              <label className="flex cursor-pointer items-center gap-1.5 text-xs text-text-secondary">
                                <input
                                  type="checkbox"
                                  checked={proctorTabSwitch}
                                  onChange={(e) => setProctorTabSwitch(e.target.checked)}
                                  className="accent-silver-400"
                                />
                                {t('proctor.tabSwitchLabel')}
                              </label>
                              <label className="flex cursor-pointer items-center gap-1.5 text-xs text-text-secondary">
                                <input
                                  type="checkbox"
                                  checked={proctorGaze}
                                  onChange={(e) => setProctorGaze(e.target.checked)}
                                  className="accent-silver-400"
                                />
                                {t('proctor.gazeLabel')}
                              </label>
                            </>
                          )}
                        </div>
                        <div className="flex items-center gap-2">
                        <select
                          value={generateLang}
                          onChange={(e) => setGenerateLang(e.target.value as LanguageCode)}
                          title={t('interviews.candidateLanguage')}
                          className="shrink-0 rounded-lg border border-border-default bg-surface-overlay px-2 py-1.5 text-sm text-text-primary outline-none focus:border-accent-primary"
                        >
                          {SUPPORTED_LANGUAGES.map((l) => (
                            <option key={l.code} value={l.code}>{l.label}</option>
                          ))}
                        </select>
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
                      </div>
                    )}
                  </>
                )}
              </div>
            )}
            {(accessConfig?.proctor?.tabSwitch || accessConfig?.proctor?.gaze) && (
              <div className="space-y-3 pt-3">
                <ProctorSummaryCard summary={proctorSummary} />
                <ProctorLivePanel events={proctorEvents} />
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

      {/* 生成计划中的非阻塞提示条（页面保持可交互） */}
      {isGenerating && (
        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          className="fixed bottom-4 right-4 z-50 flex items-center gap-3 rounded-lg border border-border-default bg-surface-overlay px-4 py-3 shadow-lg"
        >
          <div className="h-4 w-4 animate-spin rounded-full border-2 border-silver-300/30 border-t-silver-300" />
          <p className="text-sm text-text-secondary">{t('interviews.generatingPlan')}</p>
        </motion.div>
      )}

      {/* 密码展示弹窗（生成/重置时仅展示一次，明文不持久存储） */}
      {revealedPassword !== null && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-4"
          onClick={() => setRevealedPassword(null)}
        >
          <GlassCard
            className="w-full max-w-sm p-6"
            onClick={(e) => e.stopPropagation()}
          >
            <h3 className="mb-1 text-lg font-semibold text-text-primary">{t('interviews.passwordDialogTitle')}</h3>
            <p className="mb-4 text-sm text-text-muted">{t('interviews.passwordDialogHint')}</p>
            <div className="flex items-center gap-2 rounded-lg border border-border-subtle bg-surface-overlay px-3 py-2.5">
              <code className="min-w-0 flex-1 truncate text-sm font-mono text-silver-300">
                {revealedPassword}
              </code>
              <SilverButton variant="ghost" onClick={handleCopyPassword}>
                {t('interviews.copy')}
              </SilverButton>
            </div>
            <div className="mt-4 flex justify-end">
              <SilverButton onClick={() => setRevealedPassword(null)}>
                {t('common.confirm')}
              </SilverButton>
            </div>
          </GlassCard>
        </div>
      )}
    </div>
  );
}
