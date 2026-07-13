import { reactive } from 'vue';
import { postJson } from '../../shared/apiClient';
import { writeClipboardText as writeBridgeClipboardText } from '../../shared/desktopBridge';
import { eventBus } from '../../shared/eventBus';
import type { ProfileSuggestion, ReplySelectedPayload, SuggestionShowPayload } from './types';

export const copyBackfillState = reactive({
  suggestionToastVisible: false,
  suggestionToastCollapsed: false,
  suggestionToastPhone: '',
  suggestionToastSuggestions: [] as ProfileSuggestion[],
  toast: ''
});

let pendingSendConfirm: AbortController | null = null;

export async function handleReplySelected(payload: ReplySelectedPayload): Promise<void> {
  if (!payload.text.trim()) {
    copyBackfillState.toast = '复制失败，请重试';
    return;
  }

  abortPendingSendConfirm();
  const clipboardWritten = await writeClipboardText(payload.text);
  if (!clipboardWritten) {
    copyBackfillState.toast = '复制失败，请重试';
    return;
  }
  copyBackfillState.toast = '已复制到剪贴板，请粘贴到微信发送';

  if (!payload.phone) {
    return;
  }

  const controller = new AbortController();
  pendingSendConfirm = controller;
  void sendConfirm(payload, controller).finally(() => {
    if (pendingSendConfirm === controller) {
      pendingSendConfirm = null;
    }
  });
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
  abortPendingSendConfirm();
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

async function sendConfirm(payload: ReplySelectedPayload, controller: AbortController): Promise<void> {
  try {
    const response = await postJson('/api/v1/chat/send-confirm', {
      phone: payload.phone,
      conversationSummary: '',
      isNewCustomer: false,
      sentText: payload.text,
      selectedDirection: payload.isFallback ? 'SYSTEM_FALLBACK' : payload.direction
    }, undefined, controller.signal);
    if (!response.success) {
      throw new Error(response.message ?? response.errorCode ?? 'send confirm failed');
    }
    copyBackfillState.toast = '已复制并记录发送，档案正在刷新';
    eventBus.emit('reply:send-confirmed', { phone: payload.phone });
  } catch {
    if (!controller.signal.aborted) {
      copyBackfillState.toast = '已复制，但发送记录失败，请稍后刷新档案确认';
    }
  }
}

function abortPendingSendConfirm(): void {
  if (pendingSendConfirm) {
    pendingSendConfirm.abort();
    pendingSendConfirm = null;
  }
}
