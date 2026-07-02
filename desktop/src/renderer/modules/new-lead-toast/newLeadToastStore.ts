import { reactive } from 'vue';
import { loadDesktopConfig } from '../../shared/config';
import { eventBus } from '../../shared/eventBus';
import type { NewLeadAlertPayload, NewLeadToastItem } from './types';

export const newLeadToastState = reactive({
  visibleQueue: [] as NewLeadToastItem[],
  pendingQueue: [] as NewLeadToastItem[],
  toast: ''
});

export function enqueueNewLeadToast(payload: NewLeadAlertPayload): void {
  if (payload.isReconnectBatch || !isValidPayload(payload)) {
    return;
  }
  const item: NewLeadToastItem = {
    ...payload,
    id: `${payload.phoneFull ?? payload.phone}-${Date.now()}-${Math.random().toString(16).slice(2)}`
  };
  eventBus.emit('toast:show', { type: 'new-lead', payload });
  const config = loadDesktopConfig();
  if (newLeadToastState.visibleQueue.length < config.toastMaxCount) {
    showToast(item);
  } else {
    newLeadToastState.pendingQueue.push(item);
  }
}

export async function copyNewLeadPhone(item: NewLeadToastItem): Promise<void> {
  const phone = normalizePhone(item.phoneFull ?? '');
  if (!phone) {
    newLeadToastState.toast = '请在推广表查看完整手机号';
    return;
  }
  try {
    const result = await window.desktopBridge.writeClipboardText(phone);
    if (!result.success) {
      throw new Error(result.error ?? 'clipboard failed');
    }
    newLeadToastState.toast = '已复制，请在微信搜索添加';
  } catch {
    newLeadToastState.toast = '复制失败，请手动记录手机号';
  }
}

export function generateOpening(item: NewLeadToastItem): void {
  removeToast(item.id);
  eventBus.emit('customer:selected', {
    phone: item.phoneFull ?? item.phone,
    scene: 'OPENING',
    leadType: item.leadType ?? 'PENDING',
    sourceFrom: 'NEW_LEAD',
    wsPayload: item
  });
}

export function removeToast(id: string): void {
  const item = newLeadToastState.visibleQueue.find((candidate) => candidate.id === id);
  if (item?.timerId) {
    window.clearTimeout(item.timerId);
  }
  newLeadToastState.visibleQueue = newLeadToastState.visibleQueue.filter((candidate) => candidate.id !== id);
  promotePendingToast();
}

export function switchToNewLeadTab(): void {
  eventBus.emit('followup:switch-tab', { tab: 'NEW_LEAD' });
  newLeadToastState.pendingQueue = [];
}

export function cleanupNewLeadToastStore(): void {
  newLeadToastState.visibleQueue.forEach((item) => {
    if (item.timerId) {
      window.clearTimeout(item.timerId);
    }
  });
  newLeadToastState.visibleQueue = [];
  newLeadToastState.pendingQueue = [];
}

function showToast(item: NewLeadToastItem): void {
  const dismissMs = loadDesktopConfig().toastNewLeadDismissS * 1000;
  item.timerId = window.setTimeout(() => removeToast(item.id), dismissMs);
  newLeadToastState.visibleQueue.push(item);
}

function promotePendingToast(): void {
  const config = loadDesktopConfig();
  while (newLeadToastState.visibleQueue.length < config.toastMaxCount && newLeadToastState.pendingQueue.length > 0) {
    const next = newLeadToastState.pendingQueue.shift();
    if (next) {
      showToast(next);
    }
  }
}

function isValidPayload(payload: NewLeadAlertPayload): boolean {
  return Boolean(payload.phone && (payload.assignedKeeper || payload.nickname || payload.phoneFull));
}

function normalizePhone(phone: string): string {
  return phone.replace(/[\s-]/g, '');
}
