import { useRef, useCallback, useEffect } from 'react';
import { WS_BASE } from '@/lib/constants';
import type { WsClientMessage, WsServerMessage } from '@/types/interview';

interface UseWebSocketOptions {
  sessionId: number | null;
  onMessage: (msg: WsServerMessage) => void;
  onOpen?: () => void;
  onClose?: () => void;
  onError?: (event: Event) => void;
}

const HEARTBEAT_INTERVAL = 30_000;
const MAX_RETRIES = 3;
const BASE_DELAY = 1000;

/**
 * WebSocket hook：连接、心跳、指数退避重连、消息分发
 */
export function useWebSocket(options: UseWebSocketOptions) {
  const { sessionId, onMessage, onOpen, onClose, onError } = options;

  const wsRef = useRef<WebSocket | null>(null);
  const heartbeatRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const retryCountRef = useRef(0);
  const shouldConnectRef = useRef(false);
  // 使用 ref 保存最新回调，避免重建连接
  const onMessageRef = useRef(onMessage);
  const onOpenRef = useRef(onOpen);
  const onCloseRef = useRef(onClose);
  const onErrorRef = useRef(onError);

  onMessageRef.current = onMessage;
  onOpenRef.current = onOpen;
  onCloseRef.current = onClose;
  onErrorRef.current = onError;

  const clearHeartbeat = useCallback(() => {
    if (heartbeatRef.current) {
      clearInterval(heartbeatRef.current);
      heartbeatRef.current = null;
    }
  }, []);

  const connect = useCallback(() => {
    if (!sessionId) return;
    shouldConnectRef.current = true;

    const url = `${WS_BASE}/ws/interview/${sessionId}?token=${localStorage.getItem('accessToken') ?? ''}`;
    const ws = new WebSocket(url);
    wsRef.current = ws;

    ws.onopen = () => {
      retryCountRef.current = 0;
      clearHeartbeat();
      // 30s 心跳
      heartbeatRef.current = setInterval(() => {
        if (ws.readyState === WebSocket.OPEN) {
          ws.send(JSON.stringify({ type: 'HEARTBEAT' } satisfies WsClientMessage));
        }
      }, HEARTBEAT_INTERVAL);
      onOpenRef.current?.();
    };

    ws.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data) as WsServerMessage;
        onMessageRef.current(msg);
      } catch {
        // 忽略无法解析的消息
      }
    };

    ws.onclose = () => {
      clearHeartbeat();
      onCloseRef.current?.();
      // 指数退避重连（1s, 2s, 4s，最多 3 次）
      if (shouldConnectRef.current && retryCountRef.current < MAX_RETRIES) {
        const delay = BASE_DELAY * Math.pow(2, retryCountRef.current);
        retryCountRef.current++;
        setTimeout(() => {
          if (shouldConnectRef.current) connect();
        }, delay);
      }
    };

    ws.onerror = (event) => {
      onErrorRef.current?.(event);
    };
  }, [sessionId, clearHeartbeat]);

  const send = useCallback((msg: WsClientMessage) => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify(msg));
      return true;
    }
    return false;
  }, []);

  const disconnect = useCallback(() => {
    shouldConnectRef.current = false;
    retryCountRef.current = 0;
    clearHeartbeat();
    if (wsRef.current) {
      wsRef.current.onclose = null;
      wsRef.current.close();
      wsRef.current = null;
    }
  }, [clearHeartbeat]);

  const reconnect = useCallback(() => {
    retryCountRef.current = 0;
    connect();
  }, [connect]);

  // 组件卸载时断开连接
  useEffect(() => {
    return () => {
      disconnect();
    };
  }, [disconnect]);

  return { connect, send, disconnect, reconnect };
}
