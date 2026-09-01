import { useQuery } from '@tanstack/react-query';
import {
  queryInstant,
  queryRange,
  RANGE_OPTIONS,
  stepFor,
  type PromInstant,
  type PromSeries,
  type RangeKey,
} from '@/api/prometheus';

/** 监控面板自动刷新间隔 */
const REFETCH_MS = 30_000;

export type PromChartSeries = { name: string; points: { x: number; y: number }[] };

/** 区间查询（30s 自动刷新），series 已解析为 {name, points[{x:ms, y:number}]} */
export function usePromRange(expr: string, range: RangeKey) {
  return useQuery<PromSeries[]>({
    queryKey: ['prom', 'range', expr, range],
    queryFn: async () => {
      const endSec = Date.now() / 1000;
      const startSec = endSec - RANGE_OPTIONS[range];
      return queryRange(expr, startSec, endSec, stepFor(range));
    },
    refetchInterval: REFETCH_MS,
  });
}

/** 瞬时查询（统计卡用，30s 自动刷新） */
export function usePromInstant(expr: string) {
  return useQuery<PromInstant[]>({
    queryKey: ['prom', 'instant', expr],
    queryFn: () => queryInstant(expr),
    refetchInterval: REFETCH_MS,
  });
}
