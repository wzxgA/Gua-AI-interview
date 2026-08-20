import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { http } from './client';
import type { DashboardStats, ScorePoint } from '@/types/dashboard';

const KEY = 'dashboard';

/** 仪表盘聚合统计，30 秒轮询保持数据新鲜 */
export function useDashboardStats(enabled = true) {
  return useQuery({
    queryKey: [KEY, 'stats'],
    queryFn: () => http.get<DashboardStats>('/api/v1/dashboard/stats'),
    enabled,
    refetchInterval: 30_000,
  });
}

/** 得分分布/散点图的时间过滤参数。 */
export interface ScoreStatsParams {
  /** 最近 N 天（如 7/30/90） */
  days?: number;
  /** 自定义区间起始日期，yyyy-MM-dd */
  start?: string;
  /** 自定义区间结束日期，yyyy-MM-dd */
  end?: string;
}

/**
 * 得分散点图数据（得分 + 时间），支持按时间范围过滤。
 *
 * 过滤优先级：start/end 自定义区间（含当天） > days 最近 N 天 > 全部时间。
 * 切换过滤时通过 keepPreviousData 保留旧数据，避免图表闪烁。
 */
export function useScorePoints(params?: ScoreStatsParams) {
  const query = buildScoreFilterQuery(params);
  return useQuery({
    queryKey: [KEY, 'score-points', query || 'all'],
    queryFn: () => http.get<ScorePoint[]>(`/api/v1/dashboard/score-points${query}`),
    placeholderData: keepPreviousData,
  });
}

/** 组装过滤查询串（如 ?days=30 / ?start=2026-08-01&end=2026-08-19），无过滤时返回空串。 */
function buildScoreFilterQuery(params?: ScoreStatsParams): string {
  if (!params) return '';
  const qs = new URLSearchParams();
  if (params.start && params.end) {
    qs.set('start', params.start);
    qs.set('end', params.end);
  } else if (params.days) {
    qs.set('days', String(params.days));
  }
  const query = qs.toString();
  return query ? `?${query}` : '';
}
