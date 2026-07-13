import { computed, reactive } from 'vue';
import { getJson } from '../../shared/apiClient';
import { loadDesktopConfig } from '../../shared/config';
import { eventBus } from '../../shared/eventBus';
import { newLeadToastState } from '../new-lead-toast/newLeadToastStore';
import type {
  FollowupItem,
  FollowupReminderPayload,
  NewLeadAlertPayload,
  WorkbenchFollowupItem,
  WorkbenchMetrics,
  WorkbenchNotice,
  WorkbenchNoticePayload
} from './types';

const FOLLOWUP_TIMEOUT_MS = 5000;
const EMPTY_METRICS: WorkbenchMetrics = {
  pendingFollowup: { total: 0, tuanGou: 0, xianSuo: 0 },
  appointment: { total: 0, tuanGou: 0, xianSuo: 0 },
  newLead: { total: 0, tuanGou: 0, xianSuo: 0 }
};

export const workbenchState = reactive({
  loading: false,
  loaded: false,
  followups: [] as FollowupItem[],
  lastFetchAt: 0,
  followupDataDirty: false,
  dismissedNoticeIds: new Set<string>(),
  notices: [] as WorkbenchNotice[],
  fetchFailedCount: 0,
  stale: false,
  retryOnly: false,
  toast: ''
});

export const workbenchMetrics = computed<WorkbenchMetrics>(() => {
  const next = cloneMetrics();
  workbenchState.followups.forEach((item) => {
    if (item.reminderType === 'OVERDUE' || item.reminderType === 'DUE_TODAY') {
      incrementMetric(next.pendingFollowup, item.leadType);
    } else if (item.reminderType === 'APPOINTMENT') {
      incrementMetric(next.appointment, item.leadType);
    } else if (item.reminderType === 'NEW_LEAD') {
      incrementMetric(next.newLead, item.leadType);
    }
  });
  return next;
});

export const urgentFollowups = computed<WorkbenchFollowupItem[]>(() => {
  const limit = loadDesktopConfig().workbenchFollowupListLimit;
  return workbenchState.followups
    .filter((item): item is WorkbenchFollowupItem => item.reminderType === 'OVERDUE' || item.reminderType === 'DUE_TODAY')
    .sort(compareFollowupUrgency)
    .slice(0, limit);
});

export const recentNewLeads = computed<NewLeadAlertPayload[]>(() => {
  const limit = loadDesktopConfig().workbenchNewLeadListLimit;
  const shared = [...newLeadToastState.visibleQueue, ...newLeadToastState.pendingQueue]
    .sort((left, right) => (right.arrivedAt ?? '').localeCompare(left.arrivedAt ?? ''))
    .slice(0, limit);
  if (shared.length > 0) {
    return shared;
  }
  return workbenchState.followups
    .filter((item) => item.reminderType === 'NEW_LEAD')
    .sort((left, right) => (right.arrivedAt ?? '').localeCompare(left.arrivedAt ?? ''))
    .slice(0, limit)
    .map((item) => ({
      phone: item.phone,
      phoneFull: item.phoneFull,
      nickname: item.nickname ?? undefined,
      leadType: item.leadType ?? undefined,
      priority: item.priority ?? undefined,
      sourceTable: item.sourceTable ?? undefined,
      assignedKeeper: item.assignedKeeper ?? undefined,
      arrivedAt: item.arrivedAt ?? undefined
    }));
});

export const visibleWorkbenchNotices = computed(() => {
  const max = loadDesktopConfig().workbenchMaxNotices;
  const now = Date.now();
  return workbenchState.notices
    .filter((notice) => !workbenchState.dismissedNoticeIds.has(notice.noticeId))
    .filter((notice) => Date.parse(notice.expireAt) > now)
    .sort((left, right) => right.createdAt.localeCompare(left.createdAt))
    .slice(0, max);
});

