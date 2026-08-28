import { reactive } from 'vue';
import { postJson } from '../../shared/apiClient';
import { writeClipboardText as writeBridgeClipboardText } from '../../shared/desktopBridge';
import { eventBus } from '../../shared/eventBus';
import type {
  PendingSendDecision,
  ProfileSuggestion,
  ReplySelectedPayload,
  SuggestionShowPayload
} from './types';

const PENDING_SEND_STORAGE_KEY = 'copy_backfill_pending_send';
const REMINDER_INTERVAL_MS = 5 * 60 * 1000;
const MAX_REMINDERS = 5;
const TOAST_DISMISS_MS = 4000;

let reminderTimer: number | null = null;
let toastTimer: number | null = null;

export const copyBackfillState = reactive({
  pendingSendDecision: restorePendingSendDecision(),
  suggestionToastVisible: false,
  suggestionToastCollapsed: false,
  suggestionToastPhone: '',
  suggestionToastSuggestions: [] as ProfileSuggestion[],
  toast: ''
});

export async function handleReplySelected(payload: ReplySelectedPayload): Promise<void> {
  if (copyBackfillState.pendingSendDecision) {
    return;
  }
  if (!payload.text.trim()) {
    copyBackfillState.toast = '复制失败，请重试';
    return;
  }

  const clipboardWritten = await writeClipboardText(payload.text);
  if (!clipboardWritten) {
    copyBackfillState.toast = '复制失败，请重试';
    return;
  }
  copyBackfillState.toast = '已复制到剪贴板，请粘贴到微信发送';
  copyBackfillState.pendingSendDecision = {
    ...payload,
    confirmationId: createConfirmationId(),
    status: 'AWAITING_DECISION',
    createdAt: new Date().toISOString(),
    errorMessage: '',
    reminderCount: 0,
    lastReminderAt: undefined
  };
  persistPendingSendDecision();
  void registerPendingSendDecision(copyBackfillState.pendingSendDecision);
  startReminderTimer();
}

