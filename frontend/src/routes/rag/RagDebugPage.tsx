import { useState } from 'react';
import { toast } from 'sonner';
import { GlassCard } from '@/components/ui/glass-card';
import { SilverButton } from '@/components/ui/silver-button';
import { Textarea, Select, Label } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { EmptyState, ErrorState } from '@/components/common/PageHeader';
import { useRagQuestions, useRagResumes } from '@/api/rag';
import {
  CATEGORIES,
  DIFFICULTIES,
  CATEGORY_LABELS,
  DIFFICULTY_LABELS,
} from '@/lib/constants';

type SearchMode = 'question' | 'resume';

export function RagDebugPage() {
  const [mode, setMode] = useState<SearchMode>('question');
  const [inputText, setInputText] = useState('');
  const [searchText, setSearchText] = useState('');
  const [topK, setTopK] = useState(5);
  const [category, setCategory] = useState('');
  const [difficulty, setDifficulty] = useState('');

  // 仅在对应模式下触发检索，避免多余请求
  const questionsQuery = useRagQuestions(
    mode === 'question' ? searchText : '',
    topK,
    category || undefined,
    difficulty || undefined,
  );
  const resumesQuery = useRagResumes(mode === 'resume' ? searchText : '', topK);

  const activeQuery = mode === 'question' ? questionsQuery : resumesQuery;

  const handleSearch = () => {
    if (!inputText.trim()) {
      toast.error('请输入检索内容');
      return;
    }
    setSearchText(inputText.trim());
  };

  const sortedResults = activeQuery.data
    ? [...activeQuery.data].sort((a, b) => b.score - a.score)
    : [];

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
                  max={20}
                  value={topK}
                  onChange={(e) => setTopK(Number(e.target.value))}
                  className="w-full accent-silver-300"
                />
              </div>

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
                {sortedResults.length > 0 && (
                  <span className="ml-2 text-text-muted">({sortedResults.length} 条)</span>
                )}
              </h3>
            </div>

            {!searchText ? (
              <EmptyState message="输入查询内容后点击检索查看结果" />
            ) : activeQuery.isLoading ? (
              <div className="space-y-3">
                {Array.from({ length: 3 }).map((_, i) => (
                  <Skeleton key={i} className="h-24 w-full" />
                ))}
              </div>
            ) : activeQuery.isError ? (
              <ErrorState
                message={(activeQuery.error as Error)?.message || '检索失败'}
              />
            ) : sortedResults.length === 0 ? (
              <EmptyState message="未检索到相关结果" />
            ) : mode === 'question' ? (
              <div className="space-y-3">
                {(sortedResults as QuestionResult[]).map((item) => (
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
            ) : (
              <div className="space-y-3">
                {(sortedResults as ResumeResult[]).map((item) => (
                  <GlassCard key={item.id} hover className="p-4">
                    <div className="flex items-center justify-between">
                      <span className="text-sm font-medium text-text-primary">
                        {item.candidateName}
                      </span>
                      <ScoreBar score={item.score} />
                    </div>
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

interface QuestionResult {
  id: number;
  category: string;
  topic: string;
  difficulty: string;
  content: string;
  standardAnswer: string | null;
  score: number;
}

interface ResumeResult {
  id: number;
  candidateName: string;
  phone: string | null;
  email: string | null;
  score: number;
}

function ScoreBar({ score }: { score: number }) {
  const pct = Math.min(Math.max(score * 100, 0), 100);
  return (
    <div className="flex items-center gap-2">
      <div className="h-1.5 w-24 overflow-hidden rounded-full bg-white/5">
        <div
          className="h-full bg-gradient-to-r from-silver-300 via-silver-100 to-silver-300"
          style={{ width: `${pct}%` }}
        />
      </div>
      <span className="text-xs text-silver-200">{pct.toFixed(1)}%</span>
    </div>
  );
}
