import {
  createContext,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from 'react';

export type Theme = 'dark' | 'light' | 'system';

interface ThemeContextValue {
  theme: Theme;
  resolvedTheme: 'dark' | 'light';
  setTheme: (theme: Theme, origin?: { x: number; y: number }) => void;
}

const ThemeContext = createContext<ThemeContextValue | null>(null);

function getSystemPreference(): 'dark' | 'light' {
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

function resolveTheme(theme: Theme): 'dark' | 'light' {
  return theme === 'system' ? getSystemPreference() : theme;
}

function toggleClass(resolved: 'dark' | 'light') {
  document.documentElement.classList.toggle('dark', resolved === 'dark');
}

/** 直接切换（无动画），用于初始化和系统主题变化 */
function applyTheme(theme: Theme): 'dark' | 'light' {
  const resolved = resolveTheme(theme);
  toggleClass(resolved);
  return resolved;
}

/** 带圆形扩散动画的切换，用于用户点击 */
function applyThemeWithTransition(
  theme: Theme,
  origin: { x: number; y: number },
): 'dark' | 'light' {
  const resolved = resolveTheme(theme);

  // 不支持 View Transitions API 时直接切换
  if (!document.startViewTransition) {
    toggleClass(resolved);
    return resolved;
  }

  const transition = document.startViewTransition(() => {
    toggleClass(resolved);
  });

  transition.ready.then(() => {
    const maxRadius = Math.hypot(
      Math.max(origin.x, window.innerWidth - origin.x),
      Math.max(origin.y, window.innerHeight - origin.y),
    );
    document.documentElement.animate(
      {
        clipPath: [
          `circle(0px at ${origin.x}px ${origin.y}px)`,
          `circle(${maxRadius}px at ${origin.x}px ${origin.y}px)`,
        ],
      },
      {
        duration: 400,
        easing: 'ease-in-out',
        pseudoElement: '::view-transition-new(root)',
      },
    );
  });

  return resolved;
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<Theme>(() => {
    return (localStorage.getItem('theme') as Theme) || 'system';
  });
  const [resolvedTheme, setResolvedTheme] = useState<'dark' | 'light'>(() =>
    applyTheme((localStorage.getItem('theme') as Theme) || 'system'),
  );

  // 用户点击触发的切换，带动画
  const originRef = useRef<{ x: number; y: number } | null>(null);

  useEffect(() => {
    const origin = originRef.current;
    const resolved = origin
      ? applyThemeWithTransition(theme, origin)
      : applyTheme(theme);
    setResolvedTheme(resolved);
    localStorage.setItem('theme', theme);
    originRef.current = null;
  }, [theme]);

  // 监听系统主题变化（仅在 system 模式下生效，无动画）
  useEffect(() => {
    if (theme !== 'system') return;
    const mq = window.matchMedia('(prefers-color-scheme: dark)');
    const handler = () => {
      const resolved = applyTheme('system');
      setResolvedTheme(resolved);
    };
    mq.addEventListener('change', handler);
    return () => mq.removeEventListener('change', handler);
  }, [theme]);

  const setTheme = (newTheme: Theme, origin?: { x: number; y: number }) => {
    if (origin) originRef.current = origin;
    setThemeState(newTheme);
  };

  return (
    <ThemeContext.Provider value={{ theme, resolvedTheme, setTheme }}>
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme() {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error('useTheme must be used within ThemeProvider');
  return ctx;
}
