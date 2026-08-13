export const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080';
export const WS_BASE = import.meta.env.VITE_WS_BASE || 'ws://localhost:8080';

export const APP_VERSION = 'v1.0';

export const PAGE_SIZE_DEFAULT = 10;

export const CATEGORIES = ['TECHNICAL', 'BEHAVIORAL', 'PROJECT'] as const;
export const DIFFICULTIES = ['EASY', 'MEDIUM', 'HARD'] as const;
export const PARSE_STATUSES = ['PENDING', 'PARSED', 'FAILED'] as const;

export const CATEGORY_LABELS: Record<string, string> = {
  TECHNICAL: '技术',
  BEHAVIORAL: '行为',
  PROJECT: '项目',
};

export const DIFFICULTY_LABELS: Record<string, string> = {
  EASY: '简单',
  MEDIUM: '中等',
  HARD: '困难',
  BASIC: '基础',
  BALANCED: '均衡',
  ADVANCED: '深入',
};

export const PARSE_STATUS_LABELS: Record<string, string> = {
  PENDING: '解析中',
  PARSED: '已解析',
  FAILED: '解析失败',
};

export const SESSION_STATUSES = [
  'CREATED',
  'PLANNING',
  'IN_PROGRESS',
  'EVALUATING',
  'REPORTING',
  'COMPLETED',
  'PAUSED',
  'CANCELLED',
  'FAILED',
] as const;

export const SESSION_STATUS_LABELS: Record<string, string> = {
  CREATED: '待开始',
  PLANNING: '规划中',
  IN_PROGRESS: '进行中',
  EVALUATING: '评估中',
  REPORTING: '报告中',
  COMPLETED: '已完成',
  PAUSED: '已暂停',
  CANCELLED: '已取消',
  FAILED: '失败',
};

export const PERSONA_LABELS: Record<string, string> = {
  FRIENDLY: '温和型',
  PRESSURE: '压力面型',
  TECHNICAL: '深度技术型',
};

export const TIER_LABELS: Record<string, string> = {
  FLAGSHIP: '旗舰档（高质量面试对话）',
  STANDARD: '标准档（追问决策 / 评估打分 / 计划 / 报告）',
  ECONOMY: '经济档（摘要 / 低成本任务）',
  EMBEDDING: '向量化档（题库 / 简历 / JD 向量嵌入）',
};
