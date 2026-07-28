import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { http } from './client';
import type { Page } from '@/types/common';
import type {
  PositionResponse,
  CreatePositionRequest,
  UpdatePositionRequest,
  PositionQuery,
} from '@/types/position';

const KEY = 'positions';

export function usePositionList(query: PositionQuery = {}) {
  const params = new URLSearchParams();
  if (query.page) params.set('page', String(query.page));
  if (query.size) params.set('size', String(query.size));
  if (query.title) params.set('title', query.title);
  if (query.department) params.set('department', query.department);
  return useQuery({
    queryKey: [KEY, query],
    queryFn: () => http.get<Page<PositionResponse>>(`/api/v1/positions?${params}`),
  });
}

export function usePosition(id: number | undefined) {
  return useQuery({
    queryKey: [KEY, id],
    queryFn: () => http.get<PositionResponse>(`/api/v1/positions/${id}`),
    enabled: !!id,
  });
}

export function useCreatePosition() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: CreatePositionRequest) =>
      http.post<PositionResponse>('/api/v1/positions', req),
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY] }),
  });
}

export function useUpdatePosition() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: UpdatePositionRequest }) =>
      http.put<PositionResponse>(`/api/v1/positions/${id}`, data),
    onSuccess: (data) => {
      qc.invalidateQueries({ queryKey: [KEY] });
      qc.invalidateQueries({ queryKey: [KEY, data.id] });
    },
  });
}

export function useDeletePosition() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => http.del<void>(`/api/v1/positions/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY] }),
  });
}

export function useEmbedPosition() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => http.post<void>(`/api/v1/positions/${id}/embed`),
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY] }),
  });
}