export async function loadWorkbenchFollowups(manual = false): Promise<void> {
  if (workbenchState.loading) {
    return;
  }
  workbenchState.loading = true;
  workbenchState.toast = '';
  try {
    const response = await getJson<{ items?: FollowupItem[] }>('/api/v1/followups/today', FOLLOWUP_TIMEOUT_MS);
    if (!response.success || !response.data) {
      throw new Error(response.message ?? 'workbench followup fetch failed');
    }
    workbenchState.followups = normalizeFollowups(response.data.items ?? []);
    workbenchState.lastFetchAt = Date.now();
    workbenchState.followupDataDirty = false;
    workbenchState.fetchFailedCount = 0;
    workbenchState.retryOnly = false;
    workbenchState.stale = false;
    workbenchState.loaded = true;
  } catch {
    workbenchState.fetchFailedCount += 1;
    workbenchState.stale = workbenchState.loaded;
    workbenchState.retryOnly = workbenchState.fetchFailedCount >= 3;
    workbenchState.toast = manual || !workbenchState.loaded
      ? '数据加载失败，请检查网络后重试'
      : '当前数据可能不是最新，可手动重试';
  } finally {
    workbenchState.loading = false;
  }
}

export async function loadWorkbenchNotices(): Promise<void> {
  try {
    const response = await getJson<WorkbenchNoticePayload[]>('/api/v1/notices/active', FOLLOWUP_TIMEOUT_MS);
    if (!response.success || !response.data) {
      return;
    }
    workbenchState.notices = response.data
      .map(normalizeNotice)
      .filter((notice): notice is WorkbenchNotice => Boolean(notice));
  } catch {
    // 公告失败不阻断工作台主流程，后台健康页会记录服务端异常。
  }
}

export function refreshWorkbenchIfNeeded(): void {
  const intervalMs = loadDesktopConfig().workbenchRefreshIntervalS * 1000;
  if (workbenchState.retryOnly) {
    return;
  }
  if (!workbenchState.loaded || workbenchState.followupDataDirty || Date.now() - workbenchState.lastFetchAt > intervalMs) {
    void loadWorkbenchFollowups();
  }
}

export function handleWorkbenchFollowupReminder(payload: FollowupReminderPayload): void {
  const seen = new Set<string>();
  payload.reminders.forEach((reminder) => {
    const phone = payload.phoneFull ?? payload.phone;
    const key = `${phone}:${reminder.reminderType}`;
    if (seen.has(key)) {
      return;
    }
    seen.add(key);
    const existing = findFollowup(phone, reminder.reminderType);
    if (existing) {
      existing.overdueHours = reminder.overdueHours ?? existing.overdueHours;
      existing.alertLevel = reminder.alertLevel ?? existing.alertLevel;
      return;
    }
    workbenchState.followups.unshift({
      phone: payload.phone,
      phoneFull: payload.phoneFull,
      nickname: `客户 ${payload.phone.slice(-4)}`,
      leadType: 'PENDING',
      reminderType: reminder.reminderType,
      overdueHours: reminder.overdueHours ?? null,
      alertLevel: reminder.alertLevel ?? null,
      tagSuggestion: reminder.tagSuggestion ?? null,
      flashUntil: Date.now() + loadDesktopConfig().newReminderFlashMs
    });
  });
}

export function handleWorkbenchNewLead(payload: NewLeadAlertPayload): void {
  const phone = payload.phoneFull ?? payload.phone;
  if (workbenchState.followups.some((item) => (item.phoneFull ?? item.phone) === phone && item.reminderType === 'NEW_LEAD')) {
    return;
  }
  workbenchState.followups.unshift({
    phone,
    phoneFull: payload.phoneFull,
    nickname: payload.nickname,
    leadType: payload.leadType ?? 'PENDING',
    reminderType: 'NEW_LEAD',
    sourceTable: payload.sourceTable,
    assignedKeeper: payload.assignedKeeper,
    priority: payload.priority,
    arrivedAt: payload.arrivedAt
  });
}

export function handleWorkbenchNotice(payload: WorkbenchNoticePayload): void {
  const notice = normalizeNotice(payload);
  if (!notice || Date.parse(notice.expireAt) <= Date.now()) {
    return;
  }
  workbenchState.notices = workbenchState.notices.filter((item) => item.noticeId !== notice.noticeId);
  workbenchState.notices.unshift(notice);
}

export function dismissWorkbenchNotice(noticeId: string): void {
  workbenchState.dismissedNoticeIds.add(noticeId);
}

