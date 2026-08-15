import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { toast } from 'sonner';
import { useTranslation } from 'react-i18next';
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
import { PAGE_SIZE_DEFAULT, CATEGORIES, DIFFICULTIES } from '@/lib/constants';
import { useEnumLabel } from '@/hooks/useEnumLabel';

type QuestionFormValues = {
  category: string;
  topic: string;
  difficulty: string;
  content: string;
  standardAnswer: string;
  tags: string;
};

const emptyForm: QuestionFormValues = {
  category: 'TECHNICAL',
  topic: '',
  difficulty: 'MEDIUM',
  content: '',
  standardAnswer: '',
  tags: '',
};

export function QuestionListPage() {
  const { t } = useTranslation();
  const enumLabel = useEnumLabel();
  const [query, setQuery] = useState({ category: '', difficulty: '', topic: '', page: 1 });
  const [searchTopic, setSearchTopic] = useState('');
  const [modalOpen, setModalOpen] = useState(false);
  const [editingQuestion, setEditingQuestion] = useState<QuestionResponse | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<QuestionResponse | null>(null);

  const questionSchema = z.object({
    category: z.string().min(1, t('questions.validation.categoryRequired')),
    topic: z.string().min(1, t('questions.validation.topicRequired')),
    difficulty: z.string().min(1, t('questions.validation.difficultyRequired')),
    content: z.string().min(1, t('questions.validation.contentRequired')),
    standardAnswer: z.string(),
    tags: z.string(),
  });

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
        ? values.tags.split(',').map((tag) => tag.trim()).filter(Boolean)
        : undefined,
    };
    if (editingQuestion) {
      updateMutation.mutate(
        { id: editingQuestion.id, data: payload },
        {
          onSuccess: () => {
            toast.success(t('questions.updateSuccess'));
            setModalOpen(false);
          },
          onError: (err: Error) => toast.error(err.message || t('questions.updateFailed')),
        },
      );
    } else {
      createMutation.mutate(payload, {
        onSuccess: () => {
          toast.success(t('questions.createSuccess'));
          setModalOpen(false);
        },
        onError: (err: Error) => toast.error(err.message || t('questions.createFailed')),
      });
    }
  };

  const confirmDelete = () => {
    if (!deleteTarget) return;
    deleteMutation.mutate(deleteTarget.id, {
      onSuccess: () => {
        toast.success(t('questions.deleteSuccess'));
        setDeleteTarget(null);
      },
      onError: (err: Error) => toast.error(err.message || t('questions.deleteFailed')),
    });
  };

  const handleReembed = () => {
    reembedMutation.mutate(undefined, {
      onSuccess: () => toast.success(t('questions.reembedTriggered')),
      onError: (err: Error) => toast.error(err.message || t('questions.embedFailed')),
    });
  };

  const records = data?.records ?? [];
  const total = data?.total ?? 0;
  const submitting = createMutation.isPending || updateMutation.isPending;

  return (
    <div className="space-y-6">
      <PageHeader
        title={t('questions.title')}
        subtitle={t('questions.subtitle')}
        action={
          <div className="flex items-center gap-2">
            <Link
              to="/questions/import"
              className="text-sm text-silver-300 hover:text-silver-100 transition-colors"
            >
              {t('questions.batchImport')}
            </Link>
            <SilverButton onClick={openCreate}>{t('questions.create')}</SilverButton>
          </div>
        }
      />

      {/* 筛选栏 */}
      <GlassCard className="p-4">
        <div className="flex flex-wrap items-end gap-3">
          <div className="min-w-[140px]">
            <Label>{t('questions.category')}</Label>
            <Select
              value={query.category}
              onChange={(e) => setQuery((q) => ({ ...q, category: e.target.value, page: 1 }))}
            >
              <option value="">{t('questions.allCategories')}</option>
              {CATEGORIES.map((c) => (
                <option key={c} value={c}>
                  {enumLabel('category', c)}
                </option>
              ))}
            </Select>
          </div>
          <div className="min-w-[140px]">
            <Label>{t('questions.difficulty')}</Label>
            <Select
              value={query.difficulty}
              onChange={(e) => setQuery((q) => ({ ...q, difficulty: e.target.value, page: 1 }))}
            >
              <option value="">{t('questions.allDifficulties')}</option>
              {DIFFICULTIES.map((d) => (
                <option key={d} value={d}>
                  {enumLabel('difficulty', d)}
                </option>
              ))}
            </Select>
          </div>
          <div className="flex-1 min-w-[180px]">
            <Label>{t('questions.topic')}</Label>
            <Input
              placeholder={t('questions.searchTopicPlaceholder')}
              value={searchTopic}
              onChange={(e) => setSearchTopic(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
            />
          </div>
          <SilverButton variant="ghost" onClick={handleSearch}>
            {t('common.search')}
          </SilverButton>
          <SilverButton
            variant="ghost"
            onClick={handleReembed}
            disabled={reembedMutation.isPending}
          >
            {reembedMutation.isPending ? t('questions.reembedding') : t('questions.reembed')}
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
          <ErrorState message={error?.message || t('questions.loadFailed')} onRetry={() => refetch()} />
        ) : records.length === 0 ? (
          <EmptyState message={t('questions.noData')} />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border-subtle text-text-muted">
                  <th className="px-4 py-3 text-left font-medium">{t('questions.contentColumn')}</th>
                  <th className="px-4 py-3 text-left font-medium">{t('questions.category')}</th>
                  <th className="px-4 py-3 text-left font-medium">{t('questions.difficulty')}</th>
                  <th className="px-4 py-3 text-left font-medium">{t('questions.topic')}</th>
                  <th className="px-4 py-3 text-left font-medium">{t('questions.embeddingStatus')}</th>
                  <th className="px-4 py-3 text-right font-medium">{t('questions.actions')}</th>
                </tr>
              </thead>
              <tbody>
                {records.map((question) => (
                  <tr
                    key={question.id}
                    className="border-b border-border-subtle hover:bg-surface-overlay transition-colors"
                  >
                    <td className="px-4 py-3 text-text-primary">
                      {truncate(question.content, 50)}
                    </td>
                    <td className="px-4 py-3">
                      <Badge variant="category">{enumLabel('category', question.category, question.category)}</Badge>
                    </td>
                    <td className="px-4 py-3">
                      <Badge variant="difficulty">{enumLabel('difficulty', question.difficulty, question.difficulty)}</Badge>
                    </td>
                    <td className="px-4 py-3 text-text-secondary">{question.topic}</td>
                    <td className="px-4 py-3">
                      <span className={question.hasEmbedding ? 'text-success' : 'text-text-muted'}>
                        {question.hasEmbedding ? '✓' : '✗'}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center justify-end gap-2">
                        <Link
                          to={`/questions/${question.id}`}
                          className="text-xs text-silver-300 hover:text-silver-100 transition-colors"
                        >
                          {t('questions.view')}
                        </Link>
                        <button
                          onClick={() => openEdit(question)}
                          className="text-xs text-silver-300 hover:text-silver-100 transition-colors"
                        >
                          {t('common.edit')}
                        </button>
                        <button
                          onClick={() => setDeleteTarget(question)}
                          className="text-xs text-danger hover:text-danger/80 transition-colors"
                        >
                          {t('common.delete')}
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
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-4"
          onClick={() => setModalOpen(false)}
        >
          <GlassCard
            className="w-full max-w-lg p-6 max-h-[90vh] overflow-y-auto"
            onClick={(e) => e.stopPropagation()}
          >
            <h3 className="mb-4 text-lg font-semibold text-text-primary">
              {editingQuestion ? t('questions.edit') : t('questions.create')}
            </h3>
            <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <Label>{t('questions.category')}</Label>
                  <Select {...register('category')}>
                    {CATEGORIES.map((c) => (
                      <option key={c} value={c}>
                        {enumLabel('category', c)}
                      </option>
                    ))}
                  </Select>
                  {errors.category && (
                    <p className="mt-1 text-xs text-danger">{errors.category.message}</p>
                  )}
                </div>
                <div>
                  <Label>{t('questions.difficulty')}</Label>
                  <Select {...register('difficulty')}>
                    {DIFFICULTIES.map((d) => (
                      <option key={d} value={d}>
                        {enumLabel('difficulty', d)}
                      </option>
                    ))}
                  </Select>
                  {errors.difficulty && (
                    <p className="mt-1 text-xs text-danger">{errors.difficulty.message}</p>
                  )}
                </div>
              </div>
              <div>
                <Label>{t('questions.topic')}</Label>
                <Input placeholder={t('questions.inputTopicPlaceholder')} {...register('topic')} />
                {errors.topic && (
                  <p className="mt-1 text-xs text-danger">{errors.topic.message}</p>
                )}
              </div>
              <div>
                <Label>{t('questions.content')}</Label>
                <Textarea rows={4} placeholder={t('questions.inputContentPlaceholder')} {...register('content')} />
                {errors.content && (
                  <p className="mt-1 text-xs text-danger">{errors.content.message}</p>
                )}
              </div>
              <div>
                <Label>{t('questions.standardAnswer')}</Label>
                <Textarea rows={3} placeholder={t('questions.inputStandardAnswerPlaceholder')} {...register('standardAnswer')} />
              </div>
              <div>
                <Label>{t('questions.tags')}</Label>
                <Input placeholder={t('questions.inputTagsPlaceholder')} {...register('tags')} />
              </div>
              <div className="flex justify-end gap-2 pt-2">
                <SilverButton
                  variant="ghost"
                  type="button"
                  onClick={() => setModalOpen(false)}
                >
                  {t('common.cancel')}
                </SilverButton>
                <SilverButton type="submit" disabled={submitting}>
                  {submitting ? t('questions.saving') : t('questions.save')}
                </SilverButton>
              </div>
            </form>
          </GlassCard>
        </div>
      )}

      {/* 删除确认弹窗 */}
      {deleteTarget && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-4"
          onClick={() => setDeleteTarget(null)}
        >
          <GlassCard
            className="w-full max-w-sm p-6"
            onClick={(e) => e.stopPropagation()}
          >
            <h3 className="mb-2 text-lg font-semibold text-text-primary">{t('questions.deleteConfirmTitle')}</h3>
            <p className="mb-4 text-sm text-text-secondary">
              {t('questions.deleteConfirmMessage')}
            </p>
            <div className="flex justify-end gap-2">
              <SilverButton
                variant="ghost"
                type="button"
                onClick={() => setDeleteTarget(null)}
              >
                {t('common.cancel')}
              </SilverButton>
              <SilverButton
                variant="danger"
                type="button"
                disabled={deleteMutation.isPending}
                onClick={confirmDelete}
              >
                {deleteMutation.isPending ? t('questions.deleting') : t('questions.confirmDelete')}
              </SilverButton>
            </div>
          </GlassCard>
        </div>
      )}
    </div>
  );
}
