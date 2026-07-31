import { cn } from '@/lib/utils';

const config: Record<string, { color: string; label: string; pulse?: boolean }> = {
  CREATED: { color: 'bg-slate-400', label: '待开始' },
  PLANNING: { color: 'bg-silver-300', label: '规划中', pulse: true },
  IN_PROGRESS: { color: 'bg-silver-200', label: '进行中', pulse: true },
  EVALUATING: { color: 'bg-amber-300', label: '评估中', pulse: true },
  REPORTING: { color: 'bg-sky-300', label: '报告中', pulse: true },
  COMPLETED: { color: 'bg-silver-100', label: '已完成' },
  PAUSED: { color: 'bg-silver-dim', label: '已暂停' },
  CANCELLED: { color: 'bg-slate-500', label: '已取消' },
  FAILED: { color: 'bg-danger', label: '失败' },
  PENDING: { color: 'bg-amber-400', label: '解析中', pulse: true },
  PARSED: { color: 'bg-success', label: '已解析' },
  FAILED_RESUME: { color: 'bg-danger', label: '解析失败' },
  ACTIVE: { color: 'bg-success', label: '活跃' },
  INACTIVE: { color: 'bg-slate-500', label: '停用' },
};

export function StatusDot({ status, label }: { status: string; label?: string }) {
  const cfg = config[status] ?? { color: 'bg-slate-500', label: status };
  return (
    <span className="inline-flex items-center gap-2">
      <span
        className={cn(
          'h-2 w-2 rounded-full',
          cfg.color,
          cfg.pulse && 'animate-pulse-slow',
        )}
      />
      <span className="text-xs text-text-secondary">
        {label ?? cfg.label}
      </span>
    </span>
  );
}

export function StatusBadge({ status, label }: { status: string; label?: string }) {
  const cfg = config[status] ?? { color: 'bg-slate-500', label: status };
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs',
        'border border-border-default bg-surface-hover',
      )}
    >
      <span className={cn('h-1.5 w-1.5 rounded-full', cfg.color, cfg.pulse && 'animate-pulse-slow')} />
      {label ?? cfg.label}
    </span>
  );
}
