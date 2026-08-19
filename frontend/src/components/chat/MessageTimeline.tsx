import { useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { QuestionBubble } from './QuestionBubble';
import { AnswerBubble } from './AnswerBubble';
import { EndBubble } from './EndBubble';
import type { ChatMessage } from '@/types/interview';

interface MessageTimelineProps {
  messages: ChatMessage[];
}

/** 消息时间线：渲染 ChatMessage 列表，自动滚动到底部 */
export function MessageTimeline({ messages }: MessageTimelineProps) {
  const { t } = useTranslation();
  const bottomRef = useRef<HTMLDivElement>(null);

  // 消息变化时自动滚动到底部
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  if (messages.length === 0) {
    return (
      <div className="flex min-h-full items-center justify-center">
        <p className="text-sm text-text-muted">{t('interviews.waitingToStart')}</p>
      </div>
    );
  }

  return (
    <div className="space-y-4 overflow-y-auto">
      {messages.map((msg) => {
        if (msg.role === 'system') {
          return <EndBubble key={msg.id} message={msg} />;
        }
        return msg.role === 'question' ? (
          <QuestionBubble key={msg.id} message={msg} />
        ) : (
          <AnswerBubble key={msg.id} message={msg} />
        );
      })}
      <div ref={bottomRef} />
    </div>
  );
}
