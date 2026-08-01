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
  evaluationStatus: string | null;
  evaluatedRounds: number | null;
  totalRoundsToEvaluate: number | null;
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

/** WebSocket 客户端 -> 服务端消息 */
export type WsClientMessage =
  | { type: 'ANSWER'; text: string }
  | { type: 'HEARTBEAT' }
  | { type: 'PAUSE' }
  | { type: 'FINISH' }
  | { type: 'CANCEL' };

/** WebSocket 服务端 -> 客户端消息 */
export interface WsServerMessage {
  type:
    | 'SESSION_READY'
    | 'QUESTION_START'
    | 'QUESTION_CHUNK'
    | 'QUESTION_END'
    | 'ANSWER_ACK'
    | 'STATUS'
    | 'SESSION_COMPLETED'
    | 'HEARTBEAT_ACK'
    | 'ERROR';
  sessionId?: number;
  roundId?: number;
  seq?: number;
  status?: SessionStatus;
  code?: number;
  message?: string;
  text?: string;
  followUpType?: string;
}

/** 聊天消息 */
export interface ChatMessage {
  id: string;
  role: 'question' | 'answer';
  text: string;
  roundId?: number;
  seq?: number;
  followUpType?: string;
  parentSeq?: number;
  timestamp: string;
  streaming?: boolean;
}

/** 面试列表查询参数 */
export interface InterviewQuery {
  page?: number;
  size?: number;
  status?: SessionStatus;
}

/** 面试轮次响应 */
export interface RoundResponse {
  id: number;
  seq: number;
  question: string;
  answer: string | null;
  followUpType: string | null;
  parentSeq: number | null;
  createdAt: string;
}
