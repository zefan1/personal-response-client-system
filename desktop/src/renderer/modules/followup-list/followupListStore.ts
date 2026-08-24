import { computed, reactive } from 'vue';
import { getJson, postJson } from '../../shared/apiClient';
import { loadDesktopConfig } from '../../shared/config';
import { eventBus } from '../../shared/eventBus';
import type { FollowupItem, FollowupReminderPayload, FollowupTab, FollowupTodayResponse, NewLeadAlertPayload, ReminderType } from './types';

const TABS: FollowupTab[] = ['DUE_TODAY', 'OVERDUE', 'APPOINTMENT', 'NEW_LEAD'];
const FOLLOWUP_TIMEOUT_MS = 10000;

export const followupListState = reactive({
  loading: false,
  loaded: false,
  activeTab: 'DUE_TODAY' as FollowupTab,
  keeperId: '',
  groups: {
    OVERDUE: [] as FollowupItem[],
    DUE_TODAY: [] as FollowupItem[],
    APPOINTMENT: [] as FollowupItem[],
    NEW_LEAD: [] as FollowupItem[]
  },
  selectedPhones: new Set<string>(),
  newReminderCount: 0,
  pendingNewLeadCount: 0,
  retainedNewLeadCount: 0,
  newReminderTab: 'DUE_TODAY' as FollowupTab,
  lastLoadedAt: 0,
  error: '',
  stale: false
});

export const leadValidityUpdatingPhones = reactive(new Set<string>());

export const activeFollowupItems = computed(() => followupListState.groups[followupListState.activeTab]);
export const selectedFollowupItems = computed(() =>
  activeFollowupItems.value.filter((item) => followupListState.selectedPhones.has(item.phoneFull ?? item.phone))
);

export async function loadTodayFollowups(): Promise<void> {
  if (followupListState.loading) return;
  followupListState.loading = true;
  followupListState.error = '';
  try {
    const response = await getJson<FollowupTodayResponse>('/api/v1/followups/today', FOLLOWUP_TIMEOUT_MS);
    if (!response.success || !response.data) {
      followupListState.error = '加载失败，请检查网络后重试';
      followupListState.stale = followupListState.loaded;
      return;
    }
    followupListState.keeperId = response.data.keeperId ?? '';
    for (const tab of TABS) {
      followupListState.groups[tab] = [];
    }
    response.data.items.forEach((item) => {
      const tab = normalizeTab(item.reminderType);
      if (tab) {
        followupListState.groups[tab].push({ ...item, selected: false });
      }
    });
    const newLeadItems = followupListState.groups.NEW_LEAD;
    followupListState.pendingNewLeadCount = response.data.pendingNewLeadCount
      ?? newLeadItems.filter((item) => !item.leadProcessed).length;
    followupListState.retainedNewLeadCount = response.data.retainedNewLeadCount
      ?? newLeadItems.filter((item) => item.leadProcessed).length;
    followupListState.newReminderCount = followupListState.pendingNewLeadCount;
    followupListState.newReminderTab = 'NEW_LEAD';
    followupListState.loaded = true;
    followupListState.lastLoadedAt = Date.now();
    followupListState.stale = false;
  } catch {
    followupListState.error = '加载失败，请检查网络后重试';
    followupListState.stale = followupListState.loaded;
  } finally {
    followupListState.loading = false;
  }
}

export function setActiveFollowupTab(tab: FollowupTab): void {
  if (followupListState.activeTab === tab) {
    return;
  }
  followupListState.selectedPhones.clear();
  followupListState.activeTab = tab;
}

export function handleFollowupReminder(payload: FollowupReminderPayload): void {
  const primaryReminder = choosePrimaryReminder(payload);
  if (!primaryReminder) {
    return;
  }
  const tab = normalizeTab(primaryReminder.reminderType) ?? 'OVERDUE';
  const phone = payload.phoneFull ?? payload.phone;
  const existing = findItem(phone);
  const flashUntil = Date.now() + loadDesktopConfig().newReminderFlashMs;
  const nextItem: FollowupItem = {
    ...(existing?.item ?? {}),
    phone: payload.phone,
    phoneFull: payload.phoneFull,
    nickname: existing?.item.nickname ?? `客户 ${payload.phone.slice(-4)}`,
    reminderType: tab,
    overdueHours: primaryReminder.overdueHours ?? existing?.item.overdueHours ?? null,
    alertLevel: highestAlertLevel(payload),
    tagSuggestion: primaryReminder.tagSuggestion ?? existing?.item.tagSuggestion ?? null,
    flashUntil
  };
  if (existing) {
    followupListState.groups[existing.tab] = followupListState.groups[existing.tab].filter((item) => (item.phoneFull ?? item.phone) !== phone);
  }
  followupListState.groups[tab].unshift(nextItem);
  followupListState.newReminderCount += 1;
  followupListState.newReminderTab = tab;
  scheduleFlashCleanup(tab, phone, flashUntil);
}

