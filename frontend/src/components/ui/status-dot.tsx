import { useTranslation } from 'react-i18next';
import { cn } from '@/lib/utils';

const config: Record<string, { color: string; labelKey: string; pulse?: boolean }> = {
  CREATED: { color: 'bg-slate-400', labelKey: 'common.sessionStatus.CREATED' },
  PLANNING: { color: 'bg-silver-300', labelKey: 'common.sessionStatus.PLANNING', pulse: true },
  IN_PROGRESS: { color: 'bg-silver-200', labelKey: 'common.sessionStatus.IN_PROGRESS', pulse: true },
  EVALUATING: { color: 'bg-amber-300', labelKey: 'common.sessionStatus.EVALUATING', pulse: true },
  REPORTING: { color: 'bg-sky-300', labelKey: 'common.sessionStatus.REPORTING', pulse: true },
  COMPLETED: { color: 'bg-silver-100', labelKey: 'common.sessionStatus.COMPLETED' },
  PAUSED: { color: 'bg-silver-dim', labelKey: 'common.sessionStatus.PAUSED' },
  CANCELLED: { color: 'bg-slate-500', labelKey: 'common.sessionStatus.CANCELLED' },
  FAILED: { color: 'bg-danger', labelKey: 'common.sessionStatus.FAILED' },
  PENDING: { color: 'bg-amber-400', labelKey: 'common.parseStatus.PENDING', pulse: true },
  PARSED: { color: 'bg-success', labelKey: 'common.parseStatus.PARSED' },
  FAILED_RESUME: { color: 'bg-danger', labelKey: 'common.parseStatus.FAILED' },
  ACTIVE: { color: 'bg-success', labelKey: 'common.positionStatus.ACTIVE' },
  INACTIVE: { color: 'bg-slate-500', labelKey: 'common.positionStatus.INACTIVE' },
};

export function StatusDot({ status, label }: { status: string; label?: string }) {
  const { t } = useTranslation();
  const cfg = config[status] ?? { color: 'bg-slate-500', labelKey: '' };
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
        {label ?? (cfg.labelKey ? t(cfg.labelKey) : status)}
      </span>
    </span>
  );
}

export function StatusBadge({ status, label }: { status: string; label?: string }) {
  const { t } = useTranslation();
  const cfg = config[status] ?? { color: 'bg-slate-500', labelKey: '' };
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs',
        'border border-border-default bg-surface-hover',
      )}
    >
      <span className={cn('h-1.5 w-1.5 rounded-full', cfg.color, cfg.pulse && 'animate-pulse-slow')} />
      {label ?? (cfg.labelKey ? t(cfg.labelKey) : status)}
    </span>
  );
}
