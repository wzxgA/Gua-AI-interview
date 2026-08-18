import { useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { toast } from 'sonner';
import { useTranslation } from 'react-i18next';
import { GlassCard } from '@/components/ui/glass-card';
import { SilverButton } from '@/components/ui/silver-button';
import { Badge } from '@/components/ui/badge';
import { Input, Textarea, Label } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { PageHeader, ErrorState } from '@/components/common/PageHeader';
import { useQuestion, useUpdateQuestion } from '@/api/questions';
import { formatDate } from '@/lib/utils';
import { CATEGORIES, DIFFICULTIES } from '@/lib/constants';
import { useEnumLabel } from '@/hooks/useEnumLabel';

interface EditFormValues {
  category: string;
  topic: string;
  difficulty: string;
  content: string;
  standardAnswer: string;
  tags: string;
}

const emptyForm: EditFormValues = {
  category: 'TECHNICAL',
  topic: '',
  difficulty: 'MEDIUM',
  content: '',
  standardAnswer: '',
  tags: '',
};

export function QuestionDetailPage() {
  const { t } = useTranslation();
  const enumLabel = useEnumLabel();
  const { id } = useParams();
  const navigate = useNavigate();
  const questionId = id ? Number(id) : undefined;
  const { data: question, isLoading, isError, error } = useQuestion(questionId);
  const updateMutation = useUpdateQuestion();

  const editSchema = z.object({
    category: z.string().min(1, t('questions.validation.categoryRequired')),
    topic: z.string().min(1, t('questions.validation.topicRequired')),
    difficulty: z.string().min(1, t('questions.validation.difficultyRequired')),
    content: z.string().min(1, t('questions.validation.contentRequired')),
    standardAnswer: z.string(),
    tags: z.string(),
  });

  const {
    register,
    control,
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
        onSuccess: () => toast.success(t('questions.updateSuccess')),
        onError: (err: Error) => toast.error(err.message || t('questions.updateFailed')),
      },
    );
  };

  if (isLoading) {
    return (
      <div className="space-y-6">
        <PageHeader title={t('questions.detail')} />
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
        <PageHeader title={t('questions.detail')} />
        <ErrorState
          message={error?.message || t('questions.notFound')}
          onRetry={() => navigate('/questions')}
        />
      </div>
    );
  }

  const tags = question.tags ?? [];

  return (
    <div className="space-y-6">
      <PageHeader
        title={t('questions.detail')}
        subtitle={question.topic || question.content}
        action={
          <SilverButton variant="ghost" onClick={() => navigate('/questions')}>
            {t('questions.backToList')}
          </SilverButton>
        }
      />

      <GlassCard className="p-6">
        <h3 className="mb-4 text-sm font-medium text-text-muted">{t('questions.basicInfo')}</h3>
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
          <div>
            <p className="text-xs text-text-muted">{t('questions.category')}</p>
            <div className="mt-1">
              <Badge variant="category">
                {enumLabel('category', question.category)}
              </Badge>
            </div>
          </div>
          <div>
            <p className="text-xs text-text-muted">{t('questions.difficulty')}</p>
            <div className="mt-1">
              <Badge variant="difficulty">
                {enumLabel('difficulty', question.difficulty)}
              </Badge>
            </div>
          </div>
          <div>
            <p className="text-xs text-text-muted">{t('questions.topic')}</p>
            <p className="mt-1 text-sm text-text-primary">{question.topic || '-'}</p>
          </div>
          <div>
            <p className="text-xs text-text-muted">{t('questions.embeddingStatus')}</p>
            <p className="mt-1 text-sm">
              <span className={question.hasEmbedding ? 'text-success' : 'text-text-muted'}>
                {question.hasEmbedding ? t('questions.embedded') : t('questions.notEmbedded')}
              </span>
            </p>
          </div>
        </div>
        <p className="mt-4 text-xs text-text-muted">
          {t('questions.createdAt', { date: formatDate(question.createdAt) })}
        </p>
      </GlassCard>

      <GlassCard className="p-6">
        <h3 className="mb-3 text-sm font-medium text-text-muted">{t('questions.content')}</h3>
        <pre className="whitespace-pre-wrap break-words rounded-md bg-surface-overlay p-4 text-sm text-text-secondary">
          {question.content}
        </pre>
      </GlassCard>

      <GlassCard className="p-6">
        <h3 className="mb-3 text-sm font-medium text-text-muted">{t('questions.standardAnswer')}</h3>
        {question.standardAnswer ? (
          <pre className="whitespace-pre-wrap break-words rounded-md bg-surface-overlay p-4 text-sm text-text-secondary">
            {question.standardAnswer}
          </pre>
        ) : (
          <p className="text-sm text-text-muted">{t('questions.noStandardAnswer')}</p>
        )}
      </GlassCard>

      <GlassCard className="p-6">
        <h3 className="mb-3 text-sm font-medium text-text-muted">{t('questions.tags')}</h3>
        {tags.length > 0 ? (
          <div className="flex flex-wrap gap-2">
            {tags.map((tag) => (
              <Badge key={tag} variant="category">
                {tag}
              </Badge>
            ))}
          </div>
        ) : (
          <p className="text-sm text-text-muted">{t('questions.noTags')}</p>
        )}
      </GlassCard>

      <GlassCard className="p-6">
        <h3 className="mb-4 text-sm font-medium text-text-muted">{t('questions.edit')}</h3>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <Label>{t('questions.category')}</Label>
              <Controller
                name="category"
                control={control}
                render={({ field }) => (
                  <Select
                    value={field.value}
                    onChange={field.onChange}
                    options={CATEGORIES.map((category) => ({
                      value: category,
                      label: enumLabel('category', category),
                    }))}
                  />
                )}
              />
              {errors.category && <p className="mt-1 text-xs text-danger">{errors.category.message}</p>}
            </div>
            <div>
              <Label>{t('questions.difficulty')}</Label>
              <Controller
                name="difficulty"
                control={control}
                render={({ field }) => (
                  <Select
                    value={field.value}
                    onChange={field.onChange}
                    options={DIFFICULTIES.map((difficulty) => ({
                      value: difficulty,
                      label: enumLabel('difficulty', difficulty),
                    }))}
                  />
                )}
              />
              {errors.difficulty && <p className="mt-1 text-xs text-danger">{errors.difficulty.message}</p>}
            </div>
          </div>
          <div>
            <Label>{t('questions.topic')}</Label>
            <Input placeholder={t('questions.inputTopicPlaceholder')} {...register('topic')} />
            {errors.topic && <p className="mt-1 text-xs text-danger">{errors.topic.message}</p>}
          </div>
          <div>
            <Label>{t('questions.content')}</Label>
            <Textarea rows={5} placeholder={t('questions.inputContentPlaceholder')} {...register('content')} />
            {errors.content && <p className="mt-1 text-xs text-danger">{errors.content.message}</p>}
          </div>
          <div>
            <Label>{t('questions.standardAnswer')}</Label>
            <Textarea rows={4} placeholder={t('questions.inputStandardAnswerPlaceholder')} {...register('standardAnswer')} />
          </div>
          <div>
            <Label>{t('questions.tags')}</Label>
            <Input placeholder={t('questions.inputTagsPlaceholder')} {...register('tags')} />
          </div>
          <div className="flex justify-end">
            <SilverButton type="submit" disabled={updateMutation.isPending}>
              {updateMutation.isPending ? t('questions.saving') : t('questions.saveChanges')}
            </SilverButton>
          </div>
        </form>
      </GlassCard>
    </div>
  );
}
