import { computed, reactive } from 'vue';
import { getJson } from '../../shared/apiClient';
import { notifyReplyTask, onReplyTaskOpen } from '../../shared/desktopBridge';
import { eventBus } from '../../shared/eventBus';
import {
  removeMissingPendingReplySessions,
  removePendingReplyTaskSession,
  resetReplySuggestionStoreForSessionChange,
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
const notifiedWaitingTaskIds = new Set<string>();
const waitingNotificationAttempts = new Map<string, symbol>();
const foregroundAttendedWaitingTaskIds = new Set<string>();
let removeReplyTaskOpenListener: (() => void) | null = null;

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
    handleWaitingTaskAttention(response.data);
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
  if (task.status !== 'WAITING_CUSTOMER') {
    clearTaskAttention(task.taskId);
  }
}

export function receivePendingReplyTask(task: PendingReplyTask): void {
  syncPendingReplyTask(task);
  cleanupTaskAttention(pendingReplyTaskState.tasks);
  if (task.status !== 'WAITING_CUSTOMER') return;
  if (document.hasFocus()) {
    foregroundAttendedWaitingTaskIds.add(task.taskId);
    return;
  }
  requestWaitingTaskNotification(task);
}

export function openRecoveredReplyTask(taskId: string): boolean {
  const task = pendingReplyTaskState.tasks.find((item) => item.taskId === taskId);
  if (!task) return false;
  if (!restoreAndActivatePendingReplyTask(task)) return false;
  pendingReplyTaskState.activeTaskId = taskId;
  if (task.status === 'WAITING_CUSTOMER') {
    foregroundAttendedWaitingTaskIds.add(task.taskId);
    eventBus.emit('recognize:multiple', {
      sessionId: task.replySessionId,
      taskId: task.taskId,
      candidates: task.candidates.slice()
    });
  }
  return true;
}

export const openPendingReplyTask = openRecoveredReplyTask;

export function initializePendingReplyTaskOpenListener(): () => void {
  removeReplyTaskOpenListener?.();
  const dispose = onReplyTaskOpen((payload) => {
    if (payload?.taskId) {
      openRecoveredReplyTask(payload.taskId);
    }
  });
  removeReplyTaskOpenListener = dispose;
  return () => {
    if (removeReplyTaskOpenListener === dispose) {
      removeReplyTaskOpenListener = null;
    }
    dispose();
  };
}

export function removePendingReplyTask(taskId: string, replySessionId = ''): void {
  const task = pendingReplyTaskState.tasks.find((item) => item.taskId === taskId);
  const sessionId = replySessionId || task?.replySessionId || '';
  pendingReplyTaskState.tasks = pendingReplyTaskState.tasks.filter((item) => item.taskId !== taskId);
  if (pendingReplyTaskState.activeTaskId === taskId) {
    pendingReplyTaskState.activeTaskId = '';
  }
  if (sessionId) {
    removePendingReplyTaskSession(taskId, sessionId);
  }
  clearTaskAttention(taskId);
}

export function resetPendingReplyTasksForSessionChange(): void {
  refreshSequence += 1;
  pendingReplyTaskState.tasks = [];
  pendingReplyTaskState.activeTaskId = '';
  pendingReplyTaskState.isRefreshing = false;
  pendingReplyTaskState.lastError = '';
  notifiedWaitingTaskIds.clear();
  waitingNotificationAttempts.clear();
  foregroundAttendedWaitingTaskIds.clear();
  resetReplySuggestionStoreForSessionChange();
}

function handleWaitingTaskAttention(tasks: PendingReplyTask[]): void {
  cleanupTaskAttention(tasks);
  const waitingTasks = tasks.filter((task) => task.status === 'WAITING_CUSTOMER');
  if (document.hasFocus()) {
    const taskToOpen = waitingTasks.find((task) => !foregroundAttendedWaitingTaskIds.has(task.taskId));
    waitingTasks.forEach((task) => foregroundAttendedWaitingTaskIds.add(task.taskId));
    if (taskToOpen) {
      openRecoveredReplyTask(taskToOpen.taskId);
    }
    return;
  }
  waitingTasks.forEach(requestWaitingTaskNotification);
}

function cleanupTaskAttention(tasks: PendingReplyTask[]): void {
  const waitingTaskIds = new Set(
    tasks.filter((task) => task.status === 'WAITING_CUSTOMER').map((task) => task.taskId)
  );
  for (const taskId of notifiedWaitingTaskIds) {
    if (!waitingTaskIds.has(taskId)) {
      notifiedWaitingTaskIds.delete(taskId);
    }
  }
  for (const taskId of waitingNotificationAttempts.keys()) {
    if (!waitingTaskIds.has(taskId)) {
      waitingNotificationAttempts.delete(taskId);
    }
  }
  for (const taskId of foregroundAttendedWaitingTaskIds) {
    if (!waitingTaskIds.has(taskId)) {
      foregroundAttendedWaitingTaskIds.delete(taskId);
    }
  }
}

function clearTaskAttention(taskId: string): void {
  notifiedWaitingTaskIds.delete(taskId);
  waitingNotificationAttempts.delete(taskId);
  foregroundAttendedWaitingTaskIds.delete(taskId);
}

function requestWaitingTaskNotification(task: PendingReplyTask): void {
  if (foregroundAttendedWaitingTaskIds.has(task.taskId)
    || notifiedWaitingTaskIds.has(task.taskId)
    || waitingNotificationAttempts.has(task.taskId)) return;
  const attempt = Symbol(task.taskId);
  waitingNotificationAttempts.set(task.taskId, attempt);
  void notifyReplyTask({ taskId: task.taskId }).then((result) => {
    if (waitingNotificationAttempts.get(task.taskId) !== attempt) return;
    waitingNotificationAttempts.delete(task.taskId);
    if (result.success) {
      notifiedWaitingTaskIds.add(task.taskId);
    }
  }, () => {
    if (waitingNotificationAttempts.get(task.taskId) === attempt) {
      waitingNotificationAttempts.delete(task.taskId);
    }
  });
}
