import { Moon, Sun, Monitor } from 'lucide-react';
import { SilverButton } from './silver-button';
import { useTheme, type Theme } from '@/contexts/ThemeContext';

const iconMap: Record<Theme, typeof Moon> = {
  dark: Moon,
  light: Sun,
  system: Monitor,
};

const nextMap: Record<Theme, Theme> = {
  dark: 'light',
  light: 'system',
  system: 'dark',
};

const labelMap: Record<Theme, string> = {
  dark: '深色',
  light: '浅色',
  system: '跟随系统',
};

export function ThemeToggle() {
  const { theme, setTheme } = useTheme();
  const Icon = iconMap[theme];

  return (
    <SilverButton
      variant="ghost"
      onClick={() => setTheme(nextMap[theme])}
      title={`主题: ${labelMap[theme]}`}
      className="px-2.5 py-2"
    >
      <Icon className="h-4 w-4" />
    </SilverButton>
  );
}
