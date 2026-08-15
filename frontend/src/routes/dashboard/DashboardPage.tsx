import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Briefcase, FileQuestion, FileText, Search, Activity } from 'lucide-react';
import { GlassCard } from '@/components/ui/glass-card';
import { useHealth } from '@/api/health';
import { usePositionList } from '@/api/positions';
import { useQuestionList } from '@/api/questions';
import { useResumeList } from '@/api/resumes';

export function DashboardPage() {
  const { t } = useTranslation();
  const { data: health } = useHealth();
  const { data: positions } = usePositionList({ page: 1, size: 1 });
  const { data: questions } = useQuestionList({ page: 1, size: 1 });
  const { data: resumes } = useResumeList({ page: 1, size: 1 });

  const isUp = health?.status === 'UP';

  const stats = [
    { label: t('dashboard.backendStatus'), value: isUp ? t('dashboard.online') : t('dashboard.offline'), icon: Activity, color: isUp ? 'text-success' : 'text-danger' },
    { label: t('dashboard.positionCount'), value: positions?.total ?? '-', icon: Briefcase, color: 'text-silver-200' },
    { label: t('dashboard.questionCount'), value: questions?.total ?? '-', icon: FileQuestion, color: 'text-silver-200' },
    { label: t('dashboard.resumeCount'), value: resumes?.total ?? '-', icon: FileText, color: 'text-silver-200' },
  ];

  const shortcuts = [
    { to: '/positions', label: t('sidebar.menu.positions'), icon: Briefcase },
    { to: '/questions', label: t('sidebar.menu.questions'), icon: FileQuestion },
    { to: '/resumes', label: t('sidebar.menu.resumes'), icon: FileText },
    { to: '/rag', label: t('sidebar.menu.rag'), icon: Search },
  ];

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-semibold text-text-primary">{t('dashboard.title')}</h2>
        <p className="mt-1 text-sm text-text-muted">{t('dashboard.subtitle')}</p>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {stats.map((stat) => (
          <GlassCard key={stat.label} hover className="p-5">
            <div className="flex items-center justify-between">
              <span className="text-sm text-text-muted">{stat.label}</span>
              <stat.icon className={`h-4 w-4 ${stat.color}`} />
            </div>
            <p className="mt-2 text-2xl font-semibold text-text-primary">{stat.value}</p>
          </GlassCard>
        ))}
      </div>

      <div>
        <h3 className="mb-3 text-sm font-medium text-text-secondary">{t('dashboard.shortcuts')}</h3>
        <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          {shortcuts.map((item) => (
            <Link key={item.to} to={item.to}>
              <GlassCard hover className="flex flex-col items-center gap-3 p-6">
                <item.icon className="h-6 w-6 text-silver-300" />
                <span className="text-sm text-text-secondary">{item.label}</span>
              </GlassCard>
            </Link>
          ))}
        </div>
      </div>
    </div>
  );
}
