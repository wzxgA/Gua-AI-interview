import { useEffect, useRef } from 'react';
import { postProctorEvents, type ProctorEventItem } from '@/api/access';
import { API_BASE } from '@/api/client';

const FLUSH_INTERVAL = 10_000;

/** 正在进行的"离开屏幕"记录（visibility 隐藏 / 窗口失焦）。 */
interface ActiveAway {
  type: 'TAB_SWITCH' | 'WINDOW_BLUR';
  at: number;
}

/**
 * 切屏检测：监听标签页隐藏/最小化/窗口失焦，聚合上报防作弊事件。
 * 仅在 proctor.tabSwitch 开启时启用。
 */
export function useTabSwitchDetection(enabled: boolean, sessionId: number | null) {
  const bufferRef = useRef<ProctorEventItem[]>([]);
  const activeRef = useRef<ActiveAway | null>(null);
  const enabledRef = useRef(enabled);
  const sessionIdRef = useRef(sessionId);
  enabledRef.current = enabled;
  sessionIdRef.current = sessionId;

  useEffect(() => {
    if (!enabled || !sessionId) return;

    const pushEvent = (type: ActiveAway['type'], durationMs: number) => {
      bufferRef.current.push({
        eventType: type,
        occurredAt: new Date(Date.now() - durationMs).toISOString(),
        durationMs,
      });
    };

    const flush = () => {
      if (bufferRef.current.length === 0 || !sessionIdRef.current) return;
      const items = bufferRef.current.splice(0);
      // 失败静默丢弃，避免高频重试占用；下个周期继续采集
      postProctorEvents(sessionIdRef.current, items).catch(() => {});
    };

    /** 离开屏幕开始计时。 */
    const startAway = (type: ActiveAway['type']) => {
      if (activeRef.current) return; // 已在计时中，避免重复
      activeRef.current = { type, at: Date.now() };
    };

    /** 回到屏幕结束计时并入 buffer。 */
    const endAway = () => {
      const active = activeRef.current;
      if (!active) return;
      activeRef.current = null;
      pushEvent(active.type, Date.now() - active.at);
    };

    const handleVisibility = () => {
      if (document.hidden) {
        // hidden 语义比 blur 更准确：若 blur 已先行计时，升级为 TAB_SWITCH
        if (activeRef.current) activeRef.current.type = 'TAB_SWITCH';
        else startAway('TAB_SWITCH');
      } else {
        endAway();
      }
    };
    const handleBlur = () => startAway('WINDOW_BLUR');
    const handleFocus = () => endAway();

    /** 离开页面时用 keepalive 兜底上报（fetch 可能被取消，sendBeacon 无法带自定义头）。 */
    const handlePageHide = () => {
      endAway(); // 结算进行中的离开事件，避免切走后未回来直接关页面丢失
      if (bufferRef.current.length === 0) return;
      const items = bufferRef.current.splice(0);
      const token = sessionStorage.getItem('guestToken');
      if (!sessionIdRef.current || !token) return;
      fetch(`${API_BASE}/api/v1/access/interviews/${sessionIdRef.current}/proctor/events`, {
        method: 'POST',
        keepalive: true,
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ events: items }),
      }).catch(() => {});
    };

    document.addEventListener('visibilitychange', handleVisibility);
    window.addEventListener('blur', handleBlur);
    window.addEventListener('focus', handleFocus);
    window.addEventListener('pagehide', handlePageHide);
    const timer = window.setInterval(flush, FLUSH_INTERVAL);

    return () => {
      document.removeEventListener('visibilitychange', handleVisibility);
      window.removeEventListener('blur', handleBlur);
      window.removeEventListener('focus', handleFocus);
      window.removeEventListener('pagehide', handlePageHide);
      window.clearInterval(timer);
      endAway(); // 结算进行中的离开事件
      flush(); // 停用/卸载时上报未上报事件，避免丢失最后一批
    };
  }, [enabled, sessionId]);
}