export function noticeLevelLabel(level: string): string {
  if (level === 'ERROR') return '故障';
  if (level === 'WARN') return '提醒';
  return '公告';
}

export function markWorkbenchDirty(): void {
  workbenchState.followupDataDirty = true;
}

export function openWorkbenchCustomer(phone: string, leadType?: string | null): void {
  eventBus.emit('customer:selected', {
    phone,
    scene: 'ACTIVE_REPLY',
    leadType: leadType ?? '',
    sourceFrom: 'DASHBOARD'
  });
}

export function openAllFollowups(): void {
  const tab = urgentFollowups.value.some((item) => item.reminderType === 'OVERDUE') ? 'OVERDUE' : 'DUE_TODAY';
  eventBus.emit('followup:switch-tab', { tab });
}

export function openAllNewLeads(): void {
  eventBus.emit('followup:switch-tab', { tab: 'NEW_LEAD' });
}

export function startWorkbenchCapture(): void {
  eventBus.emit('workbench:capture-chat', {});
}

export function openWorkbenchQuickSearch(): void {
  eventBus.emit('quick-search:show', {});
}

export function startWorkbenchBatchTemplate(): void {
  openAllFollowups();
  workbenchState.toast = '请在待办队列中选择客户后点击批量发模板';
}

function normalizeFollowups(items: FollowupItem[]): FollowupItem[] {
  return items.map((item) => ({
    ...item,
    reminderType: item.reminderType ?? 'DUE_TODAY',
    leadType: item.leadType ?? 'PENDING'
  }));
}

function normalizeNotice(payload: WorkbenchNoticePayload): WorkbenchNotice | null {
  const createdAt = payload.createdAt ?? new Date().toISOString();
  const content = (payload.content ?? payload.title ?? '').trim();
  if (!content) {
    return null;
  }
  const fallbackExpire = new Date(Date.parse(createdAt) + 24 * 60 * 60 * 1000).toISOString();
  const level = payload.level === 'ERROR' || payload.level === 'WARN' || payload.level === 'INFO' ? payload.level : 'INFO';
  return {
    noticeId: payload.noticeId ?? `${content}:${createdAt}`,
    title: payload.title ?? '系统公告',
    content,
    level,
    createdAt,
    expireAt: payload.expireAt ?? fallbackExpire
  };
}

function cloneMetrics(): WorkbenchMetrics {
  return {
    pendingFollowup: { ...EMPTY_METRICS.pendingFollowup },
    appointment: { ...EMPTY_METRICS.appointment },
    newLead: { ...EMPTY_METRICS.newLead }
  };
}

function incrementMetric(metric: { total: number; tuanGou: number; xianSuo: number }, leadType?: string | null): void {
  metric.total += 1;
  if (leadType === 'TUAN_GOU') {
    metric.tuanGou += 1;
  } else if (leadType === 'XIAN_SUO') {
    metric.xianSuo += 1;
  }
}

function compareFollowupUrgency(left: WorkbenchFollowupItem, right: WorkbenchFollowupItem): number {
  if (left.reminderType !== right.reminderType) {
    return left.reminderType === 'OVERDUE' ? -1 : 1;
  }
  const leadTypeDiff = leadTypeRank(left.leadType) - leadTypeRank(right.leadType);
  if (left.reminderType === 'OVERDUE') {
    const overdueDiff = (right.overdueHours ?? -1) - (left.overdueHours ?? -1);
    return overdueDiff || leadTypeDiff || (left.nickname ?? '').localeCompare(right.nickname ?? '');
  }
  const timeDiff = (left.nextFollowupAt ?? '').localeCompare(right.nextFollowupAt ?? '');
  return timeDiff || leadTypeDiff || (left.nickname ?? '').localeCompare(right.nickname ?? '');
}

function leadTypeRank(value?: string | null): number {
  if (value === 'TUAN_GOU') return 0;
  if (value === 'XIAN_SUO') return 1;
  return 2;
}

function findFollowup(phone: string, reminderType: string): FollowupItem | undefined {
  return workbenchState.followups.find((item) => (item.phoneFull ?? item.phone) === phone && item.reminderType === reminderType);
}
