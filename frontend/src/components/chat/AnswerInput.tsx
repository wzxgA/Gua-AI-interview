import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { GlassCard } from '@/components/ui/glass-card';
import { SilverButton } from '@/components/ui/silver-button';
import { Textarea } from '@/components/ui/input';
import { VoiceInputButton } from '@/components/ui/VoiceInputButton';
import { useSpeechToText, type SpeechError } from '@/hooks/useSpeechToText';
import { useLanguage } from '@/contexts/LanguageContext';

interface AnswerInputProps {
  onSend: (text: string) => void;
  disabled?: boolean;
}

const MAX_LENGTH = 10000;

/** 回答输入区：多行文本域、字数统计、Ctrl+Enter 快捷发送、语音输入 */
export function AnswerInput({ onSend, disabled = false }: AnswerInputProps) {
  const { t } = useTranslation();
  const { language } = useLanguage();
  const [text, setText] = useState('');
  // 标记当前文本是否由语音转写写入：为 true 时后续语音结果覆盖，否则追加
  const speechActiveRef = useRef(false);

  const { supported, listening, error, start, stop, resetError } = useSpeechToText({
    lang: language,
    onResult: (final) => {
      setText((prev) => {
        const next = speechActiveRef.current ? final : `${prev}${final}`;
        return next.slice(0, MAX_LENGTH);
      });
    },
  });

  // 语音识别错误 → toast 提示（不阻塞操作）
  useEffect(() => {
    if (!error) return;
    const key: Record<SpeechError, string> = {
      'not-allowed': 'speech.permissionDenied',
      'no-speech': 'speech.noSpeech',
      'audio-capture': 'speech.audioCapture',
      network: 'speech.networkError',
      aborted: '',
      'service-not-allowed': 'speech.serviceNotAllowed',
      generic: 'speech.genericError',
    };
    const msg = key[error];
    if (msg) toast.error(t(msg));
    resetError();
  }, [error, t, resetError]);

  const handleSend = () => {
    if (!text.trim() || disabled) return;
    onSend(text);
    setText('');
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleVoiceToggle = () => {
    if (listening) {
      stop();
    } else {
      speechActiveRef.current = true;
      start();
    }
  };

  return (
    <GlassCard className="p-4">
      <Textarea
        value={text}
        onChange={(e) => {
          speechActiveRef.current = false;
          setText(e.target.value.slice(0, MAX_LENGTH));
        }}
        onKeyDown={handleKeyDown}
        placeholder={t('interviews.answerPlaceholder')}
        rows={3}
        disabled={disabled}
        className="resize-none"
      />
      <div className="mt-2 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <VoiceInputButton
            supported={supported}
            listening={listening}
            disabled={disabled}
            onClick={handleVoiceToggle}
          />
          {listening && (
            <span className="text-xs text-danger animate-pulse">{t('speech.listening')}</span>
          )}
        </div>
        <SilverButton onClick={handleSend} disabled={disabled || !text.trim()}>
          {t('interviews.send')}
        </SilverButton>
      </div>
    </GlassCard>
  );
}
