import { useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { toast } from 'sonner';
import { useTranslation } from 'react-i18next';
import { GlassCard } from '@/components/ui/glass-card';
import { SilverButton } from '@/components/ui/silver-button';
import { Input, Textarea, Label } from '@/components/ui/input';
import { StatusBadge } from '@/components/ui/status-dot';
import { Skeleton } from '@/components/ui/skeleton';
import { PageHeader, ErrorState } from '@/components/common/PageHeader';
import { usePosition, useUpdatePosition, useEmbedPosition } from '@/api/positions';
import { formatDate } from '@/lib/utils';

type EditFormValues = {
  title: string;
  department: string;
  jdText: string;
};

export function PositionDetailPage() {
  const { t } = useTranslation();
  const { id } = useParams();
  const navigate = useNavigate();
  const positionId = id ? Number(id) : undefined;

  const editSchema = z.object({
    title: z.string().min(1, t('positions.validation.titleRequired')),
    department: z.string(),
    jdText: z.string().min(1, t('positions.validation.jdRequired')),
  });

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
        onSuccess: () => toast.success(t('positions.updateSuccess')),
        onError: (err: Error) => toast.error(err.message || t('positions.updateFailed')),
      },
    );
  };

  const handleEmbed = () => {
    if (!positionId) return;
    embedMutation.mutate(positionId, {
      onSuccess: () => toast.success(t('positions.embedSuccess')),
      onError: (err: Error) => toast.error(err.message || t('positions.embedFailed')),
    });
  };

  if (isLoading) {
    return (
      <div className="space-y-6">
        <PageHeader title={t('positions.detail')} />
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
        <PageHeader title={t('positions.detail')} />
        <ErrorState
          message={error?.message || t('positions.notFound')}
          onRetry={() => navigate('/positions')}
        />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title={t('positions.detail')}
        subtitle={position.title}
        action={
          <SilverButton variant="ghost" onClick={() => navigate('/positions')}>
            {t('positions.backToList')}
          </SilverButton>
        }
      />

      {/* 基本信息 */}
      <GlassCard className="p-6">
        <h3 className="mb-4 text-sm font-medium text-text-muted">{t('positions.basicInfo')}</h3>
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
          <div>
            <p className="text-xs text-text-muted">{t('positions.name')}</p>
            <p className="mt-1 text-sm text-text-primary">{position.title}</p>
          </div>
          <div>
            <p className="text-xs text-text-muted">{t('positions.department')}</p>
            <p className="mt-1 text-sm text-text-primary">{position.department || '-'}</p>
          </div>
          <div>
            <p className="text-xs text-text-muted">{t('positions.status')}</p>
            <div className="mt-1">
              <StatusBadge status={position.status} />
            </div>
          </div>
          <div>
            <p className="text-xs text-text-muted">{t('positions.embeddingStatus')}</p>
            <p className="mt-1 text-sm">
              <span className={position.hasEmbedding ? 'text-success' : 'text-text-muted'}>
                {position.hasEmbedding ? t('positions.embedded') : t('positions.notEmbedded')}
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
            {embedMutation.isPending ? t('positions.embedding') : t('positions.reEmbed')}
          </SilverButton>
          <span className="text-xs text-text-muted">
            {t('positions.createdAt', { date: formatDate(position.createdAt) })}
          </span>
        </div>
      </GlassCard>

      {/* JD 文本展示 */}
      <GlassCard className="p-6">
        <h3 className="mb-3 text-sm font-medium text-text-muted">{t('positions.jdTitle')}</h3>
        <pre className="whitespace-pre-wrap break-words rounded-md bg-surface-overlay p-4 text-sm text-text-secondary">
          {position.jdText}
        </pre>
      </GlassCard>

      {/* 编辑表单 */}
      <GlassCard className="p-6">
        <h3 className="mb-4 text-sm font-medium text-text-muted">{t('positions.edit')}</h3>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div>
            <Label>{t('positions.name')}</Label>
            <Input placeholder={t('positions.inputNamePlaceholder')} {...register('title')} />
            {errors.title && (
              <p className="mt-1 text-xs text-danger">{errors.title.message}</p>
            )}
          </div>
          <div>
            <Label>{t('positions.department')}</Label>
            <Input placeholder={t('positions.inputDepartmentPlaceholder')} {...register('department')} />
          </div>
          <div>
            <Label>{t('positions.jdTitle')}</Label>
            <Textarea rows={6} placeholder={t('positions.inputJdPlaceholder')} {...register('jdText')} />
            {errors.jdText && (
              <p className="mt-1 text-xs text-danger">{errors.jdText.message}</p>
            )}
          </div>
          <div className="flex justify-end">
            <SilverButton type="submit" disabled={updateMutation.isPending}>
              {updateMutation.isPending ? t('positions.saving') : t('positions.saveChanges')}
            </SilverButton>
          </div>
        </form>
      </GlassCard>
    </div>
  );
}
