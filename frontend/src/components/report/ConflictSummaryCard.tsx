import { useTranslation } from 'react-i18next';

/** v1.1-F4：候选人与简历矛盾点卡片（报告页，无矛盾时隐藏）。 */
export function ConflictSummaryCard({ conflictSummary }: { conflictSummary: string }) {
  const { t } = useTranslation();
  if (!conflictSummary || conflictSummary.trim() === '') return null;

  const lines = conflictSummary
    .split('\n')
    .map((l) => l.trim())
    .filter((l) => l.length > 0);

  return (
    <div className="rounded-xl border border-rose-400/30 bg-rose-400/5 p-4">
      <div className="mb-2 flex items-center gap-2 text-sm font-medium text-rose-300">
        <span aria-hidden>⚠️</span>
        <span>{t('report.conflictSummaryTitle')}</span>
      </div>
      <ul className="space-y-1 text-sm text-text-primary">
        {lines.map((line, idx) => (
          <li key={idx} className="flex gap-2">
            <span className="text-rose-300/70">•</span>
            <span>{line}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
