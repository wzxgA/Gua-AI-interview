import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { toast } from 'sonner';
import { useTranslation } from 'react-i18next';
import { GlassCard } from '@/components/ui/glass-card';
import { SilverButton } from '@/components/ui/silver-button';
import { Input, Textarea, Label } from '@/components/ui/input';
import { StatusBadge } from '@/components/ui/status-dot';
import { TableSkeleton } from '@/components/ui/skeleton';
import { Pagination } from '@/components/ui/pagination';
import { PageHeader, EmptyState, ErrorState } from '@/components/common/PageHeader';
import {
  usePositionList,
  useCreatePosition,
  useUpdatePosition,
  useDeletePosition,
  useEmbedPosition,
} from '@/api/positions';
import type { PositionResponse } from '@/types/position';
import { formatDate } from '@/lib/utils';
import { PAGE_SIZE_DEFAULT } from '@/lib/constants';

type PositionFormValues = {
  title: string;
  department: string;
  jdText: string;
};

export function PositionListPage() {
  const { t } = useTranslation();
  const [query, setQuery] = useState({ title: '', department: '', page: 1 });
  const [searchInput, setSearchInput] = useState({ title: '', department: '' });
  const [modalOpen, setModalOpen] = useState(false);
  const [editingPosition, setEditingPosition] = useState<PositionResponse | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<PositionResponse | null>(null);
  const [embeddingId, setEmbeddingId] = useState<number | null>(null);

  const positionSchema = z.object({
    title: z.string().min(1, t('positions.validation.titleRequired')),
    department: z.string(),
    jdText: z.string().min(1, t('positions.validation.jdRequired')),
  });

  const { data, isLoading, isError, error, refetch } = usePositionList({
    page: query.page,
    size: PAGE_SIZE_DEFAULT,
    title: query.title || undefined,
    department: query.department || undefined,
  });

  const createMutation = useCreatePosition();
  const updateMutation = useUpdatePosition();
  const deleteMutation = useDeletePosition();
  const embedMutation = useEmbedPosition();

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<PositionFormValues>({
    resolver: zodResolver(positionSchema),
    defaultValues: { title: '', department: '', jdText: '' },
  });

  const handleSearch = () => {
    setQuery({ title: searchInput.title, department: searchInput.department, page: 1 });
  };

  const openCreate = () => {
    setEditingPosition(null);
    reset({ title: '', department: '', jdText: '' });
    setModalOpen(true);
  };

  const openEdit = (position: PositionResponse) => {
    setEditingPosition(position);
    reset({
      title: position.title,
      department: position.department ?? '',
      jdText: position.jdText,
    });
    setModalOpen(true);
  };

  const onSubmit = (values: PositionFormValues) => {
    const payload = {
      title: values.title,
      department: values.department || undefined,
      jdText: values.jdText,
    };
    if (editingPosition) {
      updateMutation.mutate(
        { id: editingPosition.id, data: payload },
        {
          onSuccess: () => {
            toast.success(t('positions.updateSuccess'));
            setModalOpen(false);
          },
          onError: (err: Error) => toast.error(err.message || t('positions.updateFailed')),
        },
      );
    } else {
      createMutation.mutate(payload, {
        onSuccess: () => {
          toast.success(t('positions.createSuccess'));
          setModalOpen(false);
        },
        onError: (err: Error) => toast.error(err.message || t('positions.createFailed')),
      });
    }
  };

  const confirmDelete = () => {
    if (!deleteTarget) return;
    deleteMutation.mutate(deleteTarget.id, {
      onSuccess: () => {
        toast.success(t('positions.deleteSuccess'));
        setDeleteTarget(null);
      },
      onError: (err: Error) => toast.error(err.message || t('positions.deleteFailed')),
    });
  };

  const handleEmbed = (id: number) => {
    setEmbeddingId(id);
    embedMutation.mutate(id, {
      onSuccess: () => {
        toast.success(t('positions.embedSuccess'));
        setEmbeddingId(null);
      },
      onError: (err: Error) => {
        toast.error(err.message || t('positions.embedFailed'));
        setEmbeddingId(null);
      },
    });
  };

  const records = data?.records ?? [];
  const total = data?.total ?? 0;
  const submitting = createMutation.isPending || updateMutation.isPending;

  return (
    <div className="space-y-6">
      <PageHeader
        title={t('positions.title')}
        subtitle={t('positions.subtitle')}
        action={
          <SilverButton onClick={openCreate}>{t('positions.create')}</SilverButton>
        }
      />

      <GlassCard className="p-4">
        <div className="flex flex-wrap items-end gap-3">
          <div className="flex-1 min-w-[180px]">
            <Label>{t('positions.name')}</Label>
            <Input
              placeholder={t('positions.searchNamePlaceholder')}
              value={searchInput.title}
              onChange={(e) => setSearchInput((s) => ({ ...s, title: e.target.value }))}
              onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
            />
          </div>
          <div className="flex-1 min-w-[180px]">
            <Label>{t('positions.department')}</Label>
            <Input
              placeholder={t('positions.searchDepartmentPlaceholder')}
              value={searchInput.department}
              onChange={(e) => setSearchInput((s) => ({ ...s, department: e.target.value }))}
              onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
            />
          </div>
          <SilverButton variant="ghost" onClick={handleSearch}>
            {t('common.search')}
          </SilverButton>
        </div>
      </GlassCard>

      <GlassCard className="overflow-hidden">
        {isLoading ? (
          <div className="p-4">
            <TableSkeleton />
          </div>
        ) : isError ? (
          <ErrorState message={error?.message || t('positions.loadFailed')} onRetry={() => refetch()} />
        ) : records.length === 0 ? (
          <EmptyState message={t('positions.noData')} />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border-subtle text-text-muted">
                  <th className="px-4 py-3 text-left font-medium">{t('positions.name')}</th>
                  <th className="px-4 py-3 text-left font-medium">{t('positions.department')}</th>
                  <th className="px-4 py-3 text-left font-medium">{t('positions.status')}</th>
                  <th className="px-4 py-3 text-left font-medium">{t('positions.embeddingStatus')}</th>
                  <th className="px-4 py-3 text-left font-medium">{t('positions.createdAtColumn')}</th>
                  <th className="px-4 py-3 text-right font-medium">{t('positions.actions')}</th>
                </tr>
              </thead>
              <tbody>
                {records.map((position) => (
                  <tr key={position.id} className="border-b border-border-subtle hover:bg-surface-overlay transition-colors">
                    <td className="px-4 py-3">
                      <Link
                        to={`/positions/${position.id}`}
                        className="text-text-primary hover:text-silver-200 transition-colors"
                      >
                        {position.title}
                      </Link>
                    </td>
                    <td className="px-4 py-3 text-text-secondary">
                      {position.department || '-'}
                    </td>
                    <td className="px-4 py-3">
                      <StatusBadge status={position.status} />
                    </td>
                    <td className="px-4 py-3">
                      <span className={position.hasEmbedding ? 'text-success' : 'text-text-muted'}>
                        {position.hasEmbedding ? '✓' : '✗'}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-text-secondary">
                      {formatDate(position.createdAt)}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center justify-end gap-2">
                        <Link
                          to={`/positions/${position.id}`}
                          className="text-xs text-silver-300 hover:text-silver-100 transition-colors"
                        >
                          {t('positions.view')}
                        </Link>
                        <button
                          onClick={() => openEdit(position)}
                          className="text-xs text-silver-300 hover:text-silver-100 transition-colors"
                        >
                          {t('common.edit')}
                        </button>
                        <button
                          onClick={() => setDeleteTarget(position)}
                          className="text-xs text-danger hover:text-danger/80 transition-colors"
                        >
                          {t('common.delete')}
                        </button>
                        <button
                          onClick={() => handleEmbed(position.id)}
                          disabled={embeddingId === position.id}
                          className="text-xs text-silver-300 hover:text-silver-100 transition-colors disabled:opacity-40 disabled:pointer-events-none"
                        >
                          {embeddingId === position.id ? t('positions.embedding') : t('positions.embed')}
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
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-modal-scrim backdrop-blur-sm p-4">
          <GlassCard className="w-full max-w-lg p-6">
            <h3 className="mb-4 text-lg font-semibold text-text-primary">
              {editingPosition ? t('positions.edit') : t('positions.create')}
            </h3>
            <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
              <div>
                <Label>{t('positions.name')}</Label>
                <Input
                  placeholder={t('positions.inputNamePlaceholder')}
                  {...register('title')}
                />
                {errors.title && (
                  <p className="mt-1 text-xs text-danger">{errors.title.message}</p>
                )}
              </div>
              <div>
                <Label>{t('positions.department')}</Label>
                <Input
                  placeholder={t('positions.inputDepartmentPlaceholder')}
                  {...register('department')}
                />
              </div>
              <div>
                <Label>{t('positions.jdTitle')}</Label>
                <Textarea
                  rows={6}
                  placeholder={t('positions.inputJdPlaceholder')}
                  {...register('jdText')}
                />
                {errors.jdText && (
                  <p className="mt-1 text-xs text-danger">{errors.jdText.message}</p>
                )}
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
                  {submitting ? t('positions.saving') : t('positions.save')}
                </SilverButton>
              </div>
            </form>
          </GlassCard>
        </div>
      )}

      {/* 删除确认弹窗 */}
      {deleteTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-modal-scrim backdrop-blur-sm p-4">
          <GlassCard className="w-full max-w-sm p-6">
            <h3 className="mb-2 text-lg font-semibold text-text-primary">{t('positions.deleteConfirmTitle')}</h3>
            <p className="mb-4 text-sm text-text-secondary">
              {t('positions.deleteConfirmMessage', { title: deleteTarget.title })}
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
                {deleteMutation.isPending ? t('positions.deleting') : t('positions.confirmDelete')}
              </SilverButton>
            </div>
          </GlassCard>
        </div>
      )}
    </div>
  );
}
