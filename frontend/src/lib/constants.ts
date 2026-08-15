export const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080';
export const WS_BASE = import.meta.env.VITE_WS_BASE || 'ws://localhost:8080';

export const APP_VERSION = 'v1.0';

export const PAGE_SIZE_DEFAULT = 10;

export const CATEGORIES = ['TECHNICAL', 'BEHAVIORAL', 'PROJECT'] as const;
export const DIFFICULTIES = ['EASY', 'MEDIUM', 'HARD'] as const;
export const PARSE_STATUSES = ['PENDING', 'PARSED', 'FAILED'] as const;

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
