import { useQuery } from '@tanstack/react-query';
import { http } from './client';
import type { RoundResponse } from '@/types/interview';
import type { EvaluationResponse, ReportResponse } from '@/types/report';

/** 候选入口信息 */
export interface AccessInfo {
  sessionId: number;
  candidateName: string;
  position: string;
  status: string;
  requirePassword: boolean;
  enabled: boolean;
}

/** 密码校验通过响应 */
export interface VerifyResult {
  sessionId: number;
  guestToken: string;
}

/** 候选会话视图 */
export interface GuestSession {
  id: number;
  status: string;
  persona: string | null;
  planJson: string | null;
  startedAt: string | null;
  endedAt: string | null;
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

export function getGuestReport(sessionId: number) {
  return http.get<ReportResponse>(`/api/v1/access/interviews/${sessionId}/report`);
}

export function getGuestEvaluations(sessionId: number) {
  return http.get<EvaluationResponse[]>(`/api/v1/access/interviews/${sessionId}/evaluations`);
}

export function resumeGuestSession(sessionId: number) {
  return http.post<void>(`/api/v1/access/interviews/${sessionId}/resume`);
}

export function startGuestSession(sessionId: number) {
  return http.post<GuestSession>(`/api/v1/access/interviews/${sessionId}/start`);
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
