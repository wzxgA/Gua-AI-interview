import { useTranslation } from 'react-i18next';
import { GlassCard } from '@/components/ui/glass-card';

export function PageHeader({
  title,
  subtitle,
  action,
}: {
  title: string;
  subtitle?: string;
  action?: React.ReactNode;
}) {
  return (
    <div className="mb-6 flex items-center justify-between">
      <div>
        <h2 className="text-xl font-semibold text-text-primary">{title}</h2>
        {subtitle && <p className="mt-1 text-sm text-text-muted">{subtitle}</p>}
      </div>
      {action}
    </div>
  );
}

export function EmptyState({ message }: { message: string }) {
  return (
    <GlassCard className="flex items-center justify-center py-16">
      <p className="text-sm text-text-muted">{message}</p>
    </GlassCard>
  );
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  const { t } = useTranslation();
  return (
    <GlassCard className="flex flex-col items-center justify-center gap-4 py-16">
      <p className="text-sm text-danger">{message}</p>
      {onRetry && (
        <button
          onClick={onRetry}
          className="text-xs text-silver-300 hover:text-silver-100 transition-colors"
        >
          {t('common.retry')}
        </button>
      )}
    </GlassCard>
  );
}
