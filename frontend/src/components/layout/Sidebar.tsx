import { NavLink, useLocation } from 'react-router-dom';
import { LayoutDashboard, Briefcase, FileQuestion, FileText, Search, Settings } from 'lucide-react';
import { cn } from '@/lib/utils';

const menuItems = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/positions', label: '岗位管理', icon: Briefcase },
  { to: '/questions', label: '题库管理', icon: FileQuestion },
  { to: '/resumes', label: '简历管理', icon: FileText },
  { to: '/rag', label: 'RAG 调试', icon: Search },
  { to: '/settings', label: '设置', icon: Settings },
];

export function Sidebar() {
  const location = useLocation();

  return (
    <aside className="sticky top-0 flex h-screen w-56 flex-col border-r border-white/5 bg-space-800/50 backdrop-blur-xl">
      <div className="flex items-center gap-2 px-5 py-6">
        <div className="h-8 w-8 rounded-lg bg-gradient-to-br from-silver-100 to-silver-300 shadow-[0_0_12px_var(--silver-glow)]" />
        <span className="text-lg font-semibold text-text-primary">AIMS</span>
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
                  ? 'bg-white/[0.06] text-silver-100 shadow-[0_0_12px_rgba(200,212,232,0.06)]'
                  : 'text-text-muted hover:bg-white/[0.03] hover:text-text-secondary',
              )}
            >
              <item.icon className="h-4 w-4" />
              {item.label}
            </NavLink>
          );
        })}
      </nav>

      <div className="border-t border-white/5 px-5 py-4">
        <p className="text-xs text-text-muted">v0.1.0 · F1</p>
      </div>
    </aside>
  );
}
