import { Link } from 'react-router-dom';
import { Briefcase, FileQuestion, FileText, Search, Activity } from 'lucide-react';
import { GlassCard } from '@/components/ui/glass-card';
import { useHealth } from '@/api/health';
import { usePositionList } from '@/api/positions';
import { useQuestionList } from '@/api/questions';
import { useResumeList } from '@/api/resumes';

export function DashboardPage() {
  const { data: health } = useHealth();
  const { data: positions } = usePositionList({ page: 1, size: 1 });
  const { data: questions } = useQuestionList({ page: 1, size: 1 });
  const { data: resumes } = useResumeList({ page: 1, size: 1 });

  const isUp = health?.status === 'UP';

  const stats = [
    { label: '后端状态', value: isUp ? '在线' : '离线', icon: Activity, color: isUp ? 'text-success' : 'text-danger' },
    { label: '岗位总数', value: positions?.total ?? '-', icon: Briefcase, color: 'text-silver-200' },
    { label: '题目总数', value: questions?.total ?? '-', icon: FileQuestion, color: 'text-silver-200' },
    { label: '简历总数', value: resumes?.total ?? '-', icon: FileText, color: 'text-silver-200' },
  ];

  const shortcuts = [
    { to: '/positions', label: '岗位管理', icon: Briefcase },
    { to: '/questions', label: '题库管理', icon: FileQuestion },
    { to: '/resumes', label: '简历管理', icon: FileText },
    { to: '/rag', label: 'RAG 调试', icon: Search },
  ];

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-semibold text-text-primary">平台概览</h2>
        <p className="mt-1 text-sm text-text-muted">AI 智能面试 Agent 平台 · 管理端</p>
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
        <h3 className="mb-3 text-sm font-medium text-text-secondary">快捷入口</h3>
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
