import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { toast } from 'sonner';
import { GlassCard } from '@/components/ui/glass-card';
import { SilverButton } from '@/components/ui/silver-button';
import { Input, Textarea, Select, Label } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { TableSkeleton } from '@/components/ui/skeleton';
import { Pagination } from '@/components/ui/pagination';
import { PageHeader, EmptyState, ErrorState } from '@/components/common/PageHeader';
import {
  useQuestionList,
  useCreateQuestion,
  useUpdateQuestion,
  useDeleteQuestion,
  useReembedQuestions,
} from '@/api/questions';
import type { QuestionResponse } from '@/types/question';
import { truncate } from '@/lib/utils';
import {
  PAGE_SIZE_DEFAULT,
  CATEGORIES,
  DIFFICULTIES,
  CATEGORY_LABELS,
  DIFFICULTY_LABELS,
} from '@/lib/constants';

const questionSchema = z.object({
  category: z.string().min(1, '请选择分类'),
  topic: z.string().min(1, '请输入主题'),
  difficulty: z.string().min(1, '请选择难度'),
  content: z.string().min(1, '请输入题干'),
  standardAnswer: z.string(),
  tags: z.string(),
});

type QuestionFormValues = z.infer<typeof questionSchema>;

const emptyForm: QuestionFormValues = {
  category: 'TECHNICAL',
  topic: '',
  difficulty: 'MEDIUM',
  content: '',
  standardAnswer: '',
  tags: '',
};

