import { Moon, Sun } from 'lucide-react';
import { SilverButton } from './silver-button';
import { useTheme, type Theme } from '@/contexts/ThemeContext';

const iconMap: Record<Theme, typeof Moon> = {
  dark: Moon,
  light: Sun,
  system: Sun,
};

const nextMap: Record<Theme, Theme> = {
  dark: 'light',
  light: 'dark',
  system: 'dark',
};

const labelMap: Record<Theme, string> = {
  dark: '深色',
  light: '浅色',
  system: '浅色',
};

export function ThemeToggle() {
  const { theme, setTheme } = useTheme();
  const Icon = iconMap[theme];

  const handleClick = (e: React.MouseEvent<HTMLButtonElement>) => {
    const rect = e.currentTarget.getBoundingClientRect();
    setTheme(nextMap[theme], {
      x: rect.left + rect.width / 2,
      y: rect.top + rect.height / 2,
    });
  };

  return (
    <SilverButton
      variant="ghost"
      onClick={handleClick}
      title={`主题: ${labelMap[theme]}`}
      className="px-2.5 py-2"
    >
      <Icon className="h-4 w-4" />
    </SilverButton>
  );
}
