import { useTranslation } from 'react-i18next';
import { GlassCard } from '@/components/ui/glass-card';
import { MonitorOff, EyeOff, Eye, Timer } from 'lucide-react';
import type { ProctorSummary } from '@/types/interview';

/** 防作弊事件类型 key → 展示用 i18n key */
const EVENT_KEYS: Record<string, string> = {
  TAB_SWITCH: 'proctor.eventTabSwitch',
  WINDOW_BLUR: 'proctor.eventWindowBlur',
  FACE_LOST: 'proctor.eventFaceLost',
  GAZE_AWAY: 'proctor.eventGazeAway',
};

function fmtDuration(ms: number): string {
  const s = Math.round(ms / 1000);
  return s >= 60 ? `${Math.floor(s / 60)}m${String(s % 60).padStart(2, '0')}s` : `${s}s`;
}

/**
 * 报告页「专注度」维度摘要：与评估打分解耦，仅作参考展示。
 * 展示切屏/失焦/眼神偏离次数与累计偏离时长（仅该面试开启防作弊时渲染）。
 */
export function ProctorFocusCard({ summary }: { summary?: ProctorSummary }) {
  const { t } = useTranslation();
  const items = summary?.items ?? [];
  const byType = new Map(items.map((i) => [i.type, i]));
  const stat = (type: string) => {
    const it = byType.get(type);
    // 后端 Long 序列化为字符串，需 Number() 转换
    return { count: it?.count ?? 0, ms: Number(it?.totalDurationMs ?? 0) };
  };
  const tab = stat('TAB_SWITCH');
  const blur = stat('WINDOW_BLUR');
  const gazeCount = stat('FACE_LOST').count + stat('GAZE_AWAY').count;
  const totalMs = ['TAB_SWITCH', 'WINDOW_BLUR', 'FACE_LOST', 'GAZE_AWAY'].reduce(
    (s, type) => s + stat(type).ms,
    0,
  );

  const cells = [
    { icon: MonitorOff, label: t(EVENT_KEYS.TAB_SWITCH), value: String(tab.count) },
    { icon: EyeOff, label: t(EVENT_KEYS.WINDOW_BLUR), value: String(blur.count) },
    { icon: Eye, label: t(EVENT_KEYS.GAZE_AWAY), value: String(gazeCount) },
    { icon: Timer, label: t('proctor.totalOffFocusLabel'), value: fmtDuration(totalMs) },
  ];

  return (
    <GlassCard className="p-5">
      <h3 className="mb-1 text-sm font-medium text-text-primary">{t('proctor.focusTitle')}</h3>
      <p className="mb-3 text-xs text-text-muted">{t('proctor.focusNotScored')}</p>
      {items.length === 0 ? (
        <p className="text-sm text-text-muted">{t('proctor.empty')}</p>
      ) : (
        <div className="grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
          {cells.map((c) => (
            <div
              key={c.label}
              className="flex flex-col gap-1 rounded-lg border border-border-subtle bg-surface-overlay px-3 py-2.5"
            >
              <span className="flex items-center gap-1.5 text-xs text-text-muted">
                <c.icon className="h-3.5 w-3.5" />
                {c.label}
              </span>
              <span className="text-base font-semibold text-text-primary">{c.value}</span>
            </div>
          ))}
        </div>
      )}
    </GlassCard>
  );
}
