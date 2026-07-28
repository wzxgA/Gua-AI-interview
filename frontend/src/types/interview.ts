/** 对齐 SessionStatus 枚举（9 个值） */
export type SessionStatus =
  | 'CREATED'
  | 'PLANNING'
  | 'IN_PROGRESS'
  | 'EVALUATING'
  | 'REPORTING'
  | 'COMPLETED'
  | 'PAUSED'
  | 'CANCELLED'
  | 'FAILED';

export const TERMINAL_STATUSES: SessionStatus[] = ['COMPLETED', 'CANCELLED', 'FAILED'];

/** 对齐 InterviewResponse */
export interface InterviewResponse {
  id: number;
  candidateId: number;
  positionId: number | null;
  status: SessionStatus;
  planJson: string | null;
  startedAt: string | null;
  endedAt: string | null;
  totalScore: number | null;
  createdAt: string;
  updatedAt: string;
}

/** 对齐 CreateInterviewRequest */
export interface CreateInterviewRequest {
  candidateId: number;
  positionId?: number | null;
}

/** 对齐 InterviewPlan */
export interface InterviewPlan {
  candidateName: string;
  position: string;
  sections: PlanSection[];
  questions: PlannedQuestion[];
  estimatedMinutes: number;
  version: string;
}

export interface PlanSection {
  name: string;
  questionCount: number;
  objective: string;
}

export interface PlannedQuestion {
  questionId: string;
  topic: string;
  difficulty: string;
  followUpHints: string[];
  evaluationFocus: string;
}
