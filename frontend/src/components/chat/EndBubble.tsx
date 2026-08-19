import { useTranslation } from 'react-i18next';
import type { ChatMessage } from '@/types/interview';

interface EndBubbleProps {
  message: ChatMessage;
}

/** 结束回执气泡：会话进入终态/评估中时居中展示感谢语与结束原因，消除"突兀置灰"。 */
export function EndBubble({ message }: EndBubbleProps) {
  const { t } = useTranslation();
  const { status, finishedBy, finishReason } = message;

  const resolveTitle = () => {
    if (finishReason === 'CANCELLED') return t('interviews.ending.titleCancelled');
    if (finishReason === 'FAILED' || status === 'FAILED') {
      return t('interviews.ending.titleFailed');
    }
    if (status === 'COMPLETED' || finishReason === 'COMPLETED') {
      return t('interviews.ending.titleDone');
    }
    return t('interviews.ending.titleFinished');
  };

  const resolveBody = () => {
    if (finishReason === 'CANCELLED') return t('interviews.ending.bodyCancelled');
    if (finishReason === 'FAILED' || status === 'FAILED') {
      return t('interviews.ending.bodyFailed');
    }
    if (status === 'COMPLETED') return t('interviews.ending.bodyDone');
    if (finishedBy === 'CANDIDATE') return t('interviews.ending.bodyFinishedCandidate');
    if (finishedBy === 'ADMIN') return t('interviews.ending.bodyFinishedAdmin');
    return t('interviews.ending.bodyDone');
  };

  return (
    <div className="flex justify-center px-4">
      <div className="max-w-md rounded-2xl border border-border-default bg-space-700/50 px-5 py-4 text-center backdrop-blur-sm">
        <p className="text-sm font-medium text-silver-100">{resolveTitle()}</p>
        <p className="mt-1.5 text-xs leading-relaxed text-text-muted">{resolveBody()}</p>
        {status === 'EVALUATING' && (
          <p className="mt-2 text-xs text-amber-300">{t('interviews.ending.evaluatingHint')}</p>
        )}
      </div>
    </div>
  );
}