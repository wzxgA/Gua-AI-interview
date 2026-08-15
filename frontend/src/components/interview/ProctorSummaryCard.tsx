import { useTranslation } from 'react-i18next';
import { GlassCard } from '@/components/ui/glass-card';
import { MonitorOff, EyeOff } from 'lucide-react';
import type { ProctorSummary } from '@/types/interview';

/** 防作弊摘要卡片（面试控制台，仅开启防作弊时展示） */
export function ProctorSummaryCard({ summary }: { summary?: ProctorSummary }) {
  const { t } = useTranslation();
  const items = summary?.items ?? [];
  const byType = new Map(items.map((i) => [i.type, i]));
  const tabCount = byType.get('TAB_SWITCH')?.count ?? 0;
  const blurCount = byType.get('WINDOW_BLUR')?.count ?? 0;
  // 后端 Long 全局序列化为字符串（防精度丢失），需显式转数字，避免 + 变成字符串拼接
  const totalMs = items.reduce((s, i) => s + Number(i.totalDurationMs ?? 0), 0);
  const totalSec = Math.round(totalMs / 1000);
  const durationText =
    totalSec >= 60 ? `${Math.floor(totalSec / 60)}m${String(totalSec % 60).padStart(2, '0')}s` : `${totalSec}s`;

  return (
    <GlassCard className="p-5">
      <h3 className="mb-3 text-sm font-medium text-text-muted">{t('proctor.summaryTitle')}</h3>
      <div className="grid grid-cols-3 gap-3 text-sm">
        <div className="flex flex-col gap-1 rounded-lg border border-border-subtle bg-surface-overlay px-3 py-2.5">
          <span className="flex items-center gap-1.5 text-xs text-text-muted">
            <MonitorOff className="h-3.5 w-3.5" />
            {t('proctor.eventTabSwitch')}
          </span>
          <span className="text-base font-semibold text-text-primary">{tabCount}</span>
        </div>
        <div className="flex flex-col gap-1 rounded-lg border border-border-subtle bg-surface-overlay px-3 py-2.5">
          <span className="flex items-center gap-1.5 text-xs text-text-muted">
            <EyeOff className="h-3.5 w-3.5" />
            {t('proctor.eventWindowBlur')}
          </span>
          <span className="text-base font-semibold text-text-primary">{blurCount}</span>
        </div>
        <div className="flex flex-col gap-1 rounded-lg border border-border-subtle bg-surface-overlay px-3 py-2.5">
          <span className="text-xs text-text-muted">{t('proctor.totalOffFocusLabel')}</span>
          <span className="text-base font-semibold text-text-primary">{durationText}</span>
        </div>
      </div>
    </GlassCard>
  );
}
