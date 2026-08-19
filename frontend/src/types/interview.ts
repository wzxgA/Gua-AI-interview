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

/** 面试级防作弊开关（生成候选人链接时配置） */
export interface ProctorConfig {
  tabSwitch: boolean;
  gaze: boolean;
}

/** 防作弊事件（管理端视图） */
export interface ProctorEvent {
  id: number;
  eventType: string;
  occurredAt: string | null;
  durationMs: number | null;
  detail: string | null;
}

/** 防作弊摘要（管理端视图） */
export interface ProctorSummary {
  items: { type: string; count: number; totalDurationMs: number }[];
}

/** 面试官人设 */
export type InterviewerPersona = 'FRIENDLY' | 'PRESSURE' | 'TECHNICAL';

/** 对齐 InterviewResponse */
export interface InterviewResponse {
  id: number;
  candidateId: number | null;
  /** 本场面试所用简历 ID（v1.1-C TD2） */
  resumeId: number | null;
  positionId: number | null;
  status: SessionStatus;
  persona: InterviewerPersona;
  planJson: string | null;
  startedAt: string | null;
  endedAt: string | null;
  totalScore: number | null;
  evaluationStatus: string | null;
  evaluatedRounds: number | null;
  totalRoundsToEvaluate: number | null;
  createdAt: string;
  updatedAt: string;
  /** 候选人访问链接令牌（管理端） */
  accessToken?: string | null;
  /** 访问密码明文（仅创建/重置时返回） */
  accessPassword?: string | null;
  /** 入口模式：NONE=未生成链接，CANDIDATE_ONLY=仅候选端，DISABLED=已作废 */
  accessMode?: 'NONE' | 'CANDIDATE_ONLY' | 'DISABLED';
  /** 结束方（谁结束）：ADMIN/CANDIDATE/SYSTEM */
  finishedBy?: string | null;
  /** 结束方式：MANUAL_FINISH/CANCELLED/FAILED */
  finishReason?: string | null;
}

/** 对齐 CreateInterviewRequest（v1.1-C TD2：入参为简历 ID） */
export interface CreateInterviewRequest {
  resumeId: number;
  positionId?: number | null;
  persona?: InterviewerPersona;
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
  | { type: 'CANCEL' }
  | { type: 'BEGIN' };

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
    | 'AUDIO_READY'
    | 'ERROR';
  sessionId?: number;
  roundId?: number;
  seq?: number;
  parentSeq?: number;
  followUpIndex?: number;
  status?: SessionStatus;
  code?: number;
  message?: string;
  text?: string;
  followUpType?: string;
  audioUrl?: string;
  durationMs?: number;
  finishedBy?: string;
  finishReason?: string;
}

/** 聊天消息 */
export interface ChatMessage {
  id: string;
  role: 'question' | 'answer' | 'system';
  text: string;
  roundId?: number;
  seq?: number;
  followUpType?: string;
  parentSeq?: number;
  followUpIndex?: number;
  audioUrl?: string;
  durationMs?: number;
  status?: SessionStatus;
  finishedBy?: string;
  finishReason?: string;
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
  seq: number | null;
  question: string;
  answer: string | null;
  followUpType: string | null;
  parentSeq: number | null;
  followUpIndex: number | null;
  createdAt: string;
}