export function QuestionListPage() {
  const [query, setQuery] = useState({ category: '', difficulty: '', topic: '', page: 1 });
  const [searchTopic, setSearchTopic] = useState('');
  const [modalOpen, setModalOpen] = useState(false);
  const [editingQuestion, setEditingQuestion] = useState<QuestionResponse | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<QuestionResponse | null>(null);

  const { data, isLoading, isError, error, refetch } = useQuestionList({
    page: query.page,
    size: PAGE_SIZE_DEFAULT,
    category: query.category || undefined,
    difficulty: query.difficulty || undefined,
    topic: query.topic || undefined,
  });

  const createMutation = useCreateQuestion();
  const updateMutation = useUpdateQuestion();
  const deleteMutation = useDeleteQuestion();
  const reembedMutation = useReembedQuestions();

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<QuestionFormValues>({
    resolver: zodResolver(questionSchema),
    defaultValues: emptyForm,
  });

  const handleSearch = () => {
    setQuery((q) => ({ ...q, topic: searchTopic, page: 1 }));
  };

  const openCreate = () => {
    setEditingQuestion(null);
    reset(emptyForm);
    setModalOpen(true);
  };

  const openEdit = (question: QuestionResponse) => {
    setEditingQuestion(question);
    reset({
      category: question.category,
      topic: question.topic,
      difficulty: question.difficulty,
      content: question.content,
      standardAnswer: question.standardAnswer ?? '',
      tags: (question.tags ?? []).join(', '),
    });
    setModalOpen(true);
  };

  const onSubmit = (values: QuestionFormValues) => {
    const payload = {
      category: values.category,
      topic: values.topic,
      difficulty: values.difficulty,
      content: values.content,
      standardAnswer: values.standardAnswer || undefined,
      tags: values.tags
        ? values.tags.split(',').map((t) => t.trim()).filter(Boolean)
        : undefined,
    };
    if (editingQuestion) {
      updateMutation.mutate(
        { id: editingQuestion.id, data: payload },
        {
          onSuccess: () => {
            toast.success('题目更新成功');
            setModalOpen(false);
          },
          onError: (err: Error) => toast.error(err.message || '更新失败'),
        },
      );
    } else {
      createMutation.mutate(payload, {
        onSuccess: () => {
          toast.success('题目创建成功');
          setModalOpen(false);
        },
        onError: (err: Error) => toast.error(err.message || '创建失败'),
      });
    }
  };

  const confirmDelete = () => {
    if (!deleteTarget) return;
    deleteMutation.mutate(deleteTarget.id, {
      onSuccess: () => {
        toast.success('题目已删除');
        setDeleteTarget(null);
      },
      onError: (err: Error) => toast.error(err.message || '删除失败'),
    });
  };

  const handleReembed = () => {
    reembedMutation.mutate(undefined, {
      onSuccess: () => toast.success('全量重新向量化已触发'),
      onError: (err: Error) => toast.error(err.message || '向量化失败'),
    });
  };

  const records = data?.records ?? [];
  const total = data?.total ?? 0;
  const submitting = createMutation.isPending || updateMutation.isPending;

  return (
    <div className="space-y-6">
      <PageHeader
        title="题库管理"
        subtitle="管理面试题目、分类与向量化状态"
        action={
          <div className="flex items-center gap-2">
            <Link
              to="/questions/import"
              className="text-sm text-silver-300 hover:text-silver-100 transition-colors"
            >
              批量导入
            </Link>
            <SilverButton onClick={openCreate}>创建题目</SilverButton>
          </div>
        }
      />

      {/* 筛选栏 */}
      <GlassCard className="p-4">
        <div className="flex flex-wrap items-end gap-3">
          <div className="min-w-[140px]">
            <Label>分类</Label>
            <Select
              value={query.category}
              onChange={(e) => setQuery((q) => ({ ...q, category: e.target.value, page: 1 }))}
            >
              <option value="">全部分类</option>
              {CATEGORIES.map((c) => (
                <option key={c} value={c}>
                  {CATEGORY_LABELS[c]}
                </option>
              ))}
            </Select>
          </div>
          <div className="min-w-[140px]">
            <Label>难度</Label>
            <Select
              value={query.difficulty}
              onChange={(e) => setQuery((q) => ({ ...q, difficulty: e.target.value, page: 1 }))}
            >
              <option value="">全部难度</option>
              {DIFFICULTIES.map((d) => (
                <option key={d} value={d}>
                  {DIFFICULTY_LABELS[d]}
                </option>
              ))}
            </Select>
          </div>
          <div className="flex-1 min-w-[180px]">
            <Label>主题</Label>
            <Input
              placeholder="搜索主题"
              value={searchTopic}
              onChange={(e) => setSearchTopic(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
            />
          </div>
          <SilverButton variant="ghost" onClick={handleSearch}>
            搜索
          </SilverButton>
          <SilverButton
            variant="ghost"
            onClick={handleReembed}
            disabled={reembedMutation.isPending}
          >
            {reembedMutation.isPending ? '向量化中...' : '全量重新向量化'}
          </SilverButton>
        </div>
      </GlassCard>

      {/* 表格 */}
      <GlassCard className="overflow-hidden">
        {isLoading ? (
          <div className="p-4">
            <TableSkeleton />
          </div>
        ) : isError ? (
          <ErrorState message={error?.message || '加载失败'} onRetry={() => refetch()} />
        ) : records.length === 0 ? (
          <EmptyState message="暂无题目数据" />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-white/5 text-text-muted">
                  <th className="px-4 py-3 text-left font-medium">题目内容</th>
                  <th className="px-4 py-3 text-left font-medium">分类</th>
                  <th className="px-4 py-3 text-left font-medium">难度</th>
                  <th className="px-4 py-3 text-left font-medium">主题</th>
                  <th className="px-4 py-3 text-left font-medium">向量状态</th>
                  <th className="px-4 py-3 text-right font-medium">操作</th>
                </tr>
              </thead>
              <tbody>
                {records.map((question) => (
                  <tr
                    key={question.id}
                    className="border-b border-white/5 hover:bg-white/[0.02] transition-colors"
                  >
                    <td className="px-4 py-3 text-text-primary">
                      {truncate(question.content, 50)}
                    </td>
                    <td className="px-4 py-3">
                      <Badge variant="category">{CATEGORY_LABELS[question.category] ?? question.category}</Badge>
                    </td>
                    <td className="px-4 py-3">
                      <Badge variant="difficulty">{DIFFICULTY_LABELS[question.difficulty] ?? question.difficulty}</Badge>
                    </td>
                    <td className="px-4 py-3 text-text-secondary">{question.topic}</td>
                    <td className="px-4 py-3">
                      <span className={question.hasEmbedding ? 'text-success' : 'text-text-muted'}>
                        {question.hasEmbedding ? '✓' : '✗'}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center justify-end gap-2">
                        <button
                          onClick={() => openEdit(question)}
                          className="text-xs text-silver-300 hover:text-silver-100 transition-colors"
                        >
                          编辑
                        </button>
                        <button
                          onClick={() => setDeleteTarget(question)}
                          className="text-xs text-danger hover:text-danger/80 transition-colors"
                        >
                          删除
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </GlassCard>

      {total > 0 && (
        <div className="flex justify-center">
          <Pagination
            current={query.page}
            total={total}
            size={PAGE_SIZE_DEFAULT}
            onPageChange={(page) => setQuery((q) => ({ ...q, page }))}
          />
        </div>
      )}

      {/* 创建/编辑弹窗 */}
      {modalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
          <GlassCard className="w-full max-w-lg p-6 max-h-[90vh] overflow-y-auto">
            <h3 className="mb-4 text-lg font-semibold text-text-primary">
              {editingQuestion ? '编辑题目' : '创建题目'}
            </h3>
            <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <Label>分类</Label>
                  <Select {...register('category')}>
                    {CATEGORIES.map((c) => (
                      <option key={c} value={c}>
                        {CATEGORY_LABELS[c]}
                      </option>
                    ))}
                  </Select>
                  {errors.category && (
                    <p className="mt-1 text-xs text-danger">{errors.category.message}</p>
                  )}
                </div>
                <div>
                  <Label>难度</Label>
                  <Select {...register('difficulty')}>
                    {DIFFICULTIES.map((d) => (
                      <option key={d} value={d}>
                        {DIFFICULTY_LABELS[d]}
                      </option>
                    ))}
                  </Select>
                  {errors.difficulty && (
                    <p className="mt-1 text-xs text-danger">{errors.difficulty.message}</p>
                  )}
                </div>
              </div>
              <div>
                <Label>主题</Label>
                <Input placeholder="请输入主题" {...register('topic')} />
                {errors.topic && (
                  <p className="mt-1 text-xs text-danger">{errors.topic.message}</p>
                )}
              </div>
              <div>
                <Label>题干</Label>
                <Textarea rows={4} placeholder="请输入题干" {...register('content')} />
                {errors.content && (
                  <p className="mt-1 text-xs text-danger">{errors.content.message}</p>
                )}
              </div>
              <div>
                <Label>标准答案</Label>
                <Textarea rows={3} placeholder="请输入标准答案（可选）" {...register('standardAnswer')} />
              </div>
              <div>
                <Label>标签</Label>
                <Input placeholder="多个标签用逗号分隔（可选）" {...register('tags')} />
              </div>
              <div className="flex justify-end gap-2 pt-2">
                <SilverButton
                  variant="ghost"
                  type="button"
                  onClick={() => setModalOpen(false)}
                >
                  取消
                </SilverButton>
                <SilverButton type="submit" disabled={submitting}>
                  {submitting ? '保存中...' : '保存'}
                </SilverButton>
              </div>
            </form>
          </GlassCard>
        </div>
      )}

      {/* 删除确认弹窗 */}
      {deleteTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
          <GlassCard className="w-full max-w-sm p-6">
            <h3 className="mb-2 text-lg font-semibold text-text-primary">确认删除</h3>
            <p className="mb-4 text-sm text-text-secondary">
              确定要删除该题目吗？此操作不可撤销。
            </p>
            <div className="flex justify-end gap-2">
              <SilverButton
                variant="ghost"
                type="button"
                onClick={() => setDeleteTarget(null)}
              >
                取消
              </SilverButton>
              <SilverButton
                variant="danger"
                type="button"
                disabled={deleteMutation.isPending}
                onClick={confirmDelete}
              >
                {deleteMutation.isPending ? '删除中...' : '确认删除'}
              </SilverButton>
            </div>
          </GlassCard>
        </div>
      )}
    </div>
  );
}
