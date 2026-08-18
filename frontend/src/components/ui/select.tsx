import { useCallback, useEffect, useId, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { ChevronDown } from 'lucide-react';
import { cn } from '@/lib/utils';

export interface SelectOption {
  value: string;
  label: string;
}

export interface SelectProps {
  value: string | number;
  onChange: (value: string) => void;
  options: SelectOption[];
  placeholder?: string;
  className?: string;
  disabled?: boolean;
  title?: string;
  id?: string;
  'aria-label'?: string;
}

/** 下拉列表最大高度，与 max-h-60 保持一致 */
const LIST_MAX_HEIGHT = 240;
/** 触发器与下拉列表之间的间距 */
const GAP = 4;

interface DropPosition {
  left: number;
  width: number;
  /** 向下展开时：距视口顶部的距离 */
  top: number;
  /** 向上展开时：距视口底部的距离 */
  bottom: number;
  openUp: boolean;
}

export function Select({
  value,
  onChange,
  options,
  placeholder,
  className,
  disabled,
  title,
  id,
  ...rest
}: SelectProps) {
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);
  const [pos, setPos] = useState<DropPosition | null>(null);
  const rootRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const listRef = useRef<HTMLUListElement>(null);
  const listId = useId();

  const selected = options.find((o) => String(o.value) === String(value));

  // 下拉列表通过 Portal 渲染到 body，使用 fixed 定位，
  // 避免被父级卡片（backdrop-blur 形成的层叠上下文）遮挡或被 overflow 容器裁剪
  const measure = useCallback(() => {
    const btn = buttonRef.current;
    if (!btn) return;
    const r = btn.getBoundingClientRect();
    const spaceBelow = window.innerHeight - r.bottom;
    const spaceAbove = r.top;
    setPos({
      left: r.left,
      width: r.width,
      top: r.bottom + GAP,
      bottom: window.innerHeight - r.top + GAP,
      openUp: spaceBelow < LIST_MAX_HEIGHT + GAP && spaceAbove > spaceBelow,
    });
  }, []);

  const openMenu = useCallback(() => {
    measure();
    setOpen(true);
    setActiveIndex(options.findIndex((o) => String(o.value) === String(value)));
  }, [measure, options, value]);

  useEffect(() => {
    if (!open) return;
    const handleClickOutside = (e: MouseEvent) => {
      const target = e.target as Node;
      if (rootRef.current?.contains(target) || listRef.current?.contains(target)) return;
      setOpen(false);
    };
    const handleReposition = (e: Event) => {
      // 下拉列表自身滚动时不需要重新定位
      if (e.target instanceof Node && listRef.current?.contains(e.target)) return;
      measure();
    };
    document.addEventListener('mousedown', handleClickOutside);
    window.addEventListener('resize', handleReposition);
    window.addEventListener('scroll', handleReposition, true);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      window.removeEventListener('resize', handleReposition);
      window.removeEventListener('scroll', handleReposition, true);
    };
  }, [open, measure]);

  // 键盘导航时让激活项滚动到可见区域
  useEffect(() => {
    if (!open || activeIndex < 0) return;
    listRef.current?.children[activeIndex]?.scrollIntoView({ block: 'nearest' });
  }, [open, activeIndex]);

  const selectOption = (opt: SelectOption) => {
    onChange(opt.value);
    setOpen(false);
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Escape') {
      setOpen(false);
      return;
    }
    if (!open) {
      if (e.key === 'Enter' || e.key === ' ' || e.key === 'ArrowDown') {
        e.preventDefault();
        openMenu();
      }
      return;
    }
    switch (e.key) {
      case 'ArrowDown':
        e.preventDefault();
        setActiveIndex((i) => (i + 1) % options.length);
        break;
      case 'ArrowUp':
        e.preventDefault();
        setActiveIndex((i) => (i <= 0 ? options.length - 1 : i - 1));
        break;
      case 'Enter':
        e.preventDefault();
        if (activeIndex >= 0 && options[activeIndex]) {
          selectOption(options[activeIndex]);
        }
        break;
      case 'Tab':
        setOpen(false);
        break;
    }
  };

  return (
    <div ref={rootRef} className={cn('relative', className)}>
      <button
        type="button"
        ref={buttonRef}
        id={id}
        title={title}
        disabled={disabled}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-label={rest['aria-label']}
        onClick={() => (open ? setOpen(false) : openMenu())}
        onKeyDown={handleKeyDown}
        className={cn(
          'flex w-full items-center justify-between gap-2 rounded-md border border-border-default bg-surface-overlay px-3 py-2 text-sm',
          'text-text-primary',
          'focus:border-silver-300/50 focus:outline-none focus:ring-1 focus:ring-silver-300/30',
          'transition-colors',
          open && 'border-silver-300/50 ring-1 ring-silver-300/30',
          disabled && 'cursor-not-allowed opacity-50',
        )}
      >
        <span className={cn('truncate', !selected && 'text-text-muted')}>
          {selected ? selected.label : placeholder}
        </span>
        <ChevronDown
          className={cn('h-4 w-4 shrink-0 text-text-muted transition-transform', open && 'rotate-180')}
        />
      </button>
      {open &&
        pos &&
        createPortal(
          <ul
            ref={listRef}
            id={listId}
            role="listbox"
            style={{
              position: 'fixed',
              left: pos.left,
              width: pos.width,
              zIndex: 100,
              ...(pos.openUp ? { bottom: pos.bottom } : { top: pos.top }),
            }}
            className="max-h-60 overflow-auto rounded-md border border-border-default bg-space-700 py-1 shadow-lg shadow-black/20"
          >
            {options.map((opt, index) => (
              <li
                key={opt.value}
                role="option"
                aria-selected={String(opt.value) === String(value)}
                onClick={() => selectOption(opt)}
                onMouseEnter={() => setActiveIndex(index)}
                className={cn(
                  'cursor-pointer px-3 py-1.5 text-sm transition-colors',
                  String(opt.value) === String(value)
                    ? 'bg-silver-400/10 text-silver-100'
                    : index === activeIndex
                      ? 'bg-surface-hover text-text-primary'
                      : 'text-text-secondary hover:bg-surface-hover',
                )}
              >
                {opt.label}
              </li>
            ))}
          </ul>,
          document.body,
        )}
    </div>
  );
}
