import type { SessionStatus } from './interview';

/** 仪表盘聚合统计，对齐 com.aims.core.dashboard.DashboardStats */
export interface DashboardStats {
  /** 按会话状态分组的数量分布（对齐 SessionStatus 枚举顺序） */
  statusCounts: StatusCount[];
  /** 近 30 天每日创建数趋势（升序） */
  dailyTrend: TrendPoint[];
  /** 得分统计（5 分制） */
  scoreStats: ScoreStats;
  /** 最近面试摘要（按创建时间倒序，最多 8 条） */
  recentInterviews: RecentInterview[];
}

export interface StatusCount {
  status: string;
  count: number;
}

export interface TrendPoint {
  /** yyyy-MM-dd */
  date: string;
  count: number;
}

export interface ScoreStats {
  avgScore: number;
  distribution: ScoreRange[];
}

export interface ScoreRange {
  /** 如 "0-1" */
  range: string;
  count: number;
}

/** 得分点（散点图）：score 为 5 分制综合得分，createdAt 为会话创建时间 */
export interface ScorePoint {
  score: number;
  createdAt: string | null;
}

export interface RecentInterview {
  id: number;
  candidateName: string | null;
  positionTitle: string | null;
  status: SessionStatus;
  totalScore: number | null;
  createdAt: string | null;
}
