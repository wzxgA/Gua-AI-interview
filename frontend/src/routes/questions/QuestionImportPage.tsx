import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { useTranslation } from 'react-i18next';
import { GlassCard } from '@/components/ui/glass-card';
import { SilverButton } from '@/components/ui/silver-button';
import { Input, Textarea, Select, Label } from '@/components/ui/input';
import { PageHeader } from '@/components/common/PageHeader';
import { useImportQuestions } from '@/api/questions';
import type { CreateQuestionRequest } from '@/types/question';
import { CATEGORIES, DIFFICULTIES } from '@/lib/constants';
import { useEnumLabel } from '@/hooks/useEnumLabel';

interface QuestionRow {
  key: string;
  category: string;
  topic: string;
  difficulty: string;
  content: string;
  standardAnswer: string;
  tags: string;
}

function createEmptyRow(): QuestionRow {
  return {
    key: crypto.randomUUID(),
    category: 'TECHNICAL',
    topic: '',
    difficulty: 'MEDIUM',
    content: '',
    standardAnswer: '',
    tags: '',
  };
}

export function QuestionImportPage() {
  const { t } = useTranslation();
  const enumLabel = useEnumLabel();
  const navigate = useNavigate();
  const [rows, setRows] = useState<QuestionRow[]>([createEmptyRow()]);
  const [jsonText, setJsonText] = useState('');
  const importMutation = useImportQuestions();

  const updateRow = (key: string, field: keyof QuestionRow, value: string) => {
    setRows((prev) => prev.map((r) => (r.key === key ? { ...r, [field]: value } : r)));
  };

  const addRow = () => {
    setRows((prev) => [...prev, createEmptyRow()]);
  };

  const removeRow = (key: string) => {
    setRows((prev) => prev.filter((r) => r.key !== key));
  };

  const parseJson = () => {
    if (!jsonText.trim()) {
      toast.error(t('questions.import.pasteFirst'));
      return;
    }
    try {
      const parsed = JSON.parse(jsonText);
      if (!Array.isArray(parsed)) {
        toast.error(t('questions.import.arrayRequired'));
        return;
      }
      const newRows: QuestionRow[] = parsed.map((item: Record<string, unknown>) => ({
        key: crypto.randomUUID(),
        category: (item.category as string) || 'TECHNICAL',
        topic: (item.topic as string) || '',
        difficulty: (item.difficulty as string) || 'MEDIUM',
        content: (item.content as string) || '',
        standardAnswer: (item.standardAnswer as string) || '',
        tags: Array.isArray(item.tags) ? (item.tags as string[]).join(', ') : (item.tags as string) || '',
      }));
      setRows(newRows);
      setJsonText('');
      toast.success(t('questions.import.parsedCount', { count: newRows.length }));
    } catch {
      toast.error(t('questions.import.parseFailed'));
    }
  };

  const handleSubmit = () => {
    const validRows = rows.filter((r) => r.content.trim() && r.topic.trim());
    if (validRows.length === 0) {
      toast.error(t('questions.import.atLeastOne'));
      return;
    }

    const questions: CreateQuestionRequest[] = validRows.map((r) => ({
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
          toast.success(t('questions.import.importSuccess', { count: questions.length }));
          navigate('/questions');
        },
        onError: (err: Error) => toast.error(err.message || t('questions.import.importFailed')),
      },
    );
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title={t('questions.import.title')}
        subtitle={t('questions.import.subtitle')}
        action={
          <SilverButton variant="ghost" onClick={() => navigate('/questions')}>
            {t('questions.backToList')}
          </SilverButton>
        }
      />

      {/* JSON 批量填充 */}
      <GlassCard className="p-6">
        <h3 className="mb-3 text-sm font-medium text-text-muted">
          {t('questions.import.pasteJsonTitle')}
        </h3>
        <Textarea
          rows={5}
          placeholder={t('questions.import.jsonPlaceholder')}
          value={jsonText}
          onChange={(e) => setJsonText(e.target.value)}
          className="font-mono text-xs"
        />
        <div className="mt-2 flex justify-end">
          <SilverButton variant="ghost" onClick={parseJson}>
            {t('questions.import.parseJson')}
          </SilverButton>
        </div>
      </GlassCard>

      {/* 题目行列表 */}
      <div className="space-y-4">
        {rows.map((row, index) => (
          <GlassCard key={row.key} className="p-4">
            <div className="mb-3 flex items-center justify-between">
              <span className="text-sm font-medium text-text-secondary">
                {t('questions.import.questionIndex', { index: index + 1 })}
              </span>
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
                  placeholder={t('questions.inputTopicPlaceholder')}
                  value={row.topic}
                  onChange={(e) => updateRow(row.key, 'topic', e.target.value)}
                />
              </div>
            </div>
            <div className="mt-3">
              <Label>{t('questions.content')}</Label>
              <Textarea
                rows={2}
                placeholder={t('questions.inputContentPlaceholder')}
                value={row.content}
                onChange={(e) => updateRow(row.key, 'content', e.target.value)}
              />
            </div>
            <div className="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-2">
              <div>
                <Label>{t('questions.standardAnswer')}</Label>
                <Textarea
                  rows={2}
                  placeholder={t('questions.inputStandardAnswerPlaceholder')}
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
        <SilverButton variant="ghost" onClick={addRow}>
          {t('questions.import.addRow')}
        </SilverButton>
        <SilverButton
          onClick={handleSubmit}
          disabled={importMutation.isPending}
        >
          {importMutation.isPending
            ? t('questions.import.importing')
            : t('questions.import.submitImport', {
                count: rows.filter((r) => r.content.trim() && r.topic.trim()).length,
              })}
        </SilverButton>
      </div>
    </div>
  );
}
