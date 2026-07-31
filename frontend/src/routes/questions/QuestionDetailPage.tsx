import { useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { toast } from 'sonner';
import { GlassCard } from '@/components/ui/glass-card';
import { SilverButton } from '@/components/ui/silver-button';
import { Badge } from '@/components/ui/badge';
import { Input, Textarea, Select, Label } from '@/components/ui/input';
import { Skeleton } from '@/components/ui/skeleton';
import { PageHeader, ErrorState } from '@/components/common/PageHeader';
import { useQuestion, useUpdateQuestion } from '@/api/questions';
import { formatDate } from '@/lib/utils';
import { CATEGORY_LABELS, CATEGORIES, DIFFICULTIES, DIFFICULTY_LABELS } from '@/lib/constants';

const editSchema = z.object({
  category: z.string().min(1, '请选择分类'),
  topic: z.string().min(1, '请输入主题'),
  difficulty: z.string().min(1, '请选择难度'),
  content: z.string().min(1, '请输入题干'),
  standardAnswer: z.string(),
  tags: z.string(),
});

type EditFormValues = z.infer<typeof editSchema>;

const emptyForm: EditFormValues = {
  category: 'TECHNICAL',
  topic: '',
  difficulty: 'MEDIUM',
  content: '',
  standardAnswer: '',
  tags: '',
};

export function QuestionDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const questionId = id ? Number(id) : undefined;
  const { data: question, isLoading, isError, error } = useQuestion(questionId);
  const updateMutation = useUpdateQuestion();

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<EditFormValues>({
    resolver: zodResolver(editSchema),
    defaultValues: emptyForm,
  });

  useEffect(() => {
    if (question) {
      reset({
        category: question.category,
        topic: question.topic,
        difficulty: question.difficulty,
        content: question.content,
        standardAnswer: question.standardAnswer ?? '',
        tags: (question.tags ?? []).join(', '),
      });
    }
  }, [question, reset]);

  const onSubmit = (values: EditFormValues) => {
    if (!questionId) return;
    updateMutation.mutate(
      {
        id: questionId,
        data: {
          category: values.category,
          topic: values.topic,
          difficulty: values.difficulty,
          content: values.content,
          standardAnswer: values.standardAnswer || undefined,
          tags: values.tags
            ? values.tags.split(',').map((tag) => tag.trim()).filter(Boolean)
            : undefined,
        },
      },
      {
        onSuccess: () => toast.success('题目更新成功'),
        onError: (err: Error) => toast.error(err.message || '更新失败'),
      },
    );
  };

  if (isLoading) {
    return (
      <div className="space-y-6">
        <PageHeader title="题目详情" />
        <GlassCard className="p-6">
          <Skeleton className="h-8 w-48" />
          <div className="mt-4 space-y-3">
            <Skeleton className="h-5 w-full" />
            <Skeleton className="h-5 w-3/4" />
            <Skeleton className="h-5 w-1/2" />
          </div>
        </GlassCard>
        <GlassCard className="p-6">
          <Skeleton className="h-32 w-full" />
        </GlassCard>
      </div>
    );
  }

  if (isError || !question) {
    return (
      <div className="space-y-6">
        <PageHeader title="题目详情" />
        <ErrorState
          message={error?.message || '题目不存在'}
          onRetry={() => navigate('/questions')}
        />
      </div>
    );
  }

  const tags = question.tags ?? [];

  return (
    <div className="space-y-6">
      <PageHeader
        title="题目详情"
        subtitle={question.topic || question.content}
        action={
          <SilverButton variant="ghost" onClick={() => navigate('/questions')}>
            返回列表
          </SilverButton>
        }
      />

      <GlassCard className="p-6">
        <h3 className="mb-4 text-sm font-medium text-text-muted">基本信息</h3>
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
          <div>
            <p className="text-xs text-text-muted">分类</p>
            <div className="mt-1">
              <Badge variant="category">
                {CATEGORY_LABELS[question.category] ?? question.category}
              </Badge>
            </div>
          </div>
          <div>
            <p className="text-xs text-text-muted">难度</p>
            <div className="mt-1">
              <Badge variant="difficulty">
                {DIFFICULTY_LABELS[question.difficulty] ?? question.difficulty}
              </Badge>
            </div>
          </div>
          <div>
            <p className="text-xs text-text-muted">主题</p>
            <p className="mt-1 text-sm text-text-primary">{question.topic || '-'}</p>
          </div>
          <div>
            <p className="text-xs text-text-muted">向量状态</p>
            <p className="mt-1 text-sm">
              <span className={question.hasEmbedding ? 'text-success' : 'text-text-muted'}>
                {question.hasEmbedding ? '✓ 已向量化' : '✗ 未向量化'}
              </span>
            </p>
          </div>
        </div>
        <p className="mt-4 text-xs text-text-muted">创建于 {formatDate(question.createdAt)}</p>
      </GlassCard>

      <GlassCard className="p-6">
        <h3 className="mb-3 text-sm font-medium text-text-muted">题干</h3>
        <pre className="whitespace-pre-wrap break-words rounded-md bg-surface-overlay p-4 text-sm text-text-secondary">
          {question.content}
        </pre>
      </GlassCard>

      <GlassCard className="p-6">
        <h3 className="mb-3 text-sm font-medium text-text-muted">标准答案</h3>
        {question.standardAnswer ? (
          <pre className="whitespace-pre-wrap break-words rounded-md bg-surface-overlay p-4 text-sm text-text-secondary">
            {question.standardAnswer}
          </pre>
        ) : (
          <p className="text-sm text-text-muted">暂无标准答案</p>
        )}
      </GlassCard>

      <GlassCard className="p-6">
        <h3 className="mb-3 text-sm font-medium text-text-muted">标签</h3>
        {tags.length > 0 ? (
          <div className="flex flex-wrap gap-2">
            {tags.map((tag) => (
              <Badge key={tag} variant="category">
                {tag}
              </Badge>
            ))}
          </div>
        ) : (
          <p className="text-sm text-text-muted">暂无标签</p>
        )}
      </GlassCard>

      <GlassCard className="p-6">
        <h3 className="mb-4 text-sm font-medium text-text-muted">编辑题目</h3>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <Label>分类</Label>
              <Select {...register('category')}>
                {CATEGORIES.map((category) => (
                  <option key={category} value={category}>
                    {CATEGORY_LABELS[category]}
                  </option>
                ))}
              </Select>
              {errors.category && <p className="mt-1 text-xs text-danger">{errors.category.message}</p>}
            </div>
            <div>
              <Label>难度</Label>
              <Select {...register('difficulty')}>
                {DIFFICULTIES.map((difficulty) => (
                  <option key={difficulty} value={difficulty}>
                    {DIFFICULTY_LABELS[difficulty]}
                  </option>
                ))}
              </Select>
              {errors.difficulty && <p className="mt-1 text-xs text-danger">{errors.difficulty.message}</p>}
            </div>
          </div>
          <div>
            <Label>主题</Label>
            <Input placeholder="请输入主题" {...register('topic')} />
            {errors.topic && <p className="mt-1 text-xs text-danger">{errors.topic.message}</p>}
          </div>
          <div>
            <Label>题干</Label>
            <Textarea rows={5} placeholder="请输入题干" {...register('content')} />
            {errors.content && <p className="mt-1 text-xs text-danger">{errors.content.message}</p>}
          </div>
          <div>
            <Label>标准答案</Label>
            <Textarea rows={4} placeholder="请输入标准答案（可选）" {...register('standardAnswer')} />
          </div>
          <div>
            <Label>标签</Label>
            <Input placeholder="多个标签用逗号分隔（可选）" {...register('tags')} />
          </div>
          <div className="flex justify-end">
            <SilverButton type="submit" disabled={updateMutation.isPending}>
              {updateMutation.isPending ? '保存中...' : '保存修改'}
            </SilverButton>
          </div>
        </form>
      </GlassCard>
    </div>
  );
}
