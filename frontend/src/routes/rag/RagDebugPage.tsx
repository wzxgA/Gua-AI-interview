import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { GlassCard } from '@/components/ui/glass-card';
import { SilverButton } from '@/components/ui/silver-button';
import { Textarea, Select, Label } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { EmptyState, ErrorState } from '@/components/common/PageHeader';
import { useRagQuestions, useRagResumes } from '@/api/rag';
import type { SearchMetrics } from '@/api/rag';
import { CATEGORIES, DIFFICULTIES } from '@/lib/constants';
import { useEnumLabel } from '@/hooks/useEnumLabel';

/** 低分阈值：低于此分数显示警告 */
const LOW_SCORE_THRESHOLD = 0.5;

type SearchMode = 'question' | 'resume';

export function RagDebugPage() {
  const { t } = useTranslation();
  const enumLabel = useEnumLabel();
  const navigate = useNavigate();
  const [mode, setMode] = useState<SearchMode>('question');
  const [inputText, setInputText] = useState('');
  const [searchText, setSearchText] = useState('');
  const [topK, setTopK] = useState(5);
  const [minScore, setMinScore] = useState(0);
  const [category, setCategory] = useState('');
  const [difficulty, setDifficulty] = useState('');

  // 仅在对应模式下触发检索，避免多余请求
  const questionsQuery = useRagQuestions(
    mode === 'question' ? searchText : '',
    topK,
    category || undefined,
    difficulty || undefined,
  );
  const resumesQuery = useRagResumes(
    mode === 'resume' ? searchText : '',
    topK,
    minScore > 0 ? minScore : undefined,
  );

  const handleSearch = () => {
    if (!inputText.trim()) {
      toast.error(t('rag.inputRequired'));
      return;
    }
    setSearchText(inputText.trim());
  };

  // 简历模式返回 RagSearchResponse，题库模式同样返回 RagSearchResponse
  const resumeData = resumesQuery.data;
  const resumeResults = resumeData?.results ?? [];
  const resumeMetrics = resumeData?.metrics;
  const sortedResumeResults = [...resumeResults].sort((a, b) => b.score - a.score);

  const questionData = questionsQuery.data;
  const questionResults = questionData?.results ?? [];
  const questionMetrics = questionData?.metrics;
  const sortedQuestionResults = [...questionResults].sort((a, b) => b.score - a.score);

  const isLoading = mode === 'question' ? questionsQuery.isLoading : resumesQuery.isLoading;
  const isError = mode === 'question' ? questionsQuery.isError : resumesQuery.isError;
  const error = mode === 'question' ? questionsQuery.error : resumesQuery.error;

  return (
    <div className="space-y-6">
      <div className="mb-6">
        <h2 className="text-xl font-semibold text-text-primary">{t('rag.title')}</h2>
        <p className="mt-1 text-sm text-text-muted">{t('rag.subtitle')}</p>
      </div>

      <div className="flex gap-4">
        {/* 左栏 - 检索表单 */}
        <div className="w-2/5">
          <GlassCard className="p-6">
            <h3 className="mb-4 text-sm font-medium text-text-muted">{t('rag.params')}</h3>
            <div className="space-y-4">
              {/* 检索类型切换 */}
              <div>
                <Label>{t('rag.searchType')}</Label>
                <div className="flex gap-2">
                  <SilverButton
                    variant={mode === 'question' ? 'primary' : 'ghost'}
                    onClick={() => setMode('question')}
                    className="flex-1"
                  >
                    {t('rag.questionMode')}
                  </SilverButton>
                  <SilverButton
                    variant={mode === 'resume' ? 'primary' : 'ghost'}
                    onClick={() => setMode('resume')}
                    className="flex-1"
                  >
                    {t('rag.resumeMode')}
                  </SilverButton>
                </div>
              </div>

              {/* 查询文本 */}
              <div>
                <Label>{t('rag.queryLabel')}</Label>
                <Textarea
                  rows={5}
                  placeholder={t('rag.queryPlaceholder')}
                  value={inputText}
                  onChange={(e) => setInputText(e.target.value)}
                />
              </div>

              {/* TopK 滑块 */}
              <div>
                <div className="mb-1.5 flex items-center justify-between">
                  <Label>TopK</Label>
                  <span className="text-xs text-text-secondary">{topK}</span>
                </div>
                <input
                  type="range"
                  min={1}
                  max={50}
                  value={topK}
                  onChange={(e) => setTopK(Number(e.target.value))}
                  className="w-full accent-silver-300"
                />
              </div>

              {/* 简历模式额外参数 */}
              {mode === 'resume' && (
                <div>
                  <div className="mb-1.5 flex items-center justify-between">
                    <Label>{t('rag.minScore')}</Label>
                    <span className="text-xs text-text-secondary">
                      {minScore.toFixed(2)}
                    </span>
                  </div>
                  <input
                    type="range"
                    min={0}
                    max={1}
                    step={0.05}
                    value={minScore}
                    onChange={(e) => setMinScore(Number(e.target.value))}
                    className="w-full accent-silver-300"
                  />
                </div>
              )}

              {/* 题库模式额外筛选 */}
              {mode === 'question' && (
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <Label>{t('rag.categoryLabel')}</Label>
                    <Select
                      value={category}
                      onChange={(e) => setCategory(e.target.value)}
                    >
                      <option value="">{t('rag.allCategories')}</option>
                      {CATEGORIES.map((c) => (
                        <option key={c} value={c}>
                          {enumLabel('category', c)}
                        </option>
                      ))}
                    </Select>
                  </div>
                  <div>
                    <Label>{t('rag.difficultyLabel')}</Label>
                    <Select
                      value={difficulty}
                      onChange={(e) => setDifficulty(e.target.value)}
                    >
                      <option value="">{t('rag.allDifficulties')}</option>
                      {DIFFICULTIES.map((d) => (
                        <option key={d} value={d}>
                          {enumLabel('difficulty', d)}
                        </option>
                      ))}
                    </Select>
                  </div>
                </div>
              )}

              {/* 检索按钮 */}
              <SilverButton onClick={handleSearch} className="w-full">
                {t('rag.search')}
              </SilverButton>
            </div>
          </GlassCard>
        </div>

        {/* 右栏 - 结果展示 */}
        <div className="w-3/5">
          <GlassCard className="p-6 min-h-[400px]">
            <div className="mb-4 flex items-center justify-between">
              <h3 className="text-sm font-medium text-text-muted">
                {t('rag.results')}
                {(mode === 'resume' ? sortedResumeResults : sortedQuestionResults).length > 0 && (
                  <span className="ml-2 text-text-muted">
                    ({t('rag.resultCount', { num: (mode === 'resume' ? sortedResumeResults : sortedQuestionResults).length })})
                  </span>
                )}
              </h3>
            </div>

            {/* 检索过程指标 */}
            {mode === 'resume' && resumeMetrics && <MetricsBar metrics={resumeMetrics} />}
            {mode === 'question' && questionMetrics && <MetricsBar metrics={questionMetrics} />}

            {!searchText ? (
              <EmptyState message={t('rag.emptyHint')} />
            ) : isLoading ? (
              <div className="space-y-3">
                {Array.from({ length: 3 }).map((_, i) => (
                  <Skeleton key={i} className="h-24 w-full" />
                ))}
              </div>
            ) : isError ? (
              <ErrorState message={(error as Error)?.message || t('rag.searchFailed')} />
            ) : mode === 'question' ? (
              sortedQuestionResults.length === 0 ? (
                <EmptyState message={t('rag.noResults')} />
              ) : (
                <div className="space-y-3">
                  {sortedQuestionResults.map((item) => (
                    <GlassCard key={item.id} hover className="p-4">
                      <div className="flex items-start justify-between gap-2">
                        <p className="text-sm text-text-primary">{item.content}</p>
                        <Badge
                          variant={
                            item.recallSource === 'HYBRID' ? 'category' : 'difficulty'
                          }
                          className="shrink-0"
                        >
                          {item.recallSource === 'HYBRID'
                            ? t('rag.hybridSource')
                            : t('rag.vectorSource')}
                        </Badge>
                      </div>
                      <div className="mt-3 flex flex-wrap items-center gap-2">
                        <Badge variant="category">
                          {enumLabel('category', item.category, item.category)}
                        </Badge>
                        <Badge variant="difficulty">
                          {enumLabel('difficulty', item.difficulty, item.difficulty)}
                        </Badge>
                        <ScoreBar score={item.score} />
                      </div>
                      <div className="mt-1 flex gap-3 text-xs text-text-muted">
                        <span title={t('rag.vectorTitle')}>
                          {t('rag.vectorScore', {
                            value: (item.vectorScore * 100).toFixed(0),
                          })}
                        </span>
                        {item.keywordScore > 0 && (
                          <span title={t('rag.keywordTitle')} className="text-silver-200">
                            {t('rag.keywordScore', {
                              value: (item.keywordScore * 100).toFixed(0),
                            })}
                          </span>
                        )}
                      </div>
                      {item.matchedTerms && item.matchedTerms.length > 0 && (
                        <div className="mt-2 flex flex-wrap items-center gap-1">
                          <span className="text-xs text-text-muted">
                            {t('rag.matchedTermsLabel')}:
                          </span>
                          {item.matchedTerms.map((term) => (
                            <Badge key={term} variant="category">
                              {term}
                            </Badge>
                          ))}
                        </div>
                      )}
                      {item.matchedFields && item.matchedFields.length > 0 && (
                        <div className="mt-1 flex flex-wrap items-center gap-1">
                          <span className="text-xs text-text-muted">
                            {t('rag.matchedFieldsLabel')}:
                          </span>
                          {item.matchedFields.map((field) => (
                            <span key={field} className="text-xs text-silver-200">
                              {field}
                            </span>
                          ))}
                        </div>
                      )}
                      {item.highlightSnippet && (
                        <p className="mt-2 text-xs text-text-muted">
                          {t('rag.highlightLabel')}: {item.highlightSnippet}
                        </p>
                      )}
                      {item.standardAnswer && (
                        <p className="mt-2 text-xs text-text-muted">
                          {t('rag.standardAnswer', { answer: item.standardAnswer })}
                        </p>
                      )}
                    </GlassCard>
                  ))}
                </div>
              )
            ) : sortedResumeResults.length === 0 ? (
              <EmptyResults minScore={minScore} />
            ) : (
              <div className="space-y-3">
                {sortedResumeResults.map((item) => (
                  <GlassCard
                    key={item.id}
                    hover
                    className="p-4 cursor-pointer"
                    onClick={() => navigate(`/resumes/${item.id}`)}
                  >
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-medium text-text-primary">
                          {item.candidateName}
                        </span>
                        {item.currentTitle && (
                          <span className="text-xs text-text-secondary">
                            {item.currentTitle}
                          </span>
                        )}
                        {item.yearsOfExperience != null && (
                          <span className="text-xs text-text-muted">
                            {t('rag.years', { years: item.yearsOfExperience })}
                          </span>
                        )}
                      </div>
                      <div className="flex items-center gap-2">
                        <Badge
                          variant={
                            item.recallSource === 'HYBRID' ? 'category' : 'difficulty'
                          }
                          className="shrink-0"
                        >
                          {item.recallSource === 'HYBRID'
                            ? t('rag.hybridSource')
                            : t('rag.vectorSource')}
                        </Badge>
                        {item.score < LOW_SCORE_THRESHOLD && (
                          <Badge variant="difficulty">{t('rag.lowRelevance')}</Badge>
                        )}
                        <ScoreBar score={item.score} />
                      </div>
                    </div>
                    <div className="mt-1 flex gap-3 text-xs text-text-muted">
                      <span title={t('rag.vectorTitle')}>
                        {t('rag.vectorScore', { value: (item.vectorScore * 100).toFixed(0) })}
                      </span>
                      {item.keywordScore > 0 && (
                        <span title={t('rag.keywordTitle')} className="text-silver-200">
                          {t('rag.keywordScore', { value: (item.keywordScore * 100).toFixed(0) })}
                        </span>
                      )}
                    </div>
                    {item.skills.length > 0 && (
                      <div className="mt-2 flex flex-wrap gap-1">
                        {item.skills.slice(0, 8).map((skill, i) => (
                          <Badge key={i} variant="category">
                            {skill}
                          </Badge>
                        ))}
                        {item.skills.length > 8 && (
                          <span className="text-xs text-text-muted">
                            +{item.skills.length - 8}
                          </span>
                        )}
                      </div>
                    )}
                    {item.matchedSnippet && (
                      <p className="mt-2 text-xs text-text-muted">
                        {item.matchedSnippet}
                      </p>
                    )}
                    {item.matchedTerms && item.matchedTerms.length > 0 && (
                      <div className="mt-2 flex flex-wrap items-center gap-1">
                        <span className="text-xs text-text-muted">
                          {t('rag.matchedTermsLabel')}:
                        </span>
                        {item.matchedTerms.map((term) => (
                          <Badge key={term} variant="category">
                            {term}
                          </Badge>
                        ))}
                      </div>
                    )}
                    {item.matchedFields && item.matchedFields.length > 0 && (
                      <div className="mt-1 flex flex-wrap items-center gap-1">
                        <span className="text-xs text-text-muted">
                          {t('rag.matchedFieldsLabel')}:
                        </span>
                        {item.matchedFields.map((field) => (
                          <span key={field} className="text-xs text-silver-200">
                            {field}
                          </span>
                        ))}
                      </div>
                    )}
                    <div className="mt-2 flex gap-4 text-xs text-text-secondary">
                      <span>{t('rag.phone', { value: item.phone || '-' })}</span>
                      <span>{t('rag.email', { value: item.email || '-' })}</span>
                    </div>
                  </GlassCard>
                ))}
              </div>
            )}
          </GlassCard>
        </div>
      </div>
    </div>
  );
}

