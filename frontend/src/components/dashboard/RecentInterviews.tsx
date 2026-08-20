import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ChevronRight } from 'lucide-react';
import { GlassCard } from '@/components/ui/glass-card';
import { StatusBadge } from '@/components/ui/status-dot';
import { formatDate } from '@/lib/utils';
import type { DashboardStats } from '@/types/dashboard';

interface RecentInterviewsProps {
  data: DashboardStats['recentInterviews'];
}

export function RecentInterviews({ data }: RecentInterviewsProps) {
  const { t } = useTranslation();

  if (data.length === 0) {
    return (
      <GlassCard className="flex h-full flex-col p-5">
        <h3 className="text-sm font-medium text-text-muted">{t('dashboard.recentInterviews')}</h3>
        <div className="flex flex-1 items-center justify-center">
          <span className="text-sm text-text-muted">{t('dashboard.noInterviews')}</span>
        </div>
      </GlassCard>
    );
  }

  return (
    <GlassCard className="flex h-full flex-col p-5">
      <h3 className="mb-3 text-sm font-medium text-text-muted">{t('dashboard.recentInterviews')}</h3>
      <ul className="flex-1 space-y-1">
        {data.map((item) => (
          <li key={item.id}>
            <Link
              to={`/interviews/${item.id}`}
              className="group flex items-center gap-3 rounded-lg px-2 py-2 text-sm transition-colors hover:bg-surface-hover"
            >
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  <span className="truncate font-medium text-text-primary">
                    {item.candidateName ?? '-'}
                  </span>
                  {item.positionTitle && (
                    <span className="truncate text-xs text-text-muted">{item.positionTitle}</span>
                  )}
                </div>
                <div className="mt-0.5 flex items-center gap-2">
                  <StatusBadge status={item.status} />
                  <span className="text-xs text-text-muted">{formatDate(item.createdAt)}</span>
                </div>
              </div>
              <div className="flex items-center gap-2">
                {item.totalScore != null && (
                  <span className="rounded-md bg-surface-hover px-2 py-0.5 font-mono text-xs font-semibold text-sky-400">
                    {item.totalScore.toFixed(1)}
                    <span className="text-text-muted">/5</span>
                  </span>
                )}
                <ChevronRight className="h-4 w-4 text-text-muted transition-transform group-hover:translate-x-0.5 group-hover:text-sky-400" />
              </div>
            </Link>
          </li>
        ))}
      </ul>
    </GlassCard>
  );
}
