import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { GlassCard } from '@/components/ui/glass-card';
import { SilverButton } from '@/components/ui/silver-button';
import { Badge } from '@/components/ui/badge';
import { StatusBadge } from '@/components/ui/status-dot';
import { Skeleton } from '@/components/ui/skeleton';
import { PageHeader, ErrorState } from '@/components/common/PageHeader';
import { useResume, useParseResume, useEmbedResume, useUpdateParsedResume } from '@/api/resumes';
import { formatDate } from '@/lib/utils';
import type { ParsedResume, WorkExperience, ProjectExperience, Award } from '@/types/resume';

// ---------- 编辑模式辅助组件 ----------

/** 可增删的标签输入器（技能、奖项） */
function TagEditor({
  tags,
  onChange,
  placeholder,
}: {
  tags: string[];
  onChange: (v: string[]) => void;
  placeholder?: string;
}) {
  const [input, setInput] = useState('');
  const add = () => {
    const v = input.trim();
    if (v && !tags.includes(v)) onChange([...tags, v]);
    setInput('');
  };
  return (
    <div className="flex flex-wrap items-center gap-2">
      {tags.map((t, i) => (
        <button
          key={i}
          onClick={() => onChange(tags.filter((_, idx) => idx !== i))}
          className="rounded-full bg-white/[0.08] px-3 py-1 text-xs text-text-primary transition hover:bg-white/[0.16]"
        >
          {t} ×
        </button>
      ))}
      <input
        value={input}
        onChange={(e) => setInput(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Enter') {
            e.preventDefault();
            add();
          }
        }}
        placeholder={placeholder || '输入后回车添加'}
        className="bg-transparent text-sm text-text-primary outline-none placeholder:text-text-muted"
      />
    </div>
  );
}

const inputClass =
  'w-full rounded-md border border-white/10 bg-white/[0.03] px-3 py-2 text-sm text-text-primary outline-none transition focus:border-silver-400/50';

// ---------- 主页面 ----------

