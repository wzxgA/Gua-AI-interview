/** 对齐 QuestionResponse */
export interface QuestionResponse {
  id: number;
  category: string;
  topic: string;
  difficulty: string;
  content: string;
  standardAnswer: string | null;
  tags: string[];
  hasEmbedding: boolean;
  createdAt: string;
  updatedAt: string;
}

/** 对齐 CreateQuestionRequest */
export interface CreateQuestionRequest {
  category: string;
  topic: string;
  difficulty: string;
  content: string;
  standardAnswer?: string;
  tags?: string[];
}

/** 对齐 UpdateQuestionRequest */
export interface UpdateQuestionRequest {
  category?: string;
  topic?: string;
  difficulty?: string;
  content?: string;
  standardAnswer?: string;
  tags?: string[];
}

/** 对齐 QuestionImportRequest */
export interface QuestionImportRequest {
  questions: CreateQuestionRequest[];
}

/** 对齐 ParsedQuestionResponse（面经解析结果，未落库） */
export interface ParsedQuestion {
  category: string;
  topic: string;
  difficulty: string;
  content: string;
  standardAnswer: string;
  tags: string[];
  matchedExistingId: number | null;
}

/** 对齐 InterviewNoteParseRequest */
export interface InterviewNoteParseRequest {
  text: string;
  categoryHint?: string;
}

/** 对齐 InterviewNoteParseTaskResponse（异步任务，提交/轮询共用） */
export interface InterviewNoteParseTask {
  taskId: string;
  status: string; // RUNNING / SUCCESS / FAILED / NOT_FOUND
  message: string | null;
  results: ParsedQuestion[] | null;
}

/** 对齐 QuestionSearchResult */
export interface QuestionSearchResult {
  id: number;
  category: string;
  topic: string;
  difficulty: string;
  content: string;
  standardAnswer: string | null;
  score: number;
}

/** 列表查询参数 */
export interface QuestionQuery {
  page?: number;
  size?: number;
  category?: string;
  difficulty?: string;
  topic?: string;
}
