import { NavLink, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { LayoutDashboard, Briefcase, FileQuestion, FileText, Users, Search, Settings, Activity } from 'lucide-react';
import { cn } from '@/lib/utils';
import { useAuth } from '@/contexts/AuthContext';

const menuItems = [
  { to: '/', labelKey: 'sidebar.menu.dashboard', icon: LayoutDashboard },
  { to: '/positions', labelKey: 'sidebar.menu.positions', icon: Briefcase },
  { to: '/questions', labelKey: 'sidebar.menu.questions', icon: FileQuestion },
  { to: '/resumes', labelKey: 'sidebar.menu.resumes', icon: FileText },
  { to: '/interviews', labelKey: 'sidebar.menu.interviews', icon: Users },
  { to: '/rag', labelKey: 'sidebar.menu.rag', icon: Search },
  { to: '/monitor', labelKey: 'sidebar.menu.monitor', icon: Activity, adminOnly: true },
  { to: '/settings', labelKey: 'sidebar.menu.settings', icon: Settings },
];

export function Sidebar() {
  const { t } = useTranslation();
  const { user } = useAuth();
  const location = useLocation();
  // 监控菜单仅对 ADMIN 可见
  const visibleItems = menuItems.filter((item) => !('adminOnly' in item && item.adminOnly) || user?.role === 'ADMIN');

  return (
    <aside className="sticky top-0 flex h-screen w-56 flex-col border-r border-border-subtle bg-space-800/40 backdrop-blur-xl">
      <div className="flex items-center gap-2 px-5 py-6">
        <img src="/logo.svg" alt="logo" className="h-12 w-12 shrink-0" />
        <span className="text-lg font-semibold text-text-primary">{t('common.appName')}</span>
      </div>

      <nav className="flex-1 space-y-1 px-3">
        {visibleItems.map((item) => {
          const isActive =
            item.to === '/'
              ? location.pathname === '/'
              : location.pathname.startsWith(item.to);
          return (
            <NavLink
              key={item.to}
              to={item.to}
              className={cn(
                'flex items-center gap-3 rounded-md px-3 py-2 text-sm transition-all',
                isActive
                  ? 'bg-surface-hover text-silver-100 shadow-[0_0_12px_var(--shadow-glow)]'
                  : 'text-text-muted hover:bg-surface-overlay hover:text-text-secondary',
              )}
            >
              <item.icon className="h-4 w-4" />
              {t(item.labelKey)}
            </NavLink>
          );
        })}
      </nav>

    </aside>
  );
}
