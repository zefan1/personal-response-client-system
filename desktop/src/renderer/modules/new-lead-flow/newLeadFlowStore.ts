import { reactive } from 'vue';
import { getJson, postJson } from '../../shared/apiClient';
import { writeClipboardText } from '../../shared/desktopBridge';
import { eventBus } from '../../shared/eventBus';
import type { LeadContactItem, FriendRequestTemplate } from './types';

type ProfileResponse = {
  customer?: { version?: number | null; nickname?: string | null };
  phoneFull?: string | null;
};

type CustomerProfileSnapshot = {
  version: number;
  nickname: string;
};

const TOAST_DISMISS_MS = 4000;
let toastTimer: number | null = null;

export const newLeadFlowState = reactive({
  open: false,
  item: null as LeadContactItem | null,
  nicknameDraft: '',
  templates: [] as FriendRequestTemplate[],
  selectedTemplateId: '',
  copiedContact: false,
  loading: false,
  saving: false,
  error: '',
  toast: ''
});

export function requestLeadContact(item: LeadContactItem): void {
  eventBus.emit('new-lead:copy-contact', item);
}

export async function openLeadContact(item: LeadContactItem): Promise<void> {
  const value = contactValue(item);
  if (!value) {
    setToast('没有可复制的手机号或微信号');
    return;
  }
  const copied = await writeClipboardText(value);
  if (!copied.success) {
    setToast('复制失败，请重试');
    return;
  }
  newLeadFlowState.item = item;
  newLeadFlowState.nicknameDraft = item.nickname?.trim() ?? '';
  newLeadFlowState.copiedContact = true;
  newLeadFlowState.open = true;
  newLeadFlowState.error = '';
  setToast(`${item.contactType === 'WECHAT' ? '微信号' : '手机号'}已复制`);
  await loadFriendRequestTemplates();
}

export function closeLeadContact(): void {
  if (newLeadFlowState.saving) return;
  newLeadFlowState.open = false;
  newLeadFlowState.item = null;
  newLeadFlowState.error = '';
}

export async function loadFriendRequestTemplates(): Promise<void> {
  newLeadFlowState.loading = true;
  try {
    const response = await getJson<{ templates?: FriendRequestTemplate[] }>('/api/v1/followups/friend-request-templates', 8000);
    const templates = response.success ? response.data?.templates ?? [] : [];
    newLeadFlowState.templates = templates.filter((item) => item.enabled && item.text.trim());
    newLeadFlowState.selectedTemplateId = newLeadFlowState.templates[0]?.id ?? '';
  } catch {
    newLeadFlowState.templates = [];
    newLeadFlowState.selectedTemplateId = '';
  } finally {
    newLeadFlowState.loading = false;
  }
}

export async function confirmLeadContact(): Promise<void> {
  const item = newLeadFlowState.item;
  if (!item) return;
  const nickname = newLeadFlowState.nicknameDraft.trim();
  if (!nickname) {
    newLeadFlowState.error = '请填写添加好友后看到的微信昵称';
    return;
  }
  newLeadFlowState.saving = true;
  newLeadFlowState.error = '';
  try {
    const profile = await resolveCustomerProfile(item);
    if (!profile) {
      throw new Error('客户档案版本缺失，请刷新新客资列表后重试');
    }
    const contact = contactValue(item);
    let response = await submitLeadProcessing(
      contact,
      profile.version,
      nickname
    );
    if (!response.success && response.errorCode === '50-10002') {
      const refreshed = await resolveCustomerProfile(item, true);
      if (!refreshed) {
        throw new Error('客户档案已被更新，暂时无法读取最新版本，请稍后重试');
      }
      item.customerVersion = refreshed.version;
      if (refreshed.nickname && refreshed.nickname !== nickname) {
        throw new Error('该客户的微信昵称已被其他人填写，请确认后再提交');
      }
      response = await submitLeadProcessing(
        contact,
        refreshed.version,
        nickname
      );
    }
    if (!response.success || !response.data) {
      throw new Error(response.message || '保存失败，请刷新后重试');
    }
    const template = newLeadFlowState.templates.find((candidate) => candidate.id === newLeadFlowState.selectedTemplateId)
      ?? newLeadFlowState.templates[0];
    if (template) {
      const copied = await writeClipboardText(template.text);
      if (!copied.success) {
        setToast('昵称已保存，但话术复制失败，请重新点击复制话术');
      } else {
        setToast('昵称已保存，添加好友话术已复制');
      }
    } else {
      setToast('昵称已保存');
    }
    eventBus.emit('new-lead:processed', { phone: item.phoneFull ?? item.phone, nickname });
    newLeadFlowState.open = false;
  } catch (error) {
    newLeadFlowState.error = error instanceof Error ? error.message : '保存失败，请重试';
  } finally {
    newLeadFlowState.saving = false;
  }
}

async function submitLeadProcessing(contact: string, version: number, nickname: string) {
  return postJson<{ version: number }>(
      `/api/v1/customers/${encodeURIComponent(contact)}/lead-processing`,
      { version, nickname, operator: 'desktop' },
      10000
    );
}

export async function copySelectedFriendRequestTemplate(): Promise<void> {
  const template = newLeadFlowState.templates.find((candidate) => candidate.id === newLeadFlowState.selectedTemplateId)
    ?? newLeadFlowState.templates[0];
  if (!template) {
    newLeadFlowState.error = '当前没有可用的添加好友话术';
    return;
  }
  const result = await writeClipboardText(template.text);
  setToast(result.success ? '添加好友话术已复制' : '话术复制失败，请重试');
}

function setToast(message: string): void {
  if (toastTimer !== null) window.clearTimeout(toastTimer);
  newLeadFlowState.toast = message;
  toastTimer = window.setTimeout(() => {
    newLeadFlowState.toast = '';
    toastTimer = null;
  }, TOAST_DISMISS_MS);
}

function contactValue(item: LeadContactItem): string {
  return String(item.contactValue || item.phoneFull || item.phone || '').trim();
}

async function resolveCustomerProfile(item: LeadContactItem, forceRefresh = false): Promise<CustomerProfileSnapshot | null> {
  if (!forceRefresh && typeof item.customerVersion === 'number') {
    return { version: item.customerVersion, nickname: item.nickname?.trim() ?? '' };
  }
  const contact = contactValue(item);
  if (!contact) return null;
  const response = await getJson<ProfileResponse>(`/api/v1/customers/${encodeURIComponent(contact)}`, 8000);
  if (!response.success || !response.data?.customer) return null;
  const version = response.data.customer.version;
  if (typeof version !== 'number') return null;
  return {
    version,
    nickname: response.data.customer.nickname?.trim() ?? ''
  };
}