export function handleNewLeadAlert(payload: NewLeadAlertPayload): void {
  const flashUntil = Date.now() + loadDesktopConfig().newReminderFlashMs;
  const phone = payload.phoneFull ?? payload.phone;
  upsertInTab('NEW_LEAD', {
    phone,
    phoneFull: payload.phoneFull,
    nickname: payload.nickname ?? `客户 ${payload.phone.slice(-4)}`,
    leadType: payload.leadType ?? 'PENDING',
    reminderType: 'NEW_LEAD',
    alertLevel: payload.priority === 'HIGH' ? 'HIGH' : 'NORMAL',
    sourceTable: payload.sourceTable,
    assignedKeeper: payload.assignedKeeper,
    arrivedAt: payload.arrivedAt,
    contactValue: payload.contactValue,
    contactType: payload.contactType,
    customerVersion: payload.customerVersion,
    leadProcessed: payload.leadProcessed,
    leadInvalid: payload.leadInvalid,
    leadRetainedUntil: payload.leadRetainedUntil,
    flashUntil
  });
  followupListState.newReminderCount += 1;
  followupListState.newReminderTab = 'NEW_LEAD';
  scheduleFlashCleanup('NEW_LEAD', phone, flashUntil);
}

export function openFollowupCustomer(item: FollowupItem): void {
  eventBus.emit('customer:selected', {
    phone: item.phoneFull ?? item.phone,
    scene: 'ACTIVE_REPLY',
    leadType: item.leadType ?? '',
    reminderType: item.reminderType,
    sourceFrom: 'FOLLOWUP_LIST'
  });
}

export function toggleFollowupSelection(item: FollowupItem): void {
  const phone = item.phoneFull ?? item.phone;
  if (followupListState.selectedPhones.has(phone)) {
    followupListState.selectedPhones.delete(phone);
  } else {
    followupListState.selectedPhones.add(phone);
  }
}

export function completeFollowup(phone: string, reminderType?: FollowupTab | null): void {
  if (reminderType !== 'DUE_TODAY' && reminderType !== 'OVERDUE') {
    return;
  }
  followupListState.groups[reminderType] = followupListState.groups[reminderType]
    .filter((item) => (item.phoneFull ?? item.phone) !== phone);
  followupListState.selectedPhones.delete(phone);
}

export function selectAllActiveFollowups(): void {
  activeFollowupItems.value.forEach((item) => followupListState.selectedPhones.add(item.phoneFull ?? item.phone));
}

export function invertActiveFollowupSelection(): void {
  activeFollowupItems.value.forEach((item) => toggleFollowupSelection(item));
}

export function startBatchTemplate(): void {
  const phones = selectedFollowupItems.value.map((item) => item.phoneFull ?? item.phone);
  if (phones.length === 0) {
    return;
  }
  eventBus.emit('batch:start', { phones, source: 'FOLLOWUP_LIST' });
}

export function openNewReminderBanner(): void {
  setActiveFollowupTab(followupListState.newReminderTab);
  followupListState.newReminderCount = 0;
}

export function markNewLeadProcessed(item: FollowupItem, nickname: string): void {
  const phone = item.phoneFull ?? item.phone;
  const index = followupListState.groups.NEW_LEAD.findIndex((candidate) => (candidate.phoneFull ?? candidate.phone) === phone);
  if (index < 0) return;
  const current = followupListState.groups.NEW_LEAD[index];
  followupListState.groups.NEW_LEAD.splice(index, 1, {
    ...current,
    nickname: nickname || current.nickname,
    leadProcessed: true,
    leadInvalid: false,
    leadRetainedUntil: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString()
  });
  followupListState.newReminderCount = Math.max(0, followupListState.newReminderCount - 1);
  followupListState.pendingNewLeadCount = Math.max(0, followupListState.pendingNewLeadCount - 1);
  followupListState.retainedNewLeadCount += 1;
}

export type LeadValidityTarget = {
  phone: string;
  phoneFull?: string | null;
  customerVersion?: number | null;
  leadInvalid?: boolean;
};

export function isLeadValidityUpdating(target: LeadValidityTarget): boolean {
  return leadValidityUpdatingPhones.has(target.phoneFull ?? target.phone);
}

