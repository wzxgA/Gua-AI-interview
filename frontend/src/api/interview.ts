import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { http } from './client';
import type { Page } from '@/types/common';
import type {
  InterviewResponse,
  CreateInterviewRequest,
  InterviewQuery,
} from '@/types/interview';

const KEY = 'interviews';

/** 面试分页列表 */
export function useInterviewList(query: InterviewQuery = {}) {
  const params = new URLSearchParams();
  if (query.page) params.set('page', String(query.page));
  if (query.size) params.set('size', String(query.size));
  if (query.status) params.set('status', query.status);
  return useQuery({
    queryKey: [KEY, query],
    queryFn: () =>
      http.get<Page<InterviewResponse>>(`/api/v1/interviews?${params}`),
  });
}

/** 面试详情，PLANNING 状态时 2s 轮询 */
export function useInterview(id: number | undefined) {
  return useQuery({
    queryKey: [KEY, id],
    queryFn: () => http.get<InterviewResponse>(`/api/v1/interviews/${id}`),
    enabled: !!id,
    refetchInterval: (query) =>
      query.state.data?.status === 'PLANNING' ? 2000 : false,
  });
}

/** 创建面试 */
export function useCreateInterview() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: CreateInterviewRequest) =>
      http.post<InterviewResponse>('/api/v1/interviews', req),
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY] }),
  });
}

/** 开始面试（生成计划并开始） */
export function useStartInterview() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) =>
      http.post<InterviewResponse>(`/api/v1/interviews/${id}/start`),
    onSuccess: (data) => {
      qc.invalidateQueries({ queryKey: [KEY] });
      qc.invalidateQueries({ queryKey: [KEY, data.id] });
    },
  });
}

/** 结束面试 */
export function useFinishInterview() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) =>
      http.post<InterviewResponse>(`/api/v1/interviews/${id}/finish`),
    onSuccess: (data) => {
      qc.invalidateQueries({ queryKey: [KEY] });
      qc.invalidateQueries({ queryKey: [KEY, data.id] });
    },
  });
}

/** 取消面试 */
export function useCancelInterview() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) =>
      http.post<InterviewResponse>(`/api/v1/interviews/${id}/cancel`),
    onSuccess: (data) => {
      qc.invalidateQueries({ queryKey: [KEY] });
      qc.invalidateQueries({ queryKey: [KEY, data.id] });
    },
  });
}

/** 恢复面试 */
export function useResumeInterview() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) =>
      http.post<InterviewResponse>(`/api/v1/interviews/${id}/resume`),
    onSuccess: (data) => {
      qc.invalidateQueries({ queryKey: [KEY] });
      qc.invalidateQueries({ queryKey: [KEY, data.id] });
    },
  });
}
