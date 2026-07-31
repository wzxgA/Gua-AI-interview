import { useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { toast } from 'sonner';
import { GlassCard } from '@/components/ui/glass-card';
import { SilverButton } from '@/components/ui/silver-button';
import { Input, Textarea, Label } from '@/components/ui/input';
import { StatusBadge } from '@/components/ui/status-dot';
import { Skeleton } from '@/components/ui/skeleton';
import { PageHeader, ErrorState } from '@/components/common/PageHeader';
import { usePosition, useUpdatePosition, useEmbedPosition } from '@/api/positions';
import { formatDate } from '@/lib/utils';

const editSchema = z.object({
  title: z.string().min(1, '请输入岗位名称'),
  department: z.string(),
  jdText: z.string().min(1, '请输入岗位描述'),
});

type EditFormValues = z.infer<typeof editSchema>;

export function PositionDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const positionId = id ? Number(id) : undefined;

  const { data: position, isLoading, isError, error } = usePosition(positionId);
  const updateMutation = useUpdatePosition();
  const embedMutation = useEmbedPosition();

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<EditFormValues>({
    resolver: zodResolver(editSchema),
    defaultValues: { title: '', department: '', jdText: '' },
  });

  useEffect(() => {
    if (position) {
      reset({
        title: position.title,
        department: position.department ?? '',
        jdText: position.jdText,
      });
    }
  }, [position, reset]);

  const onSubmit = (values: EditFormValues) => {
    if (!positionId) return;
    updateMutation.mutate(
      {
        id: positionId,
        data: {
          title: values.title,
          department: values.department || undefined,
          jdText: values.jdText,
        },
      },
      {
        onSuccess: () => toast.success('岗位更新成功'),
        onError: (err: Error) => toast.error(err.message || '更新失败'),
      },
    );
  };

  const handleEmbed = () => {
    if (!positionId) return;
    embedMutation.mutate(positionId, {
      onSuccess: () => toast.success('向量化完成'),
      onError: (err: Error) => toast.error(err.message || '向量化失败'),
    });
  };

  if (isLoading) {
    return (
      <div className="space-y-6">
        <PageHeader title="岗位详情" />
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

  if (isError || !position) {
    return (
      <div className="space-y-6">
        <PageHeader title="岗位详情" />
        <ErrorState
          message={error?.message || '岗位不存在'}
          onRetry={() => navigate('/positions')}
        />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="岗位详情"
        subtitle={position.title}
        action={
          <SilverButton variant="ghost" onClick={() => navigate('/positions')}>
            返回列表
          </SilverButton>
        }
      />

      {/* 基本信息 */}
      <GlassCard className="p-6">
        <h3 className="mb-4 text-sm font-medium text-text-muted">基本信息</h3>
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
          <div>
            <p className="text-xs text-text-muted">岗位名称</p>
            <p className="mt-1 text-sm text-text-primary">{position.title}</p>
          </div>
          <div>
            <p className="text-xs text-text-muted">部门</p>
            <p className="mt-1 text-sm text-text-primary">{position.department || '-'}</p>
          </div>
          <div>
            <p className="text-xs text-text-muted">状态</p>
            <div className="mt-1">
              <StatusBadge status={position.status} />
            </div>
          </div>
          <div>
            <p className="text-xs text-text-muted">向量状态</p>
            <p className="mt-1 text-sm">
              <span className={position.hasEmbedding ? 'text-success' : 'text-text-muted'}>
                {position.hasEmbedding ? '✓ 已向量化' : '✗ 未向量化'}
              </span>
            </p>
          </div>
        </div>
        <div className="mt-4 flex items-center gap-2">
          <SilverButton
            variant="ghost"
            onClick={handleEmbed}
            disabled={embedMutation.isPending}
          >
            {embedMutation.isPending ? '向量化中...' : '重新向量化'}
          </SilverButton>
          <span className="text-xs text-text-muted">
            创建于 {formatDate(position.createdAt)}
          </span>
        </div>
      </GlassCard>

      {/* JD 文本展示 */}
      <GlassCard className="p-6">
        <h3 className="mb-3 text-sm font-medium text-text-muted">岗位描述（JD）</h3>
        <pre className="whitespace-pre-wrap break-words rounded-md bg-surface-overlay p-4 text-sm text-text-secondary">
          {position.jdText}
        </pre>
      </GlassCard>

      {/* 编辑表单 */}
      <GlassCard className="p-6">
        <h3 className="mb-4 text-sm font-medium text-text-muted">编辑岗位</h3>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div>
            <Label>岗位名称</Label>
            <Input placeholder="请输入岗位名称" {...register('title')} />
            {errors.title && (
              <p className="mt-1 text-xs text-danger">{errors.title.message}</p>
            )}
          </div>
          <div>
            <Label>部门</Label>
            <Input placeholder="请输入部门（可选）" {...register('department')} />
          </div>
          <div>
            <Label>岗位描述（JD）</Label>
            <Textarea rows={6} placeholder="请输入岗位描述" {...register('jdText')} />
            {errors.jdText && (
              <p className="mt-1 text-xs text-danger">{errors.jdText.message}</p>
            )}
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