export async function toggleLeadInvalid(target: LeadValidityTarget): Promise<void> {
  const phone = target.phoneFull ?? target.phone;
  const invalid = !Boolean(target.leadInvalid);
  const previousInvalid = Boolean(target.leadInvalid);
  if (!phone || leadValidityUpdatingPhones.has(phone)) return;
  if (typeof target.customerVersion !== 'number') {
    followupListState.error = '客户档案版本缺失，请刷新后重试';
    return;
  }
  leadValidityUpdatingPhones.add(phone);
  try {
    let response = await submitLeadValidity(phone, invalid, target.customerVersion);
    if (!response.success && response.errorCode === '50-10002') {
      await loadTodayFollowups();
      const refreshed = followupListState.groups.NEW_LEAD
        .find((item) => (item.phoneFull ?? item.phone) === phone);
      if (refreshed?.leadInvalid === invalid) {
        return;
      }
      if (refreshed && refreshed.leadInvalid === previousInvalid
          && typeof refreshed.customerVersion === 'number') {
        response = await submitLeadValidity(phone, invalid, refreshed.customerVersion);
      } else {
        followupListState.error = '客户档案已被其他操作更新，请确认当前状态后再处理';
        return;
      }
    }
    if (!response.success || !response.data) {
      followupListState.error = response.message || (invalid ? '标记无效失败，请刷新后重试' : '撤回无效失败，请刷新后重试');
      return;
    }
    applyLeadValidityChange({
      phone,
      invalid: response.data.invalid,
      processedAt: response.data.processedAt ?? null,
      processedBy: response.data.processedBy ?? null,
      retainedUntil: response.data.retainedUntil ?? null,
      version: response.data.version
    });
    eventBus.emit('new-lead:validity-changed', {
      phone,
      invalid: response.data.invalid,
      processedAt: response.data.processedAt ?? null,
      processedBy: response.data.processedBy ?? null,
      retainedUntil: response.data.retainedUntil ?? null,
      version: response.data.version
    });
  } catch {
    followupListState.error = invalid ? '标记无效失败，请检查网络后重试' : '撤回无效失败，请检查网络后重试';
  } finally {
    leadValidityUpdatingPhones.delete(phone);
  }
}

type LeadValidityResponse = {
  version: number;
  invalid: boolean;
  processedAt?: string | null;
  processedBy?: string | null;
  retainedUntil?: string | null;
};

function submitLeadValidity(phone: string, invalid: boolean, version: number) {
  return postJson<LeadValidityResponse>(
    `/api/v1/customers/${encodeURIComponent(phone)}/lead-validity`,
    { version, invalid, operator: 'desktop' },
    FOLLOWUP_TIMEOUT_MS
  );
}

export function applyLeadValidityChange(payload: {
  phone: string;
  invalid: boolean;
  processedAt?: string | null;
  processedBy?: string | null;
  retainedUntil?: string | null;
  version?: number | null;
}): void {
  const phone = payload.phone;
  const group = followupListState.groups.NEW_LEAD;
  const item = group.find((candidate) => (candidate.phoneFull ?? candidate.phone) === phone);
  if (!item) return;
  const wasProcessed = Boolean(item.leadProcessed);
  item.leadInvalid = payload.invalid;
  item.leadProcessed = true;
  item.leadRetainedUntil = payload.retainedUntil ?? null;
  if (typeof payload.version === 'number') item.customerVersion = payload.version;
  if (wasProcessed !== item.leadProcessed) {
    if (item.leadProcessed) {
      followupListState.pendingNewLeadCount = Math.max(0, followupListState.pendingNewLeadCount - 1);
      followupListState.retainedNewLeadCount += 1;
      followupListState.newReminderCount = Math.max(0, followupListState.newReminderCount - 1);
    } else {
      followupListState.pendingNewLeadCount += 1;
      followupListState.retainedNewLeadCount = Math.max(0, followupListState.retainedNewLeadCount - 1);
    }
  }
}

function choosePrimaryReminder(payload: FollowupReminderPayload): FollowupReminderPayload['reminders'][number] | null {
  return payload.reminders.find((item) => item.reminderType === 'OVERDUE')
    ?? payload.reminders.find((item) => item.reminderType === 'DUE_TODAY')
    ?? payload.reminders.find((item) => item.reminderType === 'APPOINTMENT')
    ?? payload.reminders.find((item) => item.reminderType === 'NEW_LEAD')
    ?? payload.reminders[0]
    ?? null;
}

function normalizeTab(type: ReminderType): FollowupTab | null {
  if (type === 'OVERDUE' || type === 'DUE_TODAY' || type === 'APPOINTMENT' || type === 'NEW_LEAD') {
    return type;
  }
  return null;
}

function highestAlertLevel(payload: FollowupReminderPayload): string {
  return payload.reminders.some((item) => item.alertLevel === 'HIGH') ? 'HIGH' : 'NORMAL';
}

function findItem(phone: string): { tab: FollowupTab; item: FollowupItem } | null {
  for (const tab of TABS) {
    const item = followupListState.groups[tab].find((candidate) => candidate.phone === phone || candidate.phoneFull === phone);
    if (item) {
      return { tab, item };
    }
  }
  return null;
}

function upsertInTab(tab: FollowupTab, item: FollowupItem): void {
  followupListState.groups[tab] = followupListState.groups[tab].filter((candidate) => (candidate.phoneFull ?? candidate.phone) !== (item.phoneFull ?? item.phone));
  followupListState.groups[tab].unshift(item);
}

function scheduleFlashCleanup(tab: FollowupTab, phone: string, flashUntil: number): void {
  window.setTimeout(() => {
    const item = followupListState.groups[tab].find((candidate) => (candidate.phoneFull ?? candidate.phone) === phone);
    if (item && item.flashUntil === flashUntil) {
      item.flashUntil = undefined;
    }
  }, loadDesktopConfig().newReminderFlashMs);
}
