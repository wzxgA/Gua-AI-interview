import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { toast } from 'sonner';
import { useTranslation } from 'react-i18next';
import { GlassCard } from '@/components/ui/glass-card';
import { SilverButton } from '@/components/ui/silver-button';
import { Input, Textarea, Select, Label } from '@/components/ui/input';
import { PageHeader } from '@/components/common/PageHeader';
import { useSubmitInterviewNote, useInterviewNoteTask, useImportQuestions } from '@/api/questions';
import type { ParsedQuestion } from '@/types/question';
import { addNoteTask } from '@/lib/noteParseTasks';
import { CATEGORIES, DIFFICULTIES } from '@/lib/constants';
import { useEnumLabel } from '@/hooks/useEnumLabel';

interface NoteRow {
  key: string;
  checked: boolean;
  category: string;
  topic: string;
  difficulty: string;
  content: string;
  standardAnswer: string;
  tags: string;
  matchedExistingId: number | null;
}

function toRow(parsed: ParsedQuestion): NoteRow {
  return {
    key: crypto.randomUUID(),
    checked: true,
    category: parsed.category || 'TECHNICAL',
    topic: parsed.topic || '',
    difficulty: parsed.difficulty || 'MEDIUM',
    content: parsed.content || '',
    standardAnswer: parsed.standardAnswer || '',
    tags: Array.isArray(parsed.tags) ? parsed.tags.join(', ') : '',
    matchedExistingId: parsed.matchedExistingId ?? null,
  };
}

