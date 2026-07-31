import {
  Radar,
  RadarChart,
  PolarGrid,
  PolarAngleAxis,
  PolarRadiusAxis,
  ResponsiveContainer,
} from 'recharts';
import { DIMENSION_CONFIG, type EvaluationDimension } from '@/types/report';
import { GlassCard } from '@/components/ui/glass-card';
import { useTheme } from '@/contexts/ThemeContext';

interface DimensionRadarChartProps {
  scores: Partial<Record<EvaluationDimension, number>>;
}

export function DimensionRadarChart({ scores }: DimensionRadarChartProps) {
  const { resolvedTheme } = useTheme();
  const isDark = resolvedTheme === 'dark';

  const gridColor = isDark ? 'rgba(200,212,232,0.1)' : 'rgba(0,0,0,0.08)';
  const axisColor = isDark ? 'rgba(200,212,232,0.6)' : 'rgba(0,0,0,0.5)';
  const radiusColor = isDark ? 'rgba(200,212,232,0.3)' : 'rgba(0,0,0,0.2)';
  const strokeColor = isDark ? 'rgba(56,189,248,0.8)' : 'rgba(2,132,199,0.9)';
  const fillColor = isDark ? 'rgba(56,189,248,0.2)' : 'rgba(2,132,199,0.15)';

  const data = (Object.keys(DIMENSION_CONFIG) as EvaluationDimension[]).map(
    (dim) => ({
      dimension: DIMENSION_CONFIG[dim].label,
      score: scores[dim] ?? 0,
    }),
  );

  return (
    <GlassCard className="p-6">
      <h3 className="mb-4 text-sm font-medium text-text-muted">维度雷达图</h3>
      <div className="h-64 w-full">
        <ResponsiveContainer width="100%" height="100%">
          <RadarChart data={data}>
            <PolarGrid stroke={gridColor} />
            <PolarAngleAxis
              dataKey="dimension"
              tick={{ fill: axisColor, fontSize: 12 }}
            />
            <PolarRadiusAxis
              domain={[0, 5]}
              tick={{ fill: radiusColor, fontSize: 10 }}
              axisLine={false}
            />
            <Radar
              dataKey="score"
              stroke={strokeColor}
              fill={fillColor}
              strokeWidth={2}
            />
          </RadarChart>
        </ResponsiveContainer>
      </div>
    </GlassCard>
  );
}
