import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { GlassCard } from '@/components/ui/glass-card';
import { SilverButton } from '@/components/ui/silver-button';
import { Select, Label } from '@/components/ui/input';
import { Skeleton } from '@/components/ui/skeleton';
import { PageHeader, ErrorState } from '@/components/common/PageHeader';
import { useCreateInterview } from '@/api/interview';
import { useResumeList } from '@/api/resumes';
import { usePositionList } from '@/api/positions';

export function InterviewCreatePage() {
  const navigate = useNavigate();
  const [candidateId, setCandidateId] = useState<number | ''>('');
  const [positionId, setPositionId] = useState<number | ''>('');

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
    if (!candidateId) {
      toast.error('请选择候选人简历');
      return;
    }
    createMutation.mutate(
      {
        candidateId,
        positionId: positionId || null,
      },
      {
        onSuccess: (data) => {
          toast.success('面试已创建');
          navigate(`/interviews/${data.id}`);
        },
        onError: (err: Error) => toast.error(err.message || '创建失败'),
      },
    );
  };

  if (resumesError || positionsError) {
    return (
      <div className="space-y-6">
        <PageHeader title="创建面试" />
        <ErrorState
          message="加载数据失败"
          onRetry={() => navigate('/interviews')}
        />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="创建面试"
        subtitle="选择候选人简历与岗位，创建面试会话"
        action={
          <SilverButton variant="ghost" onClick={() => navigate('/interviews')}>
            返回列表
          </SilverButton>
        }
      />

      <GlassCard className="max-w-2xl p-6">
        <div className="space-y-5">
          {/* 选择简历 */}
          <div>
            <Label>候选人简历 *</Label>
            {resumesLoading ? (
              <Skeleton className="h-10 w-full" />
            ) : (
              <Select
                value={candidateId}
                onChange={(e) =>
                  setCandidateId(e.target.value ? Number(e.target.value) : '')
                }
              >
                <option value="">请选择已解析的简历</option>
                {resumes.map((r) => (
                  <option key={r.id} value={r.id}>
                    {r.candidateName}（#{r.id}）
                  </option>
                ))}
              </Select>
            )}
            {resumes.length === 0 && !resumesLoading && (
              <p className="mt-1 text-xs text-text-muted">
                暂无已解析的简历，请先上传并解析简历
              </p>
            )}
          </div>

          {/* 选择岗位 */}
          <div>
            <Label>面试岗位（可选）</Label>
            {positionsLoading ? (
              <Skeleton className="h-10 w-full" />
            ) : (
              <Select
                value={positionId}
                onChange={(e) =>
                  setPositionId(e.target.value ? Number(e.target.value) : '')
                }
              >
                <option value="">不指定岗位</option>
                {positions.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.title}（#{p.id}）
                  </option>
                ))}
              </Select>
            )}
          </div>

          {/* 提交按钮 */}
          <div className="flex justify-end gap-2 pt-2">
            <SilverButton
              variant="ghost"
              type="button"
              onClick={() => navigate('/interviews')}
            >
              取消
            </SilverButton>
            <SilverButton
              type="button"
              onClick={handleSubmit}
              disabled={createMutation.isPending || !candidateId}
            >
              {createMutation.isPending ? '创建中...' : '创建面试'}
            </SilverButton>
          </div>
        </div>
      </GlassCard>
    </div>
  );
}
