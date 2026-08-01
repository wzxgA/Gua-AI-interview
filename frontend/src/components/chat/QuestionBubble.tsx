import { motion } from 'framer-motion';
import { GlassCard } from '@/components/ui/glass-card';
import { TypewriterCursor } from './TypewriterCursor';
import { MarkdownRenderer } from './MarkdownRenderer';
import type { ChatMessage } from '@/types/interview';

interface QuestionBubbleProps {
  message: ChatMessage;
  followUpIndex?: number;
}

/** AI 面试官问题气泡：左侧竖条（主问题银色 / 追问 amber），流式文本 + 打字机光标 */
export function QuestionBubble({ message, followUpIndex }: QuestionBubbleProps) {
  const isFollowUp = message.parentSeq != null && message.followUpType;
  const stripeClass = isFollowUp
    ? 'bg-gradient-to-b from-amber-400 to-amber-200'
    : 'bg-gradient-to-b from-silver-300 to-silver-100';

  const seqLabel = isFollowUp
    ? `Q${message.parentSeq}.${followUpIndex ?? 1}`
    : message.seq != null
      ? `Q${message.seq}`
      : null;

  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3, ease: 'easeOut' }}
    >
      <GlassCard className="relative overflow-hidden p-4 pl-5">
        {/* 左侧竖条 */}
        <div className={`absolute left-0 top-0 h-full w-[3px] ${stripeClass}`} />

        {/* 顶部：AI 面试官 + 序号 */}
        <div className="mb-2 flex items-center gap-2">
          <span className="text-sm font-medium text-silver-200">AI 面试官</span>
          {seqLabel && (
            <span
              className={`rounded-full border px-2 py-0.5 text-xs ${
                isFollowUp
                  ? 'border-amber-400/30 bg-amber-500/10 text-amber-400'
                  : 'border-border-default bg-surface-hover text-text-muted'
              }`}
            >
              {seqLabel}
            </span>
          )}
        </div>

        {/* 流式文本 + 打字机光标 / 非流式 Markdown 渲染 */}
        {message.streaming ? (
          <div className="whitespace-pre-wrap break-words text-sm text-text-primary">
            {message.text}
            <TypewriterCursor />
          </div>
        ) : (
          <MarkdownRenderer
            content={message.text}
            className="text-sm text-text-primary"
          />
        )}
      </GlassCard>
    </motion.div>
  );
}
