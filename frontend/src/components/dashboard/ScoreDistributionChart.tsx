import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { GlassCard } from '@/components/ui/glass-card';
import { DateRangePicker } from '@/components/ui/date-range-picker';
import { Skeleton } from '@/components/ui/skeleton';
import { useTheme } from '@/contexts/ThemeContext';
import { useScorePoints } from '@/api/dashboard';
import { cn } from '@/lib/utils';
import type { ScorePoint } from '@/types/dashboard';

/** 时间范围快捷预设；days 传后端，undefined 表示全部时间。 */
const RANGE_OPTIONS = [
  { value: 'all', days: undefined },
  { value: '7', days: 7 },
  { value: '30', days: 30 },
  { value: '90', days: 90 },
] as const;

/** 空数据兜底，避免解构 undefined。 */
const EMPTY_POINTS: ScorePoint[] = [];

export function ScoreDistributionChart() {
  const { t } = useTranslation();
  const { resolvedTheme } = useTheme();
  const isDark = resolvedTheme === 'dark';

  // 快捷预设（days）与自定义区间（appliedRange）互斥；自定义输入在点击「应用」前不生效。
  // 默认展示最近 7 天。
  const [days, setDays] = useState<number | undefined>(7);
  const [customStart, setCustomStart] = useState('');
  const [customEnd, setCustomEnd] = useState('');
  const [appliedRange, setAppliedRange] = useState<{ start: string; end: string } | null>(null);

  const params = appliedRange
    ? { start: appliedRange.start, end: appliedRange.end }
    : days
      ? { days }
      : undefined;
  const { data, isLoading, isPlaceholderData } = useScorePoints(params);

  const points = data ?? EMPTY_POINTS;
  const scores = useMemo(() => points.map((p) => p.score), [points]);
  const avgScore = scores.length
    ? scores.reduce((sum, s) => sum + s, 0) / scores.length
    : 0;
  const minScore = scores.length ? Math.min(...scores) : 0;
  const maxScore = scores.length ? Math.max(...scores) : 0;
  const isEmpty = points.length === 0;

  function handlePreset(nextDays?: number) {
    setDays(nextDays);
    setCustomStart('');
    setCustomEnd('');
    setAppliedRange(null);
  }

  function applyCustom(start: string, end: string) {
    if (!start || !end) {
      toast.error(t('dashboard.dateRangeRequired'));
      return;
    }
    if (start > end) {
      toast.error(t('dashboard.invalidDateRange'));
      return;
    }
    setCustomStart(start);
    setCustomEnd(end);
    setAppliedRange({ start, end });
    setDays(undefined);
  }

  function handleReset() {
    setCustomStart('');
    setCustomEnd('');
    setAppliedRange(null);
    setDays(7);
  }

  return (
    <GlassCard className="flex h-full flex-col p-5">
      <div className="flex items-baseline justify-between">
        <h3 className="text-sm font-medium text-text-muted">{t('dashboard.scoreDistribution')}</h3>
        <span className="text-xs text-text-muted">
          {t('dashboard.avgScore')}:{' '}
          <span className="font-semibold text-sky-400">
            {!isEmpty ? avgScore.toFixed(2) : '-'}
          </span>
        </span>
      </div>

      <div className="mt-3 flex flex-wrap items-center gap-x-3 gap-y-2">
        <div className="inline-flex items-center gap-0.5 rounded-lg border border-border-default bg-surface-overlay p-0.5">
          {RANGE_OPTIONS.map((opt) => {
            const selected = !appliedRange && days === opt.days;
            return (
              <button
                key={opt.value}
                type="button"
                onClick={() => handlePreset(opt.days)}
                className={cn(
                  'rounded-md px-2.5 py-1 text-xs transition-colors',
                  selected
                    ? 'bg-silver-400/15 font-medium text-silver-100'
                    : 'text-text-muted hover:text-text-primary',
                )}
              >
                {opt.value === 'all'
                  ? t('dashboard.timeAll')
                  : t(`dashboard.last${opt.value}Days`)}
              </button>
            );
          })}
        </div>

        <div className="flex items-center gap-1.5">
          <DateRangePicker
            start={customStart}
            end={customEnd}
            onApply={applyCustom}
            onClear={() => {
              setCustomStart('');
              setCustomEnd('');
            }}
            placeholder={t('dashboard.customRange')}
            className="w-44"
          />
          <button
            type="button"
            onClick={() => applyCustom(customStart, customEnd)}
            className="rounded-md bg-sky-500/15 px-2.5 py-1 text-xs font-medium text-sky-400 transition-colors hover:bg-sky-500/25"
          >
            {t('dashboard.apply')}
          </button>
          <button
            type="button"
            onClick={handleReset}
            className="rounded-md px-2 py-1 text-xs text-text-muted transition-colors hover:text-text-primary"
          >
            {t('dashboard.reset')}
          </button>
        </div>
      </div>

      {/* 图表占满卡片剩余高度，SVG 按容器实际尺寸渲染，无留白 */}
      <div
        className={cn(
          'mt-2 min-h-56 w-full flex-1 transition-opacity',
          isPlaceholderData && 'opacity-60',
        )}
      >
        {isLoading && !data ? (
          <div className="flex h-full w-full flex-col items-center justify-center gap-3">
            <Skeleton className="h-4 w-32" />
            <Skeleton className="h-32 w-full" />
          </div>
        ) : isEmpty ? (
          <div className="flex h-full w-full items-center justify-center">
            <span className="text-sm text-text-muted">{t('dashboard.noData')}</span>
          </div>
        ) : (
          <ScatterSvg points={points} avgScore={avgScore} isDark={isDark} />
        )}
      </div>

      {!isEmpty && (
        <div className="mt-3 flex flex-wrap items-center gap-x-3 gap-y-1 border-t border-border-default pt-3 text-xs text-text-muted">
          <span>
            {t('dashboard.samples')}:{' '}
            <span className="font-medium text-text-primary">{points.length}</span>
          </span>
          <span>
            {t('dashboard.min')}:{' '}
            <span className="font-medium text-text-primary">{minScore.toFixed(1)}</span>
          </span>
          <span>
            {t('dashboard.max')}:{' '}
            <span className="font-medium text-text-primary">{maxScore.toFixed(1)}</span>
          </span>
          <span>
            {t('dashboard.avgScore')}:{' '}
            <span className="font-medium text-text-primary">{avgScore.toFixed(2)}</span>
          </span>
        </div>
      )}
    </GlassCard>
  );
}

