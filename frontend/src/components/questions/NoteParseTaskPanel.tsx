import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { GlassCard } from '@/components/ui/glass-card';
import { useNoteParseTasks } from '@/hooks/useNoteParseTasks';
import type { InterviewNoteParseTask } from '@/types/question';

function TaskStatus({ task }: { task: InterviewNoteParseTask }) {
  const { t } = useTranslation();
  if (task.status === 'RUNNING') {
    return <span className="text-xs text-text-muted">{t('questions.noteImport.parsing')}</span>;
  }
  if (task.status === 'SUCCESS') {
    return (
      <span className="text-xs text-success">
        {t('questions.noteImport.taskSuccess', { count: task.results?.length ?? 0 })}
      </span>
    );
  }
  if (task.status === 'FAILED') {
    return (
      <span className="text-xs text-danger">
        {t('questions.noteImport.taskFailed')}
        {task.message ? `：${task.message}` : ''}
      </span>
    );
  }
  return (
    <span className="text-xs text-text-muted">{t('questions.noteImport.taskExpired')}</span>
  );
}

/** 题库管理页顶部：面经解析任务卡片区（多任务，状态每 2s 轮询，F5 刷新保留）。 */
export function NoteParseTaskPanel() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { tasks, hasTasks, removeTask } = useNoteParseTasks();

  if (!hasTasks) return null;

  return (
    <GlassCard className="p-4">
      <div className="mb-3">
        <h3 className="text-sm font-semibold text-text-primary">
          {t('questions.noteImport.taskPanelTitle')}
        </h3>
      </div>
      <div className="space-y-2">
        {tasks.map((task) => (
          <div
            key={task.taskId}
            className="flex items-center justify-between gap-3 rounded-lg border border-border-subtle px-3 py-2"
          >
            <div className="flex min-w-0 items-center gap-2">
              {task.status === 'RUNNING' && (
                <span className="h-2.5 w-2.5 shrink-0 animate-spin rounded-full border-2 border-silver-300 border-t-transparent" />
              )}
              <span className="text-xs text-text-muted">
                {t('questions.noteImport.taskIdLabel', { taskId: task.taskId.slice(0, 8) })}
              </span>
              <TaskStatus task={task} />
            </div>
            <div className="flex shrink-0 items-center gap-3">
              {task.status === 'SUCCESS' && (
                <button
                  onClick={() => navigate(`/questions/note-import?task=${task.taskId}`)}
                  className="text-xs text-silver-300 hover:text-silver-100 transition-colors"
                >
                  {t('questions.noteImport.viewResults')}
                </button>
              )}
              <button
                onClick={() => removeTask(task.taskId)}
                className="text-xs text-danger hover:text-danger/80 transition-colors"
              >
                {t('common.delete')}
              </button>
            </div>
          </div>
        ))}
      </div>
    </GlassCard>
  );
}