export function QuestionNoteImportPage() {
  const { t } = useTranslation();
  const enumLabel = useEnumLabel();
  const navigate = useNavigate();
  const [noteText, setNoteText] = useState('');
  const [categoryHint, setCategoryHint] = useState('');
  const [rows, setRows] = useState<NoteRow[]>([]);
  const [parsedCount, setParsedCount] = useState(0);
  const [searchParams] = useSearchParams();
  // 从题库管理页"查看结果"跳转进入时携带 ?task=，自动轮询并填充预览
  const [taskId, setTaskId] = useState<string | null>(searchParams.get('task'));

  const submitMutation = useSubmitInterviewNote();
  const taskQuery = useInterviewNoteTask(taskId);
  const importMutation = useImportQuestions();
  const parsing = submitMutation.isPending || taskId != null;

  // 轮询异步解析任务：完成后自动填充预览（跳回本页查看场景）
  useEffect(() => {
    const task = taskQuery.data;
    if (!task) return;
    if (task.status === 'SUCCESS') {
      const parsed = task.results ?? [];
      setRows(parsed.map(toRow));
      setParsedCount(parsed.length);
      setTaskId(null);
      toast.success(t('questions.noteImport.parsedCount', { count: parsed.length }));
      navigate('/questions/note-import', { replace: true });
    } else if (task.status === 'FAILED') {
      setTaskId(null);
      toast.error(task.message || t('questions.noteImport.parseFailed'));
      navigate('/questions/note-import', { replace: true });
    }
    // NOT_FOUND 继续轮询兜底（任务表超时清理等场景），无需处理
  }, [taskQuery.data, navigate, t]);

  const updateRow = (key: string, field: keyof NoteRow, value: string) => {
    setRows((prev) => prev.map((r) => (r.key === key ? { ...r, [field]: value } : r)));
  };

  const toggleRow = (key: string) => {
    setRows((prev) => prev.map((r) => (r.key === key ? { ...r, checked: !r.checked } : r)));
  };

  const removeRow = (key: string) => {
    setRows((prev) => prev.filter((r) => r.key !== key));
  };

  const toggleAll = () => {
    const allChecked = rows.length > 0 && rows.every((r) => r.checked);
    setRows((prev) => prev.map((r) => ({ ...r, checked: !allChecked })));
  };

  const handleParse = () => {
    if (!noteText.trim()) {
      toast.error(t('questions.noteImport.textRequired'));
      return;
    }
    setRows([]);
    submitMutation.mutate(
      { text: noteText, categoryHint: categoryHint.trim() || undefined },
      {
        onSuccess: (task) => {
          // 记录任务并跳转题库管理页，由任务卡片区展示解析状态
          addNoteTask(task.taskId);
          toast.success(t('questions.noteImport.submitted'));
          navigate('/questions');
        },
        onError: (err: Error) =>
          toast.error(err.message || t('questions.noteImport.parseFailed')),
      },
    );
  };

  const handleSubmit = () => {
    const selected = rows.filter((r) => r.checked && r.content.trim() && r.topic.trim());
    if (selected.length === 0) {
      toast.error(t('questions.noteImport.atLeastOne'));
      return;
    }
    const questions = selected.map((r) => ({
      category: r.category,
      topic: r.topic,
      difficulty: r.difficulty,
      content: r.content,
      standardAnswer: r.standardAnswer || undefined,
      tags: r.tags
        ? r.tags.split(',').map((tag) => tag.trim()).filter(Boolean)
        : undefined,
    }));
    importMutation.mutate(
      { questions },
      {
        onSuccess: () => {
          toast.success(t('questions.noteImport.importSuccess', { count: questions.length }));
          navigate('/questions');
        },
        onError: (err: Error) => toast.error(err.message || t('questions.import.importFailed')),
      },
    );
  };

  const allChecked = rows.length > 0 && rows.every((r) => r.checked);
  const selectedCount = rows.filter((r) => r.checked).length;

  return (
    <div className="space-y-6">
      <PageHeader
        title={t('questions.noteImport.title')}
        subtitle={t('questions.noteImport.subtitle')}
        action={
          <SilverButton variant="ghost" onClick={() => navigate('/questions')}>
            {t('questions.backToList')}
          </SilverButton>
        }
      />

      {/* 面经文本输入 */}
      <GlassCard className="p-6">
        <Label>{t('questions.noteImport.textLabel')}</Label>
        <Textarea
          rows={8}
          placeholder={t('questions.noteImport.textPlaceholder')}
          value={noteText}
          onChange={(e) => setNoteText(e.target.value)}
          className="mt-2 text-sm"
        />
        <div className="mt-3 flex flex-wrap items-end justify-between gap-3">
          <div className="min-w-[220px] flex-1">
            <Label>{t('questions.noteImport.directionLabel')}</Label>
            <Input
              className="mt-1"
              placeholder={t('questions.noteImport.directionHint')}
              value={categoryHint}
              onChange={(e) => setCategoryHint(e.target.value)}
            />
          </div>
          <SilverButton onClick={handleParse} disabled={parsing || !noteText.trim()}>
            {parsing ? t('questions.noteImport.parsing') : t('questions.noteImport.parse')}
          </SilverButton>
        </div>
      </GlassCard>

      {/* 解析结果预览 */}
      {rows.length > 0 && (
        <>
          <div className="flex items-center justify-between">
            <p className="text-sm text-text-muted">
              {t('questions.noteImport.importHint')}
              {` · ${t('questions.noteImport.parsedCount', { count: parsedCount })}`}
            </p>
            <button
              onClick={toggleAll}
              className="text-xs text-silver-300 hover:text-silver-100 transition-colors"
            >
              {allChecked ? t('questions.noteImport.uncheckAll') : t('questions.noteImport.checkAll')}
            </button>
          </div>

          <div className="space-y-4">
            {rows.map((row, index) => (
              <GlassCard key={row.key} className="p-4">
                <div className="mb-3 flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <input
                      type="checkbox"
                      checked={row.checked}
                      onChange={() => toggleRow(row.key)}
                      className="h-4 w-4 accent-silver-400"
                    />
                    <span className="text-sm font-medium text-text-secondary">
                      {t('questions.import.questionIndex', { index: index + 1 })}
                    </span>
                    {row.matchedExistingId != null && (
                      <span className="text-xs text-amber-500">
                        {t('questions.noteImport.duplicateWith', {
                          id: row.matchedExistingId,
                        })}
                      </span>
                    )}
                  </div>
                  <button
                    onClick={() => removeRow(row.key)}
                    className="text-xs text-danger hover:text-danger/80 transition-colors"
                  >
                    {t('common.delete')}
                  </button>
                </div>
                <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                  <div>
                    <Label>{t('questions.category')}</Label>
                    <Select
                      value={row.category}
                      onChange={(e) => updateRow(row.key, 'category', e.target.value)}
                    >
                      {CATEGORIES.map((c) => (
                        <option key={c} value={c}>
                          {enumLabel('category', c)}
                        </option>
                      ))}
                    </Select>
                  </div>
                  <div>
                    <Label>{t('questions.difficulty')}</Label>
                    <Select
                      value={row.difficulty}
                      onChange={(e) => updateRow(row.key, 'difficulty', e.target.value)}
                    >
                      {DIFFICULTIES.map((d) => (
                        <option key={d} value={d}>
                          {enumLabel('difficulty', d)}
                        </option>
                      ))}
                    </Select>
                  </div>
                  <div className="col-span-2">
                    <Label>{t('questions.topic')}</Label>
                    <Input
                      value={row.topic}
                      onChange={(e) => updateRow(row.key, 'topic', e.target.value)}
                    />
                  </div>
                </div>
                <div className="mt-3">
                  <Label>{t('questions.content')}</Label>
                  <Textarea
                    rows={2}
                    value={row.content}
                    onChange={(e) => updateRow(row.key, 'content', e.target.value)}
                  />
                </div>
                <div className="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-2">
                  <div>
                    <Label>{t('questions.standardAnswer')}</Label>
                    <Textarea
                      rows={2}
                      value={row.standardAnswer}
                      onChange={(e) => updateRow(row.key, 'standardAnswer', e.target.value)}
                    />
                  </div>
                  <div>
                    <Label>{t('questions.tags')}</Label>
                    <Input
                      placeholder={t('questions.inputTagsPlaceholderRequired')}
                      value={row.tags}
                      onChange={(e) => updateRow(row.key, 'tags', e.target.value)}
                    />
                  </div>
                </div>
              </GlassCard>
            ))}
          </div>

          {/* 底部操作 */}
          <div className="flex items-center justify-between">
            <span className="text-sm text-text-muted">
              {t('questions.import.submitImport', { count: selectedCount })}
            </span>
            <SilverButton
              onClick={handleSubmit}
              disabled={importMutation.isPending || selectedCount === 0}
            >
              {importMutation.isPending
                ? t('questions.import.importing')
                : t('questions.noteImport.submitImport')}
            </SilverButton>
          </div>
        </>
      )}
    </div>
  );
}
