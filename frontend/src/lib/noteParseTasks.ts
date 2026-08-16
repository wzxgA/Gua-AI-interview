const STORAGE_KEY = 'aims.noteParseTasks';

/** 读取已提交的面经解析任务 ID 列表（localStorage，跨页面/刷新保留）。 */
export function loadNoteTaskIds(): string[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const arr = JSON.parse(raw);
    return Array.isArray(arr) ? arr.filter((x): x is string => typeof x === 'string') : [];
  } catch {
    return [];
  }
}

/** 记录一个已提交的解析任务 ID（去重）。 */
export function addNoteTask(taskId: string): void {
  const ids = loadNoteTaskIds();
  if (!ids.includes(taskId)) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify([...ids, taskId]));
  }
}

/** 从记录中移除一个任务 ID。 */
export function removeNoteTask(taskId: string): void {
  localStorage.setItem(
    STORAGE_KEY,
    JSON.stringify(loadNoteTaskIds().filter((id) => id !== taskId)),
  );
}
