import { useEffect, useRef } from 'react';
import { QuestionBubble } from './QuestionBubble';
import { AnswerBubble } from './AnswerBubble';
import type { ChatMessage } from '@/types/interview';

interface MessageTimelineProps {
  messages: ChatMessage[];
}

/** 消息时间线：渲染 ChatMessage 列表，自动滚动到底部 */
export function MessageTimeline({ messages }: MessageTimelineProps) {
  const bottomRef = useRef<HTMLDivElement>(null);

  // 预计算追问序号：同一 parentSeq 下的第几个追问
  const followUpIndexMap = new Map<string, number>();
  const parentSeqCounters = new Map<number, number>();
  messages.forEach((msg) => {
    if (msg.role === 'question' && msg.parentSeq != null) {
      const counter = (parentSeqCounters.get(msg.parentSeq) ?? 0) + 1;
      parentSeqCounters.set(msg.parentSeq, counter);
      followUpIndexMap.set(msg.id, counter);
    }
  });

  // 消息变化时自动滚动到底部
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  if (messages.length === 0) {
    return (
      <div className="flex flex-1 items-center justify-center">
        <p className="text-sm text-text-muted">等待面试开始...</p>
      </div>
    );
  }

  return (
    <div className="space-y-4 overflow-y-auto">
      {messages.map((msg) =>
        msg.role === 'question' ? (
          <QuestionBubble
            key={msg.id}
            message={msg}
            followUpIndex={followUpIndexMap.get(msg.id)}
          />
        ) : (
          <AnswerBubble key={msg.id} message={msg} />
        ),
      )}
      <div ref={bottomRef} />
    </div>
  );
}
