import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { GlassCard } from '@/components/ui/glass-card';
import { StatusDot } from '@/components/ui/status-dot';
import { useEnumLabel } from '@/hooks/useEnumLabel';
import type { InterviewResponse } from '@/types/interview';

interface StatusCardProps {
  interview: InterviewResponse;
}

/** 面试状态卡片：状态指示灯、时间信息、已用时长 */
export function StatusCard({ interview }: StatusCardProps) {
  const { t, i18n } = useTranslation();
  const enumLabel = useEnumLabel();
  const [now, setNow] = useState(Date.now());

  // IN_PROGRESS 时每秒刷新已用时长
  useEffect(() => {
    if (interview.status !== 'IN_PROGRESS') return;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [interview.status]);

  const elapsed =
    interview.startedAt && interview.status === 'IN_PROGRESS'
      ? now - new Date(interview.startedAt).getTime()
      : interview.startedAt && interview.endedAt
        ? new Date(interview.endedAt).getTime() -
          new Date(interview.startedAt).getTime()
        : 0;

  /** 计算已用时长（毫秒 -> 可读文本） */
  const formatDuration = (ms: number): string => {
    const totalSec = Math.floor(ms / 1000);
    const h = Math.floor(totalSec / 3600);
    const m = Math.floor((totalSec % 3600) / 60);
    const s = totalSec % 60;
    if (h > 0) return t('interviews.durationHours', { h, m, s });
    if (m > 0) return t('interviews.durationMinutes', { m, s });
    return t('interviews.durationSeconds', { s });
  };

  return (
    <GlassCard className="p-5">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-medium text-text-muted">{t('interviews.statusCardTitle')}</h3>
        <StatusDot
          status={interview.status}
          label={enumLabel('sessionStatus', interview.status, interview.status)}
        />
      </div>

      <div className="mt-4 space-y-3">
        <div className="flex items-center justify-between">
          <span className="text-xs text-text-muted">{t('interviews.startTime')}</span>
          <span className="text-sm text-text-secondary">
            {interview.startedAt
              ? new Date(interview.startedAt).toLocaleString(i18n.language)
              : '-'}
          </span>
        </div>
        <div className="flex items-center justify-between">
          <span className="text-xs text-text-muted">{t('interviews.endTime')}</span>
          <span className="text-sm text-text-secondary">
            {interview.endedAt
              ? new Date(interview.endedAt).toLocaleString(i18n.language)
              : '-'}
          </span>
        </div>
        {elapsed > 0 && (
          <div className="flex items-center justify-between">
            <span className="text-xs text-text-muted">{t('interviews.elapsedTime')}</span>
            <span className="text-sm font-medium text-silver-200">
              {formatDuration(elapsed)}
            </span>
          </div>
        )}
        <div className="flex items-center justify-between">
          <span className="text-xs text-text-muted">{t('interviews.personaLabel')}</span>
          <span className="text-sm text-text-secondary">
            {enumLabel('persona', interview.persona, interview.persona)}
          </span>
        </div>
      </div>
    </GlassCard>
  );
}
