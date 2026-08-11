import { useCallback } from 'react';
import { useQueryClient } from '@tanstack/react-query';
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
  const qc = useQueryClient();

  const invalidateRounds = useCallback(() => {
    if (sessionId) {
      qc.invalidateQueries({ queryKey: ['interviews', sessionId, 'rounds'] });
    }
  }, [qc, sessionId]);

  const handleMessage = useCallback(
    (msg: WsServerMessage) => {
      switch (msg.type) {
        case 'SESSION_READY':
          store.setConnected(true);
          if (msg.sessionId && msg.status) {
            store.setSession(msg.sessionId, msg.status);
          }
          break;

        case 'QUESTION_START':
          store.setConnected(true);
          store.startQuestion(
            msg.roundId ?? undefined,
            msg.seq,
            msg.followUpType,
            msg.parentSeq,
            msg.followUpIndex,
          );
          break;

        case 'QUESTION_CHUNK':
          if (msg.text) store.appendChunk(msg.text);
          break;

        case 'QUESTION_END':
          store.finalizeQuestion();
          invalidateRounds();
          break;

        case 'ANSWER_ACK':
          invalidateRounds();
          break;

        case 'AUDIO_READY':
          if (msg.roundId != null && msg.audioUrl) {
            store.setAudio(msg.roundId, msg.audioUrl, msg.durationMs);
          }
          break;

        case 'HEARTBEAT_ACK':
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
    [store, invalidateRounds],
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

  /** 提交回答 */
  const submitAnswer = useCallback(
    (text: string) => {
      if (!text.trim()) return;
      store.addAnswer(text.trim(), store.currentRoundId ?? undefined);
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
    submitAnswer,
    pauseInterview,
    finishInterview,
    cancelInterview,
    reset: store.reset,
  };
}
