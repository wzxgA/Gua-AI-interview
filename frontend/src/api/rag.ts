import { useQuery } from '@tanstack/react-query';
import { http } from './client';
import type { QuestionSearchResult } from '@/types/question';
import type { ResumeSearchResult } from '@/types/resume';

export interface SearchMetrics {
  embeddingMs: number;
  sqlMs: number;
  totalMs: number;
  resultCount: number;
}

export interface RagSearchResponse<T> {
  results: T[];
  metrics: SearchMetrics;
}

export function useRagQuestions(
  query: string,
  topK: number = 5,
  category?: string,
  difficulty?: string,
) {
  const params = new URLSearchParams({ query, topK: String(topK) });
  if (category) params.set('category', category);
  if (difficulty) params.set('difficulty', difficulty);
  return useQuery({
    queryKey: ['rag', 'questions', query, topK, category, difficulty],
    queryFn: () =>
      http.get<RagSearchResponse<QuestionSearchResult>>(
        `/api/v1/rag/questions?${params}`,
      ),
    enabled: query.length > 0,
  });
}

export function useRagResumes(
  query: string,
  topK: number = 5,
  minScore?: number,
) {
  const params = new URLSearchParams({ query, topK: String(topK) });
  if (minScore !== undefined && minScore > 0) {
    params.set('minScore', String(minScore));
  }
  return useQuery({
    queryKey: ['rag', 'resumes', query, topK, minScore],
    queryFn: () =>
      http.get<RagSearchResponse<ResumeSearchResult>>(
        `/api/v1/rag/resumes?${params}`,
      ),
    enabled: query.length > 0,
  });
}
