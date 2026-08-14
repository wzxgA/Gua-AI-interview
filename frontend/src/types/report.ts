/** 对齐 EvaluationDimension 枚举 */
export type EvaluationDimension =
  | 'PROFESSIONAL'
  | 'LOGIC'
  | 'COMMUNICATION'
  | 'JOB_MATCH'
  | 'POTENTIAL';

/** 对齐 Recommendation 枚举 */
export type Recommendation =
  | 'STRONGLY_RECOMMEND'
  | 'RECOMMEND'
  | 'NEUTRAL'
  | 'NOT_RECOMMEND';

/** 对齐后端 EvaluationResponse DTO */
export interface EvaluationResponse {
  id: number;
  sessionId: number;
  roundId: number;
  dimension: EvaluationDimension;
  dimensionLabel: string;
  score: number;
  comment: string;
  evidenceQuote: string | null;
  createdAt: string;
}

/** 对齐后端 ReportResponse DTO */
export interface ReportResponse {
  id: number;
  sessionId: number;
  summary: string;
  dimensionsJson: string;
  recommendation: Recommendation;
  recommendationLabel: string;
  totalScore: number | null;
  reportPdfUrl: string | null;
  createdAt: string;
}

/** 维度配置 */
export const DIMENSION_CONFIG: Record<
  EvaluationDimension,
  { weight: number }
> = {
  PROFESSIONAL: { weight: 0.4 },
  LOGIC: { weight: 0.2 },
  COMMUNICATION: { weight: 0.15 },
  JOB_MATCH: { weight: 0.15 },
  POTENTIAL: { weight: 0.1 },
};

/** 录用建议颜色 */
export const RECOMMENDATION_COLORS: Record<Recommendation, string> = {
  STRONGLY_RECOMMEND: 'text-emerald-400 border-emerald-400/30 bg-emerald-400/10',
  RECOMMEND: 'text-sky-400 border-sky-400/30 bg-sky-400/10',
  NEUTRAL: 'text-amber-400 border-amber-400/30 bg-amber-400/10',
  NOT_RECOMMEND: 'text-rose-400 border-rose-400/30 bg-rose-400/10',
};
