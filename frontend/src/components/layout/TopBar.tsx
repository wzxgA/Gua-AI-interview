import { useLocation } from 'react-router-dom';
import { useHealth } from '@/api/health';
import { cn } from '@/lib/utils';

const pageTitles: Record<string, string> = {
  '/': 'Dashboard',
  '/positions': '岗位管理',
  '/questions': '题库管理',
  '/resumes': '简历管理',
  '/rag': 'RAG 检索调试',
  '/settings': '设置',
};

export function TopBar() {
  const { data: health } = useHealth();
  const isUp = health?.status === 'UP';
  const location = useLocation();
  const path = location.pathname;
  const title = pageTitles[path] ?? pageTitles[Object.keys(pageTitles).find((k) => path.startsWith(k) && k !== '/') ?? '/'] ?? 'AIMS';

  return (
    <header className="sticky top-0 z-10 flex h-14 items-center justify-between border-b border-white/5 bg-space-800/50 px-8 backdrop-blur-xl">
      <h1 className="text-base font-semibold text-text-primary">{title}</h1>
      <div className="flex items-center gap-4">
        <span className="rounded-full border border-white/10 bg-white/5 px-2 py-0.5 text-xs text-text-muted">
          local
        </span>
        <span className="flex items-center gap-2 text-xs text-text-secondary">
          <span
            className={cn(
              'h-2 w-2 rounded-full',
              isUp ? 'bg-success animate-pulse-slow' : 'bg-danger',
            )}
          />
          {isUp ? '后端在线' : '后端离线'}
        </span>
      </div>
    </header>
  );
}
