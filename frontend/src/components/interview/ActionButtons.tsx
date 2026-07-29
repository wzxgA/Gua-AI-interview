import { SilverButton } from '@/components/ui/silver-button';
import type { SessionStatus } from '@/types/interview';

interface ActionButtonsProps {
  status: SessionStatus;
  onStart?: () => void;
  onCancel: () => void;
  onEnterRoom?: () => void;
  onPause?: () => void;
  onFinish?: () => void;
  onResume?: () => void;
  onBack: () => void;
}

/** 按状态显示操作按钮 */
export function ActionButtons({
  status,
  onStart,
  onCancel,
  onEnterRoom,
  onPause,
  onFinish,
  onResume,
  onBack,
}: ActionButtonsProps) {
  switch (status) {
    case 'CREATED':
      return (
        <div className="flex flex-wrap gap-2">
          {onStart && (
            <SilverButton onClick={onStart}>开始</SilverButton>
          )}
          <SilverButton variant="danger" onClick={onCancel}>
            取消
          </SilverButton>
        </div>
      );

    case 'PLANNING':
      return (
        <div className="flex flex-wrap gap-2">
          <SilverButton disabled>规划中，请稍候...</SilverButton>
        </div>
      );

    case 'IN_PROGRESS':
      return (
        <div className="flex flex-wrap gap-2">
          {onEnterRoom && (
            <SilverButton onClick={onEnterRoom}>进入面试间</SilverButton>
          )}
          {onPause && (
            <SilverButton variant="ghost" onClick={onPause}>
              暂停
            </SilverButton>
          )}
          {onFinish && (
            <SilverButton variant="ghost" onClick={onFinish}>
              结束
            </SilverButton>
          )}
          <SilverButton variant="danger" onClick={onCancel}>
            取消
          </SilverButton>
        </div>
      );

    case 'PAUSED':
      return (
        <div className="flex flex-wrap gap-2">
          {onResume && (
            <SilverButton onClick={onResume}>恢复</SilverButton>
          )}
          {onFinish && (
            <SilverButton variant="ghost" onClick={onFinish}>
              结束
            </SilverButton>
          )}
          <SilverButton variant="danger" onClick={onCancel}>
            取消
          </SilverButton>
        </div>
      );

    case 'COMPLETED':
    case 'FAILED':
    case 'CANCELLED':
      return (
        <div className="flex flex-wrap gap-2">
          <SilverButton variant="ghost" onClick={onBack}>
            返回列表
          </SilverButton>
        </div>
      );

    default:
      return null;
  }
}
