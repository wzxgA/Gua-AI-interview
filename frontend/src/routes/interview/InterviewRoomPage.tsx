import { useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { GlassCard } from '@/components/ui/glass-card';
import { Skeleton } from '@/components/ui/skeleton';
import { StatusBadge } from '@/components/ui/status-dot';
import { PageHeader, ErrorState } from '@/components/common/PageHeader';
import { MessageTimeline } from '@/components/chat/MessageTimeline';
import { AnswerInput } from '@/components/chat/AnswerInput';
import { DisconnectOverlay } from '@/components/chat/DisconnectOverlay';
import { ActionButtons } from '@/components/interview/ActionButtons';
import { useInterview, useInterviewRounds, useResumeInterview } from '@/api/interview';
import { useInterviewSession } from '@/hooks/useInterviewSession';
import { useSessionStore } from '@/stores/sessionStore';

export function InterviewRoomPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const interviewId = id ? Number(id) : undefined;

  const { data: interview, isLoading, isError, error } = useInterview(interviewId);
  const { data: rounds } = useInterviewRounds(interviewId);
  const resumeMutation = useResumeInterview();

  const session = useInterviewSession({ sessionId: interviewId ?? null });
  const setSession = useSessionStore((s) => s.setSession);
  const resetStore = useSessionStore((s) => s.reset);
  const addQuestion = useSessionStore((s) => s.addQuestion);
  const addAnswer = useSessionStore((s) => s.addAnswer);
  const { connect, disconnect } = session;

  // 历史消息是否已恢复（避免重复恢复）
  const historyRestoredRef = useRef(false);

  // WebSocket 连接（只依赖 interviewId，不受 rounds 加载影响）
  useEffect(() => {
    if (!interviewId) return;

    resetStore();
    historyRestoredRef.current = false;
    connect();

    return () => disconnect();
  }, [interviewId, connect, disconnect, resetStore]);

  // 历史消息恢复（依赖 rounds，不触发 WebSocket 重连）
  useEffect(() => {
    if (!rounds || rounds.length === 0) return;
    if (historyRestoredRef.current) return;

    rounds.forEach((r) => {
      addQuestion(r.id, r.question);
      if (r.answer) {
        addAnswer(r.answer);
      }
    });
    historyRestoredRef.current = true;
  }, [rounds, addQuestion, addAnswer]);

  // 同步 REST 状态到 store
  useEffect(() => {
    if (interview) {
      setSession(interview.id, interview.status);
    }
  }, [interview, setSession]);

  // WebSocket 错误提示
  useEffect(() => {
    if (session.error) {
      toast.error(session.error);
    }
  }, [session.error]);

  const handleResume = () => {
    if (!interviewId) return;
    resumeMutation.mutate(interviewId, {
      onSuccess: () => toast.success('面试已恢复'),
      onError: (err: Error) => toast.error(err.message || '恢复失败'),
    });
  };

  if (isLoading) {
    return (
      <div className="space-y-6">
        <PageHeader title="面试间" />
        <Skeleton className="h-96 w-full" />
      </div>
    );
  }

  if (isError || !interview) {
    return (
      <div className="space-y-6">
        <PageHeader title="面试间" />
        <ErrorState
          message={error?.message || '面试不存在'}
          onRetry={() => navigate('/interviews')}
        />
      </div>
    );
  }

  const status = session.status;
  const isTerminal = ['COMPLETED', 'CANCELLED', 'FAILED'].includes(status);

  return (
    <div className="relative flex h-[calc(100vh-7.5rem)] flex-col gap-4">
      {/* 顶部栏：返回控制台 + 面试间 #id + 状态 + 操作按钮 */}
      <GlassCard className="flex flex-wrap items-center justify-between gap-3 p-4">
        <div className="flex items-center gap-3">
          <button
            onClick={() => navigate(`/interviews/${interview.id}`)}
            className="text-xs text-silver-300 hover:text-silver-100 transition-colors"
          >
            ← 控制台
          </button>
          <span className="text-sm font-medium text-text-primary">
            面试间 #{interview.id}
          </span>
          <StatusBadge status={status} />
        </div>
        <ActionButtons
          status={status}
          onCancel={() => session.cancelInterview()}
          onPause={() => session.pauseInterview()}
          onFinish={() => session.finishInterview()}
          onResume={handleResume}
          onBack={() => navigate(`/interviews/${interview.id}`)}
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
