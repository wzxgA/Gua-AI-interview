import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { GlassCard } from '@/components/ui/glass-card';
import { SilverButton } from '@/components/ui/silver-button';
import { Input, Textarea, Select, Label } from '@/components/ui/input';
import { PageHeader } from '@/components/common/PageHeader';
import { useImportQuestions } from '@/api/questions';
import type { CreateQuestionRequest } from '@/types/question';
import {
  CATEGORIES,
  DIFFICULTIES,
  CATEGORY_LABELS,
  DIFFICULTY_LABELS,
} from '@/lib/constants';

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
      toast.error('请先粘贴 JSON 内容');
      return;
    }
    try {
      const parsed = JSON.parse(jsonText);
      if (!Array.isArray(parsed)) {
        toast.error('JSON 应为数组格式');
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
      toast.success(`已解析 ${newRows.length} 条题目`);
    } catch {
      toast.error('JSON 解析失败，请检查格式');
    }
  };

  const handleSubmit = () => {
    const validRows = rows.filter((r) => r.content.trim() && r.topic.trim());
    if (validRows.length === 0) {
      toast.error('请至少填写一条完整题目（主题和题干必填）');
      return;
    }

    const questions: CreateQuestionRequest[] = validRows.map((r) => ({
      category: r.category,
      topic: r.topic,
      difficulty: r.difficulty,
      content: r.content,
      standardAnswer: r.standardAnswer || undefined,
      tags: r.tags
        ? r.tags.split(',').map((t) => t.trim()).filter(Boolean)
        : undefined,
    }));

    importMutation.mutate(
      { questions },
      {
        onSuccess: () => {
          toast.success(`成功导入 ${questions.length} 条题目`);
          navigate('/questions');
        },
        onError: (err: Error) => toast.error(err.message || '导入失败'),
      },
    );
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="批量导入题目"
        subtitle="支持手动添加或粘贴 JSON 批量填充"
        action={
          <SilverButton variant="ghost" onClick={() => navigate('/questions')}>
            返回列表
          </SilverButton>
        }
      />

      {/* JSON 批量填充 */}
      <GlassCard className="p-6">
        <h3 className="mb-3 text-sm font-medium text-text-muted">
          粘贴 JSON 批量填充
        </h3>
        <Textarea
          rows={5}
          placeholder='[{"category":"TECHNICAL","topic":"Java","difficulty":"EASY","content":"什么是多态？","standardAnswer":"...","tags":["Java","OOP"]}]'
          value={jsonText}
          onChange={(e) => setJsonText(e.target.value)}
          className="font-mono text-xs"
        />
        <div className="mt-2 flex justify-end">
          <SilverButton variant="ghost" onClick={parseJson}>
            解析 JSON
          </SilverButton>
        </div>
      </GlassCard>

      {/* 题目行列表 */}
      <div className="space-y-4">
        {rows.map((row, index) => (
          <GlassCard key={row.key} className="p-4">
            <div className="mb-3 flex items-center justify-between">
              <span className="text-sm font-medium text-text-secondary">
                题目 {index + 1}
              </span>
              <button
                onClick={() => removeRow(row.key)}
                className="text-xs text-danger hover:text-danger/80 transition-colors"
              >
                删除
              </button>
            </div>
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
              <div>
                <Label>分类</Label>
                <Select
                  value={row.category}
                  onChange={(e) => updateRow(row.key, 'category', e.target.value)}
                >
                  {CATEGORIES.map((c) => (
                    <option key={c} value={c}>
                      {CATEGORY_LABELS[c]}
                    </option>
                  ))}
                </Select>
              </div>
              <div>
                <Label>难度</Label>
                <Select
                  value={row.difficulty}
                  onChange={(e) => updateRow(row.key, 'difficulty', e.target.value)}
                >
                  {DIFFICULTIES.map((d) => (
                    <option key={d} value={d}>
                      {DIFFICULTY_LABELS[d]}
                    </option>
                  ))}
                </Select>
              </div>
              <div className="col-span-2">
                <Label>主题</Label>
                <Input
                  placeholder="请输入主题"
                  value={row.topic}
                  onChange={(e) => updateRow(row.key, 'topic', e.target.value)}
                />
              </div>
            </div>
            <div className="mt-3">
              <Label>题干</Label>
              <Textarea
                rows={2}
                placeholder="请输入题干"
                value={row.content}
                onChange={(e) => updateRow(row.key, 'content', e.target.value)}
              />
            </div>
            <div className="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-2">
              <div>
                <Label>标准答案</Label>
                <Textarea
                  rows={2}
                  placeholder="请输入标准答案（可选）"
                  value={row.standardAnswer}
                  onChange={(e) => updateRow(row.key, 'standardAnswer', e.target.value)}
                />
              </div>
              <div>
                <Label>标签</Label>
                <Input
                  placeholder="多个标签用逗号分隔"
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
          + 添加题目
        </SilverButton>
        <SilverButton
          onClick={handleSubmit}
          disabled={importMutation.isPending}
        >
          {importMutation.isPending
            ? '导入中...'
            : `提交导入（${rows.filter((r) => r.content.trim() && r.topic.trim()).length} 条）`}
        </SilverButton>
      </div>
    </div>
  );
}
