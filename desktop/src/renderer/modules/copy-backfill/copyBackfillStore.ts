import { reactive } from 'vue';
import { postJson } from '../../shared/apiClient';
import type { ProfileSuggestion, ReplySelectedPayload, SuggestionShowPayload } from './types';

const SUGGESTION_TOAST_AUTO_COLLAPSE_MS = 15000;

export const copyBackfillState = reactive({
  suggestionToastVisible: false,
  suggestionToastCollapsed: false,
  suggestionToastPhone: '',
  suggestionToastSuggestions: [] as ProfileSuggestion[],
  toast: ''
});

let pendingSendConfirm: AbortController | null = null;
let suggestionToastTimer: number | null = null;

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
  copyBackfillState.suggestionToastVisible = true;
  copyBackfillState.suggestionToastCollapsed = false;
  copyBackfillState.suggestionToastPhone = payload.phone;
  copyBackfillState.suggestionToastSuggestions = payload.suggestions.map((item) => ({
    ...item,
    resolved: item.resolved ?? false,
    resolving: false
  }));
  scheduleSuggestionToastCollapse();
}

export function reopenSuggestionToast(): void {
  copyBackfillState.suggestionToastVisible = true;
  copyBackfillState.suggestionToastCollapsed = false;
  scheduleSuggestionToastCollapse();
}

export function closeSuggestionToast(): void {
  copyBackfillState.suggestionToastVisible = false;
  copyBackfillState.suggestionToastCollapsed = true;
  clearSuggestionToastTimer();
}

export async function resolveToastSuggestion(action: 'CONFIRM' | 'REJECT', suggestion?: ProfileSuggestion): Promise<void> {
  const targets = suggestion ? [suggestion] : copyBackfillState.suggestionToastSuggestions.filter((item) => !item.resolved);
  if (!copyBackfillState.suggestionToastPhone || targets.length === 0) {
    return;
  }
  clearSuggestionToastTimer();
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
    } else {
      scheduleSuggestionToastCollapse();
    }
  } catch {
    targets.forEach((item) => {
      item.resolving = false;
    });
    copyBackfillState.toast = '操作失败，请重试';
    scheduleSuggestionToastCollapse();
  }
}

export function cleanupCopyBackfillStore(): void {
  clearSuggestionToastTimer();
  abortPendingSendConfirm();
}

async function writeClipboardText(text: string): Promise<boolean> {
  const electronResult = await window.desktopBridge.writeClipboardText(text);
  if (electronResult.success) {
    return true;
  }
  if (navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(text);
      return true;
    } catch {
      return fallbackCopyText(text);
    }
  }
  return fallbackCopyText(text);
}

function fallbackCopyText(text: string): boolean {
  const textarea = document.createElement('textarea');
  textarea.value = text;
  textarea.setAttribute('readonly', 'true');
  textarea.style.position = 'fixed';
  textarea.style.opacity = '0';
  document.body.appendChild(textarea);
  textarea.select();
  try {
    return document.execCommand('copy');
  } finally {
    document.body.removeChild(textarea);
  }
}

async function sendConfirm(payload: ReplySelectedPayload, controller: AbortController): Promise<void> {
  try {
    await postJson('/api/v1/chat/send-confirm', {
      phone: payload.phone,
      conversationSummary: '',
      isNewCustomer: false,
      sentText: payload.text,
      selectedDirection: payload.isFallback ? 'SYSTEM_FALLBACK' : payload.direction
    }, undefined, controller.signal);
  } catch {
    // send-confirm is intentionally silent: the user already has the text in the clipboard.
  }
}

function abortPendingSendConfirm(): void {
  if (pendingSendConfirm) {
    pendingSendConfirm.abort();
    pendingSendConfirm = null;
  }
}

function scheduleSuggestionToastCollapse(): void {
  clearSuggestionToastTimer();
  suggestionToastTimer = window.setTimeout(() => {
    if (copyBackfillState.suggestionToastSuggestions.every((item) => item.resolved)) {
      copyBackfillState.suggestionToastVisible = false;
      copyBackfillState.suggestionToastCollapsed = false;
      return;
    }
    copyBackfillState.suggestionToastVisible = false;
    copyBackfillState.suggestionToastCollapsed = true;
  }, SUGGESTION_TOAST_AUTO_COLLAPSE_MS);
}

function clearSuggestionToastTimer(): void {
  if (suggestionToastTimer) {
    window.clearTimeout(suggestionToastTimer);
    suggestionToastTimer = null;
  }
}