/** 散点图（5 分制，横轴为时间、纵轴为得分），每点代表一次已评分会话。
 *  尺寸由 ResizeObserver 动态测量容器，SVG 按实际像素渲染，完全占满可用区域。 */
function ScatterSvg({
  points,
  avgScore,
  isDark,
}: {
  points: ScorePoint[];
  avgScore: number;
  isDark: boolean;
}) {
  const { t, i18n } = useTranslation();
  const containerRef = useRef<HTMLDivElement>(null);
  const [hovered, setHovered] = useState<ScorePoint | null>(null);
  const [size, setSize] = useState({ w: 640, h: 240 });

  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    const ro = new ResizeObserver(() => {
      const rect = el.getBoundingClientRect();
      setSize({ w: rect.width, h: rect.height });
    });
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  const W = Math.max(size.w, 320);
  const H = Math.max(size.h, 200);
  const left = 58;
  const right = W - 24;
  const axisY = H - 44;
  const top = 24;
  const plotW = right - left;
  const plotH = axisY - top;

  // 有效点：需要可解析的时间戳（createdAt 为 null 时跳过）
  const valid = points.filter((p) => p.createdAt != null && !Number.isNaN(new Date(p.createdAt!).getTime()));
  const times = valid.map((p) => new Date(p.createdAt!).getTime());

  let tMin = Math.min(...times);
  let tMax = Math.max(...times);
  if (tMin === tMax) {
    // 单点场景：前后各扩 12 小时，避免分母为 0
    tMin -= 12 * 3600_000;
    tMax += 12 * 3600_000;
  } else {
    const pad = (tMax - tMin) * 0.05;
    tMin -= pad;
    tMax += pad;
  }
  const toX = (t: number) => left + ((t - tMin) / (tMax - tMin)) * plotW;
  const toY = (score: number) => axisY - (Math.min(5, Math.max(0, score)) / 5) * plotH;

  const axisColor = isDark ? 'rgba(220,224,230,0.55)' : 'rgba(0,0,0,0.45)';
  const gridColor = isDark ? 'rgba(220,224,230,0.12)' : 'rgba(0,0,0,0.06)';
  const avgColor = '#f59e0b';
  const pointColor = isDark ? 'rgba(56,189,248,0.6)' : 'rgba(2,132,199,0.5)';
  const r = points.length > 300 ? 2.5 : 3;

  // 横轴时间刻度：5 个均匀刻度；跨天显示 MM-dd，同一天内显示 HH:mm
  const xTicks = Array.from({ length: 5 }, (_, i) => tMin + ((tMax - tMin) * i) / 4);
  const spanMs = tMax - tMin;
  const fmtTime = (ts: number) => {
    const d = new Date(ts);
    if (spanMs > 24 * 3600_000) {
      return `${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    }
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  };

  // 纵轴得分刻度 0-5
  const yTicks = [0, 1, 2, 3, 4, 5];

  return (
    <div ref={containerRef} className="h-full w-full">
    <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="xMidYMid meet" className="h-full w-full" role="img">
      {yTicks.map((tick) => {
        const y = toY(tick);
        return (
          <g key={tick}>
            <line x1={left} y1={y} x2={right} y2={y} stroke={gridColor} strokeDasharray="4 4" />
            <text x={left - 8} y={y + 4} textAnchor="end" fontSize={11} fill={axisColor}>
              {tick}
            </text>
          </g>
        );
      })}
      {xTicks.map((ts) => (
        <text key={ts} x={toX(ts)} y={axisY + 18} textAnchor="middle" fontSize={11} fill={axisColor}>
          {fmtTime(ts)}
        </text>
      ))}
      <line x1={left} y1={axisY} x2={right} y2={axisY} stroke={axisColor} />
      <line x1={left} y1={top} x2={left} y2={axisY} stroke={axisColor} />

      {/* 得分点 */}
      {valid.map((p) => {
        const t = new Date(p.createdAt!).getTime();
        const isHovered = hovered === p;
        return (
          <circle
            key={`${p.createdAt}-${p.score}`}
            cx={toX(t)}
            cy={toY(p.score)}
            r={isHovered ? r + 2.5 : r}
            fill={pointColor}
            stroke={isHovered ? (isDark ? 'rgba(56,189,248,0.95)' : 'rgba(2,132,199,0.95)') : 'none'}
            strokeWidth={1}
            onMouseEnter={() => setHovered(p)}
            onMouseLeave={() => setHovered(null)}
            onFocus={() => setHovered(p)}
            onBlur={() => setHovered(null)}
            tabIndex={0}
            className="cursor-pointer transition-all focus:outline-none"
          />
        );
      })}

      {/* 平均分参考线 */}
      <line
        x1={left}
        y1={toY(avgScore)}
        x2={right}
        y2={toY(avgScore)}
        stroke={avgColor}
        strokeWidth={1.5}
        strokeDasharray="6 4"
      />
      <text x={right - 2} y={toY(avgScore) - 5} textAnchor="end" fontSize={10} fill={avgColor}>
        avg: {avgScore.toFixed(2)}
      </text>

      {/* 悬停提示 */}
      {hovered &&
        (() => {
          const ts = new Date(hovered.createdAt!).getTime();
          const x = toX(ts);
          const y = toY(hovered.score);
          const tooltipW = 158;
          const tooltipH = 44;
          const tx = x + 12 + tooltipW > right ? x - tooltipW - 12 : x + 12;
          const ty = y - tooltipH - 10 < top - 12 ? y + 12 : y - tooltipH - 10;
          return (
            <g pointerEvents="none">
              <rect
                x={tx}
                y={ty}
                width={tooltipW}
                height={tooltipH}
                rx={8}
                strokeWidth={1}
                style={{
                  fill: 'var(--space-700)',
                  stroke: 'var(--border-default)',
                  filter: 'drop-shadow(0 2px 8px rgba(0,0,0,0.25))',
                }}
              />
              <text
                x={tx + 12}
                y={ty + 18}
                fontSize={11}
                fontWeight={600}
                style={{ fill: 'var(--text-primary)' }}
              >
                {`${t('dashboard.score')} ${hovered.score.toFixed(2)} · ${(hovered.score * 20).toFixed(1)}%`}
              </text>
              <text
                x={tx + 12}
                y={ty + 33}
                fontSize={10}
                style={{ fill: 'var(--text-muted)' }}
              >
                {new Date(ts).toLocaleString(i18n.language, {
                  year: 'numeric',
                  month: '2-digit',
                  day: '2-digit',
                  hour: '2-digit',
                  minute: '2-digit',
                })}
              </text>
            </g>
          );
        })()}
    </svg>
    </div>
  );
}
