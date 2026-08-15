import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { GlassCard } from '@/components/ui/glass-card';
import { SilverButton } from '@/components/ui/silver-button';
import type { StartPlanBody } from '@/api/interview';

interface PlanConfigDialogProps {
  open: boolean;
  onClose: () => void;
  onConfirm: (params: StartPlanBody) => void;
}

/** 面试计划生成参数配置对话框 */
export function PlanConfigDialog({ open, onClose, onConfirm }: PlanConfigDialogProps) {
  const { t } = useTranslation();
  const [questionCount, setQuestionCount] = useState(10);
  const [difficulty, setDifficulty] = useState<'BASIC' | 'BALANCED' | 'ADVANCED'>('BALANCED');

  const DIFFICULTY_OPTIONS = [
    { value: 'BASIC', labelKey: 'interviews.planDifficultyBasic', descKey: 'interviews.planDifficultyDescBasic', minutes: 2 },
    { value: 'BALANCED', labelKey: 'interviews.planDifficultyBalanced', descKey: 'interviews.planDifficultyDescBalanced', minutes: 3 },
    { value: 'ADVANCED', labelKey: 'interviews.planDifficultyAdvanced', descKey: 'interviews.planDifficultyDescAdvanced', minutes: 5 },
  ] as const;

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
          <h2 className="mb-1 text-lg font-semibold text-text-primary">{t('interviews.planDialogTitle')}</h2>
          <p className="mb-5 text-sm text-text-muted">{t('interviews.planDialogSubtitle')}</p>

          {/* 题数输入框 */}
          <div className="mb-5">
            <label className="mb-2 block text-sm font-medium text-text-secondary">
              {t('interviews.questionCount')}
              <span className="ml-2 text-xs text-text-muted">{t('interviews.questionCountRange')}</span>
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
            <label className="mb-2 block text-sm font-medium text-text-secondary">{t('interviews.difficultyPreference')}</label>
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
                    <span className="text-sm font-medium text-text-primary">{t(opt.labelKey)}</span>
                    <span className="ml-2 text-xs text-text-muted">{t(opt.descKey)}</span>
                  </div>
                  <span className="text-xs text-text-muted">{t('interviews.minutesPerQuestion', { minutes: opt.minutes })}</span>
                </button>
              ))}
            </div>
          </div>

          {/* 预计时长（自动推算） */}
          <div className="mb-6 rounded-lg border border-border-subtle bg-surface-overlay px-3 py-2.5">
            <span className="text-sm text-text-muted">{t('interviews.estimatedDuration')}</span>
            <span className="ml-2 text-sm font-semibold text-text-primary">{t('interviews.estimatedMinutes', { minutes: estimatedMinutes })}</span>
            <span className="ml-2 text-xs text-text-muted">{t('interviews.estimatedDetail', { count: questionCount, minutes: selectedDiff.minutes })}</span>
          </div>

          {/* 操作按钮 */}
          <div className="flex justify-end gap-3">
            <SilverButton variant="ghost" onClick={onClose}>
              {t('common.cancel')}
            </SilverButton>
            <SilverButton onClick={handleConfirm}>{t('interviews.confirmGenerate')}</SilverButton>
          </div>
        </GlassCard>
      </div>
    </div>
  );
}
