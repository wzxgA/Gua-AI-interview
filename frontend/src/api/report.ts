import { useQuery } from '@tanstack/react-query';
import { http } from './client';
import type { ReportResponse, EvaluationResponse } from '@/types/report';

/** 获取面试报告 */
export function useInterviewReport(id: number | undefined) {
  return useQuery({
    queryKey: ['report', id],
    queryFn: () =>
      http.get<ReportResponse>(`/api/v1/interviews/${id}/report`),
    enabled: !!id,
  });
}

/** 获取所有轮次评分明细 */
export function useInterviewEvaluations(id: number | undefined) {
  return useQuery({
    queryKey: ['evaluations', id],
    queryFn: () =>
      http.get<EvaluationResponse[]>(`/api/v1/interviews/${id}/evaluations`),
    enabled: !!id,
  });
}

/** 获取指定轮次评分明细 */
export function useRoundEvaluations(
  sessionId: number | undefined,
  roundId: number | undefined,
) {
  return useQuery({
    queryKey: ['evaluations', sessionId, roundId],
    queryFn: () =>
      http.get<EvaluationResponse[]>(
        `/api/v1/interviews/${sessionId}/evaluations/${roundId}`,
      ),
    enabled: !!sessionId && !!roundId,
  });
}
