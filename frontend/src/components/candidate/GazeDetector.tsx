import { useTranslation } from 'react-i18next';
import { GlassCard } from '@/components/ui/glass-card';
import { SilverButton } from '@/components/ui/silver-button';
import { Camera } from 'lucide-react';
import type { GazeDetectionHandle } from '@/hooks/useGazeDetection';

/**
 * 眼神检测授权弹窗（无蒙版背板，复用 PlanConfigDialog 交互模式）：
 * 仅当 hook 处于 'prompt'（等待授权）且面试进行中时渲染。
 * 同意 → 启动摄像头本地推理；拒绝 → 记录 CAMERA_DENIED，不阻断面试。
 */
export function GazeDetector({ gaze }: { gaze: GazeDetectionHandle }) {
  const { t } = useTranslation();

  if (gaze.camState !== 'prompt') return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" onClick={gaze.deny}>
      <div className="w-full max-w-md" onClick={(e) => e.stopPropagation()}>
        <GlassCard className="p-6">
          <div className="mb-3 flex items-center gap-2">
            <Camera className="h-5 w-5 text-text-muted" />
            <h2 className="text-lg font-semibold text-text-primary">{t('proctor.gazeDialogTitle')}</h2>
          </div>
          <p className="mb-5 text-sm text-text-muted">{t('proctor.gazeDialogHint')}</p>
          <div className="flex justify-end gap-2">
            <SilverButton variant="ghost" onClick={gaze.deny}>
              {t('common.cancel')}
            </SilverButton>
            <SilverButton onClick={gaze.grant}>{t('proctor.gazeAllow')}</SilverButton>
          </div>
        </GlassCard>
      </div>
    </div>
  );
}
