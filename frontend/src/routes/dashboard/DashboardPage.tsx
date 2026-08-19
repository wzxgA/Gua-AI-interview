import { useTranslation } from 'react-i18next';
import {
  Activity,
  BarChart3,
  Briefcase,
  CheckCircle2,
  ClipboardList,
  FileQuestion,
  FileText,
  Sparkles,
} from 'lucide-react';
import { GlassCard } from '@/components/ui/glass-card';
import { useHealth } from '@/api/health';
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
  const { data: health } = useHealth();
  const { data: positions } = usePositionList({ page: 1, size: 1 });
  const { data: questions } = useQuestionList({ page: 1, size: 1 });
  const { data: resumes } = useResumeList({ page: 1, size: 1 });
  const { data: stats } = useDashboardStats();

  const isUp = health?.status === 'UP';

  const totalSessions =
    stats?.statusCounts.reduce((sum, item) => sum + item.count, 0) ?? 0;
  const inProgress =
    stats?.statusCounts.find((item) => item.status === 'IN_PROGRESS')?.count ?? 0;
  const completed =
    stats?.statusCounts.find((item) => item.status === 'COMPLETED')?.count ?? 0;
  const avgScore = stats?.scoreStats.avgScore;

  const kpis = [
    {
      label: t('dashboard.backendStatus'),
      value: isUp ? t('dashboard.online') : t('dashboard.offline'),
      icon: Activity,
      color: isUp ? 'text-success' : 'text-danger',
    },
    {
      label: t('dashboard.interviewTotal'),
      value: totalSessions,
      icon: ClipboardList,
      color: 'text-sky-300',
    },
    {
      label: t('dashboard.inProgress'),
      value: inProgress,
      icon: Sparkles,
      color: 'text-amber-300',
    },
    {
      label: t('dashboard.completed'),
      value: completed,
      icon: CheckCircle2,
      color: 'text-emerald-400',
    },
    {
      label: t('dashboard.avgScore'),
      value: avgScore != null ? avgScore.toFixed(2) : '-',
      icon: BarChart3,
      color: 'text-silver-200',
    },
  ];

  const overview = [
    {
      label: t('dashboard.positionCount'),
      value: positions?.total ?? '-',
      icon: Briefcase,
      color: 'text-silver-200',
    },
    {
      label: t('dashboard.questionCount'),
      value: questions?.total ?? '-',
      icon: FileQuestion,
      color: 'text-silver-200',
    },
    {
      label: t('dashboard.resumeCount'),
      value: resumes?.total ?? '-',
      icon: FileText,
      color: 'text-silver-200',
    },
  ];

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-semibold text-text-primary">{t('dashboard.title')}</h2>
        <p className="mt-1 text-sm text-text-muted">{t('dashboard.subtitle')}</p>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 md:grid-cols-3 xl:grid-cols-5">
        {kpis.map((stat) => (
          <GlassCard key={stat.label} hover className="p-5">
            <div className="flex items-center justify-between">
              <span className="text-sm text-text-muted">{stat.label}</span>
              <stat.icon className={`h-4 w-4 ${stat.color}`} />
            </div>
            <p className="mt-2 text-2xl font-semibold text-text-primary">{stat.value}</p>
          </GlassCard>
        ))}
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        {overview.map((stat) => (
          <GlassCard key={stat.label} className="p-5">
            <div className="flex items-center justify-between">
              <span className="text-sm text-text-muted">{stat.label}</span>
              <stat.icon className={`h-4 w-4 ${stat.color}`} />
            </div>
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
