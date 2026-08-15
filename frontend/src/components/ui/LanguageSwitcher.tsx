import { useEffect, useRef, useState } from 'react';
import { Globe, Check } from 'lucide-react';
import { cn } from '@/lib/utils';
import { SUPPORTED_LANGUAGES } from '@/i18n';
import { useLanguage } from '@/contexts/LanguageContext';

/** 语言切换下拉组件；variant: compact（TopBar/候选端页头）/ full（设置页 radio 样式由设置页自行实现） */
export function LanguageSwitcher({ className }: { className?: string }) {
  const { language, setLanguage } = useLanguage();
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const current = SUPPORTED_LANGUAGES.find((l) => l.code === language);

  useEffect(() => {
    if (!open) return;
    const handler = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [open]);

  return (
    <div ref={containerRef} className={cn('relative', className)}>
      <button
        onClick={() => setOpen((v) => !v)}
        className={cn(
          'inline-flex items-center gap-1.5 rounded-md border border-border-default px-2.5 py-2',
          'text-xs text-text-secondary transition-all',
          'hover:border-border-strong hover:bg-surface-hover hover:text-text-primary',
        )}
      >
        <Globe className="h-4 w-4" />
        <span>{current?.label ?? language}</span>
      </button>

      {open && (
        <div
          className={cn(
            'absolute right-0 top-full z-50 mt-1 min-w-36 overflow-hidden rounded-md',
            'border border-border-default bg-space-800 shadow-lg backdrop-blur-xl',
          )}
        >
          {SUPPORTED_LANGUAGES.map((lang) => (
            <button
              key={lang.code}
              onClick={() => {
                setLanguage(lang.code);
                setOpen(false);
              }}
              className={cn(
                'flex w-full items-center justify-between px-3 py-2 text-xs transition-colors',
                lang.code === language
                  ? 'bg-surface-hover text-silver-100'
                  : 'text-text-secondary hover:bg-surface-hover hover:text-text-primary',
              )}
            >
              <span>{lang.label}</span>
              {lang.code === language && <Check className="h-3.5 w-3.5" />}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
