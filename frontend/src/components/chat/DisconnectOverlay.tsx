import { motion } from 'framer-motion';
import { WifiOff } from 'lucide-react';
import { SilverButton } from '@/components/ui/silver-button';

interface DisconnectOverlayProps {
  retryCount: number;
  maxRetries: number;
  onReconnect: () => void;
}

/** 断线遮罩：全屏半透明遮罩 + 断线图标 + 重连按钮 */
export function DisconnectOverlay({
  retryCount,
  maxRetries,
  onReconnect,
}: DisconnectOverlayProps) {
  return (
    <div className="absolute inset-0 z-50 flex items-center justify-center bg-modal-scrim backdrop-blur-sm">
      <motion.div
        initial={{ opacity: 0, scale: 0.9 }}
        animate={{ opacity: 1, scale: 1 }}
        transition={{ duration: 0.2 }}
        className="flex flex-col items-center gap-4"
      >
        <WifiOff className="h-12 w-12 text-danger" />
        <p className="text-lg font-medium text-text-primary">连接已断开</p>
        {retryCount < maxRetries ? (
          <p className="text-sm text-text-muted">
            正在重连 {retryCount}/{maxRetries}...
          </p>
        ) : (
          <p className="text-sm text-danger">重连失败，请手动重试</p>
        )}
        <SilverButton variant="ghost" onClick={onReconnect}>
          重新连接
        </SilverButton>
      </motion.div>
    </div>
  );
}
