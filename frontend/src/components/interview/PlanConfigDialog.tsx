import { useState } from 'react';
import { GlassCard } from '@/components/ui/glass-card';
import { SilverButton } from '@/components/ui/silver-button';
import type { StartPlanBody } from '@/api/interview';

interface PlanConfigDialogProps {
  open: boolean;
  onClose: () => void;
  onConfirm: (params: StartPlanBody) => void;
}

const DIFFICULTY_OPTIONS = [
  { value: 'BASIC', label: '基础', desc: '侧重核心概念和基本功', minutes: 2 },
  { value: 'BALANCED', label: '均衡', desc: '基础与进阶合理搭配', minutes: 3 },
  { value: 'ADVANCED', label: '深入', desc: '侧重系统设计和底层原理', minutes: 5 },
] as const;

/** 面试计划生成参数配置对话框 */
export function PlanConfigDialog({ open, onClose, onConfirm }: PlanConfigDialogProps) {
  const [questionCount, setQuestionCount] = useState(10);
  const [difficulty, setDifficulty] = useState<'BASIC' | 'BALANCED' | 'ADVANCED'>('BALANCED');

  if (!open) return null;

  const selectedDiff = DIFFICULTY_OPTIONS.find((d) => d.value === difficulty)!;
  const estimatedMinutes = questionCount * selectedDiff.minutes;

  const handleConfirm = () => {
    onConfirm({ questionCount, difficulty });
  };

  const handleCountChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const v = parseInt(e.target.value, 10);
    if (isNaN(v)) {
      setQuestionCount(1);
      return;
    }
    setQuestionCount(Math.max(1, Math.min(30, v)));
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
      onClick={onClose}
    >
      <div className="w-full max-w-md" onClick={(e) => e.stopPropagation()}>
        <GlassCard className="p-6">
          <h2 className="mb-1 text-lg font-semibold text-text-primary">生成面试计划</h2>
          <p className="mb-5 text-sm text-text-muted">配置面试参数，AI 将据此生成结构化面试计划</p>

          {/* 题数输入框 */}
          <div className="mb-5">
            <label className="mb-2 block text-sm font-medium text-text-secondary">
              面试题数
              <span className="ml-2 text-xs text-text-muted">（1-30 题）</span>
            </label>
            <input
              type="number"
              min={1}
              max={30}
              value={questionCount}
              onChange={handleCountChange}
              className="w-full rounded-lg border border-border-default bg-surface-overlay px-3 py-2 text-sm text-text-primary outline-none focus:border-silver-400"
            />
          </div>

          {/* 难度偏好 */}
          <div className="mb-5">
            <label className="mb-2 block text-sm font-medium text-text-secondary">难度偏好</label>
            <div className="space-y-2">
              {DIFFICULTY_OPTIONS.map((opt) => (
                <button
                  key={opt.value}
                  onClick={() => setDifficulty(opt.value)}
                  className={`flex w-full items-center justify-between rounded-lg border px-3 py-2.5 text-left transition-colors ${
                    difficulty === opt.value
                      ? 'border-silver-400 bg-silver-400/10'
                      : 'border-border-default bg-surface-overlay hover:border-border-strong'
                  }`}
                >
                  <div>
                    <span className="text-sm font-medium text-text-primary">{opt.label}</span>
                    <span className="ml-2 text-xs text-text-muted">{opt.desc}</span>
                  </div>
                  <span className="text-xs text-text-muted">{opt.minutes}min/题</span>
                </button>
              ))}
            </div>
          </div>

          {/* 预计时长（自动推算） */}
          <div className="mb-6 rounded-lg border border-border-subtle bg-surface-overlay px-3 py-2.5">
            <span className="text-sm text-text-muted">预计时长</span>
            <span className="ml-2 text-sm font-semibold text-text-primary">约 {estimatedMinutes} 分钟</span>
            <span className="ml-2 text-xs text-text-muted">（{questionCount} 题 × {selectedDiff.minutes} 分钟/题）</span>
          </div>

          {/* 操作按钮 */}
          <div className="flex justify-end gap-3">
            <SilverButton variant="ghost" onClick={onClose}>
              取消
            </SilverButton>
            <SilverButton onClick={handleConfirm}>确认生成</SilverButton>
          </div>
        </GlassCard>
      </div>
    </div>
  );
}
