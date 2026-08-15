import { useTranslation } from 'react-i18next';
import { cn } from '@/lib/utils';

interface PaginationProps {
  current: number;
  total: number;
  size: number;
  onPageChange: (page: number) => void;
}

export function Pagination({ current, total, size, onPageChange }: PaginationProps) {
  const { t } = useTranslation();
  const totalPages = Math.ceil(total / size);
  if (totalPages <= 1) return null;

  const pages = Array.from({ length: totalPages }, (_, i) => i + 1);
  const visiblePages = pages.filter(
    (p) => p === 1 || p === totalPages || Math.abs(p - current) <= 1,
  );

  return (
    <div className="flex items-center gap-1">
      <button
        disabled={current <= 1}
        onClick={() => onPageChange(current - 1)}
        className={cn(
          'rounded-md px-3 py-1 text-xs transition-colors',
          current <= 1
            ? 'text-text-muted/40 pointer-events-none'
            : 'text-text-secondary hover:bg-surface-hover hover:text-text-primary',
        )}
      >
        {t('pagination.prev')}
      </button>
      {visiblePages.map((p, i) => {
        const prev = visiblePages[i - 1];
        const showEllipsis = prev && p - prev > 1;
        return (
          <span key={p} className="flex items-center">
            {showEllipsis && <span className="px-1 text-text-muted">...</span>}
            <button
              onClick={() => onPageChange(p)}
              className={cn(
                'rounded-md px-3 py-1 text-xs transition-colors',
                p === current
                  ? 'bg-silver-300/20 text-silver-100 border border-silver-300/30'
                  : 'text-text-secondary hover:bg-surface-hover hover:text-text-primary',
              )}
            >
              {p}
            </button>
          </span>
        );
      })}
      <button
        disabled={current >= totalPages}
        onClick={() => onPageChange(current + 1)}
        className={cn(
          'rounded-md px-3 py-1 text-xs transition-colors',
          current >= totalPages
            ? 'text-text-muted/40 pointer-events-none'
            : 'text-text-secondary hover:bg-surface-hover hover:text-text-primary',
        )}
      >
        {t('pagination.next')}
      </button>
      <span className="ml-2 text-xs text-text-muted">
        {t('pagination.total', { total })}
      </span>
    </div>
  );
}
