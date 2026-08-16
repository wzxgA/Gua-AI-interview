import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { http } from '@/api/client';
import { loadNoteTaskIds, removeNoteTask } from '@/lib/noteParseTasks';
import type { InterviewNoteParseTask } from '@/types/question';

/**
 * 面经解析任务列表（跨页面共享）：任务 ID 持久化在 localStorage（F5 刷新保留），
 * 状态经 GET /interview-notes/parse/{taskId} 轮询获取（后端内存任务表）。
 */
export function useNoteParseTasks() {
  const [taskIds, setTaskIds] = useState<string[]>(() => loadNoteTaskIds());

  // 跨标签页同步：其他标签页增删任务时刷新本页列表
  useEffect(() => {
    const handler = () => setTaskIds(loadNoteTaskIds());
    window.addEventListener('storage', handler);
    return () => window.removeEventListener('storage', handler);
  }, []);

  const query = useQuery({
    queryKey: ['note-parse-tasks', taskIds],
    queryFn: async (): Promise<InterviewNoteParseTask[]> => {
      const results = await Promise.all(
        taskIds.map((id) =>
          http.get<InterviewNoteParseTask>(`/api/v1/questions/interview-notes/parse/${id}`),
        ),
      );
      return results;
    },
    enabled: taskIds.length > 0,
    refetchInterval: 2000,
  });

  const handleRemove = (taskId: string) => {
    removeNoteTask(taskId);
    setTaskIds((prev) => prev.filter((id) => id !== taskId));
  };

  return {
    tasks: query.data ?? [],
    hasTasks: taskIds.length > 0,
    removeTask: handleRemove,
    refetch: query.refetch,
  };
}
