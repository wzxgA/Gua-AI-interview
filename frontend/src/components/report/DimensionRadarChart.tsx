import { useId } from 'react';
import {
  Radar,
  RadarChart,
  PolarGrid,
  PolarAngleAxis,
  PolarRadiusAxis,
  ResponsiveContainer,
  Tooltip,
  type TooltipProps,
} from 'recharts';
import { useTranslation } from 'react-i18next';
import { DIMENSION_CONFIG, type EvaluationDimension } from '@/types/report';
import { GlassCard } from '@/components/ui/glass-card';
import { useTheme } from '@/contexts/ThemeContext';

interface DimensionRadarChartProps {
  scores: Partial<Record<EvaluationDimension, number>>;
}

interface RadarDataItem {
  dimension: string;
  score: number;
}

export function DimensionRadarChart({ scores }: DimensionRadarChartProps) {
  const { t } = useTranslation();
  const { resolvedTheme } = useTheme();
  const isDark = resolvedTheme === 'dark';

  // 唯一 ID（useId 含冒号，去除以安全用于 SVG url(#id)）
  const gradId = useId().replace(/:/g, 'radar-grad');
  const glowId = useId().replace(/:/g, 'radar-glow');
  const shadowId = useId().replace(/:/g, 'radar-shadow');

  const gridColor = isDark ? 'rgba(220,224,230,0.1)' : 'rgba(0,0,0,0.08)';
  const axisColor = isDark ? 'rgba(220,224,230,0.6)' : 'rgba(0,0,0,0.5)';
  const strokeColor = isDark ? 'rgba(56,189,248,0.9)' : 'rgba(2,132,199,0.95)';
  const glowStroke = isDark ? 'rgba(56,189,248,0.45)' : 'rgba(2,132,199,0.4)';
  // 白天模式填充更亮更饱和，避免深色叠加导致整体发暗
  const fillCenter = isDark ? 'rgba(56,189,248,0.55)' : 'rgba(14,165,233,0.55)';
  const fillEdge = isDark ? 'rgba(56,189,248,0.05)' : 'rgba(14,165,233,0.08)';
  const dotColor = isDark ? '#7dd3fc' : '#0284c7';
  const tooltipBg = isDark ? 'var(--space-700)' : '#ffffff';
  const tooltipBorder = isDark ? 'var(--border-default)' : 'rgba(0,0,0,0.1)';
  // 底部投影：白天模式用浅蓝灰且低不透明度，避免压暗图表
  const shadowFill = isDark ? '#0b1220' : '#7ea8d9';
  const shadowOpacity = isDark ? 0.45 : 0.18;
  const shadowDrop = isDark ? 0.35 : 0.18;

  const data: RadarDataItem[] = (Object.keys(DIMENSION_CONFIG) as EvaluationDimension[]).map(
    (dim) => ({
      dimension: t(`interviews.dimension.${dim}`),
      score: scores[dim] ?? 0,
    }),
  );

  /** hover 提示：维度名 + 分数 */
  const renderTooltip = ({ active, payload }: TooltipProps<number, string>) => {
    if (!active || !payload || payload.length === 0) return null;
    const item = payload[0].payload as RadarDataItem;
    return (
      <div
        className="rounded-lg border px-3 py-1.5 text-xs shadow-lg"
        style={{ background: tooltipBg, borderColor: tooltipBorder }}
      >
        <span className="text-text-secondary">{item.dimension}</span>
        <span className="ml-2 font-semibold text-sky-400">{item.score.toFixed(1)} / 5</span>
      </div>
    );
  };

  return (
    <GlassCard className="p-6">
      <h3 className="mb-4 text-sm font-medium text-text-muted">{t('interviews.radarChartTitle')}</h3>
      <div className="h-64 w-full">
        <ResponsiveContainer width="100%" height="100%">
          <RadarChart data={data} outerRadius="72%">
            <defs>
              {/* 径向渐变：中心亮 → 边缘淡，营造立体光感 */}
              <radialGradient id={gradId} cx="50%" cy="50%" r="55%">
                <stop offset="0%" stopColor={fillCenter} />
                <stop offset="100%" stopColor={fillEdge} />
              </radialGradient>
              {/* 边缘辉光滤镜 */}
              <filter id={glowId} x="-40%" y="-40%" width="180%" height="180%">
                <feGaussianBlur stdDeviation="4" />
              </filter>
              {/* 底部投影滤镜 */}
              <filter id={shadowId} x="-20%" y="-20%" width="140%" height="170%">
                <feDropShadow dx="0" dy="5" stdDeviation="6" floodColor="#000000" floodOpacity={shadowDrop} />
              </filter>
            </defs>

            <PolarGrid stroke={gridColor} />
            <PolarAngleAxis
              dataKey="dimension"
              tick={{ fill: axisColor, fontSize: 12 }}
            />
            {/* 去掉半径刻度数字（0/1/.../5） */}
            <PolarRadiusAxis
              domain={[0, 5]}
              tick={false}
              axisLine={false}
            />

            {/* 底部投影层：半透明填充 + 模糊投影，增强立体厚度 */}
            <Radar
              dataKey="score"
              stroke="none"
              fill={shadowFill}
              fillOpacity={shadowOpacity}
              filter={`url(#${shadowId})`}
              isAnimationActive={false}
            />
            {/* 辉光层：粗半透明描边 + 高斯模糊 */}
            <Radar
              dataKey="score"
              stroke={glowStroke}
              strokeWidth={6}
              fill="none"
              filter={`url(#${glowId})`}
              isAnimationActive={false}
            />
            {/* 主层：径向渐变填充 + 数据点高光 + hover 放大 */}
            <Radar
              dataKey="score"
              stroke={strokeColor}
              strokeWidth={2}
              fill={`url(#${gradId})`}
              animationDuration={500}
              dot={{ r: 3, fill: dotColor, strokeWidth: 0 }}
              activeDot={{ r: 6, fill: dotColor, stroke: isDark ? '#0b1220' : '#ffffff', strokeWidth: 2 }}
            />

            <Tooltip content={renderTooltip} cursor={false} />
          </RadarChart>
        </ResponsiveContainer>
      </div>
    </GlassCard>
  );
}
