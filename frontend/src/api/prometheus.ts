/**
 * Prometheus HTTP API 封装。
 *
 * 走同源 `/prometheus` 前缀（dev 由 vite proxy 转发到 9090，生产由反向代理转发），
 * 避免 Prometheus 未开启 CORS 导致的跨域失败。
 */

const PROM_BASE = '/prometheus';

export interface PromSeries {
  metric: Record<string, string>;
  values: [number, string][];
}

export interface PromInstant {
  metric: Record<string, string>;
  value: [number, string];
}

export const RANGE_OPTIONS = { '1h': 3600, '6h': 21600, '24h': 86400 } as const;
export type RangeKey = keyof typeof RANGE_OPTIONS;

/** 范围 → rate 窗口（保证窗口覆盖足够样本且平滑） */
export const RATE_WINDOW: Record<RangeKey, string> = {
  '1h': '5m',
  '6h': '30m',
  '24h': '2h',
};

/** 范围 → 查询步长（秒），控制返回点数 ≈ 240 */
export function stepFor(rangeKey: RangeKey): number {
  return RANGE_OPTIONS[rangeKey] / 240;
}

interface PromData<T> {
  status: string;
  data: T;
}

async function get<T>(path: string, params: Record<string, string>): Promise<T> {
  const qs = new URLSearchParams(params).toString();
  const res = await fetch(`${PROM_BASE}${path}?${qs}`);
  if (!res.ok) {
    throw new Error(`Prometheus ${path} HTTP ${res.status}`);
  }
  const body: PromData<T> = await res.json();
  if (body.status !== 'success') {
    throw new Error(`Prometheus query failed: ${body.status}`);
  }
  return body.data;
}

/** 区间查询：返回多条时间序列 */
export async function queryRange(
  expr: string,
  startSec: number,
  endSec: number,
  stepSec: number,
): Promise<PromSeries[]> {
  const data = await get<{ resultType: 'matrix'; result: PromSeries[] }>(
    '/api/v1/query_range',
    {
      query: expr,
      start: String(Math.floor(startSec)),
      end: String(Math.floor(endSec)),
      step: String(stepSec),
    },
  );
  return data.result;
}

/** 瞬时查询：统计卡用 */
export async function queryInstant(expr: string): Promise<PromInstant[]> {
  const data = await get<{ resultType: 'vector'; result: PromInstant[] }>(
    '/api/v1/query',
    { query: expr },
  );
  return data.result;
}

/** 数值格式化：大数缩写 + 合理小数位 */
export function fmtNumber(v: number | null | undefined): string {
  if (v === null || v === undefined || !Number.isFinite(v)) return '-';
  const abs = Math.abs(v);
  if (abs >= 1e9) return `${(v / 1e9).toFixed(1)}G`;
  if (abs >= 1e6) return `${(v / 1e6).toFixed(1)}M`;
  if (abs >= 1e3) return `${(v / 1e3).toFixed(1)}k`;
  if (abs >= 10) return v.toFixed(0);
  if (abs >= 1) return v.toFixed(2);
  if (abs === 0) return '0';
  return v.toPrecision(2);
}
