import { Mic, Square } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { cn } from '@/lib/utils';

interface VoiceInputButtonProps {
  supported: boolean;
  listening: boolean;
  disabled?: boolean;
  onClick: () => void;
}

/** 语音输入按钮：空闲为银色轮廓麦克风，录音中为红色脉动方块（点击停止） */
export function VoiceInputButton({ supported, listening, disabled = false, onClick }: VoiceInputButtonProps) {
  const { t } = useTranslation();
  if (!supported) return null;

  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      title={listening ? t('speech.stop') : t('speech.voiceInput')}
      aria-label={listening ? t('speech.stop') : t('speech.voiceInput')}
      className={cn(
        'inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-md border transition-all',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-silver-glow/50',
        listening
          ? 'border-danger/50 bg-danger/15 text-danger shadow-[0_0_12px_var(--danger)/0.4]'
          : 'border-border-default text-text-secondary hover:bg-surface-hover hover:text-text-primary hover:border-border-strong',
        disabled && 'pointer-events-none opacity-40',
      )}
    >
      {listening ? (
        <span className="relative flex h-4 w-4 items-center justify-center">
          <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-danger/40" />
          <Square className="relative h-2.5 w-2.5 fill-current" />
        </span>
      ) : (
        <Mic className="h-4 w-4" />
      )}
    </button>
  );
}
