import { useState } from 'react';
import { GlassCard } from '@/components/ui/glass-card';
import { SilverButton } from '@/components/ui/silver-button';
import { Textarea } from '@/components/ui/input';

interface AnswerInputProps {
  onSend: (text: string) => void;
  disabled?: boolean;
}

const MAX_LENGTH = 10000;

/** 回答输入区：多行文本域、字数统计、Ctrl+Enter 快捷发送 */
export function AnswerInput({ onSend, disabled = false }: AnswerInputProps) {
  const [text, setText] = useState('');

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

  return (
    <GlassCard className="p-4">
      <Textarea
        value={text}
        onChange={(e) => setText(e.target.value.slice(0, MAX_LENGTH))}
        onKeyDown={handleKeyDown}
        placeholder="输入你的回答...（Ctrl+Enter 发送）"
        rows={4}
        disabled={disabled}
        className="resize-none"
      />
      <div className="mt-2 flex items-center justify-between">
        <span className="text-xs text-text-muted">
          {text.length}/{MAX_LENGTH}
        </span>
        <SilverButton
          onClick={handleSend}
          disabled={disabled || !text.trim()}
        >
          发送
        </SilverButton>
      </div>
    </GlassCard>
  );
}
