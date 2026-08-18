import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { useTranslation } from 'react-i18next';
import { GlassCard } from '@/components/ui/glass-card';
import { SilverButton } from '@/components/ui/silver-button';
import { Label } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { PageHeader, ErrorState } from '@/components/common/PageHeader';
import { useCreateInterview } from '@/api/interview';
import { useResumeList } from '@/api/resumes';
import { usePositionList } from '@/api/positions';
import type { InterviewerPersona } from '@/types/interview';
import { useEnumLabel } from '@/hooks/useEnumLabel';

const PERSONA_OPTIONS: { value: InterviewerPersona; descKey: string }[] = [
  { value: 'FRIENDLY', descKey: 'interviews.personaDescFriendly' },
  { value: 'PRESSURE', descKey: 'interviews.personaDescPressure' },
  { value: 'TECHNICAL', descKey: 'interviews.personaDescTechnical' },
];

export function InterviewCreatePage() {
  const { t } = useTranslation();
  const enumLabel = useEnumLabel();
  const navigate = useNavigate();
  const [resumeId, setResumeId] = useState<number | ''>('');
  const [positionId, setPositionId] = useState<number | ''>('');
  const [persona, setPersona] = useState<InterviewerPersona>('FRIENDLY');

  // 加载已解析的简历（仅 PARSED）
  const { data: resumeData, isLoading: resumesLoading, isError: resumesError } =
    useResumeList({ size: 200 });
  // 加载岗位列表
  const { data: positionData, isLoading: positionsLoading, isError: positionsError } =
    usePositionList({ size: 200 });

  const createMutation = useCreateInterview();

  const resumes = (resumeData?.records ?? []).filter(
    (r) => r.parseStatus === 'PARSED',
  );
  const positions = positionData?.records ?? [];

  const handleSubmit = () => {
    if (!resumeId) {
      toast.error(t('interviews.validation.resumeRequired'));
      return;
    }
    if (!positionId) {
      toast.error(t('interviews.validation.positionRequired'));
      return;
    }
    createMutation.mutate(
      {
        resumeId,
        positionId: positionId || null,
        persona,
      },
      {
        onSuccess: (data) => {
          toast.success(t('interviews.createSuccess'));
          navigate(`/interviews/${data.id}`);
        },
        onError: (err: Error) => toast.error(err.message || t('interviews.createFailed')),
      },
    );
  };

  if (resumesError || positionsError) {
    return (
      <div className="space-y-6">
        <PageHeader title={t('interviews.create')} />
        <ErrorState
          message={t('interviews.loadDataFailed')}
          onRetry={() => navigate('/interviews')}
        />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title={t('interviews.create')}
        subtitle={t('interviews.createSubtitle')}
        action={
          <SilverButton variant="ghost" onClick={() => navigate('/interviews')}>
            {t('interviews.backToList')}
          </SilverButton>
        }
      />

      <GlassCard className="max-w-2xl p-6">
        <div className="space-y-5">
          {/* 选择简历 */}
          <div>
            <Label>{t('interviews.candidateResume')}</Label>
            {resumesLoading ? (
              <Skeleton className="h-10 w-full" />
            ) : (
              <Select
                value={resumeId}
                onChange={(v) =>
                  setResumeId(v ? Number(v) : '')
                }
                options={[
                  { value: '', label: t('interviews.selectResumePlaceholder') },
                  ...resumes.map((r) => ({
                    value: String(r.id),
                    label: t('interviews.nameWithId', { name: r.candidateName, id: r.id }),
                  })),
                ]}
              />
            )}
            {resumes.length === 0 && !resumesLoading && (
              <p className="mt-1 text-xs text-text-muted">
                {t('interviews.noParsedResumes')}
              </p>
            )}
          </div>

          {/* 选择岗位 */}
          <div>
            <Label>{t('interviews.positionRequired')}</Label>
            {positionsLoading ? (
              <Skeleton className="h-10 w-full" />
            ) : (
              <Select
                value={positionId}
                onChange={(v) =>
                  setPositionId(v ? Number(v) : '')
                }
                options={[
                  { value: '', label: t('interviews.selectPositionPlaceholder') },
                  ...positions.map((p) => ({
                    value: String(p.id),
                    label: t('interviews.nameWithId', { name: p.title, id: p.id }),
                  })),
                ]}
              />
            )}
          </div>

          {/* 面试官人设 */}
          <div>
            <Label>{t('interviews.personaLabel')}</Label>
            <div className="space-y-2">
              {PERSONA_OPTIONS.map((opt) => (
                <button
                  key={opt.value}
                  onClick={() => setPersona(opt.value)}
                  className={`flex w-full items-center justify-between rounded-lg border px-3 py-2.5 text-left transition-colors ${
                    persona === opt.value
                      ? 'border-silver-400 bg-silver-400/10'
                      : 'border-border-default bg-surface-overlay hover:border-border-strong'
                  }`}
                >
                  <div>
                    <span className="text-sm font-medium text-text-primary">{enumLabel('persona', opt.value)}</span>
                    <span className="ml-2 text-xs text-text-muted">{t(opt.descKey)}</span>
                  </div>
                </button>
              ))}
            </div>
          </div>

          {/* 提交按钮 */}
          <div className="flex justify-end gap-2 pt-2">
            <SilverButton
              variant="ghost"
              type="button"
              onClick={() => navigate('/interviews')}
            >
              {t('common.cancel')}
            </SilverButton>
            <SilverButton
              type="button"
              onClick={handleSubmit}
              disabled={createMutation.isPending || !resumeId || !positionId}
            >
              {createMutation.isPending ? t('interviews.creating') : t('interviews.create')}
            </SilverButton>
          </div>
        </div>
      </GlassCard>
    </div>
  );
}
