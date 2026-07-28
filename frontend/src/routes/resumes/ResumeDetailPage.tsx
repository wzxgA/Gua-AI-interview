import { useParams, useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { GlassCard } from '@/components/ui/glass-card';
import { SilverButton } from '@/components/ui/silver-button';
import { Badge } from '@/components/ui/badge';
import { StatusBadge } from '@/components/ui/status-dot';
import { Skeleton } from '@/components/ui/skeleton';
import { PageHeader, ErrorState } from '@/components/common/PageHeader';
import { useResume, useParseResume, useEmbedResume } from '@/api/resumes';
import { formatDate } from '@/lib/utils';

export function ResumeDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const resumeId = id ? Number(id) : undefined;

  const { data: resume, isLoading, isError, error } = useResume(resumeId);
  const parseMutation = useParseResume();
  const embedMutation = useEmbedResume();

  const handleParse = () => {
    if (!resumeId) return;
    parseMutation.mutate(resumeId, {
      onSuccess: () => toast.success('解析已触发'),
      onError: (err: Error) => toast.error(err.message || '解析失败'),
    });
  };

  const handleEmbed = () => {
    if (!resumeId) return;
    embedMutation.mutate(resumeId, {
      onSuccess: () => toast.success('向量化完成'),
      onError: (err: Error) => toast.error(err.message || '向量化失败'),
    });
  };

  if (isLoading) {
    return (
      <div className="space-y-6">
        <PageHeader title="简历详情" />
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

  if (isError || !resume) {
    return (
      <div className="space-y-6">
        <PageHeader title="简历详情" />
        <ErrorState
          message={error?.message || '简历不存在'}
          onRetry={() => navigate('/resumes')}
        />
      </div>
    );
  }

  const parsed = resume.parsedResume;
  const parsing = resume.parseStatus === 'PENDING' || parseMutation.isPending;

  return (
    <div className="space-y-6">
      <PageHeader
        title="简历详情"
        subtitle={resume.candidateName}
        action={
          <SilverButton variant="ghost" onClick={() => navigate('/resumes')}>
            返回列表
          </SilverButton>
        }
      />

      {/* 基本信息 */}
      <GlassCard className="p-6">
        <h3 className="mb-4 text-sm font-medium text-text-muted">基本信息</h3>
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
          <div>
            <p className="text-xs text-text-muted">候选人姓名</p>
            <p className="mt-1 text-sm text-text-primary">{resume.candidateName}</p>
          </div>
          <div>
            <p className="text-xs text-text-muted">手机</p>
            <p className="mt-1 text-sm text-text-primary">{resume.phone || '-'}</p>
          </div>
          <div>
            <p className="text-xs text-text-muted">邮箱</p>
            <p className="mt-1 text-sm text-text-primary">{resume.email || '-'}</p>
          </div>
          <div>
            <p className="text-xs text-text-muted">解析状态</p>
            <div className="mt-1">
              <StatusBadge
                status={resume.parseStatus}
                label={
                  resume.parseStatus === 'FAILED'
                    ? '解析失败'
                    : undefined
                }
              />
            </div>
          </div>
        </div>
        <div className="mt-4 flex items-center gap-2">
          <SilverButton variant="ghost" onClick={handleParse} disabled={parsing}>
            {parsing ? '解析中...' : '解析简历'}
          </SilverButton>
          <SilverButton variant="ghost" onClick={handleEmbed} disabled={embedMutation.isPending}>
            {embedMutation.isPending ? '向量化中...' : '向量化'}
          </SilverButton>
          <span className="text-xs text-text-muted">
            创建于 {formatDate(resume.createdAt)}
          </span>
        </div>
      </GlassCard>

      {/* 原文摘要 */}
      <GlassCard className="p-6">
        <h3 className="mb-3 text-sm font-medium text-text-muted">原文摘要</h3>
        <pre className="whitespace-pre-wrap break-words rounded-md bg-white/[0.02] p-4 text-sm text-text-secondary">
          {resume.rawText || '暂无原文内容'}
        </pre>
      </GlassCard>

      {/* 解析结果 */}
      {parsed && (
        <>
          {/* 解析基本信息 */}
          <GlassCard className="p-6">
            <h3 className="mb-4 text-sm font-medium text-text-muted">解析信息</h3>
            <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
              <div>
                <p className="text-xs text-text-muted">工作年限</p>
                <p className="mt-1 text-sm text-text-primary">
                  {parsed.yearsOfExperience != null ? `${parsed.yearsOfExperience} 年` : '-'}
                </p>
              </div>
              <div>
                <p className="text-xs text-text-muted">学历</p>
                <p className="mt-1 text-sm text-text-primary">{parsed.education || '-'}</p>
              </div>
              <div>
                <p className="text-xs text-text-muted">当前职位</p>
                <p className="mt-1 text-sm text-text-primary">{parsed.currentTitle || '-'}</p>
              </div>
            </div>
          </GlassCard>

          {/* 技能标签云 */}
          <GlassCard className="p-6">
            <h3 className="mb-4 text-sm font-medium text-text-muted">技能标签</h3>
            {parsed.skills.length > 0 ? (
              <div className="flex flex-wrap gap-2">
                {parsed.skills.map((skill) => (
                  <Badge key={skill} variant="category">
                    {skill}
                  </Badge>
                ))}
              </div>
            ) : (
              <p className="text-sm text-text-muted">暂无技能数据</p>
            )}
          </GlassCard>

          {/* 工作经历时间线 */}
          <GlassCard className="p-6">
            <h3 className="mb-4 text-sm font-medium text-text-muted">工作经历</h3>
            {parsed.workExperiences.length > 0 ? (
              <div className="space-y-0">
                {parsed.workExperiences.map((exp, index) => (
                  <div key={index} className="relative pl-6 pb-6 last:pb-0">
                    {/* 时间线轴 */}
                    <div className="absolute left-0 top-1.5 h-2 w-2 rounded-full bg-silver-300" />
                    {index < parsed.workExperiences.length - 1 && (
                      <div className="absolute left-[3px] top-4 h-full w-px bg-white/10" />
                    )}
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="text-sm font-medium text-text-primary">
                        {exp.title}
                      </span>
                      <span className="text-xs text-text-muted">@ {exp.company}</span>
                    </div>
                    <p className="mt-0.5 text-xs text-text-muted">
                      {exp.period}
                    </p>
                    {exp.description && (
                      <p className="mt-2 text-sm text-text-secondary">{exp.description}</p>
                    )}
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-sm text-text-muted">暂无工作经历数据</p>
            )}
          </GlassCard>

          {/* 项目亮点 */}
          <GlassCard className="p-6">
            <h3 className="mb-4 text-sm font-medium text-text-muted">项目亮点</h3>
            {parsed.projectHighlights.length > 0 ? (
              <ul className="space-y-2">
                {parsed.projectHighlights.map((item, index) => (
                  <li key={index} className="flex gap-2 text-sm text-text-secondary">
                    <span className="mt-1.5 h-1 w-1 shrink-0 rounded-full bg-silver-300" />
                    <span>{item}</span>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="text-sm text-text-muted">暂无项目亮点数据</p>
            )}
          </GlassCard>
        </>
      )}
    </div>
  );
}
