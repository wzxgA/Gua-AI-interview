import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { useTranslation } from 'react-i18next';
import { Calendar, ChevronLeft, ChevronRight } from 'lucide-react';
import { cn } from '@/lib/utils';

interface DateRangePickerProps {
  /** 已选开始日期（yyyy-MM-dd），可空 */
  start: string;
  /** 已选结束日期（yyyy-MM-dd），可空 */
  end: string;
  /** 提交起止日期（yyyy-MM-dd） */
  onApply: (start: string, end: string) => void;
  /** 清空选择 */
  onClear: () => void;
  /** 无选择时触发器显示的占位文案 */
  placeholder?: string;
  className?: string;
}

/** 面板宽度，用于边界 clamp */
const PANEL_W = 500;
/** 面板估算高度，用于判断向上展开 */
const PANEL_H = 360;
/** 触发器与面板间距 */
const GAP = 6;
const DAY_NAMES = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'] as const;

function toISODate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function addMonths(base: Date, n: number): Date {
  return new Date(base.getFullYear(), base.getMonth() + n, 1);
}

/** 当月日期网格：不足 7 的倍数处补 null（周起始为周日） */
function monthGrid(year: number, month: number): (string | null)[] {
  const first = new Date(year, month, 1);
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  const cells: (string | null)[] = [];
  for (let i = 0; i < first.getDay(); i++) cells.push(null);
  for (let d = 1; d <= daysInMonth; d++) {
    cells.push(`${year}-${String(month + 1).padStart(2, '0')}-${String(d).padStart(2, '0')}`);
  }
  while (cells.length % 7 !== 0) cells.push(null);
  return cells;
}

/** yyyy-MM-dd → 2026/8/19 */
function shortDisplay(iso: string): string {
  const d = new Date(`${iso}T00:00:00`);
  return `${d.getFullYear()}/${d.getMonth() + 1}/${d.getDate()}`;
}

