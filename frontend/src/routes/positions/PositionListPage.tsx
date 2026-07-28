import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { toast } from 'sonner';
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

const positionSchema = z.object({
  title: z.string().min(1, '请输入岗位名称'),
  department: z.string(),
  jdText: z.string().min(1, '请输入岗位描述'),
});

type PositionFormValues = z.infer<typeof positionSchema>;

export function PositionListPage() {
  const [query, setQuery] = useState({ title: '', department: '', page: 1 });
  const [searchInput, setSearchInput] = useState({ title: '', department: '' });
  const [modalOpen, setModalOpen] = useState(false);
  const [editingPosition, setEditingPosition] = useState<PositionResponse | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<PositionResponse | null>(null);
  const [embeddingId, setEmbeddingId] = useState<number | null>(null);

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
            toast.success('岗位更新成功');
            setModalOpen(false);
          },
          onError: (err: Error) => toast.error(err.message || '更新失败'),
        },
      );
    } else {
      createMutation.mutate(payload, {
        onSuccess: () => {
          toast.success('岗位创建成功');
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
        toast.success('岗位已删除');
        setDeleteTarget(null);
      },
      onError: (err: Error) => toast.error(err.message || '删除失败'),
    });
  };

  const handleEmbed = (id: number) => {
    setEmbeddingId(id);
    embedMutation.mutate(id, {
      onSuccess: () => {
        toast.success('向量化完成');
        setEmbeddingId(null);
      },
      onError: (err: Error) => {
        toast.error(err.message || '向量化失败');
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
        title="岗位管理"
        subtitle="管理面试岗位、JD 文本与向量化状态"
        action={
          <SilverButton onClick={openCreate}>创建岗位</SilverButton>
        }
      />

      <GlassCard className="p-4">
        <div className="flex flex-wrap items-end gap-3">
          <div className="flex-1 min-w-[180px]">
            <Label>岗位名称</Label>
            <Input
              placeholder="搜索岗位名称"
              value={searchInput.title}
              onChange={(e) => setSearchInput((s) => ({ ...s, title: e.target.value }))}
              onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
            />
          </div>
          <div className="flex-1 min-w-[180px]">
            <Label>部门</Label>
            <Input
              placeholder="搜索部门"
              value={searchInput.department}
              onChange={(e) => setSearchInput((s) => ({ ...s, department: e.target.value }))}
              onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
            />
          </div>
          <SilverButton variant="ghost" onClick={handleSearch}>
            搜索
          </SilverButton>
        </div>
      </GlassCard>

      <GlassCard className="overflow-hidden">
        {isLoading ? (
          <div className="p-4">
            <TableSkeleton />
          </div>
        ) : isError ? (
          <ErrorState message={error?.message || '加载失败'} onRetry={() => refetch()} />
        ) : records.length === 0 ? (
          <EmptyState message="暂无岗位数据" />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-white/5 text-text-muted">
                  <th className="px-4 py-3 text-left font-medium">岗位名称</th>
                  <th className="px-4 py-3 text-left font-medium">部门</th>
                  <th className="px-4 py-3 text-left font-medium">状态</th>
                  <th className="px-4 py-3 text-left font-medium">向量状态</th>
                  <th className="px-4 py-3 text-left font-medium">创建时间</th>
                  <th className="px-4 py-3 text-right font-medium">操作</th>
                </tr>
              </thead>
              <tbody>
                {records.map((position) => (
                  <tr key={position.id} className="border-b border-white/5 hover:bg-white/[0.02] transition-colors">
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
                          查看
                        </Link>
                        <button
                          onClick={() => openEdit(position)}
                          className="text-xs text-silver-300 hover:text-silver-100 transition-colors"
                        >
                          编辑
                        </button>
                        <button
                          onClick={() => setDeleteTarget(position)}
                          className="text-xs text-danger hover:text-danger/80 transition-colors"
                        >
                          删除
                        </button>
                        <button
                          onClick={() => handleEmbed(position.id)}
                          disabled={embeddingId === position.id}
                          className="text-xs text-silver-300 hover:text-silver-100 transition-colors disabled:opacity-40 disabled:pointer-events-none"
                        >
                          {embeddingId === position.id ? '向量化中...' : '向量化'}
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
          <GlassCard className="w-full max-w-lg p-6">
            <h3 className="mb-4 text-lg font-semibold text-text-primary">
              {editingPosition ? '编辑岗位' : '创建岗位'}
            </h3>
            <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
              <div>
                <Label>岗位名称</Label>
                <Input
                  placeholder="请输入岗位名称"
                  {...register('title')}
                />
                {errors.title && (
                  <p className="mt-1 text-xs text-danger">{errors.title.message}</p>
                )}
              </div>
              <div>
                <Label>部门</Label>
                <Input
                  placeholder="请输入部门（可选）"
                  {...register('department')}
                />
              </div>
              <div>
                <Label>岗位描述（JD）</Label>
                <Textarea
                  rows={6}
                  placeholder="请输入岗位描述"
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
              确定要删除岗位「{deleteTarget.title}」吗？此操作不可撤销。
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
