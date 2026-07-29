import { motion } from 'framer-motion';
import { GlassCard } from '@/components/ui/glass-card';
import type { ChatMessage } from '@/types/interview';

interface AnswerBubbleProps {
  message: ChatMessage;
}

/** 候选人回答气泡：右侧银色竖条 */
export function AnswerBubble({ message }: AnswerBubbleProps) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3, ease: 'easeOut' }}
    >
      <GlassCard className="relative overflow-hidden p-4 pr-5">
        {/* 右侧银色竖条 */}
        <div className="absolute right-0 top-0 h-full w-[3px] bg-gradient-to-b from-silver-300 to-silver-100" />

        {/* 顶部：候选人 + 时间戳 */}
        <div className="mb-2 flex items-center justify-end gap-2">
          <span className="text-xs text-text-muted">
            {new Date(message.timestamp).toLocaleTimeString('zh-CN')}
          </span>
          <span className="text-sm font-medium text-silver-200">候选人</span>
        </div>

        {/* 回答文本 */}
        <div className="whitespace-pre-wrap break-words text-right text-sm text-text-primary">
          {message.text}
        </div>
      </GlassCard>
    </motion.div>
  );
}
