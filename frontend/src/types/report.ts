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
  { label: string; weight: number }
> = {
  PROFESSIONAL: { label: '专业能力', weight: 0.4 },
  LOGIC: { label: '逻辑思维', weight: 0.2 },
  COMMUNICATION: { label: '沟通表达', weight: 0.15 },
  JOB_MATCH: { label: '岗位匹配', weight: 0.15 },
  POTENTIAL: { label: '学习与潜力', weight: 0.1 },
};

/** 录用建议标签 */
export const RECOMMENDATION_LABELS: Record<Recommendation, string> = {
  STRONGLY_RECOMMEND: '强烈推荐',
  RECOMMEND: '推荐',
  NEUTRAL: '中立',
  NOT_RECOMMEND: '不推荐',
};

/** 录用建议颜色 */
export const RECOMMENDATION_COLORS: Record<Recommendation, string> = {
  STRONGLY_RECOMMEND: 'text-emerald-400 border-emerald-400/30 bg-emerald-400/10',
  RECOMMEND: 'text-sky-400 border-sky-400/30 bg-sky-400/10',
  NEUTRAL: 'text-amber-400 border-amber-400/30 bg-amber-400/10',
  NOT_RECOMMEND: 'text-rose-400 border-rose-400/30 bg-rose-400/10',
};
