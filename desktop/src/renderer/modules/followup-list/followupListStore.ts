import { computed, reactive } from 'vue';
import { getJson } from '../../shared/apiClient';
import { loadDesktopConfig } from '../../shared/config';
import { eventBus } from '../../shared/eventBus';
import type { FollowupItem, FollowupReminderPayload, FollowupTab, FollowupTodayResponse, NewLeadAlertPayload, ReminderType } from './types';

const TABS: FollowupTab[] = ['OVERDUE', 'DUE_TODAY', 'APPOINTMENT', 'NEW_LEAD'];
const FOLLOWUP_TIMEOUT_MS = 10000;

export const followupListState = reactive({
  loading: false,
  loaded: false,
  activeTab: 'OVERDUE' as FollowupTab,
  keeperId: '',
  groups: {
    OVERDUE: [] as FollowupItem[],
    DUE_TODAY: [] as FollowupItem[],
    APPOINTMENT: [] as FollowupItem[],
    NEW_LEAD: [] as FollowupItem[]
  },
  selectedPhones: new Set<string>(),
  newReminderCount: 0,
  newReminderTab: 'OVERDUE' as FollowupTab,
  error: '',
  stale: false
});

export const activeFollowupItems = computed(() => followupListState.groups[followupListState.activeTab]);
export const selectedFollowupItems = computed(() =>
  TABS.flatMap((tab) => followupListState.groups[tab]).filter((item) => followupListState.selectedPhones.has(item.phoneFull ?? item.phone))
);

export async function loadTodayFollowups(): Promise<void> {
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
    followupListState.loaded = true;
    followupListState.stale = false;
  } catch {
    followupListState.error = '加载失败，请检查网络后重试';
    followupListState.stale = followupListState.loaded;
  } finally {
    followupListState.loading = false;
  }
}

export function setActiveFollowupTab(tab: FollowupTab): void {
  followupListState.activeTab = tab;
}

export function handleFollowupReminder(payload: FollowupReminderPayload): void {
  const primaryReminder = choosePrimaryReminder(payload);
  if (!primaryReminder) {
    return;
  }
  const tab = normalizeTab(primaryReminder.reminderType) ?? 'OVERDUE';
  const existing = findItem(payload.phone);
  const flashUntil = Date.now() + loadDesktopConfig().newReminderFlashMs;
  const nextItem: FollowupItem = {
    ...(existing?.item ?? {}),
    phone: payload.phone,
    nickname: existing?.item.nickname ?? `客户 ${payload.phone.slice(-4)}`,
    reminderType: tab,
    overdueHours: primaryReminder.overdueHours ?? existing?.item.overdueHours ?? null,
    alertLevel: highestAlertLevel(payload),
    tagSuggestion: primaryReminder.tagSuggestion ?? existing?.item.tagSuggestion ?? null,
    flashUntil
  };
  if (existing) {
    followupListState.groups[existing.tab] = followupListState.groups[existing.tab].filter((item) => item.phone !== payload.phone);
  }
  followupListState.groups[tab].unshift(nextItem);
  followupListState.newReminderCount += 1;
  followupListState.newReminderTab = tab;
  scheduleFlashCleanup(tab, nextItem.phone, flashUntil);
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
    flashUntil
  });
  if (followupListState.activeTab === 'NEW_LEAD') {
    scheduleFlashCleanup('NEW_LEAD', phone, flashUntil);
  }
}

export function openFollowupCustomer(item: FollowupItem): void {
  eventBus.emit('customer:selected', {
    phone: item.phoneFull ?? item.phone,
    scene: 'ACTIVE_REPLY',
    leadType: item.leadType ?? '',
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
  followupListState.activeTab = followupListState.newReminderTab;
  followupListState.newReminderCount = 0;
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
