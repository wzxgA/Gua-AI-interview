import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import { GlassCard } from '@/components/ui/glass-card';
import type { ProctorEvent } from '@/types/interview';

/** 事件类型 → i18n key */
function eventTypeKey(type: string): string {
  switch (type) {
    case 'TAB_SWITCH':
      return 'proctor.eventTabSwitch';
    case 'WINDOW_BLUR':
      return 'proctor.eventWindowBlur';
    case 'CAMERA_DENIED':
      return 'proctor.eventCameraDenied';
    case 'CAMERA_OFF':
      return 'proctor.eventCameraOff';
    case 'CAMERA_ON':
      return 'proctor.eventCameraOn';
    case 'GAZE_AWAY':
      return 'proctor.eventGazeAway';
    case 'FACE_LOST':
      return 'proctor.eventFaceLost';
    default:
      return 'proctor.eventUnknown';
  }
}

function formatTime(iso: string | null): string {
  if (!iso) return '--:--:--';
  const d = new Date(iso);
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:${String(
    d.getSeconds(),
  ).padStart(2, '0')}`;
}

function formatDuration(ms: number | null): string {
  if (ms == null) return '';
  const s = Math.round(ms / 1000);
  return s >= 60 ? `${Math.floor(s / 60)}m${String(s % 60).padStart(2, '0')}s` : `${s}s`;
}

/** 防作弊实时事件面板（控制台，5s 轮询，仅开启防作弊时展示） */
export function ProctorLivePanel({ events }: { events?: ProctorEvent[] }) {
  const { t } = useTranslation();
  const list = events ?? [];
  // 倒序：最新事件在前
  const sorted = [...list].sort((a, b) => b.id - a.id);

  return (
    <GlassCard className="p-5">
      <h3 className="mb-3 text-sm font-medium text-text-muted">{t('proctor.liveTitle')}</h3>
      {sorted.length === 0 ? (
        <p className="text-sm text-text-muted">{t('proctor.empty')}</p>
      ) : (
        <ul className="max-h-64 space-y-1.5 overflow-y-auto pr-1">
          {sorted.map((e) => (
            <motion.li
              key={e.id}
              initial={{ opacity: 0, y: -6 }}
              animate={{ opacity: 1, y: 0 }}
              className="flex items-center justify-between rounded-md border border-border-subtle bg-surface-overlay px-3 py-2 text-sm"
            >
              <span className="text-text-primary">{t(eventTypeKey(e.eventType))}</span>
              <span className="flex items-center gap-3 text-xs text-text-muted">
                {formatDuration(e.durationMs) && <span>{formatDuration(e.durationMs)}</span>}
                <span>{formatTime(e.occurredAt)}</span>
              </span>
            </motion.li>
          ))}
        </ul>
      )}
    </GlassCard>
  );
}
