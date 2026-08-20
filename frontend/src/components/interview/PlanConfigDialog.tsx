import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { GlassCard } from '@/components/ui/glass-card';
import { SilverButton } from '@/components/ui/silver-button';
import { Checkbox } from '@/components/ui/checkbox';
import type { StartPlanBody } from '@/api/interview';

interface PlanConfigDialogProps {
  open: boolean;
  onClose: () => void;
  onConfirm: (params: StartPlanBody) => void;
}

/** 面试计划生成参数配置对话框 */
export function PlanConfigDialog({ open, onClose, onConfirm }: PlanConfigDialogProps) {
  const { t } = useTranslation();
  const [questionCount, setQuestionCount] = useState('5');
  const [difficulty, setDifficulty] = useState<'BASIC' | 'BALANCED' | 'ADVANCED'>('BALANCED');
  const [ttsEnabled, setTtsEnabled] = useState(false);

  const DIFFICULTY_OPTIONS = [
    { value: 'BASIC', labelKey: 'interviews.planDifficultyBasic', descKey: 'interviews.planDifficultyDescBasic', minutes: 2 },
    { value: 'BALANCED', labelKey: 'interviews.planDifficultyBalanced', descKey: 'interviews.planDifficultyDescBalanced', minutes: 3 },
    { value: 'ADVANCED', labelKey: 'interviews.planDifficultyAdvanced', descKey: 'interviews.planDifficultyDescAdvanced', minutes: 5 },
  ] as const;

  if (!open) return null;

  const selectedDiff = DIFFICULTY_OPTIONS.find((d) => d.value === difficulty)!;

  const MIN_COUNT = 1;
  const MAX_COUNT = 30;
  const parsedCount = Number(questionCount);
  const countValid = Number.isInteger(parsedCount) && parsedCount >= MIN_COUNT && parsedCount <= MAX_COUNT;
  const estimatedMinutes = countValid ? parsedCount * selectedDiff.minutes : null;

  const handleConfirm = () => {
    if (!countValid) return;
    onConfirm({ questionCount: parsedCount, difficulty, ttsEnabled });
  };

  const handleCountChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    // 允许空串/中间态输入，不即时 clamp，由校验决定是否爆红
    setQuestionCount(e.target.value);
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
              type="text"
              inputMode="numeric"
              value={questionCount}
              onChange={handleCountChange}
              aria-invalid={!countValid}
              className={`w-full rounded-lg border px-3 py-2 text-sm text-text-primary outline-none focus:border-silver-400 ${
                countValid
                  ? 'border-border-default bg-surface-overlay'
                  : 'border-danger bg-danger/5 focus:border-danger'
              }`}
            />
            {!countValid && (
              <p className="mt-1.5 text-xs text-danger">{t('interviews.questionCountInvalid')}</p>
            )}
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

          {/* 面试官 TTS 语音 */}
          <div className="mb-5">
            <button
              type="button"
              onClick={() => setTtsEnabled(!ttsEnabled)}
              className="flex w-full items-start gap-3 rounded-lg border border-border-default bg-surface-overlay px-3 py-3 text-left transition-colors hover:border-border-strong"
            >
              <Checkbox
                checked={ttsEnabled}
                readOnly
                className="mt-0.5"
              />
              <span>
                <span className="block text-sm font-medium text-text-primary">{t('interviews.ttsEnabled')}</span>
                <span className="mt-0.5 block text-xs text-text-muted">{t('interviews.ttsEnabledHint')}</span>
              </span>
            </button>
          </div>

          {/* 预计时长（自动推算） */}
          <div className="mb-6 rounded-lg border border-border-subtle bg-surface-overlay px-3 py-2.5">
            <span className="text-sm text-text-muted">{t('interviews.estimatedDuration')}</span>
            {estimatedMinutes !== null ? (
              <>
                <span className="ml-2 text-sm font-semibold text-text-primary">{t('interviews.estimatedMinutes', { minutes: estimatedMinutes })}</span>
                <span className="ml-2 text-xs text-text-muted">{t('interviews.estimatedDetail', { count: parsedCount, minutes: selectedDiff.minutes })}</span>
              </>
            ) : (
              <span className="ml-2 text-sm text-text-muted">—</span>
            )}
          </div>

          {/* 操作按钮 */}
          <div className="flex justify-end gap-3">
            <SilverButton variant="ghost" onClick={onClose}>
              {t('common.cancel')}
            </SilverButton>
            <SilverButton onClick={handleConfirm} disabled={!countValid}>
              {t('interviews.confirmGenerate')}
            </SilverButton>
          </div>
        </GlassCard>
      </div>
    </div>
  );
}
