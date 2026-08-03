import { useEffect, useState } from 'react';
import { GlassCard } from '@/components/ui/glass-card';
import { StatusDot } from '@/components/ui/status-dot';
import { PERSONA_LABELS, SESSION_STATUS_LABELS } from '@/lib/constants';
import type { InterviewResponse } from '@/types/interview';

interface StatusCardProps {
  interview: InterviewResponse;
}

/** 计算已用时长（毫秒 -> 可读文本） */
function formatDuration(ms: number): string {
  const totalSec = Math.floor(ms / 1000);
  const h = Math.floor(totalSec / 3600);
  const m = Math.floor((totalSec % 3600) / 60);
  const s = totalSec % 60;
  if (h > 0) return `${h}时${m}分${s}秒`;
  if (m > 0) return `${m}分${s}秒`;
  return `${s}秒`;
}

/** 面试状态卡片：状态指示灯、时间信息、已用时长 */
export function StatusCard({ interview }: StatusCardProps) {
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

  return (
    <GlassCard className="p-5">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-medium text-text-muted">面试状态</h3>
        <StatusDot
          status={interview.status}
          label={SESSION_STATUS_LABELS[interview.status] ?? interview.status}
        />
      </div>

      <div className="mt-4 space-y-3">
        <div className="flex items-center justify-between">
          <span className="text-xs text-text-muted">开始时间</span>
          <span className="text-sm text-text-secondary">
            {interview.startedAt
              ? new Date(interview.startedAt).toLocaleString('zh-CN')
              : '-'}
          </span>
        </div>
        <div className="flex items-center justify-between">
          <span className="text-xs text-text-muted">结束时间</span>
          <span className="text-sm text-text-secondary">
            {interview.endedAt
              ? new Date(interview.endedAt).toLocaleString('zh-CN')
              : '-'}
          </span>
        </div>
        {elapsed > 0 && (
          <div className="flex items-center justify-between">
            <span className="text-xs text-text-muted">已用时长</span>
            <span className="text-sm font-medium text-silver-200">
              {formatDuration(elapsed)}
            </span>
          </div>
        )}
        <div className="flex items-center justify-between">
          <span className="text-xs text-text-muted">面试官人设</span>
          <span className="text-sm text-text-secondary">
            {PERSONA_LABELS[interview.persona] ?? interview.persona}
          </span>
        </div>
      </div>
    </GlassCard>
  );
}