export function DateRangePicker({
  start,
  end,
  onApply,
  onClear,
  placeholder,
  className,
}: DateRangePickerProps) {
  const { t, i18n } = useTranslation();
  const [open, setOpen] = useState(false);
  const [anchor, setAnchor] = useState<Date>(() => {
    const base = start ? new Date(`${start}T00:00:00`) : new Date();
    return new Date(base.getFullYear(), base.getMonth(), 1);
  });
  const [draftStart, setDraftStart] = useState(start);
  const [draftEnd, setDraftEnd] = useState(end);
  const [hoverDate, setHoverDate] = useState<string | null>(null);
  const [pos, setPos] = useState<{ left: number; width: number; top: number; bottom: number; openUp: boolean } | null>(null);
  const rootRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);

  const views = useMemo(() => [anchor, addMonths(anchor, 1)], [anchor]);
  const today = toISODate(new Date());

  const measure = useCallback(() => {
    const btn = buttonRef.current;
    if (!btn) return;
    const r = btn.getBoundingClientRect();
    const spaceBelow = window.innerHeight - r.bottom;
    const spaceAbove = r.top;
    setPos({
      left: Math.max(8, Math.min(r.left, window.innerWidth - PANEL_W - 8)),
      width: PANEL_W,
      top: r.bottom + GAP,
      bottom: window.innerHeight - r.top + GAP,
      openUp: spaceBelow < PANEL_H + GAP && spaceAbove > spaceBelow,
    });
  }, []);

  const openPanel = useCallback(() => {
    setDraftStart(start);
    setDraftEnd(end);
    const base = start ? new Date(`${start}T00:00:00`) : new Date();
    setAnchor(new Date(base.getFullYear(), base.getMonth(), 1));
    measure();
    setOpen(true);
  }, [start, end, measure]);

  useEffect(() => {
    if (!open) return;
    const handleClickOutside = (e: MouseEvent) => {
      const target = e.target as Node;
      if (rootRef.current?.contains(target) || panelRef.current?.contains(target)) return;
      setOpen(false);
    };
    const handleKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    const handleReposition = (e: Event) => {
      if (e.target instanceof Node && panelRef.current?.contains(e.target)) return;
      measure();
    };
    document.addEventListener('mousedown', handleClickOutside);
    document.addEventListener('keydown', handleKey);
    window.addEventListener('resize', handleReposition);
    window.addEventListener('scroll', handleReposition, true);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('keydown', handleKey);
      window.removeEventListener('resize', handleReposition);
      window.removeEventListener('scroll', handleReposition, true);
    };
  }, [open, measure]);

  const handleDayClick = (iso: string) => {
    if (!draftStart || (draftStart && draftEnd)) {
      setDraftStart(iso);
      setDraftEnd('');
      return;
    }
    if (iso < draftStart) {
      setDraftStart(iso);
      return;
    }
    setDraftEnd(iso);
  };

  const handleApply = () => {
    if (!draftStart || !draftEnd) return;
    onApply(draftStart, draftEnd);
    setOpen(false);
  };

  const handleClear = () => {
    setDraftStart('');
    setDraftEnd('');
    onClear();
    setOpen(false);
  };

  const display = start && end ? `${shortDisplay(start)} ~ ${shortDisplay(end)}` : placeholder;

  const monthLabel = (d: Date) =>
    new Intl.DateTimeFormat(i18n.language, { year: 'numeric', month: 'long' }).format(d);

  return (
    <div ref={rootRef} className={cn('relative', className)}>
      <button
        type="button"
        ref={buttonRef}
        aria-haspopup="dialog"
        aria-expanded={open}
        onClick={() => (open ? setOpen(false) : openPanel())}
        className={cn(
          'flex w-full items-center justify-between gap-1.5 rounded-md border border-border-default bg-surface-overlay px-2.5 py-1 text-xs transition-colors',
          'text-text-primary',
          'hover:border-border-strong',
          'focus:border-silver-300/50 focus:outline-none focus:ring-1 focus:ring-silver-300/30',
          open && 'border-silver-300/50 ring-1 ring-silver-300/30',
        )}
      >
        <span className={cn('truncate', !start && !end && 'text-text-muted')}>{display}</span>
        <Calendar className="h-3.5 w-3.5 shrink-0 text-text-muted" />
      </button>

      {open &&
        pos &&
        createPortal(
          <div
            ref={panelRef}
            role="dialog"
            aria-label={placeholder}
            style={{
              position: 'fixed',
              left: pos.left,
              width: pos.width,
              zIndex: 100,
              ...(pos.openUp ? { bottom: pos.bottom } : { top: pos.top }),
            }}
            className="rounded-lg border border-border-default bg-space-600 p-3 shadow-lg shadow-black/20"
          >
            {/* 月份导航 */}
            <div className="flex items-center justify-between px-1">
              <button
                type="button"
                onClick={() => setAnchor((m) => addMonths(m, -1))}
                aria-label={t('common.prevMonth')}
                className="rounded-md p-1 text-text-muted transition-colors hover:bg-surface-hover hover:text-text-primary"
              >
                <ChevronLeft className="h-4 w-4" />
              </button>
              <div className="flex gap-6 text-sm font-medium text-text-primary">
                {views.map((v, i) => (
                  <span key={i} className="w-28 text-center tabular-nums">
                    {monthLabel(v)}
                  </span>
                ))}
              </div>
              <button
                type="button"
                onClick={() => setAnchor((m) => addMonths(m, 1))}
                aria-label={t('common.nextMonth')}
                className="rounded-md p-1 text-text-muted transition-colors hover:bg-surface-hover hover:text-text-primary"
              >
                <ChevronRight className="h-4 w-4" />
              </button>
            </div>

            {/* 双月历 */}
            <div className="mt-2 grid grid-cols-2 gap-5">
              {views.map((v, viewIdx) => {
                const cells = monthGrid(v.getFullYear(), v.getMonth());
                return (
                  <div key={viewIdx}>
                    <div className="grid grid-cols-7">
                      {DAY_NAMES.map((name) => (
                        <span
                          key={name}
                          className="flex h-6 items-center justify-center text-[10px] text-text-muted"
                        >
                          {name}
                        </span>
                      ))}
                    </div>
                    <div className="grid grid-cols-7 gap-y-0.5">
                      {cells.map((iso, i) => {
                        if (!iso) return <span key={i} className="h-7 w-7" />;
                        const isStart = iso === draftStart;
                        const isEnd = iso === draftEnd;
                        const rangeBase = draftStart ? (hoverDate ?? draftEnd) : null;
                        const inRange =
                          !!draftStart &&
                          !!rangeBase &&
                          iso > draftStart &&
                          iso < rangeBase;
                        const isToday = iso === today;
                        return (
                          <button
                            key={iso}
                            type="button"
                            onClick={() => handleDayClick(iso)}
                            onMouseEnter={() => setHoverDate(iso)}
                            className={cn(
                              'flex h-7 w-7 items-center justify-center rounded-full text-xs transition-colors',
                              isStart || isEnd
                                ? 'bg-silver-400/25 font-semibold text-silver-100'
                                : inRange
                                  ? 'bg-silver-400/10 text-text-primary'
                                  : isToday
                                    ? 'font-medium text-sky-400 hover:bg-surface-hover'
                                    : 'text-text-secondary hover:bg-surface-hover hover:text-text-primary',
                            )}
                          >
                            {Number(iso.slice(8))}
                          </button>
                        );
                      })}
                    </div>
                  </div>
                );
              })}
            </div>

            {/* 底部操作 */}
            <div className="mt-3 flex items-center justify-between border-t border-border-default pt-2.5">
              <button
                type="button"
                onClick={handleClear}
                className="rounded-md px-2.5 py-1 text-xs text-text-muted transition-colors hover:text-text-primary"
              >
                {t('dashboard.reset')}
              </button>
              <button
                type="button"
                onClick={handleApply}
                disabled={!draftStart || !draftEnd}
                className={cn(
                  'rounded-md bg-sky-500/15 px-3 py-1 text-xs font-medium transition-colors',
                  draftStart && draftEnd
                    ? 'text-sky-400 hover:bg-sky-500/25'
                    : 'cursor-not-allowed text-text-muted opacity-50',
                )}
              >
                {t('dashboard.apply')}
              </button>
            </div>
          </div>,
          document.body,
        )}
    </div>
  );
}