export function ResumeDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const resumeId = id ? Number(id) : undefined;

  const { data: resume, isLoading, isError, error } = useResume(resumeId);
  const parseMutation = useParseResume();
  const embedMutation = useEmbedResume();
  const updateMutation = useUpdateParsedResume();

  const [isEditing, setIsEditing] = useState(false);
  const [draft, setDraft] = useState<ParsedResume | null>(null);

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

  const startEdit = () => {
    if (resume?.parsedResume) {
      setDraft(JSON.parse(JSON.stringify(resume.parsedResume)));
      setIsEditing(true);
    }
  };

  const cancelEdit = () => {
    setIsEditing(false);
    setDraft(null);
  };

  const saveEdit = () => {
    if (!resumeId || !draft) return;
    updateMutation.mutate(
      { id: resumeId, parsed: draft },
      {
        onSuccess: () => {
          toast.success('修改已保存');
          setIsEditing(false);
          setDraft(null);
        },
        onError: (err: Error) => toast.error(err.message || '保存失败'),
      },
    );
  };

  // ---------- draft 操作工具 ----------

  const updateField = <K extends keyof ParsedResume>(key: K, value: ParsedResume[K]) =>
    setDraft((d) => (d ? { ...d, [key]: value } : d));

  const updateAward = (awards: Award[]) => updateField('awards', awards);

  const updateWorkExp = (index: number, patch: Partial<WorkExperience>) =>
    setDraft((d) => {
      if (!d) return d;
      const list = [...d.workExperiences];
      list[index] = { ...list[index], ...patch };
      return { ...d, workExperiences: list };
    });

  const addWorkExp = () =>
    setDraft((d) =>
      d
        ? {
            ...d,
            workExperiences: [
              ...d.workExperiences,
              { type: 'WORK', company: '', title: '', period: '', description: '' },
            ],
          }
        : d,
    );

  const removeWorkExp = (index: number) =>
    setDraft((d) =>
      d ? { ...d, workExperiences: d.workExperiences.filter((_, i) => i !== index) } : d,
    );

  const updateProject = (index: number, patch: Partial<ProjectExperience>) =>
    setDraft((d) => {
      if (!d) return d;
      const list = [...d.projectExperiences];
      list[index] = { ...list[index], ...patch };
      return { ...d, projectExperiences: list };
    });

  const addProject = () =>
    setDraft((d) =>
      d
        ? {
            ...d,
            projectExperiences: [
              ...d.projectExperiences,
              { name: '', role: '', period: '', description: '', highlights: [] },
            ],
          }
        : d,
    );

  const removeProject = (index: number) =>
    setDraft((d) =>
      d ? { ...d, projectExperiences: d.projectExperiences.filter((_, i) => i !== index) } : d,
    );

  const updateProjectHighlight = (pIndex: number, hIndex: number, value: string) =>
    setDraft((d) => {
      if (!d) return d;
      const list = [...d.projectExperiences];
      const highlights = [...list[pIndex].highlights];
      highlights[hIndex] = value;
      list[pIndex] = { ...list[pIndex], highlights };
      return { ...d, projectExperiences: list };
    });

  const addProjectHighlight = (pIndex: number) =>
    setDraft((d) => {
      if (!d) return d;
      const list = [...d.projectExperiences];
      list[pIndex] = { ...list[pIndex], highlights: [...list[pIndex].highlights, ''] };
      return { ...d, projectExperiences: list };
    });

  const removeProjectHighlight = (pIndex: number, hIndex: number) =>
    setDraft((d) => {
      if (!d) return d;
      const list = [...d.projectExperiences];
      list[pIndex] = {
        ...list[pIndex],
        highlights: list[pIndex].highlights.filter((_, i) => i !== hIndex),
      };
      return { ...d, projectExperiences: list };
    });

  // ---------- 渲染 ----------

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

  const parsed = isEditing ? draft : resume.parsedResume;
  const parsing = resume.parseStatus === 'PENDING' || parseMutation.isPending;
  const canEdit = resume.parseStatus === 'PARSED';

  return (
    <div className="space-y-6">
      <PageHeader
        title="简历详情"
        subtitle={resume.candidateName}
        action={
          <div className="flex items-center gap-2">
            {isEditing ? (
              <>
                <SilverButton variant="ghost" onClick={cancelEdit} disabled={updateMutation.isPending}>
                  取消
                </SilverButton>
                <SilverButton variant="primary" onClick={saveEdit} disabled={updateMutation.isPending}>
                  {updateMutation.isPending ? '保存中...' : '保存'}
                </SilverButton>
              </>
            ) : (
              <>
                {canEdit && (
                  <SilverButton variant="ghost" onClick={startEdit}>
                    编辑
                  </SilverButton>
                )}
                <SilverButton variant="ghost" onClick={() => navigate('/resumes')}>
                  返回列表
                </SilverButton>
              </>
            )}
          </div>
        }
      />

      {/* 基本信息 */}
      <GlassCard className="p-6">
        <h3 className="mb-4 text-sm font-medium text-text-muted">基本信息</h3>
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
          <div>
            <p className="text-xs text-text-muted">候选人姓名</p>
            {isEditing && draft ? (
              <input
                className={inputClass}
                value={draft.candidateName || ''}
                onChange={(e) => updateField('candidateName', e.target.value)}
              />
            ) : (
              <p className="mt-1 text-sm text-text-primary">{resume.candidateName}</p>
            )}
          </div>
          <div>
            <p className="text-xs text-text-muted">手机</p>
            {isEditing && draft ? (
              <input
                className={inputClass}
                value={draft.phone || ''}
                onChange={(e) => updateField('phone', e.target.value)}
              />
            ) : (
              <p className="mt-1 text-sm text-text-primary">{resume.phone || '-'}</p>
            )}
          </div>
          <div>
            <p className="text-xs text-text-muted">邮箱</p>
            {isEditing && draft ? (
              <input
                className={inputClass}
                value={draft.email || ''}
                onChange={(e) => updateField('email', e.target.value)}
              />
            ) : (
              <p className="mt-1 text-sm text-text-primary">{resume.email || '-'}</p>
            )}
          </div>
          <div>
            <p className="text-xs text-text-muted">解析状态</p>
            <div className="mt-1">
              <StatusBadge
                status={resume.parseStatus}
                label={resume.parseStatus === 'FAILED' ? '解析失败' : undefined}
              />
            </div>
          </div>
        </div>
        {!isEditing && (
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
        )}
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
                {isEditing && draft ? (
                  <input
                    type="number"
                    className={inputClass}
                    value={draft.yearsOfExperience ?? ''}
                    onChange={(e) =>
                      updateField(
                        'yearsOfExperience',
                        e.target.value ? Number(e.target.value) : null,
                      )
                    }
                  />
                ) : (
                  <p className="mt-1 text-sm text-text-primary">
                    {parsed.yearsOfExperience != null ? `${parsed.yearsOfExperience} 年` : '-'}
                  </p>
                )}
              </div>
              <div>
                <p className="text-xs text-text-muted">学历</p>
                {isEditing && draft ? (
                  <input
                    className={inputClass}
                    value={draft.education || ''}
                    onChange={(e) => updateField('education', e.target.value)}
                  />
                ) : (
                  <p className="mt-1 text-sm text-text-primary">{parsed.education || '-'}</p>
                )}
              </div>
              <div>
                <p className="text-xs text-text-muted">当前职位</p>
                {isEditing && draft ? (
                  <input
                    className={inputClass}
                    value={draft.currentTitle || ''}
                    onChange={(e) => updateField('currentTitle', e.target.value)}
                  />
                ) : (
                  <p className="mt-1 text-sm text-text-primary">{parsed.currentTitle || '-'}</p>
                )}
              </div>
            </div>
          </GlassCard>

          {/* 技能标签云 */}
          <GlassCard className="p-6">
            <h3 className="mb-4 text-sm font-medium text-text-muted">技能标签</h3>
            {isEditing && draft ? (
              <TagEditor
                tags={draft.skills}
                onChange={(v) => updateField('skills', v)}
                placeholder="输入技能后回车添加"
              />
            ) : parsed.skills.length > 0 ? (
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

          {/* 工作/实习经历 */}
          <GlassCard className="p-6">
            <div className="mb-4 flex items-center justify-between">
              <h3 className="text-sm font-medium text-text-muted">工作或实习经历</h3>
              {isEditing && draft && (
                <SilverButton variant="ghost" onClick={addWorkExp}>
                  + 添加
                </SilverButton>
              )}
            </div>
            {isEditing && draft ? (
              <div className="space-y-4">
                {draft.workExperiences.map((exp, index) => (
                  <div key={index} className="space-y-3 rounded-md border border-white/10 p-4">
                    <div className="flex items-center justify-between">
                      <select
                        className={inputClass}
                        value={exp.type}
                        onChange={(e) => updateWorkExp(index, { type: e.target.value })}
                      >
                        <option value="WORK">工作经历</option>
                        <option value="INTERNSHIP">实习经历</option>
                      </select>
                      <SilverButton variant="ghost" onClick={() => removeWorkExp(index)}>
                        删除
                      </SilverButton>
                    </div>
                    <div className="grid grid-cols-2 gap-3">
                      <input
                        className={inputClass}
                        placeholder="公司"
                        value={exp.company}
                        onChange={(e) => updateWorkExp(index, { company: e.target.value })}
                      />
                      <input
                        className={inputClass}
                        placeholder="职位"
                        value={exp.title}
                        onChange={(e) => updateWorkExp(index, { title: e.target.value })}
                      />
                    </div>
                    <input
                      className={inputClass}
                      placeholder="时间段"
                      value={exp.period}
                      onChange={(e) => updateWorkExp(index, { period: e.target.value })}
                    />
                    <textarea
                      className={inputClass}
                      rows={2}
                      placeholder="描述"
                      value={exp.description}
                      onChange={(e) => updateWorkExp(index, { description: e.target.value })}
                    />
                  </div>
                ))}
              </div>
            ) : parsed.workExperiences?.length > 0 ? (
              <div className="space-y-0">
                {parsed.workExperiences.map((exp, index) => {
                  const isInternship = exp.type === 'INTERNSHIP';
                  return (
                    <div key={index} className="relative pl-6 pb-6 last:pb-0">
                      <div className="absolute left-0 top-1.5 h-2 w-2 rounded-full bg-silver-300" />
                      {index < parsed.workExperiences.length - 1 && (
                        <div className="absolute left-[3px] top-4 h-full w-px bg-white/10" />
                      )}
                      <div className="flex flex-wrap items-center gap-2">
                        <Badge variant="category">
                          {isInternship ? '实习经历' : '工作经历'}
                        </Badge>
                        <span className="text-sm font-medium text-text-primary">
                          {exp.title}
                        </span>
                        <span className="text-xs text-text-muted">@ {exp.company}</span>
                      </div>
                      <p className="mt-0.5 text-xs text-text-muted">{exp.period}</p>
                      {exp.description && (
                        <p className="mt-2 text-sm text-text-secondary">{exp.description}</p>
                      )}
                    </div>
                  );
                })}
              </div>
            ) : (
              <p className="text-sm text-text-muted">暂无工作或实习经历数据</p>
            )}
          </GlassCard>

          {/* 项目经历 */}
          <GlassCard className="p-6">
            <div className="mb-4 flex items-center justify-between">
              <h3 className="text-sm font-medium text-text-muted">项目经历</h3>
              {isEditing && draft && (
                <SilverButton variant="ghost" onClick={addProject}>
                  + 添加
                </SilverButton>
              )}
            </div>
            {isEditing && draft ? (
              <div className="space-y-4">
                {draft.projectExperiences.map((project, index) => (
                  <div key={index} className="space-y-3 rounded-md border border-white/10 p-4">
                    <div className="flex items-center justify-between">
                      <input
                        className={inputClass}
                        placeholder="项目名称"
                        value={project.name}
                        onChange={(e) => updateProject(index, { name: e.target.value })}
                      />
                      <SilverButton variant="ghost" onClick={() => removeProject(index)}>
                        删除
                      </SilverButton>
                    </div>
                    <div className="grid grid-cols-2 gap-3">
                      <input
                        className={inputClass}
                        placeholder="角色"
                        value={project.role || ''}
                        onChange={(e) => updateProject(index, { role: e.target.value })}
                      />
                      <input
                        className={inputClass}
                        placeholder="时间段"
                        value={project.period || ''}
                        onChange={(e) => updateProject(index, { period: e.target.value })}
                      />
                    </div>
                    <textarea
                      className={inputClass}
                      rows={2}
                      placeholder="项目描述"
                      value={project.description || ''}
                      onChange={(e) => updateProject(index, { description: e.target.value })}
                    />
                    <div>
                      <p className="mb-2 text-xs text-text-muted">项目亮点</p>
                      <div className="space-y-2">
                        {project.highlights.map((item, hIndex) => (
                          <div key={hIndex} className="flex items-center gap-2">
                            <input
                              className={inputClass}
                              placeholder="亮点"
                              value={item}
                              onChange={(e) => updateProjectHighlight(index, hIndex, e.target.value)}
                            />
                            <SilverButton
                              variant="ghost"
                              onClick={() => removeProjectHighlight(index, hIndex)}
                            >
                              ×
                            </SilverButton>
                          </div>
                        ))}
                        <SilverButton variant="ghost" onClick={() => addProjectHighlight(index)}>
                          + 添加亮点
                        </SilverButton>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            ) : parsed.projectExperiences?.length > 0 ? (
              <div className="space-y-6">
                {parsed.projectExperiences.map((project, index) => (
                  <div
                    key={index}
                    className="border-b border-white/10 pb-6 last:border-b-0 last:pb-0"
                  >
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="text-sm font-medium text-text-primary">
                        {project.name || '未命名项目'}
                      </span>
                      {project.role && (
                        <span className="text-xs text-text-muted">· {project.role}</span>
                      )}
                    </div>
                    {project.period && (
                      <p className="mt-1 text-xs text-text-muted">{project.period}</p>
                    )}
                    {project.description && (
                      <p className="mt-2 text-sm text-text-secondary">{project.description}</p>
                    )}
                    <div className="mt-3">
                      <p className="mb-2 text-xs text-text-muted">项目亮点</p>
                      {project.highlights?.length > 0 ? (
                        <ul className="space-y-2">
                          {project.highlights.map((item, highlightIndex) => (
                            <li
                              key={highlightIndex}
                              className="flex gap-2 text-sm text-text-secondary"
                            >
                              <span className="mt-1.5 h-1 w-1 shrink-0 rounded-full bg-silver-300" />
                              <span>{item}</span>
                            </li>
                          ))}
                        </ul>
                      ) : (
                        <p className="text-sm text-text-muted">暂无项目亮点数据</p>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-sm text-text-muted">暂无项目经历数据</p>
            )}
          </GlassCard>

          {/* 竞赛/证书 */}
          <GlassCard className="p-6">
            <h3 className="mb-4 text-sm font-medium text-text-muted">竞赛/证书</h3>
            {isEditing && draft ? (
              <TagEditor
                tags={(draft.awards || []).map((a) => a.name)}
                onChange={(names) =>
                  updateAward(names.map((name) => ({ name })))
                }
                placeholder="输入奖项/证书后回车添加"
              />
            ) : parsed.awards?.length > 0 ? (
              <div className="flex flex-wrap gap-2">
                {parsed.awards.map((award, index) => (
                  <Badge key={index} variant="category">
                    {award.name}
                  </Badge>
                ))}
              </div>
            ) : (
              <p className="text-sm text-text-muted">暂无竞赛/证书数据</p>
            )}
          </GlassCard>
        </>
      )}
    </div>
  );
}
