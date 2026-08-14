import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { useTranslation } from 'react-i18next';
import { GlassCard } from '@/components/ui/glass-card';
import { SilverButton } from '@/components/ui/silver-button';
import { Badge } from '@/components/ui/badge';
import { StatusBadge } from '@/components/ui/status-dot';
import { Skeleton } from '@/components/ui/skeleton';
import { PageHeader, ErrorState } from '@/components/common/PageHeader';
import { useResume, useParseResume, useEmbedResume, useUpdateParsedResume } from '@/api/resumes';
import { formatDate } from '@/lib/utils';
import type { ParsedResume, WorkExperience, ProjectExperience, Award } from '@/types/resume';
import { useEnumLabel } from '@/hooks/useEnumLabel';

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
  const { t } = useTranslation();
  const [input, setInput] = useState('');
  const add = () => {
    const v = input.trim();
    if (v && !tags.includes(v)) onChange([...tags, v]);
    setInput('');
  };
  return (
    <div className="flex flex-wrap items-center gap-2">
      {tags.map((tag, i) => (
        <button
          key={i}
          onClick={() => onChange(tags.filter((_, idx) => idx !== i))}
          className="rounded-full bg-surface-hover px-3 py-1 text-xs text-text-primary transition hover:bg-border-strong"
        >
          {tag} ×
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
        placeholder={placeholder || t('resumes.tagPlaceholder')}
        className="bg-transparent text-sm text-text-primary outline-none placeholder:text-text-muted"
      />
    </div>
  );
}

const inputClass =
  'w-full rounded-md border border-border-default bg-surface-overlay px-3 py-2 text-sm text-text-primary outline-none transition focus:border-silver-400/50';

// ---------- 主页面 ----------

export function ResumeDetailPage() {
  const { t } = useTranslation();
  const enumLabel = useEnumLabel();
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
      onSuccess: () => toast.success(t('resumes.parseTriggered')),
      onError: (err: Error) => toast.error(err.message || t('resumes.parseFailed')),
    });
  };

  const handleEmbed = () => {
    if (!resumeId) return;
    embedMutation.mutate(resumeId, {
      onSuccess: () => toast.success(t('resumes.embedSuccess')),
      onError: (err: Error) => toast.error(err.message || t('resumes.embedFailed')),
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
          toast.success(t('resumes.saveSuccess'));
          setIsEditing(false);
          setDraft(null);
        },
        onError: (err: Error) => toast.error(err.message || t('resumes.saveFailed')),
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
        <PageHeader title={t('resumes.detail')} />
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
        <PageHeader title={t('resumes.detail')} />
        <ErrorState
          message={error?.message || t('resumes.notFound')}
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
        title={t('resumes.detail')}
        subtitle={resume.candidateName}
        action={
          <div className="flex items-center gap-2">
            {isEditing ? (
              <>
                <SilverButton variant="ghost" onClick={cancelEdit} disabled={updateMutation.isPending}>
                  {t('common.cancel')}
                </SilverButton>
                <SilverButton variant="primary" onClick={saveEdit} disabled={updateMutation.isPending}>
                  {updateMutation.isPending ? t('resumes.saving') : t('common.save')}
                </SilverButton>
              </>
            ) : (
              <>
                {canEdit && (
                  <SilverButton variant="ghost" onClick={startEdit}>
                    {t('common.edit')}
                  </SilverButton>
                )}
                <SilverButton variant="ghost" onClick={() => navigate('/resumes')}>
                  {t('resumes.backToList')}
                </SilverButton>
              </>
            )}
          </div>
        }
      />

      {/* 基本信息 */}
      <GlassCard className="p-6">
        <h3 className="mb-4 text-sm font-medium text-text-muted">{t('resumes.basicInfo')}</h3>
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
          <div>
            <p className="text-xs text-text-muted">{t('resumes.candidateName')}</p>
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
            <p className="text-xs text-text-muted">{t('resumes.phone')}</p>
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
            <p className="text-xs text-text-muted">{t('resumes.email')}</p>
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
            <p className="text-xs text-text-muted">{t('resumes.parseStatus')}</p>
            <div className="mt-1">
              <StatusBadge
                status={resume.parseStatus}
                label={enumLabel('parseStatus', resume.parseStatus)}
              />
            </div>
          </div>
        </div>
        {!isEditing && (
          <div className="mt-4 flex items-center gap-2">
            <SilverButton variant="ghost" onClick={handleParse} disabled={parsing}>
              {parsing ? t('resumes.parsing') : t('resumes.parseResume')}
            </SilverButton>
            <SilverButton variant="ghost" onClick={handleEmbed} disabled={embedMutation.isPending}>
              {embedMutation.isPending ? t('resumes.embedding') : t('resumes.embed')}
            </SilverButton>
            <span className="text-xs text-text-muted">
              {t('resumes.createdAt', { date: formatDate(resume.createdAt) })}
            </span>
          </div>
        )}
      </GlassCard>

      {/* 原文摘要 */}
      <GlassCard className="p-6">
        <h3 className="mb-3 text-sm font-medium text-text-muted">{t('resumes.rawText')}</h3>
        <pre className="whitespace-pre-wrap break-words rounded-md bg-surface-overlay p-4 text-sm text-text-secondary">
          {resume.rawText || t('resumes.noRawText')}
        </pre>
      </GlassCard>

      {/* 解析结果 */}
      {parsed && (
        <>
          {/* 解析基本信息 */}
          <GlassCard className="p-6">
            <h3 className="mb-4 text-sm font-medium text-text-muted">{t('resumes.parsedInfo')}</h3>
            <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
              <div>
                <p className="text-xs text-text-muted">{t('resumes.yearsOfExperience')}</p>
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
                    {parsed.yearsOfExperience != null
                      ? t('resumes.yearsValue', { years: parsed.yearsOfExperience })
                      : '-'}
                  </p>
                )}
              </div>
              <div>
                <p className="text-xs text-text-muted">{t('resumes.education')}</p>
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
                <p className="text-xs text-text-muted">{t('resumes.currentTitle')}</p>
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
            <h3 className="mb-4 text-sm font-medium text-text-muted">{t('resumes.skills')}</h3>
            {isEditing && draft ? (
              <TagEditor
                tags={draft.skills}
                onChange={(v) => updateField('skills', v)}
                placeholder={t('resumes.skillPlaceholder')}
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
              <p className="text-sm text-text-muted">{t('resumes.noSkills')}</p>
            )}
          </GlassCard>

          {/* 工作/实习经历 */}
          <GlassCard className="p-6">
            <div className="mb-4 flex items-center justify-between">
              <h3 className="text-sm font-medium text-text-muted">{t('resumes.workTitle')}</h3>
              {isEditing && draft && (
                <SilverButton variant="ghost" onClick={addWorkExp}>
                  {t('resumes.add')}
                </SilverButton>
              )}
            </div>
            {isEditing && draft ? (
              <div className="space-y-4">
                {draft.workExperiences.map((exp, index) => (
                  <div key={index} className="space-y-3 rounded-md border border-border-default p-4">
                    <div className="flex items-center justify-between">
                      <select
                        className={inputClass}
                        value={exp.type}
                        onChange={(e) => updateWorkExp(index, { type: e.target.value })}
                      >
                        <option value="WORK">{t('resumes.workExpType')}</option>
                        <option value="INTERNSHIP">{t('resumes.internshipType')}</option>
                      </select>
                      <SilverButton variant="ghost" onClick={() => removeWorkExp(index)}>
                        {t('common.delete')}
                      </SilverButton>
                    </div>
                    <div className="grid grid-cols-2 gap-3">
                      <input
                        className={inputClass}
                        placeholder={t('resumes.companyPlaceholder')}
                        value={exp.company}
                        onChange={(e) => updateWorkExp(index, { company: e.target.value })}
                      />
                      <input
                        className={inputClass}
                        placeholder={t('resumes.jobTitlePlaceholder')}
                        value={exp.title}
                        onChange={(e) => updateWorkExp(index, { title: e.target.value })}
                      />
                    </div>
                    <input
                      className={inputClass}
                      placeholder={t('resumes.periodPlaceholder')}
                      value={exp.period}
                      onChange={(e) => updateWorkExp(index, { period: e.target.value })}
                    />
                    <textarea
                      className={inputClass}
                      rows={2}
                      placeholder={t('resumes.descriptionPlaceholder')}
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
                        <div className="absolute left-[3px] top-4 h-full w-px bg-surface-hover" />
                      )}
                      <div className="flex flex-wrap items-center gap-2">
                        <Badge variant="category">
                          {isInternship ? t('resumes.internshipType') : t('resumes.workExpType')}
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
              <p className="text-sm text-text-muted">{t('resumes.noWorkData')}</p>
            )}
          </GlassCard>

          {/* 项目经历 */}
          <GlassCard className="p-6">
            <div className="mb-4 flex items-center justify-between">
              <h3 className="text-sm font-medium text-text-muted">{t('resumes.projectTitle')}</h3>
              {isEditing && draft && (
                <SilverButton variant="ghost" onClick={addProject}>
                  {t('resumes.add')}
                </SilverButton>
              )}
            </div>
            {isEditing && draft ? (
              <div className="space-y-4">
                {draft.projectExperiences.map((project, index) => (
                  <div key={index} className="space-y-3 rounded-md border border-border-default p-4">
                    <div className="flex items-center justify-between">
                      <input
                        className={inputClass}
                        placeholder={t('resumes.projectNamePlaceholder')}
                        value={project.name}
                        onChange={(e) => updateProject(index, { name: e.target.value })}
                      />
                      <SilverButton variant="ghost" onClick={() => removeProject(index)}>
                        {t('common.delete')}
                      </SilverButton>
                    </div>
                    <div className="grid grid-cols-2 gap-3">
                      <input
                        className={inputClass}
                        placeholder={t('resumes.rolePlaceholder')}
                        value={project.role || ''}
                        onChange={(e) => updateProject(index, { role: e.target.value })}
                      />
                      <input
                        className={inputClass}
                        placeholder={t('resumes.periodPlaceholder')}
                        value={project.period || ''}
                        onChange={(e) => updateProject(index, { period: e.target.value })}
                      />
                    </div>
                    <textarea
                      className={inputClass}
                      rows={2}
                      placeholder={t('resumes.projectDescPlaceholder')}
                      value={project.description || ''}
                      onChange={(e) => updateProject(index, { description: e.target.value })}
                    />
                    <div>
                      <p className="mb-2 text-xs text-text-muted">{t('resumes.highlights')}</p>
                      <div className="space-y-2">
                        {project.highlights.map((item, hIndex) => (
                          <div key={hIndex} className="flex items-center gap-2">
                            <input
                              className={inputClass}
                              placeholder={t('resumes.highlightPlaceholder')}
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
                          {t('resumes.addHighlight')}
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
                    className="border-b border-border-default pb-6 last:border-b-0 last:pb-0"
                  >
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="text-sm font-medium text-text-primary">
                        {project.name || t('resumes.unnamedProject')}
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
                      <p className="mb-2 text-xs text-text-muted">{t('resumes.highlights')}</p>
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
                        <p className="text-sm text-text-muted">{t('resumes.noHighlights')}</p>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-sm text-text-muted">{t('resumes.noProjects')}</p>
            )}
          </GlassCard>

          {/* 竞赛/证书 */}
          <GlassCard className="p-6">
            <h3 className="mb-4 text-sm font-medium text-text-muted">{t('resumes.awards')}</h3>
            {isEditing && draft ? (
              <TagEditor
                tags={(draft.awards || []).map((a) => a.name)}
                onChange={(names) =>
                  updateAward(names.map((name) => ({ name })))
                }
                placeholder={t('resumes.awardPlaceholder')}
              />
            ) : (parsed.awards?.length ?? 0) > 0 ? (
              <div className="flex flex-wrap gap-2">
                {(parsed.awards ?? []).map((award, index) => (
                  <Badge key={index} variant="category">
                    {award.name}
                  </Badge>
                ))}
              </div>
            ) : (
              <p className="text-sm text-text-muted">{t('resumes.noAwards')}</p>
            )}
          </GlassCard>
        </>
      )}
    </div>
  );
}
