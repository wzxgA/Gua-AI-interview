import { useTranslation } from 'react-i18next';
import { GlassCard } from '@/components/ui/glass-card';
import { usePositionList } from '@/api/positions';
import { useQuestionList } from '@/api/questions';
import { useResumeList } from '@/api/resumes';
import { useDashboardStats } from '@/api/dashboard';
import { StatusDistributionChart } from '@/components/dashboard/StatusDistributionChart';
import { TrendChart } from '@/components/dashboard/TrendChart';
import { ScoreDistributionChart } from '@/components/dashboard/ScoreDistributionChart';
import { RecentInterviews } from '@/components/dashboard/RecentInterviews';

export function DashboardPage() {
  const { t } = useTranslation();
  const { data: positions } = usePositionList({ page: 1, size: 1 });
  const { data: questions } = useQuestionList({ page: 1, size: 1 });
  const { data: resumes } = useResumeList({ page: 1, size: 1 });
  const { data: stats } = useDashboardStats();

  const totalSessions =
    stats?.statusCounts.reduce((sum, item) => sum + item.count, 0) ?? 0;
  const inProgress =
    stats?.statusCounts.find((item) => item.status === 'IN_PROGRESS')?.count ?? 0;
  const completed =
    stats?.statusCounts.find((item) => item.status === 'COMPLETED')?.count ?? 0;
  const avgScore = stats?.scoreStats.avgScore;

  const kpis = [
    {
      label: t('dashboard.interviewTotal'),
      value: totalSessions,
    },
    {
      label: t('dashboard.inProgress'),
      value: inProgress,
    },
    {
      label: t('dashboard.completed'),
      value: completed,
    },
    {
      label: t('dashboard.avgScore'),
      value: avgScore != null ? avgScore.toFixed(2) : '-',
    },
  ];

  const overview = [
    {
      label: t('dashboard.positionCount'),
      value: positions?.total ?? '-',
    },
    {
      label: t('dashboard.questionCount'),
      value: questions?.total ?? '-',
    },
    {
      label: t('dashboard.resumeCount'),
      value: resumes?.total ?? '-',
    },
  ];

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-semibold text-text-primary">{t('dashboard.title')}</h2>
        <p className="mt-1 text-sm text-text-muted">{t('dashboard.subtitle')}</p>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {kpis.map((stat) => (
          <GlassCard key={stat.label} hover className="p-5">
            <p className="text-sm text-text-muted">{stat.label}</p>
            <p className="mt-2 text-2xl font-semibold text-text-primary">{stat.value}</p>
          </GlassCard>
        ))}
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        {overview.map((stat) => (
          <GlassCard key={stat.label} className="p-5">
            <p className="text-sm text-text-muted">{stat.label}</p>
            <p className="mt-2 text-2xl font-semibold text-text-primary">{stat.value}</p>
          </GlassCard>
        ))}
      </div>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <StatusDistributionChart data={stats?.statusCounts ?? []} total={totalSessions} />
        <TrendChart data={stats?.dailyTrend ?? []} />
      </div>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <ScoreDistributionChart />
        <RecentInterviews data={stats?.recentInterviews ?? []} />
      </div>
    </div>
  );
}
