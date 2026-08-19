import { useTranslation } from 'react-i18next';
import type { RoundResponse } from '@/types/interview';
import type { EvaluationResponse } from '@/types/report';
import { GlassCard } from '@/components/ui/glass-card';

interface RoundEvaluationListProps {
  /** 面试轮次列表（含主问题/追问层级信息）；为空时回退为按轮次平铺 */
  rounds: RoundResponse[];
  evaluations: EvaluationResponse[];
}

/** 追问类型 → 竖条渐变色（与面试间 QuestionBubble 一致） */
const FOLLOW_UP_STRIPES: Record<string, string> = {
  CLARIFY: 'from-amber-400 to-amber-200',
  DEEPEN: 'from-orange-400 to-orange-200',
  REDIRECT: 'from-sky-400 to-sky-200',
};

const DEFAULT_STRIPE = 'from-silver-300 to-silver-100';

/** 单个评估卡片（维度/分数/评论/证据引用） */
function EvaluationCard({ evalItem }: { evalItem: EvaluationResponse }) {
  return (
    <div className="rounded-lg border border-border-subtle bg-surface-overlay p-4">
      <div className="mb-2 flex items-center justify-between">
        <span className="text-sm text-text-secondary">{evalItem.dimensionLabel}</span>
        <span className="flex items-center gap-1">
          <span className="text-base font-semibold text-sky-400">{evalItem.score}</span>
          <span className="text-xs text-text-muted">/5</span>
        </span>
      </div>
      <p className="mb-2 text-sm text-text-primary">{evalItem.comment}</p>
      {evalItem.evidenceQuote && (
        <blockquote className="border-l-2 border-sky-400/30 pl-3 text-sm italic text-text-muted">
          "{evalItem.evidenceQuote}"
        </blockquote>
      )}
    </div>
  );
}

export function RoundEvaluationList({ rounds, evaluations }: RoundEvaluationListProps) {
  const { t } = useTranslation();

  // 按 roundId 分组评估
  const grouped = evaluations.reduce<Record<number, EvaluationResponse[]>>(
    (acc, evalItem) => {
      const key = evalItem.roundId;
      if (!acc[key]) acc[key] = [];
      acc[key].push(evalItem);
      return acc;
    },
    {},
  );

  // 无 rounds 数据时回退：按 roundId 平铺（历史兜底，保证不白屏）
  if (rounds.length === 0) {
    const roundIds = Object.keys(grouped).map(Number).sort((a, b) => a - b);
    return (
      <GlassCard className="p-6">
        <h3 className="mb-4 text-sm font-medium text-text-muted">
          {t('interviews.roundEvaluationTitle')}
        </h3>
        <div className="space-y-6">
          {roundIds.map((roundId, idx) => (
            <div key={roundId}>
              <div className="mb-3 flex items-center gap-2">
                <span className="flex h-6 w-6 items-center justify-center rounded-full bg-sky-400/10 text-xs font-medium text-sky-400">
                  {idx + 1}
                </span>
                <span className="text-sm font-medium text-text-secondary">
                  {t('interviews.roundNumber', { index: idx + 1 })}
                </span>
              </div>
              <div className="space-y-3 pl-8">
                {(grouped[roundId] ?? []).map((evalItem) => (
                  <EvaluationCard key={evalItem.id} evalItem={evalItem} />
                ))}
              </div>
            </div>
          ))}
        </div>
      </GlassCard>
    );
  }

  // 主问题（parentSeq == null）按 seq 升序；追问按 parentSeq 归组、组内按 followUpIndex 升序
  const mainRounds = rounds
    .filter((r) => r.parentSeq == null)
    .sort((a, b) => (a.seq ?? 0) - (b.seq ?? 0));
  const followUpsByParent = rounds
    .filter((r) => r.parentSeq != null)
    .reduce<Record<number, RoundResponse[]>>((acc, r) => {
      const key = r.parentSeq!;
      if (!acc[key]) acc[key] = [];
      acc[key].push(r);
      return acc;
    }, {});
  Object.values(followUpsByParent).forEach((list) =>
    list.sort((a, b) => (a.followUpIndex ?? 0) - (b.followUpIndex ?? 0)),
  );

  const questionBlock = (round: RoundResponse, isFollowUp: boolean) => {
    const seqLabel = isFollowUp
      ? `Q${round.parentSeq}.${round.followUpIndex ?? 1}`
      : `Q${round.seq}`;
    const stripe = isFollowUp
      ? FOLLOW_UP_STRIPES[round.followUpType ?? ''] ?? DEFAULT_STRIPE
      : DEFAULT_STRIPE;
    const evalItems = grouped[round.id] ?? [];

    return (
      <div key={round.id}>
        {/* 问题头：竖条 + 序号徽章 + 问题文本 */}
        <div className="relative mb-3 overflow-hidden rounded-lg border border-border-subtle bg-surface-overlay p-3 pl-5">
          <div className={`absolute left-0 top-0 h-full w-[3px] bg-gradient-to-b ${stripe}`} />
          <div className="flex items-start gap-2">
            <span className="shrink-0 rounded-full border border-border-default bg-surface-hover px-2 py-0.5 text-xs font-medium text-text-secondary">
              {seqLabel}
            </span>
            <p className="text-sm text-text-primary">{round.question}</p>
          </div>
        </div>

        {/* 该轮评估卡片 */}
        {evalItems.length > 0 && (
          <div className="space-y-3">
            {evalItems.map((evalItem) => (
              <EvaluationCard key={evalItem.id} evalItem={evalItem} />
            ))}
          </div>
        )}
      </div>
    );
  };

  return (
    <GlassCard className="p-6">
      <h3 className="mb-4 text-sm font-medium text-text-muted">
        {t('interviews.roundEvaluationTitle')}
      </h3>
      <div className="space-y-6">
        {mainRounds.map((round) => {
          const followUps = followUpsByParent[round.seq ?? -1] ?? [];
          return (
            <div key={`main-${round.id}`}>
              {questionBlock(round, false)}
              {followUps.length > 0 && (
                <div className="mt-3 space-y-3 border-l-2 border-border-subtle pl-6">
                  {followUps.map((fu) => questionBlock(fu, true))}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </GlassCard>
  );
}
