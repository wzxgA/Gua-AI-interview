import { useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { GlassCard } from '@/components/ui/glass-card';
import { StatusBadge } from '@/components/ui/status-dot';
import { MessageTimeline } from '@/components/chat/MessageTimeline';
import { AnswerInput } from '@/components/chat/AnswerInput';
import { DisconnectOverlay } from '@/components/chat/DisconnectOverlay';
import { ActionButtons } from '@/components/interview/ActionButtons';
import { useInterviewSession } from '@/hooks/useInterviewSession';
import { useSessionStore } from '@/stores/sessionStore';
import { useGuestRounds, useGuestSession, resumeGuestSession, startGuestSession } from '@/api/access';
import { useUrlLanguageInit } from '@/hooks/useUrlLanguageInit';
import { LanguageSwitcher } from '@/components/ui/LanguageSwitcher';
import type { SessionStatus } from '@/types/interview';

/** 候选面试间：免登录，基于 guestToken 连接 WS 完成实时问答（复用现有问答链路）。 */
export function CandidateRoomPage() {
  const { t } = useTranslation();
  useUrlLanguageInit();
  const { accessToken } = useParams();
  const navigate = useNavigate();
  const guestToken = sessionStorage.getItem('guestToken');
  const sessionIdStr = sessionStorage.getItem('guestSessionId');
  const sessionId = sessionIdStr ? Number(sessionIdStr) : null;

  // 无有效 guestToken / sessionId → 回进入页
  useEffect(() => {
    if (!guestToken || !sessionId) {
      navigate(`/i/${accessToken}`, { replace: true });
    }
  }, [guestToken, sessionId, accessToken, navigate]);

  const { data: guestSession } = useGuestSession(sessionId);
  const { data: rounds } = useGuestRounds(sessionId);
  const queryClient = useQueryClient();
  const session = useInterviewSession({ sessionId, token: guestToken ?? undefined });
  const setSession = useSessionStore((s) => s.setSession);
  const setStatus = useSessionStore((s) => s.setStatus);
  const resetStore = useSessionStore((s) => s.reset);
  const addQuestion = useSessionStore((s) => s.addQuestion);
  const addAnswer = useSessionStore((s) => s.addAnswer);
  const { connect, disconnect } = session;

  // WebSocket 连接
  useEffect(() => {
    if (!sessionId) return;
    resetStore();
    connect();
    return () => disconnect();
  }, [sessionId, connect, disconnect, resetStore]);

  // 历史消息恢复（重连）
  useEffect(() => {
    if (!rounds || rounds.length === 0) return;
    rounds.forEach((r) => {
      addQuestion(
        r.id,
        r.seq ?? undefined,
        r.question,
        r.followUpType ?? undefined,
        r.parentSeq ?? undefined,
        r.followUpIndex ?? undefined,
      );
      if (r.answer) {
        addAnswer(r.answer, r.id);
      }
    });
  }, [rounds, addQuestion, addAnswer]);

  // 同步 REST 状态到 store
  useEffect(() => {
    if (guestSession) {
      setSession(guestSession.id, guestSession.status as SessionStatus);
    }
  }, [guestSession, setSession]);

  // WS 错误提示
  useEffect(() => {
    if (session.error) {
      toast.error(session.error);
    }
  }, [session.error]);

  const handleResume = () => {
    if (!sessionId) return;
    resumeGuestSession(sessionId)
      .then(() => {
        toast.success(t('candidate.resumeSuccess'));
        setStatus('IN_PROGRESS');
        queryClient.invalidateQueries({ queryKey: ['guest', 'session', sessionId] });
      })
      .catch((err: Error) => toast.error(err.message || t('candidate.resumeFailed')));
  };

  const handleBegin = () => {
    if (!sessionId) return;
    startGuestSession(sessionId)
      .then(() => {
        toast.success(t('candidate.startSuccess'));
        setStatus('IN_PROGRESS');
        queryClient.invalidateQueries({ queryKey: ['guest', 'session', sessionId] });
        // 触发服务端首题生成：向现有连接发送 BEGIN（服务端 handleBegin 复用 handleReconnectOrStart）。
        // 不能 reconnect()——重连会关闭旧连接触发服务端断线自动暂停（IN_PROGRESS -> PAUSED）
        session.send({ type: 'BEGIN' });
      })
      .catch((err: Error) => toast.error(err.message || t('candidate.startFailed')));
  };

  if (!guestSession || !sessionId) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <div className="h-8 w-8 animate-spin rounded-full border-2 border-border-default border-t-accent-primary" />
      </div>
    );
  }

  const status = session.status;
  const isTerminal = ['COMPLETED', 'CANCELLED', 'FAILED'].includes(status);

  return (
    <div className="relative mx-auto flex h-screen w-full max-w-5xl flex-col gap-4 p-4">
      {/* 顶部栏：AI 面试官 + 状态 + 操作按钮（暂停/结束/取消，可查看报告） */}
      <GlassCard className="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
        <div className="flex items-center gap-3">
          <LanguageSwitcher />
          <span className="text-sm font-medium text-text-primary">{t('interviews.aiInterviewer')}</span>
          <StatusBadge status={status} />
        </div>
        <ActionButtons
          status={status}
          onBeginInterview={handleBegin}
          onPause={() => session.pauseInterview()}
          onFinish={() => session.finishInterview()}
          onCancel={() => session.cancelInterview()}
          onResume={handleResume}
          onViewReport={() => navigate(`/i/${accessToken}/report`)}
        />
      </GlassCard>

      {/* 消息时间线 */}
      <div className="min-h-0 flex-1 overflow-y-auto">
        <MessageTimeline messages={session.messages} />
      </div>

      {/* 回答输入区 */}
      <AnswerInput
        onSend={session.submitAnswer}
        disabled={session.isStreaming || status !== 'IN_PROGRESS'}
      />

      {/* 断线遮罩 */}
      {!session.isConnected && !isTerminal && (
        <DisconnectOverlay
          retryCount={session.retryCount}
          maxRetries={3}
          onReconnect={session.reconnect}
        />
      )}
    </div>
  );
}
