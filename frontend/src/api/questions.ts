import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { http } from './client';
import type { Page } from '@/types/common';
import type {
  QuestionResponse,
  CreateQuestionRequest,
  UpdateQuestionRequest,
  QuestionImportRequest,
  QuestionQuery,
} from '@/types/question';

const KEY = 'questions';

export function useQuestionList(query: QuestionQuery = {}) {
  const params = new URLSearchParams();
  if (query.page) params.set('page', String(query.page));
  if (query.size) params.set('size', String(query.size));
  if (query.category) params.set('category', query.category);
  if (query.difficulty) params.set('difficulty', query.difficulty);
  if (query.topic) params.set('topic', query.topic);
  return useQuery({
    queryKey: [KEY, query],
    queryFn: () => http.get<Page<QuestionResponse>>(`/api/v1/questions?${params}`),
  });
}

export function useQuestion(id: number | undefined) {
  return useQuery({
    queryKey: [KEY, id],
    queryFn: () => http.get<QuestionResponse>(`/api/v1/questions/${id}`),
    enabled: !!id,
  });
}

export function useCreateQuestion() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: CreateQuestionRequest) =>
      http.post<QuestionResponse>('/api/v1/questions', req),
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY] }),
  });
}

export function useUpdateQuestion() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: UpdateQuestionRequest }) =>
      http.put<QuestionResponse>(`/api/v1/questions/${id}`, data),
    onSuccess: (data) => {
      qc.invalidateQueries({ queryKey: [KEY] });
      qc.invalidateQueries({ queryKey: [KEY, data.id] });
    },
  });
}

export function useDeleteQuestion() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => http.del<void>(`/api/v1/questions/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY] }),
  });
}

export function useImportQuestions() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: QuestionImportRequest) =>
      http.post<QuestionResponse[]>('/api/v1/questions/import', req),
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY] }),
  });
}

export function useReembedQuestions() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => http.post<number>('/api/v1/questions/reembed'),
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY] }),
  });
}
