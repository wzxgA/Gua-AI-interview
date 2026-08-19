import { useEffect, useRef, useState } from 'react';
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
import { useTabSwitchDetection } from '@/hooks/useTabSwitchDetection';
import { useGazeDetection } from '@/hooks/useGazeDetection';
import { GazeDetector } from '@/components/candidate/GazeDetector';
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
  const addSystem = useSessionStore((s) => s.addSystem);
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

  // 结束回执（重连兜底）：终态/评估中时用 REST 数据补齐 end 回执，去重由 store.addSystem 保证
  useEffect(() => {
    if (!guestSession) return;
    const st = session.status as SessionStatus;
    if (['EVALUATING', 'COMPLETED', 'CANCELLED', 'FAILED'].includes(st)) {
      addSystem(st, guestSession.finishedBy ?? undefined, guestSession.finishReason ?? undefined);
    }
  }, [session.status, guestSession, addSystem]);

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

  // 读取面试级防作弊配置（进入页写入 sessionStorage）
  const proctorJson = sessionStorage.getItem('guestProctor');
  let proctor: { tabSwitch: boolean; gaze: boolean } = { tabSwitch: false, gaze: false };
  try {
    if (proctorJson) proctor = JSON.parse(proctorJson) as typeof proctor;
  } catch {
    // 忽略非法配置，按关闭处理
  }
  // 切屏检测（仅 proctor.tabSwitch 开启且面试 IN_PROGRESS 时启用；暂停/结束/取消后停止统计）
  useTabSwitchDetection(proctor.tabSwitch && session.status === 'IN_PROGRESS', sessionId);

  // 眼神检测（仅 proctor.gaze 开启且面试 IN_PROGRESS 时启用；拒绝摄像头不阻断面试）
  // 预览视频元素始终渲染（保证 ref 有效），仅授权后可见，默认右上角、可拖动
  const gazeVideoRef = useRef<HTMLVideoElement>(null);
  const [gazePos, setGazePos] = useState<{ x: number; y: number } | null>(null);
  const gazeDragRef = useRef<{
    offsetX: number;
    offsetY: number;
    startX: number;
    startY: number;
    baseX: number;
    baseY: number;
  } | null>(null);

  const handleGazePointerDown = (e: React.PointerEvent<HTMLVideoElement>) => {
    e.preventDefault();
    const rect = e.currentTarget.getBoundingClientRect();
    const base = gazePos ?? { x: rect.left, y: rect.top };
    setGazePos(base);
    gazeDragRef.current = {
      offsetX: e.clientX - rect.left,
      offsetY: e.clientY - rect.top,
      startX: e.clientX,
      startY: e.clientY,
      baseX: base.x,
      baseY: base.y,
    };
    e.currentTarget.setPointerCapture(e.pointerId);
  };

  const handleGazePointerMove = (e: React.PointerEvent<HTMLVideoElement>) => {
    const d = gazeDragRef.current;
    if (!d) return;
    const w = 240;
    const h = 160;
    setGazePos({
      x: Math.min(Math.max(d.baseX + (e.clientX - d.startX), 0), window.innerWidth - w),
      y: Math.min(Math.max(d.baseY + (e.clientY - d.startY), 0), window.innerHeight - h),
    });
  };

  const handleGazePointerUp = () => {
    gazeDragRef.current = null;
  };

  const gaze = useGazeDetection(
    proctor.gaze && session.status === 'IN_PROGRESS',
    sessionId,
    gazeVideoRef.current,
  );

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

      {/* 眼神检测授权弹窗（仅等待授权时渲染，拒绝不阻断） */}
      <GazeDetector gaze={gaze} />

      {/* 摄像头预览（元素始终渲染保证 ref 有效，仅授权运行后可见；默认右上角，按住可拖动） */}
      <video
        ref={gazeVideoRef}
        muted
        playsInline
        autoPlay
        onPointerDown={handleGazePointerDown}
        onPointerMove={handleGazePointerMove}
        onPointerUp={handleGazePointerUp}
        onPointerCancel={handleGazePointerUp}
        style={gazePos ? { left: gazePos.x, top: gazePos.y } : undefined}
        className={`fixed z-40 h-40 w-60 cursor-move touch-none select-none rounded-xl border border-border-subtle bg-black object-cover shadow-lg ${
          gaze.camState === 'granted' ? '' : 'invisible'
        } ${gazePos ? '' : 'right-4 top-4'}`}
      />
    </div>
  );
}
