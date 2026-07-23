import { reactive } from 'vue';
import { postJson } from '../../shared/apiClient';
import { writeClipboardText as writeBridgeClipboardText } from '../../shared/desktopBridge';
import type { ProfileSuggestion, ReplySelectedPayload, SuggestionShowPayload } from './types';

export const copyBackfillState = reactive({
  suggestionToastVisible: false,
  suggestionToastCollapsed: false,
  suggestionToastPhone: '',
  suggestionToastSuggestions: [] as ProfileSuggestion[],
  toast: ''
});

export async function handleReplySelected(payload: ReplySelectedPayload): Promise<void> {
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

  if (!payload.phone || !payload.replySource) {
    return;
  }

  const controller = new AbortController();
  void recordAiUsage(payload, controller);
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
