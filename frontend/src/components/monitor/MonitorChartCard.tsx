import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Area,
  AreaChart,
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { GlassCard } from '@/components/ui/glass-card';
import { useTheme } from '@/contexts/ThemeContext';
import { fmtNumber } from '@/api/prometheus';

export interface ChartSeries {
  name: string;
  points: { x: number; y: number }[];
}

const PALETTE_DARK = ['rgba(56,189,248,0.9)', 'rgba(167,139,250,0.9)', 'rgba(52,211,153,0.9)', 'rgba(251,191,36,0.9)', 'rgba(248,113,113,0.9)'];
const PALETTE_LIGHT = ['rgba(2,132,199,0.9)', 'rgba(124,58,237,0.9)', 'rgba(5,150,105,0.9)', 'rgba(217,119,6,0.9)', 'rgba(220,38,38,0.9)'];

/** 多 series 按时间戳对齐合并为 recharts 行数据 */
function toRows(series: ChartSeries[]) {
  const map = new Map<number, Record<string, number>>();
  series.forEach((s) => {
    s.points.forEach((p) => {
      const row = map.get(p.x) ?? {};
      row[s.name] = p.y;
      map.set(p.x, row);
    });
  });
  return [...map.entries()]
    .sort((a, b) => a[0] - b[0])
    .map(([x, values]) => ({ x, ...values }));
}

function timeTick(ms: number) {
  const d = new Date(ms);
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

export function MonitorChartCard({
  title,
  series,
  unit,
  isLine = false,
  isLoading = false,
}: {
  title: string;
  series: ChartSeries[];
  /** y 值单位（tooltip 展示），如 s / ops / bytes */
  unit?: string;
  /** 多 series 时用折线避免面积重叠 */
  isLine?: boolean;
  isLoading?: boolean;
}) {
  const { t } = useTranslation();
  const { resolvedTheme } = useTheme();
  const isDark = resolvedTheme === 'dark';

  const rows = useMemo(() => toRows(series), [series]);
  const isEmpty = !isLoading && rows.length === 0;
  const palette = isDark ? PALETTE_DARK : PALETTE_LIGHT;
  const gridColor = isDark ? 'rgba(220,224,230,0.1)' : 'rgba(0,0,0,0.06)';
  const axisColor = isDark ? 'rgba(220,224,230,0.55)' : 'rgba(0,0,0,0.45)';
  const dataKeys = series.map((s) => s.name);

  return (
    <GlassCard className="p-5">
      <h3 className="mb-2 text-sm font-medium text-text-muted">{title}</h3>
      <div className="h-56 w-full">
        {isEmpty ? (
          <div className="flex h-full w-full items-center justify-center">
            <span className="text-sm text-text-muted">{t('monitor.noData')}</span>
          </div>
        ) : (
          <ResponsiveContainer width="100%" height="100%">
            {isLine ? (
              <LineChart data={rows} margin={{ top: 4, right: 8, bottom: 0, left: -8 }}>
                <CartesianGrid strokeDasharray="3 3" stroke={gridColor} vertical={false} />
                <XAxis
                  dataKey="x"
                  type="number"
                  domain={['dataMin', 'dataMax']}
                  tickFormatter={timeTick}
                  tick={{ fill: axisColor, fontSize: 10 }}
                  tickLine={false}
                  axisLine={{ stroke: gridColor }}
                  minTickGap={24}
                />
                <YAxis
                  tickFormatter={(v: number) => fmtNumber(v)}
                  tick={{ fill: axisColor, fontSize: 10 }}
                  tickLine={false}
                  axisLine={false}
                  width={48}
                />
                <Tooltip
                  formatter={(value, name) => [fmtNumber(Number(value)), String(name)]}
                  labelFormatter={(ms) => timeTick(Number(ms))}
                  contentStyle={{
                    background: 'var(--surface-overlay)',
                    border: '1px solid var(--silver-400)',
                    borderRadius: 8,
                    fontSize: 12,
                  }}
                />
                {dataKeys.map((key, i) => (
                  <Line
                    key={key}
                    type="monotone"
                    dataKey={key}
                    stroke={palette[i % palette.length]}
                    strokeWidth={2}
                    dot={false}
                    connectNulls
                  />
                ))}
              </LineChart>
            ) : (
              <AreaChart data={rows} margin={{ top: 4, right: 8, bottom: 0, left: -8 }}>
                <defs>
                  {dataKeys.map((key, i) => (
                    <linearGradient key={key} id={`grad-${title}-${i}`} x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor={palette[i % palette.length]} stopOpacity={0.3} />
                      <stop offset="100%" stopColor={palette[i % palette.length]} stopOpacity={0.02} />
                    </linearGradient>
                  ))}
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke={gridColor} vertical={false} />
                <XAxis
                  dataKey="x"
                  type="number"
                  domain={['dataMin', 'dataMax']}
                  tickFormatter={timeTick}
                  tick={{ fill: axisColor, fontSize: 10 }}
                  tickLine={false}
                  axisLine={{ stroke: gridColor }}
                  minTickGap={24}
                />
                <YAxis
                  tickFormatter={(v: number) => fmtNumber(v)}
                  tick={{ fill: axisColor, fontSize: 10 }}
                  tickLine={false}
                  axisLine={false}
                  width={48}
                />
                <Tooltip
                  formatter={(value, name) => [fmtNumber(Number(value)), String(name)]}
                  labelFormatter={(ms) => timeTick(Number(ms))}
                  contentStyle={{
                    background: 'var(--surface-overlay)',
                    border: '1px solid var(--silver-400)',
                    borderRadius: 8,
                    fontSize: 12,
                  }}
                />
                {dataKeys.map((key, i) => (
                  <Area
                    key={key}
                    type="monotone"
                    dataKey={key}
                    stroke={palette[i % palette.length]}
                    strokeWidth={2}
                    fill={`url(#grad-${title}-${i})`}
                    connectNulls
                  />
                ))}
              </AreaChart>
            )}
          </ResponsiveContainer>
        )}
      </div>
      {unit && <p className="mt-1 text-right text-[11px] text-text-muted">{unit}</p>}
    </GlassCard>
  );
}
