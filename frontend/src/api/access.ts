import { useQuery } from '@tanstack/react-query';
import { http } from './client';
import type { RoundResponse } from '@/types/interview';
import type { ProctorConfig } from '@/types/interview';

/** 候选入口信息 */
export interface AccessInfo {
  sessionId: number;
  candidateName: string;
  position: string;
  status: string;
  requirePassword: boolean;
  enabled: boolean;
  /** 面试级防作弊配置（生成链接时配置） */
  proctor: ProctorConfig;
}

/** 密码校验通过响应 */
export interface VerifyResult {
  sessionId: number;
  guestToken: string;
}

/** 防作弊事件上报项 */
export interface ProctorEventItem {
  eventType: string;
  occurredAt: string;
  durationMs: number | null;
  detail?: string | null;
}

/** 候选会话视图 */
export interface GuestSession {
  id: number;
  status: string;
  persona: string | null;
  planJson: string | null;
  startedAt: string | null;
  endedAt: string | null;
  finishedBy?: string | null;
  finishReason?: string | null;
}

// ---- 公开接口（无需 token） ----

export function getAccessInfo(accessToken: string) {
  return http.get<AccessInfo>(`/api/v1/access/interviews/${accessToken}/info`);
}

export function verifyPassword(accessToken: string, password: string) {
  return http.post<VerifyResult>(`/api/v1/access/interviews/${accessToken}/verify`, { password });
}

// ---- 候选只读接口（需 guestToken） ----

export function getGuestSession(sessionId: number) {
  return http.get<GuestSession>(`/api/v1/access/interviews/${sessionId}`);
}

export function getGuestRounds(sessionId: number) {
  return http.get<RoundResponse[]>(`/api/v1/access/interviews/${sessionId}/rounds`);
}

export function resumeGuestSession(sessionId: number) {
  return http.post<void>(`/api/v1/access/interviews/${sessionId}/resume`);
}

export function startGuestSession(sessionId: number) {
  return http.post<GuestSession>(`/api/v1/access/interviews/${sessionId}/start`);
}

/** 批量上报防作弊事件（GUEST，自动携带 guestToken） */
export function postProctorEvents(sessionId: number, events: ProctorEventItem[]) {
  return http.post<void>(`/api/v1/access/interviews/${sessionId}/proctor/events`, { events });
}

// ---- React Query hooks ----

export function useGuestSession(sessionId: number | null) {
  return useQuery({
    queryKey: ['guest', 'session', sessionId],
    queryFn: () => getGuestSession(sessionId!),
    enabled: sessionId != null,
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      if (status === 'EVALUATING' || status === 'REPORTING') {
        return 2000;
      }
      if (status === 'IN_PROGRESS' || status === 'PAUSED') {
        return 5000;
      }
      return false;
    },
  });
}

export function useGuestRounds(sessionId: number | null) {
  return useQuery({
    queryKey: ['guest', 'rounds', sessionId],
    queryFn: () => getGuestRounds(sessionId!),
    enabled: sessionId != null,
  });
}
