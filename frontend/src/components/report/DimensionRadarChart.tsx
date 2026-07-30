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

interface DimensionRadarChartProps {
  scores: Partial<Record<EvaluationDimension, number>>;
}

export function DimensionRadarChart({ scores }: DimensionRadarChartProps) {
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
            <PolarGrid stroke="rgba(200,212,232,0.1)" />
            <PolarAngleAxis
              dataKey="dimension"
              tick={{ fill: 'rgba(200,212,232,0.6)', fontSize: 12 }}
            />
            <PolarRadiusAxis
              domain={[0, 5]}
              tick={{ fill: 'rgba(200,212,232,0.3)', fontSize: 10 }}
              axisLine={false}
            />
            <Radar
              dataKey="score"
              stroke="rgba(56,189,248,0.8)"
              fill="rgba(56,189,248,0.2)"
              strokeWidth={2}
            />
          </RadarChart>
        </ResponsiveContainer>
      </div>
    </GlassCard>
  );
}
