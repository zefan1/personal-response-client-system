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
    confirmationId: crypto.randomUUID(),
    status: 'AWAITING_DECISION',
    createdAt: new Date().toISOString(),
    errorMessage: ''
  };
  persistPendingSendDecision();

  if (!payload.phone || !payload.replySource) {
    return;
  }

  const controller = new AbortController();
  void recordAiUsage(payload, controller);
}

export function discardPendingSendDecision(): void {
  copyBackfillState.pendingSendDecision = null;
  localStorage.removeItem(PENDING_SEND_STORAGE_KEY);
  copyBackfillState.toast = '已标记为未发送，不更新客户表格';
}

export async function confirmPendingSendDecision(): Promise<boolean> {
  const pending = copyBackfillState.pendingSendDecision;
  if (!pending) {
    return false;
  }
  if (!pending.customerId || pending.customerId <= 0) {
    pending.status = 'AWAITING_DECISION';
    pending.errorMessage = '当前回复没有对应的客户档案，请重新识别聊天';
    persistPendingSendDecision();
    return false;
  }
  pending.status = 'SUBMITTING';
  pending.errorMessage = '';
  persistPendingSendDecision();
  try {
    const response = await postJson<{ accepted?: boolean }>('/api/v1/chat/send-confirm', {
      confirmationId: pending.confirmationId,
      customerId: pending.customerId,
      phone: pending.phone,
      nickname: pending.nickname ?? '',
      conversationSummary: '',
      isNewCustomer: false,
      sentText: pending.text,
      selectedDirection: pending.isFallback ? 'SYSTEM_FALLBACK' : pending.direction
    });
    if (!response.success || response.data?.accepted !== true) {
      throw new Error(response.message ?? response.errorCode ?? '确认提交失败');
    }
    const phone = pending.phone;
    copyBackfillState.pendingSendDecision = null;
    localStorage.removeItem(PENDING_SEND_STORAGE_KEY);
    copyBackfillState.toast = '已确认发送，客户表格正在更新';
    if (!pending.phone) {
      copyBackfillState.toast = '已确认发送，聊天已归档；表格因缺少唯一字段暂未同步';
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
      errorMessage: parsed.status === 'SUBMIT_FAILED' ? parsed.errorMessage ?? '' : ''
    };
    localStorage.setItem(PENDING_SEND_STORAGE_KEY, JSON.stringify(restored));
    return restored;
  } catch {
    localStorage.removeItem(PENDING_SEND_STORAGE_KEY);
    return null;
  }
}
