import { useLocation } from 'react-router-dom';
import { useHealth } from '@/api/health';
import { useAuth } from '@/contexts/AuthContext';
import { cn } from '@/lib/utils';
import { ThemeToggle } from '@/components/ui/ThemeToggle';
import { LogOut } from 'lucide-react';

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
  const { user, logout } = useAuth();

  return (
    <header className="sticky top-0 z-10 flex h-14 items-center justify-between border-b border-border-subtle bg-space-800/50 px-8 backdrop-blur-xl">
      <h1 className="text-base font-semibold text-text-primary">{title}</h1>
      <div className="flex items-center gap-4">
        <ThemeToggle />
        <span className="rounded-full border border-border-default bg-surface-hover px-2 py-0.5 text-xs text-text-muted">
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
        {user && (
          <div className="flex items-center gap-3 border-l border-border-subtle pl-4">
            <span className="text-xs text-text-secondary">
              {user.displayName}
              <span className="ml-1.5 rounded bg-surface-hover px-1.5 py-0.5 text-[10px] text-text-muted">
                {user.role}
              </span>
            </span>
            <button
              onClick={logout}
              className="text-text-muted transition-colors hover:text-text-primary"
              title="退出登录"
            >
              <LogOut className="h-4 w-4" />
            </button>
          </div>
        )}
      </div>
    </header>
  );
}
