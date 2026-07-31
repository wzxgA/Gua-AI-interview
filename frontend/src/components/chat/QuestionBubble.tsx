import { motion } from 'framer-motion';
import { GlassCard } from '@/components/ui/glass-card';
import { TypewriterCursor } from './TypewriterCursor';
import { MarkdownRenderer } from './MarkdownRenderer';
import type { ChatMessage } from '@/types/interview';

interface QuestionBubbleProps {
  message: ChatMessage;
}

/** AI 面试官问题气泡：左侧银色竖条，流式文本 + 打字机光标 */
export function QuestionBubble({ message }: QuestionBubbleProps) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3, ease: 'easeOut' }}
    >
      <GlassCard className="relative overflow-hidden p-4 pl-5">
        {/* 左侧银色竖条 */}
        <div className="absolute left-0 top-0 h-full w-[3px] bg-gradient-to-b from-silver-300 to-silver-100" />

        {/* 顶部：AI 面试官 + Q{seq} */}
        <div className="mb-2 flex items-center gap-2">
          <span className="text-sm font-medium text-silver-200">AI 面试官</span>
          {message.seq != null && (
            <span className="rounded-full border border-white/10 bg-white/5 px-2 py-0.5 text-xs text-text-muted">
              Q{message.seq}
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
