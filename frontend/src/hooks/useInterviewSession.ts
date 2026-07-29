import { useCallback } from 'react';
import { useWebSocket } from './useWebSocket';
import { useSessionStore } from '@/stores/sessionStore';
import type { WsServerMessage, WsClientMessage } from '@/types/interview';

interface UseInterviewSessionOptions {
  sessionId: number | null;
}

/**
 * 组合 WebSocket + sessionStore，处理面试消息流
 */
export function useInterviewSession({ sessionId }: UseInterviewSessionOptions) {
  const store = useSessionStore();

  const handleMessage = useCallback(
    (msg: WsServerMessage) => {
      switch (msg.type) {
        case 'SESSION_READY':
          store.setConnected(true);
          if (msg.sessionId) store.setSession(msg.sessionId, 'IN_PROGRESS');
          break;

        case 'QUESTION_START':
          store.setConnected(true);
          if (msg.roundId != null) store.startQuestion(msg.roundId);
          break;

        case 'QUESTION_CHUNK':
          if (msg.text) store.appendChunk(msg.text);
          break;

        case 'QUESTION_END':
          store.finalizeQuestion();
          break;

        case 'ANSWER_ACK':
          // 答案已确认，无需额外操作
          break;

        case 'STATUS':
          if (msg.status) store.setStatus(msg.status);
          break;

        case 'SESSION_COMPLETED':
          store.setStatus('COMPLETED');
          store.finalizeQuestion();
          break;

        case 'ERROR':
          store.setError(msg.message || '面试服务异常');
          break;
      }
    },
    [store],
  );

  const { connect, send, disconnect, reconnect: wsReconnect } = useWebSocket({
    sessionId,
    onMessage: handleMessage,
    onOpen: () => store.setConnected(true),
    onClose: () => {
      store.setConnected(false);
      store.incrementRetry();
    },
  });

  /** 手动重连 */
  const reconnect = useCallback(() => {
    store.resetRetry();
    wsReconnect();
  }, [wsReconnect, store]);

  /** 开始面试 */
  const startInterview = useCallback(() => {
    send({ type: 'START' } satisfies WsClientMessage);
  }, [send]);

  /** 提交回答 */
  const submitAnswer = useCallback(
    (text: string) => {
      if (!text.trim()) return;
      store.addAnswer(text.trim());
      send({ type: 'ANSWER', text: text.trim() } satisfies WsClientMessage);
    },
    [send, store],
  );

  /** 暂停面试 */
  const pauseInterview = useCallback(() => {
    send({ type: 'PAUSE' } satisfies WsClientMessage);
  }, [send]);

  /** 结束面试 */
  const finishInterview = useCallback(() => {
    send({ type: 'FINISH' } satisfies WsClientMessage);
  }, [send]);

  /** 取消面试 */
  const cancelInterview = useCallback(() => {
    send({ type: 'CANCEL' } satisfies WsClientMessage);
  }, [send]);

  return {
    // 状态
    status: store.status,
    currentRoundId: store.currentRoundId,
    isStreaming: store.isStreaming,
    messages: store.messages,
    isConnected: store.isConnected,
    retryCount: store.retryCount,
    error: store.error,
    // 操作
    connect,
    disconnect,
    reconnect,
    startInterview,
    submitAnswer,
    pauseInterview,
    finishInterview,
    cancelInterview,
    reset: store.reset,
  };
}
