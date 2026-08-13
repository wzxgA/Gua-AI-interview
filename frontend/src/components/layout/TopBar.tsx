import { useState } from 'react';
import { useLocation } from 'react-router-dom';
import { useHealth } from '@/api/health';
import { useAuth } from '@/contexts/AuthContext';
import { cn } from '@/lib/utils';
import { ThemeToggle } from '@/components/ui/ThemeToggle';
import { GlassCard } from '@/components/ui/glass-card';
import { SilverButton } from '@/components/ui/silver-button';
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
  const title = pageTitles[path] ?? pageTitles[Object.keys(pageTitles).find((k) => path.startsWith(k) && k !== '/') ?? '/'] ?? '瓜分Offer';
  const { user, logout } = useAuth();
  const [logoutConfirmOpen, setLogoutConfirmOpen] = useState(false);

  return (
    <>
      <header className="sticky top-0 z-10 flex h-14 items-center justify-between border-b border-border-subtle bg-space-800/50 px-8 backdrop-blur-xl">
      <h1 className="text-base font-semibold text-text-primary">{title}</h1>
      <div className="flex items-center gap-4">
        <ThemeToggle />
        <span className="flex items-center gap-2 text-xs text-text-secondary">
          <span
            className={cn(
              'h-2 w-2 rounded-full',
              isUp ? 'bg-success animate-pulse-slow' : 'bg-danger',
            )}
          />
          {isUp ? '服务在线' : '服务离线'}
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
              onClick={() => setLogoutConfirmOpen(true)}
              className="text-text-muted transition-colors hover:text-text-primary"
              title="退出登录"
            >
              <LogOut className="h-4 w-4" />
            </button>
          </div>
        )}
      </div>
      </header>

      {/* 退出登录确认弹窗 */}
      {logoutConfirmOpen && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-4"
          onClick={() => setLogoutConfirmOpen(false)}
        >
          <div className="w-full max-w-sm" onClick={(e) => e.stopPropagation()}>
            <GlassCard className="p-6">
              <h2 className="mb-1 text-lg font-semibold text-text-primary">退出登录</h2>
              <p className="mb-6 text-sm text-text-muted">确定要退出当前账号吗？</p>
              <div className="flex justify-end gap-3">
                <SilverButton variant="ghost" onClick={() => setLogoutConfirmOpen(false)}>
                  取消
                </SilverButton>
                <SilverButton variant="danger" onClick={logout}>
                  退出登录
                </SilverButton>
              </div>
            </GlassCard>
          </div>
        </div>
      )}
    </>
  );
}
