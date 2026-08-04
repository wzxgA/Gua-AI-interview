import { motion } from 'framer-motion';
import { GlassCard } from '@/components/ui/glass-card';
import { TypewriterCursor } from './TypewriterCursor';
import { MarkdownRenderer } from './MarkdownRenderer';
import { AudioPlayer } from './AudioPlayer';
import type { ChatMessage } from '@/types/interview';

interface QuestionBubbleProps {
  message: ChatMessage;
}

/** 按 followUpType 映射色系 */
const FOLLOW_UP_COLORS: Record<string, { stripe: string; badge: string }> = {
  CLARIFY: {
    stripe: 'from-amber-400 to-amber-200',
    badge: 'border-amber-400/30 bg-amber-500/10 text-amber-400',
  },
  DEEPEN: {
    stripe: 'from-orange-400 to-orange-200',
    badge: 'border-orange-400/30 bg-orange-500/10 text-orange-400',
  },
  REDIRECT: {
    stripe: 'from-sky-400 to-sky-200',
    badge: 'border-sky-400/30 bg-sky-500/10 text-sky-400',
  },
};

/** AI 面试官问题气泡：左侧竖条（主问题银色 / 追问按类型分色），流式文本 + 打字机光标 */
export function QuestionBubble({ message }: QuestionBubbleProps) {
  const isFollowUp = message.parentSeq != null && message.followUpType;
  const color = FOLLOW_UP_COLORS[message.followUpType ?? ''] ?? FOLLOW_UP_COLORS.CLARIFY;

  const stripeClass = isFollowUp
    ? `bg-gradient-to-b ${color.stripe}`
    : 'bg-gradient-to-b from-silver-300 to-silver-100';

  const seqLabel = isFollowUp
    ? `Q${message.parentSeq}.${message.followUpIndex ?? 1}`
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
                  ? color.badge
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
          <>
            <MarkdownRenderer
              content={message.text}
              className="text-sm text-text-primary"
            />
            {message.audioUrl && (
              <AudioPlayer audioUrl={message.audioUrl} durationMs={message.durationMs} />
            )}
          </>
        )}
      </GlassCard>
    </motion.div>
  );
}
