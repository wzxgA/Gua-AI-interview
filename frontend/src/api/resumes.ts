import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { http } from './client';
import type { Page } from '@/types/common';
import type { ResumeResponse, ResumeQuery, ParsedResume } from '@/types/resume';

const KEY = 'resumes';

export function useResumeList(query: ResumeQuery = {}) {
  const params = new URLSearchParams();
  if (query.page) params.set('page', String(query.page));
  if (query.size) params.set('size', String(query.size));
  if (query.candidateName) params.set('candidateName', query.candidateName);
  return useQuery({
    queryKey: [KEY, query],
    queryFn: () => http.get<Page<ResumeResponse>>(`/api/v1/resumes?${params}`),
  });
}

export function useResume(id: number | undefined) {
  return useQuery({
    queryKey: [KEY, id],
    queryFn: () => http.get<ResumeResponse>(`/api/v1/resumes/${id}`),
    enabled: !!id,
    refetchInterval: (query) => (query.state.data?.parseStatus === 'PENDING' ? 3000 : false),
  });
}

export function useUploadResume() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: {
      file: File;
      candidateName: string;
      phone?: string;
      email?: string;
    }) => {
      const formData = new FormData();
      formData.append('file', data.file);
      formData.append('candidateName', data.candidateName);
      if (data.phone) formData.append('phone', data.phone);
      if (data.email) formData.append('email', data.email);
      return http.upload<ResumeResponse>('/api/v1/resumes/upload', formData);
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY] }),
  });
}

export function useParseResume() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => http.post<ResumeResponse>(`/api/v1/resumes/${id}/parse`),
    onSuccess: (data) => {
      qc.invalidateQueries({ queryKey: [KEY, data.id] });
      qc.invalidateQueries({ queryKey: [KEY] });
    },
  });
}

export function useUpdateParsedResume() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: { id: number; parsed: ParsedResume }) =>
      http.patch<ResumeResponse>(`/api/v1/resumes/${data.id}/parsed-resume`, data.parsed),
    onSuccess: (data) => {
      qc.invalidateQueries({ queryKey: [KEY, data.id] });
      qc.invalidateQueries({ queryKey: [KEY] });
    },
  });
}

export function useDeleteResume() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => http.del<void>(`/api/v1/resumes/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY] }),
  });
}

export function useEmbedResume() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => http.post<void>(`/api/v1/resumes/${id}/embed`),
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY] }),
  });
}
