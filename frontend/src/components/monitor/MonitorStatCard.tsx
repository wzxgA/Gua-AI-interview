import { GlassCard } from '@/components/ui/glass-card';

export function MonitorStatCard({
  label,
  value,
  hint,
}: {
  label: string;
  value: string;
  hint?: string;
}) {
  return (
    <GlassCard hover className="flex flex-col gap-1 p-5">
      <span className="text-xs text-text-muted">{label}</span>
      <span className="text-2xl font-semibold tabular-nums text-text-primary">{value}</span>
      {hint && <span className="text-[11px] text-text-muted">{hint}</span>}
    </GlassCard>
  );
}
