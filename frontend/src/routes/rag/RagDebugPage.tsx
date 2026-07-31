import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { GlassCard } from '@/components/ui/glass-card';
import { SilverButton } from '@/components/ui/silver-button';
import { Textarea, Select, Label } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { EmptyState, ErrorState } from '@/components/common/PageHeader';
import { useRagQuestions, useRagResumes } from '@/api/rag';
import type { SearchMetrics } from '@/api/rag';
import {
  CATEGORIES,
  DIFFICULTIES,
  CATEGORY_LABELS,
  DIFFICULTY_LABELS,
} from '@/lib/constants';

/** 低分阈值：低于此分数显示警告 */
const LOW_SCORE_THRESHOLD = 0.5;

type SearchMode = 'question' | 'resume';

export function RagDebugPage() {
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
      toast.error('请输入检索内容');
      return;
    }
    setSearchText(inputText.trim());
  };

  // 简历模式返回 RagSearchResponse，题库模式返回数组
  const resumeData = resumesQuery.data;
  const resumeResults = resumeData?.results ?? [];
  const resumeMetrics = resumeData?.metrics;
  const sortedResumeResults = [...resumeResults].sort((a, b) => b.score - a.score);

  const questionResults = questionsQuery.data ?? [];
  const sortedQuestionResults = [...questionResults].sort((a, b) => b.score - a.score);

  const isLoading = mode === 'question' ? questionsQuery.isLoading : resumesQuery.isLoading;
  const isError = mode === 'question' ? questionsQuery.isError : resumesQuery.isError;
  const error = mode === 'question' ? questionsQuery.error : resumesQuery.error;

  return (
    <div className="space-y-6">
      <div className="mb-6">
        <h2 className="text-xl font-semibold text-text-primary">RAG 检索调试</h2>
        <p className="mt-1 text-sm text-text-muted">调试向量检索结果与相似度分数</p>
      </div>

      <div className="flex gap-4">
        {/* 左栏 - 检索表单 */}
        <div className="w-2/5">
          <GlassCard className="p-6">
            <h3 className="mb-4 text-sm font-medium text-text-muted">检索参数</h3>
            <div className="space-y-4">
              {/* 检索类型切换 */}
              <div>
                <Label>检索类型</Label>
                <div className="flex gap-2">
                  <SilverButton
                    variant={mode === 'question' ? 'primary' : 'ghost'}
                    onClick={() => setMode('question')}
                    className="flex-1"
                  >
                    题库检索
                  </SilverButton>
                  <SilverButton
                    variant={mode === 'resume' ? 'primary' : 'ghost'}
                    onClick={() => setMode('resume')}
                    className="flex-1"
                  >
                    简历检索
                  </SilverButton>
                </div>
              </div>

              {/* 查询文本 */}
              <div>
                <Label>查询文本</Label>
                <Textarea
                  rows={5}
                  placeholder="请输入检索内容..."
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
                    <Label>最低相似度</Label>
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
                    <Label>分类</Label>
                    <Select
                      value={category}
                      onChange={(e) => setCategory(e.target.value)}
                    >
                      <option value="">全部分类</option>
                      {CATEGORIES.map((c) => (
                        <option key={c} value={c}>
                          {CATEGORY_LABELS[c]}
                        </option>
                      ))}
                    </Select>
                  </div>
                  <div>
                    <Label>难度</Label>
                    <Select
                      value={difficulty}
                      onChange={(e) => setDifficulty(e.target.value)}
                    >
                      <option value="">全部难度</option>
                      {DIFFICULTIES.map((d) => (
                        <option key={d} value={d}>
                          {DIFFICULTY_LABELS[d]}
                        </option>
                      ))}
                    </Select>
                  </div>
                </div>
              )}

              {/* 检索按钮 */}
              <SilverButton onClick={handleSearch} className="w-full">
                检索
              </SilverButton>
            </div>
          </GlassCard>
        </div>

        {/* 右栏 - 结果展示 */}
        <div className="w-3/5">
          <GlassCard className="p-6 min-h-[400px]">
            <div className="mb-4 flex items-center justify-between">
              <h3 className="text-sm font-medium text-text-muted">
                检索结果
                {(mode === 'resume' ? sortedResumeResults : sortedQuestionResults).length > 0 && (
                  <span className="ml-2 text-text-muted">
                    ({(mode === 'resume' ? sortedResumeResults : sortedQuestionResults).length} 条)
                  </span>
                )}
              </h3>
            </div>

            {/* 检索过程指标 */}
            {mode === 'resume' && resumeMetrics && (
              <MetricsBar metrics={resumeMetrics} />
            )}

            {!searchText ? (
              <EmptyState message="输入查询内容后点击检索查看结果" />
            ) : isLoading ? (
              <div className="space-y-3">
                {Array.from({ length: 3 }).map((_, i) => (
                  <Skeleton key={i} className="h-24 w-full" />
                ))}
              </div>
            ) : isError ? (
              <ErrorState message={(error as Error)?.message || '检索失败'} />
            ) : mode === 'question' ? (
              sortedQuestionResults.length === 0 ? (
                <EmptyState message="未检索到相关结果" />
              ) : (
                <div className="space-y-3">
                  {sortedQuestionResults.map((item) => (
                    <GlassCard key={item.id} hover className="p-4">
                      <p className="text-sm text-text-primary">{item.content}</p>
                      <div className="mt-3 flex flex-wrap items-center gap-2">
                        <Badge variant="category">
                          {CATEGORY_LABELS[item.category] ?? item.category}
                        </Badge>
                        <Badge variant="difficulty">
                          {DIFFICULTY_LABELS[item.difficulty] ?? item.difficulty}
                        </Badge>
                        <ScoreBar score={item.score} />
                      </div>
                      {item.standardAnswer && (
                        <p className="mt-2 text-xs text-text-muted">
                          参考答案：{item.standardAnswer}
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
                            {item.yearsOfExperience}年
                          </span>
                        )}
                      </div>
                      <div className="flex items-center gap-2">
                        {item.score < LOW_SCORE_THRESHOLD && (
                          <Badge variant="difficulty">低相关</Badge>
                        )}
                        <ScoreBar score={item.score} />
                      </div>
                    </div>
                    <div className="mt-1 flex gap-3 text-xs text-text-muted">
                      <span title="向量相似度得分">
                        向量: {(item.vectorScore * 100).toFixed(0)}%
                      </span>
                      {item.keywordScore > 0 && (
                        <span title="关键词匹配得分" className="text-silver-200">
                          关键词: {(item.keywordScore * 100).toFixed(0)}%
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
                    <div className="mt-2 flex gap-4 text-xs text-text-secondary">
                      <span>手机：{item.phone || '-'}</span>
                      <span>邮箱：{item.email || '-'}</span>
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
  return (
    <div className="mb-4 flex flex-wrap gap-4 rounded-lg bg-surface-hover px-3 py-2 text-xs text-text-muted">
      <span>Embedding: {metrics.embeddingMs}ms</span>
      <span>SQL: {metrics.sqlMs}ms</span>
      <span>总耗时: {metrics.totalMs}ms</span>
      <span>返回: {metrics.resultCount} 条</span>
    </div>
  );
}

function EmptyResults({ minScore }: { minScore: number }) {
  return (
    <div className="space-y-2">
      <EmptyState message="未检索到相关结果" />
      <div className="rounded-lg bg-surface-hover px-4 py-3 text-xs text-text-muted">
        <p className="mb-1 font-medium text-text-secondary">可能原因：</p>
        <ul className="ml-4 list-disc space-y-1">
          <li>简历尚未完成向量化（请到简历管理页检查向量状态）</li>
          {minScore > 0 && <li>最低相似度阈值过高（当前 {minScore.toFixed(2)}），尝试调低</li>}
          <li>没有与查询内容相关的简历</li>
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