function MetricsBar({ metrics }: { metrics: SearchMetrics }) {
  const { t } = useTranslation();
  return (
    <div className="mb-4 flex flex-wrap gap-4 rounded-lg bg-surface-hover px-3 py-2 text-xs text-text-muted">
      <span>Embedding: {metrics.embeddingMs}ms</span>
      <span>SQL: {metrics.sqlMs}ms</span>
      <span>{t('rag.totalMs', { ms: metrics.totalMs })}</span>
      <span>{t('rag.returnedCount', { num: metrics.resultCount })}</span>
    </div>
  );
}

function EmptyResults({ minScore }: { minScore: number }) {
  const { t } = useTranslation();
  return (
    <div className="space-y-2">
      <EmptyState message={t('rag.noResults')} />
      <div className="rounded-lg bg-surface-hover px-4 py-3 text-xs text-text-muted">
        <p className="mb-1 font-medium text-text-secondary">{t('rag.possibleReasons')}</p>
        <ul className="ml-4 list-disc space-y-1">
          <li>{t('rag.reasonNotVectorized')}</li>
          {minScore > 0 && (
            <li>{t('rag.reasonThresholdHigh', { value: minScore.toFixed(2) })}</li>
          )}
          <li>{t('rag.reasonNoMatch')}</li>
        </ul>
      </div>
    </div>
  );
}

function ScoreBar({ score }: { score: number }) {
  const pct = Math.min(Math.max(score * 100, 0), 100);
  return (
    <div className="flex items-center gap-2">
      <div className="h-1.5 w-24 overflow-hidden rounded-full bg-surface-hover">
        <div
          className="h-full bg-gradient-to-r from-silver-300 via-silver-100 to-silver-300"
          style={{ width: `${pct}%` }}
        />
      </div>
      <span className="text-xs text-silver-200">{pct.toFixed(1)}%</span>
    </div>
  );
}