function createConfirmationId(): string {
  if (typeof globalThis.crypto?.randomUUID === 'function') {
    return globalThis.crypto.randomUUID();
  }
  return `copy-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

export function discardPendingSendDecision(): void {
  const pending = copyBackfillState.pendingSendDecision;
  if (pending) {
    void updatePendingSendStatus(pending.confirmationId, 'UNSENT', pending.reminderCount);
  }
  stopReminderTimer();
  copyBackfillState.pendingSendDecision = null;
  localStorage.removeItem(PENDING_SEND_STORAGE_KEY);
  setToast('已标记为未发送，不更新客户表格');
}

export async function confirmPendingSendDecision(): Promise<boolean> {
  const pending = copyBackfillState.pendingSendDecision;
  if (!pending) {
    return false;
  }
  pending.status = 'SUBMITTING';
  pending.errorMessage = '';
  persistPendingSendDecision();
  try {
    const response = await postJson<{ accepted?: boolean }>('/api/v1/chat/send-confirm', {
      confirmationId: pending.confirmationId,
      customerId: pending.customerId ?? null,
      phone: pending.phone,
      nickname: pending.nickname ?? '',
      conversationSummary: '',
      isNewCustomer: !pending.customerId,
      sentText: pending.text,
      selectedDirection: pending.isFallback ? 'SYSTEM_FALLBACK' : pending.direction
    });
    if (!response.success || response.data?.accepted !== true) {
      throw new Error(response.message ?? response.errorCode ?? '确认提交失败');
    }
    const phone = pending.phone;
    if (pending.phone && pending.replySource) {
      void recordAiUsage(pending, new AbortController());
    }
    stopReminderTimer();
    copyBackfillState.pendingSendDecision = null;
    localStorage.removeItem(PENDING_SEND_STORAGE_KEY);
    setToast('已确认发送，客户表格正在更新');
    if (!pending.phone) {
      setToast('已确认发送，聊天已归档；表格因缺少唯一字段暂未同步');
    }
    eventBus.emit('reply:send-confirmed', { phone, customerId: pending.customerId });
    return true;
  } catch (error) {
    pending.status = 'SUBMIT_FAILED';
    pending.errorMessage = error instanceof Error && error.message
      ? error.message
      : '确认失败，请检查网络后重试';
    persistPendingSendDecision();
    return false;
  }
}

export function retryRecognitionFromPending(): void {
  const pending = copyBackfillState.pendingSendDecision;
  if (pending) {
    void updatePendingSendStatus(pending.confirmationId, 'RECOGNITION_RETRY', pending.reminderCount);
  }
  stopReminderTimer();
  copyBackfillState.pendingSendDecision = null;
  localStorage.removeItem(PENDING_SEND_STORAGE_KEY);
  copyBackfillState.toast = '正在重新识别当前聊天';
  eventBus.emit('workbench:capture-chat', {});
}

export function resumePendingSendReminder(): void {
  const pending = copyBackfillState.pendingSendDecision;
  if (pending && pending.reminderCount < MAX_REMINDERS) {
    startReminderTimer();
  }
}

export function handleSuggestionShow(payload: SuggestionShowPayload): void {
  copyBackfillState.suggestionToastVisible = false;
  copyBackfillState.suggestionToastCollapsed = true;
  copyBackfillState.suggestionToastPhone = payload.phone;
  copyBackfillState.suggestionToastSuggestions = payload.suggestions.map((item) => ({
    ...item,
    resolved: item.resolved ?? false,
    resolving: false
  }));
}

export function reopenSuggestionToast(): void {
  copyBackfillState.suggestionToastVisible = true;
  copyBackfillState.suggestionToastCollapsed = false;
}

export function closeSuggestionToast(): void {
  copyBackfillState.suggestionToastVisible = false;
  copyBackfillState.suggestionToastCollapsed = true;
}

export async function resolveToastSuggestion(action: 'CONFIRM' | 'REJECT', suggestion?: ProfileSuggestion): Promise<void> {
  const targets = suggestion ? [suggestion] : copyBackfillState.suggestionToastSuggestions.filter((item) => !item.resolved);
  if (!copyBackfillState.suggestionToastPhone || targets.length === 0) {
    return;
  }
  targets.forEach((item) => {
    item.resolving = true;
  });
  const suggestionIds = targets.map((item) => item.suggestionId).filter((id): id is number => typeof id === 'number');
  try {
    await postJson(`/api/v1/customers/${encodeURIComponent(copyBackfillState.suggestionToastPhone)}/suggestions/batch-resolve`, {
      action,
      suggestionIds,
      operator: 'desktop'
    });
    targets.forEach((item) => {
      item.resolved = true;
      item.resolving = false;
      item.resolveAction = action;
    });
    if (copyBackfillState.suggestionToastSuggestions.every((item) => item.resolved)) {
      copyBackfillState.suggestionToastVisible = false;
      copyBackfillState.suggestionToastCollapsed = false;
    }
  } catch {
    targets.forEach((item) => {
      item.resolving = false;
    });
    copyBackfillState.toast = '操作失败，请重试';
  }
}

export function cleanupCopyBackfillStore(): void {
  stopReminderTimer();
  copyBackfillState.suggestionToastVisible = false;
  copyBackfillState.suggestionToastCollapsed = false;
  copyBackfillState.suggestionToastPhone = '';
  copyBackfillState.suggestionToastSuggestions = [];
  copyBackfillState.toast = '';
}

async function writeClipboardText(text: string): Promise<boolean> {
  const result = await writeBridgeClipboardText(text);
  return result.success;
}

async function recordAiUsage(payload: ReplySelectedPayload, controller: AbortController): Promise<void> {
  try {
    const response = await postJson('/api/v1/chat/ai-usage', {
      phone: payload.phone,
      taskId: payload.taskId ?? null,
      replySessionId: payload.replySessionId ?? null,
      replySource: payload.replySource,
      copiedText: payload.text
    }, undefined, controller.signal);
    if (!response.success) {
      throw new Error(response.message ?? response.errorCode ?? 'AI usage record failed');
    }
  } catch {
    if (!controller.signal.aborted) {
      copyBackfillState.toast = '已复制，但 AI 使用记录未同步，不影响正常跟进';
    }
  }
}

function persistPendingSendDecision(): void {
  if (copyBackfillState.pendingSendDecision) {
    localStorage.setItem(PENDING_SEND_STORAGE_KEY, JSON.stringify(copyBackfillState.pendingSendDecision));
  }
}

function startReminderTimer(): void {
  stopReminderTimer();
  if (!copyBackfillState.pendingSendDecision) return;
  reminderTimer = window.setTimeout(() => {
    reminderTimer = null;
    const pending = copyBackfillState.pendingSendDecision;
    if (!pending || pending.status === 'SUBMITTING' || pending.reminderCount >= MAX_REMINDERS) {
      return;
    }
    pending.reminderCount += 1;
    pending.lastReminderAt = new Date().toISOString();
    copyBackfillState.toast = pending.reminderCount === MAX_REMINDERS
      ? '仍未确认发送情况，请在下次使用时确认'
      : `仍需确认发送情况（第 ${pending.reminderCount} 次提醒）`;
    persistPendingSendDecision();
    void updatePendingSendStatus(pending.confirmationId, 'AWAITING_DECISION', pending.reminderCount);
    if (pending.reminderCount < MAX_REMINDERS) {
      startReminderTimer();
    }
  }, REMINDER_INTERVAL_MS);
}

async function registerPendingSendDecision(pending: PendingSendDecision): Promise<void> {
  try {
    await postJson('/api/v1/chat/send-pending', {
      confirmationId: pending.confirmationId,
      customerId: pending.customerId ?? null,
      phone: pending.phone,
      nickname: pending.nickname ?? '',
      copiedText: pending.text,
      replySource: pending.replySource ?? null
    });
  } catch {
    // The local gate remains authoritative for the employee while the server is unavailable.
  }
}

async function updatePendingSendStatus(
  confirmationId: string,
  status: 'AWAITING_DECISION' | 'UNSENT' | 'RECOGNITION_RETRY',
  reminderCount: number
): Promise<void> {
  try {
    await postJson('/api/v1/chat/send-pending/status', {
      confirmationId,
      status,
      reminderCount
    });
  } catch {
    // Status is still retained locally and can be synchronized on the next interaction.
  }
}

function stopReminderTimer(): void {
  if (reminderTimer !== null) {
    window.clearTimeout(reminderTimer);
    reminderTimer = null;
  }
}

function setToast(message: string): void {
  if (toastTimer !== null) window.clearTimeout(toastTimer);
  copyBackfillState.toast = message;
  toastTimer = window.setTimeout(() => {
    copyBackfillState.toast = '';
    toastTimer = null;
  }, TOAST_DISMISS_MS);
}

function restorePendingSendDecision(): PendingSendDecision | null {
  const raw = localStorage.getItem(PENDING_SEND_STORAGE_KEY);
  if (!raw) {
    return null;
  }
  try {
    const parsed = JSON.parse(raw) as Partial<PendingSendDecision>;
    if (!parsed.confirmationId || !parsed.text || !parsed.direction || typeof parsed.phone !== 'string'
      || !parsed.createdAt || typeof parsed.isFallback !== 'boolean') {
      throw new Error('invalid pending send decision');
    }
    const restored: PendingSendDecision = {
      ...parsed,
      confirmationId: parsed.confirmationId,
      text: parsed.text,
      direction: parsed.direction,
      reason: parsed.reason ?? '',
      phone: parsed.phone,
      isFallback: parsed.isFallback,
      status: parsed.status === 'SUBMIT_FAILED' ? 'SUBMIT_FAILED' : 'AWAITING_DECISION',
      createdAt: parsed.createdAt,
      errorMessage: parsed.status === 'SUBMIT_FAILED' ? parsed.errorMessage ?? '' : '',
      reminderCount: typeof parsed.reminderCount === 'number' ? Math.max(0, Math.min(MAX_REMINDERS, parsed.reminderCount)) : 0,
      lastReminderAt: typeof parsed.lastReminderAt === 'string' ? parsed.lastReminderAt : undefined
    };
    localStorage.setItem(PENDING_SEND_STORAGE_KEY, JSON.stringify(restored));
    if (restored.reminderCount < MAX_REMINDERS) {
      window.setTimeout(startReminderTimer, 0);
    }
    return restored;
  } catch {
    localStorage.removeItem(PENDING_SEND_STORAGE_KEY);
    return null;
  }
}
