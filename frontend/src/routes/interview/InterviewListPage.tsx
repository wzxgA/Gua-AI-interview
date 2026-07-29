import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
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
import { PAGE_SIZE_DEFAULT, SESSION_STATUSES, SESSION_STATUS_LABELS } from '@/lib/constants';

export function InterviewListPage() {
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
        toast.success('面试已取消');
        setCancelTarget(null);
      },
      onError: (err: Error) => toast.error(err.message || '取消失败'),
    });
  };

  const confirmDelete = () => {
    if (!deleteTarget) return;
    deleteMutation.mutate(deleteTarget.id, {
      onSuccess: () => {
        toast.success('面试已删除');
        setDeleteTarget(null);
      },
      onError: (err: Error) => toast.error(err.message || '删除失败'),
    });
  };

  const records = data?.records ?? [];
  const total = data?.total ?? 0;

  return (
    <div className="space-y-6">
      <PageHeader
        title="面试管理"
        subtitle="管理面试会话、查看进度与结果"
        action={
          <SilverButton onClick={() => navigate('/interviews/new')}>
            创建面试
          </SilverButton>
        }
      />

      {/* 状态筛选 */}
      <GlassCard className="p-4">
        <div className="flex flex-wrap items-end gap-3">
          <div className="min-w-[200px]">
            <label className="mb-1.5 block text-sm text-text-secondary">状态筛选</label>
            <Select
              value={query.status ?? ''}
              onChange={(e) =>
                setQuery({
                  page: 1,
                  status: (e.target.value || undefined) as SessionStatus | undefined,
                })
              }
            >
              <option value="">全部状态</option>
              {SESSION_STATUSES.map((s) => (
                <option key={s} value={s}>
                  {SESSION_STATUS_LABELS[s]}
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
          <ErrorState message={error?.message || '加载失败'} onRetry={() => refetch()} />
        ) : records.length === 0 ? (
          <EmptyState message="暂无面试数据" />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-white/5 text-text-muted">
                  <th className="px-4 py-3 text-left font-medium">ID</th>
                  <th className="px-4 py-3 text-left font-medium">候选人 ID</th>
                  <th className="px-4 py-3 text-left font-medium">岗位 ID</th>
                  <th className="px-4 py-3 text-left font-medium">状态</th>
                  <th className="px-4 py-3 text-left font-medium">创建时间</th>
                  <th className="px-4 py-3 text-left font-medium">开始时间</th>
                  <th className="px-4 py-3 text-right font-medium">操作</th>
                </tr>
              </thead>
              <tbody>
                {records.map((item) => (
                  <tr
                    key={item.id}
                    className="border-b border-white/5 hover:bg-white/[0.02] transition-colors"
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
                      {new Date(item.createdAt).toLocaleString('zh-CN')}
                    </td>
                    <td className="px-4 py-3 text-text-secondary">
                      {item.startedAt
                        ? new Date(item.startedAt).toLocaleString('zh-CN')
                        : '-'}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center justify-end gap-3">
                        <Link
                          to={`/interviews/${item.id}`}
                          className="text-xs text-silver-300 hover:text-silver-100 transition-colors"
                        >
                          控制台
                        </Link>
                        {(item.status === 'IN_PROGRESS' ||
                          item.status === 'PAUSED') && (
                          <Link
                            to={`/interviews/${item.id}/room`}
                            className="text-xs text-silver-300 hover:text-silver-100 transition-colors"
                          >
                            面试间
                          </Link>
                        )}
                        {item.status === 'CREATED' && (
                          <button
                            onClick={() => setCancelTarget({ id: item.id })}
                            className="text-xs text-danger hover:text-danger/80 transition-colors"
                          >
                            取消
                          </button>
                        )}
                        <button
                          onClick={() => setDeleteTarget({ id: item.id })}
                          className="text-xs text-text-muted hover:text-danger transition-colors"
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

      {/* 取消确认弹窗 */}
      {cancelTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
          <GlassCard className="w-full max-w-sm p-6">
            <h3 className="mb-2 text-lg font-semibold text-text-primary">确认取消</h3>
            <p className="mb-4 text-sm text-text-secondary">
              确定要取消面试 #{cancelTarget.id} 吗？此操作不可撤销。
            </p>
            <div className="flex justify-end gap-2">
              <SilverButton
                variant="ghost"
                type="button"
                onClick={() => setCancelTarget(null)}
              >
                返回
              </SilverButton>
              <SilverButton
                variant="danger"
                type="button"
                disabled={cancelMutation.isPending}
                onClick={confirmCancel}
              >
                {cancelMutation.isPending ? '取消中...' : '确认取消'}
              </SilverButton>
            </div>
          </GlassCard>
        </div>
      )}

      {/* 删除确认弹窗 */}
      {deleteTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
          <GlassCard className="w-full max-w-sm p-6">
            <h3 className="mb-2 text-lg font-semibold text-text-primary">确认删除</h3>
            <p className="mb-4 text-sm text-text-secondary">
              确定要删除面试 #{deleteTarget.id} 吗？将级联删除所有轮次数据，此操作不可恢复。
            </p>
            <div className="flex justify-end gap-2">
              <SilverButton
                variant="ghost"
                type="button"
                onClick={() => setDeleteTarget(null)}
              >
                返回
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
