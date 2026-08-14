import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { http } from './client';
import type { Page } from '@/types/common';
import type {
  InterviewResponse,
  CreateInterviewRequest,
  InterviewQuery,
  RoundResponse,
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

/** 面试详情，PLANNING / EVALUATING / REPORTING 状态时 2s 轮询 */
export function useInterview(id: number | undefined) {
  return useQuery({
    queryKey: [KEY, id],
    queryFn: () => http.get<InterviewResponse>(`/api/v1/interviews/${id}`),
    enabled: !!id,
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      if (status === 'PLANNING' || status === 'EVALUATING' || status === 'REPORTING') {
        return 2000;
      }
      if (status === 'IN_PROGRESS' || status === 'PAUSED') {
        return 5000;
      }
      return false;
    },
  });
}

/** 查询面试轮次列表（用于面试间重连恢复历史消息） */
export function useInterviewRounds(id: number | undefined) {
  return useQuery({
    queryKey: [KEY, id, 'rounds'],
    queryFn: () => http.get<RoundResponse[]>(`/api/v1/interviews/${id}/rounds`),
    enabled: !!id,
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

/** 开始生成面试计划请求参数 */
export interface StartPlanBody {
  questionCount?: number;
  difficulty?: 'BASIC' | 'BALANCED' | 'ADVANCED';
}

/** 生成面试计划（仅生成，不开始） */
export function usePlanInterview() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (params: { id: number; body?: StartPlanBody }) =>
      http.post<InterviewResponse>(`/api/v1/interviews/${params.id}/plan`, params.body ?? {}),
    onSuccess: (data) => {
      qc.invalidateQueries({ queryKey: [KEY] });
      qc.invalidateQueries({ queryKey: [KEY, data.id] });
    },
  });
}

/** 开始面试（从 PLANNING 进入 IN_PROGRESS） */
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

/** 暂停面试 */
export function usePauseInterview() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) =>
      http.post<InterviewResponse>(`/api/v1/interviews/${id}/pause`),
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

/** 删除面试 */
export function useDeleteInterview() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => http.del<void>(`/api/v1/interviews/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY] }),
  });
}

/** 候选人访问配置 */
export interface InterviewAccessConfig {
  accessToken: string | null;
  accessEnabled: boolean | null;
  requirePassword: boolean | null;
  accessPassword: string | null;
  accessMode: 'NONE' | 'CANDIDATE_ONLY' | 'DISABLED' | null;
}

/** 获取候选人访问配置（链接令牌/开关/是否有密码） */
export function useInterviewAccess(id: number | undefined) {
  return useQuery({
    queryKey: [KEY, id, 'access'],
    queryFn: () => http.get<InterviewAccessConfig>(`/api/v1/interviews/${id}/access`),
    enabled: !!id,
  });
}

/** 设置/重置候选人访问密码 */
export function useResetAccessPassword() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (params: { id: number; password?: string }) =>
      http.post<InterviewAccessConfig>(`/api/v1/interviews/${params.id}/access/password`, {
        password: params.password,
      }),
    onSuccess: (_data, vars) => qc.invalidateQueries({ queryKey: [KEY, vars.id, 'access'] }),
  });
}

/** 作废候选人入口 */
export function useDisableAccess() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) =>
      http.post<InterviewAccessConfig>(`/api/v1/interviews/${id}/access/disable`),
    onSuccess: (_data, id) => {
      qc.invalidateQueries({ queryKey: [KEY, id, 'access'] });
      qc.invalidateQueries({ queryKey: [KEY, id] });
    },
  });
}

/** 生成候选人面试链接（设为 CANDIDATE_ONLY） */
export function useGenerateAccess() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (params: { id: number; password?: string }) =>
      http.post<InterviewAccessConfig>(
        `/api/v1/interviews/${params.id}/access/generate`,
        { password: params.password },
      ),
    onSuccess: (_data, vars) => {
      qc.invalidateQueries({ queryKey: [KEY, vars.id, 'access'] });
      qc.invalidateQueries({ queryKey: [KEY, vars.id] });
    },
  });
}
