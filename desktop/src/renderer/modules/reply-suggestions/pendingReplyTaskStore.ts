import { computed, reactive } from 'vue';
import { getJson } from '../../shared/apiClient';
import {
  removeMissingPendingReplySessions,
  restoreAndActivatePendingReplyTask,
  syncPendingReplyTaskIntoSession
} from './replySuggestionStore';
import type { PendingReplyTask } from './types';

export const pendingReplyTaskState = reactive({
  tasks: [] as PendingReplyTask[],
  activeTaskId: '',
  isRefreshing: false,
  lastError: ''
});

export const pendingReplyTaskCount = computed(() => pendingReplyTaskState.tasks.length);

let refreshSequence = 0;

export async function refreshPendingReplyTasks(): Promise<void> {
  const sequence = ++refreshSequence;
  pendingReplyTaskState.isRefreshing = true;
  try {
    const response = await getJson<PendingReplyTask[]>('/api/v1/chat/reply-tasks', 5000);
    if (sequence !== refreshSequence) return;
    if (!response.success || !response.data) {
      pendingReplyTaskState.lastError = response.message ?? '任务恢复失败';
      return;
    }
    pendingReplyTaskState.tasks = response.data.slice();
    pendingReplyTaskState.lastError = '';
    response.data.forEach(syncPendingReplyTaskIntoSession);
    removeMissingPendingReplySessions(new Set(response.data.map((task) => task.taskId)));
  } catch (error) {
    if (sequence !== refreshSequence) return;
    pendingReplyTaskState.lastError = error instanceof Error ? error.message : '任务恢复失败';
  } finally {
    if (sequence === refreshSequence) {
      pendingReplyTaskState.isRefreshing = false;
    }
  }
}

export function syncPendingReplyTask(task: PendingReplyTask): void {
  const index = pendingReplyTaskState.tasks.findIndex((item) => item.taskId === task.taskId);
  if (index >= 0) {
    pendingReplyTaskState.tasks.splice(index, 1, task);
  } else {
    pendingReplyTaskState.tasks.push(task);
  }
  syncPendingReplyTaskIntoSession(task);
}

export function openPendingReplyTask(taskId: string): boolean {
  const task = pendingReplyTaskState.tasks.find((item) => item.taskId === taskId);
  if (!task) return false;
  if (!restoreAndActivatePendingReplyTask(task)) return false;
  pendingReplyTaskState.activeTaskId = taskId;
  return true;
}
