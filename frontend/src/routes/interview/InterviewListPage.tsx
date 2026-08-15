import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { useTranslation } from 'react-i18next';
import { GlassCard } from '@/components/ui/glass-card';
import { SilverButton } from '@/components/ui/silver-button';
import { Select } from '@/components/ui/input';
import { StatusBadge } from '@/components/ui/status-dot';
import { TableSkeleton } from '@/components/ui/skeleton';
import { Pagination } from '@/components/ui/pagination';
import { PageHeader, EmptyState, ErrorState } from '@/components/common/PageHeader';
import {
  useInterviewList,
  useCancelInterview,
  useDeleteInterview,
} from '@/api/interview';
import type { SessionStatus } from '@/types/interview';
import { PAGE_SIZE_DEFAULT, SESSION_STATUSES } from '@/lib/constants';
import { useEnumLabel } from '@/hooks/useEnumLabel';

export function InterviewListPage() {
  const { t, i18n } = useTranslation();
  const enumLabel = useEnumLabel();
  const navigate = useNavigate();
  const [query, setQuery] = useState<{ page: number; status?: SessionStatus }>({
    page: 1,
  });
  const [cancelTarget, setCancelTarget] = useState<{ id: number } | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<{ id: number } | null>(null);

  const { data, isLoading, isError, error, refetch } = useInterviewList({
    page: query.page,
    size: PAGE_SIZE_DEFAULT,
    status: query.status,
  });
  const cancelMutation = useCancelInterview();
  const deleteMutation = useDeleteInterview();

  const confirmCancel = () => {
    if (!cancelTarget) return;
    cancelMutation.mutate(cancelTarget.id, {
      onSuccess: () => {
        toast.success(t('interviews.cancelSuccess'));
        setCancelTarget(null);
      },
      onError: (err: Error) => toast.error(err.message || t('interviews.cancelFailed')),
    });
  };

  const confirmDelete = () => {
    if (!deleteTarget) return;
    deleteMutation.mutate(deleteTarget.id, {
      onSuccess: () => {
        toast.success(t('interviews.deleteSuccess'));
        setDeleteTarget(null);
      },
      onError: (err: Error) => toast.error(err.message || t('interviews.deleteFailed')),
    });
  };

  const records = data?.records ?? [];
  const total = data?.total ?? 0;

  return (
    <div className="space-y-6">
      <PageHeader
        title={t('interviews.title')}
        subtitle={t('interviews.subtitle')}
        action={
          <SilverButton onClick={() => navigate('/interviews/new')}>
            {t('interviews.create')}
          </SilverButton>
        }
      />

      {/* 状态筛选 */}
      <GlassCard className="p-4">
        <div className="flex flex-wrap items-end gap-3">
          <div className="min-w-[200px]">
            <label className="mb-1.5 block text-sm text-text-secondary">{t('interviews.statusFilter')}</label>
            <Select
              value={query.status ?? ''}
              onChange={(e) =>
                setQuery({
                  page: 1,
                  status: (e.target.value || undefined) as SessionStatus | undefined,
                })
              }
            >
              <option value="">{t('interviews.allStatuses')}</option>
              {SESSION_STATUSES.map((s) => (
                <option key={s} value={s}>
                  {enumLabel('sessionStatus', s)}
                </option>
              ))}
            </Select>
          </div>
        </div>
      </GlassCard>

      {/* 表格 */}
      <GlassCard className="overflow-hidden">
        {isLoading ? (
          <div className="p-4">
            <TableSkeleton />
          </div>
        ) : isError ? (
          <ErrorState message={error?.message || t('interviews.loadFailed')} onRetry={() => refetch()} />
        ) : records.length === 0 ? (
          <EmptyState message={t('interviews.noData')} />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border-subtle text-text-muted">
                  <th className="px-4 py-3 text-left font-medium">{t('interviews.idColumn')}</th>
                  <th className="px-4 py-3 text-left font-medium">{t('interviews.candidateIdColumn')}</th>
                  <th className="px-4 py-3 text-left font-medium">{t('interviews.positionIdColumn')}</th>
                  <th className="px-4 py-3 text-left font-medium">{t('interviews.statusColumn')}</th>
                  <th className="px-4 py-3 text-left font-medium">{t('interviews.createdAtColumn')}</th>
                  <th className="px-4 py-3 text-left font-medium">{t('interviews.startedAtColumn')}</th>
                  <th className="px-4 py-3 text-right font-medium">{t('interviews.actions')}</th>
                </tr>
              </thead>
              <tbody>
                {records.map((item) => (
                  <tr
                    key={item.id}
                    className="border-b border-border-subtle hover:bg-surface-overlay transition-colors"
                  >
                    <td className="px-4 py-3">
                      <Link
                        to={`/interviews/${item.id}`}
                        className="text-text-primary hover:text-silver-200 transition-colors"
                      >
                        #{item.id}
                      </Link>
                    </td>
                    <td className="px-4 py-3 text-text-secondary">{item.candidateId}</td>
                    <td className="px-4 py-3 text-text-secondary">
                      {item.positionId ?? '-'}
                    </td>
                    <td className="px-4 py-3">
                      <StatusBadge status={item.status} />
                    </td>
                    <td className="px-4 py-3 text-text-secondary">
                      {new Date(item.createdAt).toLocaleString(i18n.language)}
                    </td>
                    <td className="px-4 py-3 text-text-secondary">
                      {item.startedAt
                        ? new Date(item.startedAt).toLocaleString(i18n.language)
                        : '-'}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center justify-end gap-3">
                        <Link
                          to={`/interviews/${item.id}`}
                          className="text-xs text-silver-300 hover:text-silver-100 transition-colors"
                        >
                          {t('interviews.console')}
                        </Link>
                        {(item.status === 'IN_PROGRESS' ||
                          item.status === 'PAUSED') && (
                          <Link
                            to={`/interviews/${item.id}/room`}
                            className="text-xs text-silver-300 hover:text-silver-100 transition-colors"
                          >
                            {t('interviews.room')}
                          </Link>
                        )}
                        {item.status === 'CREATED' && (
                          <button
                            onClick={() => setCancelTarget({ id: item.id })}
                            className="text-xs text-danger hover:text-danger/80 transition-colors"
                          >
                            {t('common.cancel')}
                          </button>
                        )}
                        <button
                          onClick={() => setDeleteTarget({ id: item.id })}
                          className="text-xs text-text-muted hover:text-danger transition-colors"
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

      {/* 取消确认弹窗 */}
      {cancelTarget && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-4"
          onClick={() => setCancelTarget(null)}
        >
          <GlassCard
            className="w-full max-w-sm p-6"
            onClick={(e) => e.stopPropagation()}
          >
            <h3 className="mb-2 text-lg font-semibold text-text-primary">{t('interviews.cancelConfirmTitle')}</h3>
            <p className="mb-4 text-sm text-text-secondary">
              {t('interviews.cancelConfirmMessage', { id: cancelTarget.id })}
            </p>
            <div className="flex justify-end gap-2">
              <SilverButton
                variant="ghost"
                type="button"
                onClick={() => setCancelTarget(null)}
              >
                {t('common.back')}
              </SilverButton>
              <SilverButton
                variant="danger"
                type="button"
                disabled={cancelMutation.isPending}
                onClick={confirmCancel}
              >
                {cancelMutation.isPending ? t('interviews.cancelling') : t('interviews.confirmCancel')}
              </SilverButton>
            </div>
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
            <h3 className="mb-2 text-lg font-semibold text-text-primary">{t('interviews.deleteConfirmTitle')}</h3>
            <p className="mb-4 text-sm text-text-secondary">
              {t('interviews.deleteConfirmMessage', { id: deleteTarget.id })}
            </p>
            <div className="flex justify-end gap-2">
              <SilverButton
                variant="ghost"
                type="button"
                onClick={() => setDeleteTarget(null)}
              >
                {t('common.back')}
              </SilverButton>
              <SilverButton
                variant="danger"
                type="button"
                disabled={deleteMutation.isPending}
                onClick={confirmDelete}
              >
                {deleteMutation.isPending ? t('interviews.deleting') : t('interviews.confirmDelete')}
              </SilverButton>
            </div>
          </GlassCard>
        </div>
      )}
    </div>
  );
}
