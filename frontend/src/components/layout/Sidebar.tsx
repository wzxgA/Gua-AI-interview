import { NavLink, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { LayoutDashboard, Briefcase, FileQuestion, FileText, Users, Search, Settings } from 'lucide-react';
import { cn } from '@/lib/utils';

const menuItems = [
  { to: '/', labelKey: 'sidebar.menu.dashboard', icon: LayoutDashboard },
  { to: '/positions', labelKey: 'sidebar.menu.positions', icon: Briefcase },
  { to: '/questions', labelKey: 'sidebar.menu.questions', icon: FileQuestion },
  { to: '/resumes', labelKey: 'sidebar.menu.resumes', icon: FileText },
  { to: '/interviews', labelKey: 'sidebar.menu.interviews', icon: Users },
  { to: '/rag', labelKey: 'sidebar.menu.rag', icon: Search },
  { to: '/settings', labelKey: 'sidebar.menu.settings', icon: Settings },
];

export function Sidebar() {
  const { t } = useTranslation();
  const location = useLocation();

  return (
    <aside className="sticky top-0 flex h-screen w-56 flex-col border-r border-border-subtle bg-space-800/50 backdrop-blur-xl">
      <div className="flex items-center gap-2 px-5 py-6">
        <svg
          viewBox="0 0 32 32"
          className="h-8 w-8 shrink-0"
          aria-hidden="true"
        >
          <g transform="rotate(135 16 16)">
            {/* 深绿瓜皮 */}
            <path d="M3 16 A13 13 0 0 1 29 16 Z" fill="#15803d" />
            {/* 浅绿瓜皮 */}
            <path d="M6 16 A10 10 0 0 1 26 16 Z" fill="#4ade80" />
            {/* 白色瓜瓤内边 */}
            <path d="M8 16 A8 8 0 0 1 24 16 Z" fill="#f8fafc" />
            {/* 红色瓜瓤 */}
            <path d="M9.5 16 A6.5 6.5 0 0 1 22.5 16 Z" fill="#ef4444" />
            {/* 西瓜子（位于红色瓜瓤内部，随整体旋转） */}
            <ellipse cx="13.5" cy="14.8" rx="0.9" ry="1.5" fill="#1f2937" transform="rotate(-25 13.5 14.8)" />
            <ellipse cx="18.5" cy="14.8" rx="0.9" ry="1.5" fill="#1f2937" transform="rotate(25 18.5 14.8)" />
            <ellipse cx="14.8" cy="13" rx="0.9" ry="1.5" fill="#1f2937" transform="rotate(-12 14.8 13)" />
            <ellipse cx="17.2" cy="13" rx="0.9" ry="1.5" fill="#1f2937" transform="rotate(12 17.2 13)" />
            <ellipse cx="16" cy="11.6" rx="0.9" ry="1.5" fill="#1f2937" />
          </g>
        </svg>
        <span className="text-lg font-semibold text-text-primary">{t('common.appName')}</span>
      </div>

      <nav className="flex-1 space-y-1 px-3">
        {menuItems.map((item) => {
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
