import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { useTranslation } from 'react-i18next';
import { GlassCard } from '@/components/ui/glass-card';
import { useTheme } from '@/contexts/ThemeContext';
import type { DashboardStats } from '@/types/dashboard';

interface TrendChartProps {
  data: DashboardStats['dailyTrend'];
}

/** yyyy-MM-dd → MM-dd */
function shortDate(date: string) {
  return date.length >= 10 ? date.slice(5) : date;
}

export function TrendChart({ data }: TrendChartProps) {
  const { t } = useTranslation();
  const { resolvedTheme } = useTheme();
  const isDark = resolvedTheme === 'dark';

  const isEmpty = data.every((item) => item.count <= 0);
  const chartData = data.map((item) => ({ ...item, label: shortDate(item.date) }));
  const chartLineColor = isDark ? 'rgba(56,189,248,0.85)' : 'rgba(2,132,199,0.9)';
  const gridColor = isDark ? 'rgba(200,212,232,0.1)' : 'rgba(0,0,0,0.06)';
  const axisColor = isDark ? 'rgba(200,212,232,0.55)' : 'rgba(0,0,0,0.45)';

  return (
    <GlassCard className="p-5">
      <h3 className="text-sm font-medium text-text-muted">{t('dashboard.trend30d')}</h3>
      <div className="h-52 w-full pt-2">
        {isEmpty ? (
          <div className="flex h-full w-full items-center justify-center">
            <span className="text-sm text-text-muted">{t('dashboard.noData')}</span>
          </div>
        ) : (
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={chartData} margin={{ top: 4, right: 8, bottom: 0, left: -16 }}>
              <defs>
                <linearGradient id="trendFill" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor={chartLineColor} stopOpacity={0.35} />
                  <stop offset="100%" stopColor={chartLineColor} stopOpacity={0.02} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke={gridColor} vertical={false} />
              <XAxis
                dataKey="label"
                tick={{ fill: axisColor, fontSize: 10 }}
                tickLine={false}
                axisLine={{ stroke: gridColor }}
                minTickGap={24}
              />
              <YAxis
                allowDecimals={false}
                tick={{ fill: axisColor, fontSize: 10 }}
                tickLine={false}
                axisLine={false}
              />
              <Tooltip
                formatter={(value: number | string) => [value, t('dashboard.unit')]}
                labelFormatter={(label) => String(label)}
                contentStyle={{
                  background: 'var(--surface-overlay)',
                  border: '1px solid var(--silver-400)',
                  borderRadius: 8,
                  fontSize: 12,
                }}
              />
              <Area
                type="monotone"
                dataKey="count"
                stroke={chartLineColor}
                strokeWidth={2}
                fill="url(#trendFill)"
              />
            </AreaChart>
          </ResponsiveContainer>
        )}
      </div>
    </GlassCard>
  );
}
