import { useMemo } from 'react';
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import { useTranslation } from 'react-i18next';
import { GlassCard } from '@/components/ui/glass-card';
import { StatusDot } from '@/components/ui/status-dot';
import { useTheme } from '@/contexts/ThemeContext';
import type { DashboardStats } from '@/types/dashboard';

interface StatusDistributionChartProps {
  data: DashboardStats['statusCounts'];
  total: number;
}

/** 与 StatusDot 语义一致、适配深浅主题的状态色板 */
const STATUS_COLORS: Record<string, { light: string; dark: string }> = {
  CREATED: { light: '#94a3b8', dark: '#64748b' },
  PLANNING: { light: '#a8b4c8', dark: '#6b7890' },
  IN_PROGRESS: { light: '#d4dce8', dark: '#3d4a6b' },
  EVALUATING: { light: '#ffc24d', dark: '#d97e0d' },
  REPORTING: { light: '#7dd3fc', dark: '#38bdf8' },
  COMPLETED: { light: '#0d9d5e', dark: '#5dffac' },
  PAUSED: { light: '#6b7890', dark: '#aab2c8' },
  CANCELLED: { light: '#64748b', dark: '#475569' },
  FAILED: { light: '#d6377e', dark: '#ff5ea8' },
};

export function StatusDistributionChart({ data, total }: StatusDistributionChartProps) {
  const { t } = useTranslation();
  const { resolvedTheme } = useTheme();
  const isDark = resolvedTheme === 'dark';

  const isEmpty = total <= 0;
  const items = useMemo(() => data.filter((item) => item.count > 0), [data]);

  return (
    <GlassCard className="p-5">
      <h3 className="text-sm font-medium text-text-muted">{t('dashboard.statusDistribution')}</h3>
      <div className="flex flex-col items-center gap-4 pt-2 sm:flex-row">
        <div className="relative h-48 w-48 shrink-0">
          {isEmpty ? (
            <div className="flex h-full w-full items-center justify-center">
              <span className="text-sm text-text-muted">{t('dashboard.noData')}</span>
            </div>
          ) : (
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={items}
                  dataKey="count"
                  nameKey="status"
                  cx="50%"
                  cy="50%"
                  innerRadius={58}
                  outerRadius={80}
                  paddingAngle={2}
                  strokeWidth={0}
                >
                  {items.map((item) => {
                    const colors = STATUS_COLORS[item.status];
                    return (
                      <Cell
                        key={item.status}
                        fill={isDark ? colors?.dark : colors?.light}
                      />
                    );
                  })}
                </Pie>
                <Tooltip
                  cursor={{ fill: 'transparent' }}
                  content={({ active, payload }) => {
                    if (!active || !payload?.length) return null;
                    const value = payload[0]?.value as number | undefined;
                    if (value == null) return null;
                    return (
                      <div className="rounded-lg border border-silver-400 bg-surface-overlay px-2.5 py-1 text-sm font-semibold text-text-primary shadow-lg">
                        {value}
                      </div>
                    );
                  }}
                />
              </PieChart>
            </ResponsiveContainer>
          )}
          {!isEmpty && (
            <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
              <span className="text-2xl font-semibold text-text-primary">{total}</span>
              <span className="text-xs text-text-muted">{t('dashboard.unit')}</span>
            </div>
          )}
        </div>
        <ul className="flex-1 space-y-1.5">
          {data.map((item) => (
            <li key={item.status} className="flex items-center justify-between gap-2 text-sm">
              <StatusDot status={item.status} />
              <span className="font-medium text-text-primary">{item.count}</span>
            </li>
          ))}
        </ul>
      </div>
    </GlassCard>
  );
}
