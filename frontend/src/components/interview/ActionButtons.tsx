import { useTranslation } from 'react-i18next';
import { SilverButton } from '@/components/ui/silver-button';
import type { SessionStatus } from '@/types/interview';

interface ActionButtonsProps {
  status: SessionStatus;
  /** 生成面试计划进行中：禁用「生成计划/开始面试」按钮，避免并发提交 */
  planning?: boolean;
  onStart?: () => void;
  onBeginInterview?: () => void;
  onCancel: () => void;
  onEnterRoom?: () => void;
  onPause?: () => void;
  onFinish?: () => void;
  onResume?: () => void;
  onViewReport?: () => void;
  onBack?: () => void;
}

/** 按状态显示操作按钮 */
export function ActionButtons({
  status,
  planning = false,
  onStart,
  onBeginInterview,
  onCancel,
  onEnterRoom,
  onPause,
  onFinish,
  onResume,
  onViewReport,
}: ActionButtonsProps) {
  const { t } = useTranslation();

  switch (status) {
    case 'CREATED':
      return (
        <div className="flex flex-wrap gap-2">
          {onStart && (
            <SilverButton onClick={onStart} disabled={planning}>
              {planning ? t('interviews.generatingPlan') : t('interviews.startPlan')}
            </SilverButton>
          )}
          <SilverButton variant="danger" onClick={onCancel}>
            {t('common.cancel')}
          </SilverButton>
        </div>
      );

    case 'PLANNING':
      return (
        <div className="flex flex-wrap gap-2">
          {onBeginInterview && (
            <SilverButton onClick={onBeginInterview} disabled={planning}>
              {planning ? t('interviews.generatingPlan') : t('interviews.beginInterview')}
            </SilverButton>
          )}
          <SilverButton variant="danger" onClick={onCancel}>
            {t('common.cancel')}
          </SilverButton>
        </div>
      );

    case 'IN_PROGRESS':
      return (
        <div className="flex flex-wrap gap-2">
          {onEnterRoom && (
            <SilverButton onClick={onEnterRoom}>{t('interviews.enterRoom')}</SilverButton>
          )}
          {onPause && (
            <SilverButton variant="ghost" onClick={onPause}>
              {t('interviews.pause')}
            </SilverButton>
          )}
          {onFinish && (
            <SilverButton variant="ghost" onClick={onFinish}>
              {t('interviews.finish')}
            </SilverButton>
          )}
          <SilverButton variant="danger" onClick={onCancel}>
            {t('common.cancel')}
          </SilverButton>
        </div>
      );

    case 'PAUSED':
      return (
        <div className="flex flex-wrap gap-2">
          {onEnterRoom && (
            <SilverButton variant="ghost" onClick={onEnterRoom}>
              {t('interviews.enterRoom')}
            </SilverButton>
          )}
          {onResume && (
            <SilverButton onClick={onResume}>{t('interviews.resume')}</SilverButton>
          )}
          {onFinish && (
            <SilverButton variant="ghost" onClick={onFinish}>
              {t('interviews.finish')}
            </SilverButton>
          )}
          <SilverButton variant="danger" onClick={onCancel}>
            {t('common.cancel')}
          </SilverButton>
        </div>
      );

    case 'EVALUATING':
      return (
        <div className="flex flex-wrap gap-2">
          <SilverButton disabled>{t('interviews.evaluatingButton')}</SilverButton>
        </div>
      );

    case 'REPORTING':
      return (
        <div className="flex flex-wrap gap-2">
          <SilverButton disabled>{t('interviews.reportingButton')}</SilverButton>
        </div>
      );

    case 'COMPLETED':
      return (
        <div className="flex flex-wrap gap-2">
          {onViewReport && (
            <SilverButton onClick={onViewReport}>{t('interviews.viewReport')}</SilverButton>
          )}
          {onEnterRoom && (
            <SilverButton variant="ghost" onClick={onEnterRoom}>
              {t('interviews.review')}
            </SilverButton>
          )}
        </div>
      );

    case 'FAILED':
    case 'CANCELLED':
      return (
        <div className="flex flex-wrap gap-2">
          {onEnterRoom && (
            <SilverButton onClick={onEnterRoom}>{t('interviews.review')}</SilverButton>
          )}
        </div>
      );

    default:
      return null;
  }
}
