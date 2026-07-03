import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { FollowupItem, FollowupReminderPayload, NewLeadAlertPayload, WorkbenchNoticePayload } from './types';

const getJsonMock = vi.fn();

vi.mock('../../shared/apiClient', () => ({
  getJson: getJsonMock
}));

type WorkbenchModule = typeof import('./workbenchStore');
type NewLeadToastModule = typeof import('../new-lead-toast/newLeadToastStore');
type EventBusModule = typeof import('../../shared/eventBus');

function installMemoryLocalStorage(): void {
  const store = new Map<string, string>();
  const storage = {
    getItem: vi.fn((key: string) => store.get(key) ?? null),
    setItem: vi.fn((key: string, value: string) => {
      store.set(key, String(value));
    }),
    removeItem: vi.fn((key: string) => {
      store.delete(key);
    }),
    clear: vi.fn(() => {
      store.clear();
    })
  };
  Object.defineProperty(globalThis, 'localStorage', {
    value: storage,
    configurable: true
  });
}

async function freshStore(): Promise<{
  workbench: WorkbenchModule;
  newLeadToast: NewLeadToastModule;
  eventBus: EventBusModule['eventBus'];
}> {
  vi.resetModules();
  localStorage.clear();
  localStorage.setItem('desktop_config', JSON.stringify({
    workbenchRefreshIntervalS: 30,
    workbenchFollowupListLimit: 3,
    workbenchNewLeadListLimit: 2,
    workbenchMaxNotices: 2,
    newReminderFlashMs: 2000,
    toastMaxCount: 2,
    toastNewLeadDismissS: 30
  }));
  getJsonMock.mockReset();
  const workbench = await import('./workbenchStore');
  const newLeadToast = await import('../new-lead-toast/newLeadToastStore');
  const { eventBus } = await import('../../shared/eventBus');
  return { workbench, newLeadToast, eventBus };
}

describe('workbenchStore', () => {
  beforeEach(() => {
    installMemoryLocalStorage();
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-07-03T12:00:00Z'));
  });

  afterEach(() => {
    vi.useRealTimers();
    localStorage.clear();
    getJsonMock.mockReset();
  });

  it('loads followups, normalizes missing fields, and computes metrics by reminder and lead type', async () => {
    const { workbench } = await freshStore();
    getJsonMock.mockResolvedValue({
      success: true,
      data: {
        items: [
          followup({ phone: '1001', reminderType: 'OVERDUE', leadType: 'TUAN_GOU' }),
          followup({ phone: '1002', reminderType: 'DUE_TODAY', leadType: 'XIAN_SUO' }),
          followup({ phone: '1003', reminderType: 'APPOINTMENT', leadType: 'TUAN_GOU' }),
          followup({ phone: '1004', reminderType: 'NEW_LEAD', leadType: 'XIAN_SUO' }),
          { phone: '1005' }
        ]
      }
    });

    await workbench.loadWorkbenchFollowups();

    expect(getJsonMock).toHaveBeenCalledWith('/api/v1/followups/today', 5000);
    expect(workbench.workbenchState.loaded).toBe(true);
    expect(workbench.workbenchState.fetchFailedCount).toBe(0);
    expect(workbench.workbenchState.followups.at(-1)).toMatchObject({ reminderType: 'DUE_TODAY', leadType: 'PENDING' });
    expect(workbench.workbenchMetrics.value).toEqual({
      pendingFollowup: { total: 3, tuanGou: 1, xianSuo: 1 },
      appointment: { total: 1, tuanGou: 1, xianSuo: 0 },
      newLead: { total: 1, tuanGou: 0, xianSuo: 1 }
    });
  });

  it('orders urgent followups by overdue severity, lead type, due time, and configured limit', async () => {
    const { workbench } = await freshStore();
    workbench.workbenchState.followups = [
      followup({ phone: '1', nickname: 'Later', reminderType: 'DUE_TODAY', leadType: 'XIAN_SUO', nextFollowupAt: '2026-07-03T18:00:00' }),
      followup({ phone: '2', nickname: 'Worst', reminderType: 'OVERDUE', leadType: 'XIAN_SUO', overdueHours: 9 }),
      followup({ phone: '3', nickname: 'Group', reminderType: 'OVERDUE', leadType: 'TUAN_GOU', overdueHours: 2 }),
      followup({ phone: '4', nickname: 'Soon', reminderType: 'DUE_TODAY', leadType: 'TUAN_GOU', nextFollowupAt: '2026-07-03T09:00:00' }),
      followup({ phone: '5', nickname: 'New', reminderType: 'NEW_LEAD', leadType: 'TUAN_GOU' })
    ];

    expect(workbench.urgentFollowups.value.map((item) => item.phone)).toEqual(['2', '3', '4']);
  });

  it('uses shared new-lead queues first and falls back to followup data', async () => {
    const { workbench, newLeadToast } = await freshStore();
    workbench.workbenchState.followups = [
      followup({ phone: 'fallback-old', reminderType: 'NEW_LEAD', arrivedAt: '2026-07-03T08:00:00Z' }),
      followup({ phone: 'fallback-new', phoneFull: '18800009999', reminderType: 'NEW_LEAD', arrivedAt: '2026-07-03T10:00:00Z' })
    ];

    expect(workbench.recentNewLeads.value.map((item) => item.phone)).toEqual(['fallback-new', 'fallback-old']);

    newLeadToast.newLeadToastState.visibleQueue = [
      newLead({ id: 'v1', phone: 'visible-old', arrivedAt: '2026-07-03T09:00:00Z' })
    ];
    newLeadToast.newLeadToastState.pendingQueue = [
      newLead({ id: 'p1', phone: 'pending-new', arrivedAt: '2026-07-03T11:00:00Z' }),
      newLead({ id: 'p2', phone: 'pending-limited-out', arrivedAt: '2026-07-03T07:00:00Z' })
    ];

    expect(workbench.recentNewLeads.value.map((item) => item.phone)).toEqual(['pending-new', 'visible-old']);
  });

  it('filters, sorts, limits, and dismisses workbench notices', async () => {
    const { workbench } = await freshStore();
    workbench.handleWorkbenchNotice(notice({ noticeId: 'expired', createdAt: '2026-07-03T09:00:00Z', expireAt: '2026-07-03T11:00:00Z' }));
    workbench.handleWorkbenchNotice(notice({ noticeId: 'old', createdAt: '2026-07-03T09:00:00Z', expireAt: '2026-07-04T12:00:00Z' }));
    workbench.handleWorkbenchNotice(notice({ noticeId: 'new', createdAt: '2026-07-03T11:00:00Z', expireAt: '2026-07-04T12:00:00Z' }));
    workbench.handleWorkbenchNotice(notice({ noticeId: 'middle', createdAt: '2026-07-03T10:00:00Z', expireAt: '2026-07-04T12:00:00Z' }));

    expect(workbench.visibleWorkbenchNotices.value.map((item) => item.noticeId)).toEqual(['new', 'middle']);

    workbench.dismissWorkbenchNotice('new');

    expect(workbench.visibleWorkbenchNotices.value.map((item) => item.noticeId)).toEqual(['middle', 'old']);
  });

  it('marks stale data after a loaded fetch fails and retry-only after repeated failures', async () => {
    const { workbench } = await freshStore();
    getJsonMock.mockResolvedValueOnce({ success: true, data: { items: [followup({ phone: 'cache' })] } });
    await workbench.loadWorkbenchFollowups();
    getJsonMock.mockRejectedValue(new Error('network down'));

    await workbench.loadWorkbenchFollowups(true);
    expect(workbench.workbenchState.stale).toBe(true);
    expect(workbench.workbenchState.retryOnly).toBe(false);
    expect(workbench.workbenchState.followups.map((item) => item.phone)).toEqual(['cache']);

    await workbench.loadWorkbenchFollowups();
    await workbench.loadWorkbenchFollowups();

    expect(workbench.workbenchState.fetchFailedCount).toBe(3);
    expect(workbench.workbenchState.retryOnly).toBe(true);
    expect(workbench.workbenchState.loading).toBe(false);
  });

  it('refreshes only when stale, dirty, expired, or not yet loaded', async () => {
    const { workbench } = await freshStore();
    getJsonMock.mockResolvedValue({ success: true, data: { items: [] } });

    workbench.refreshWorkbenchIfNeeded();
    await vi.runAllTimersAsync();
    expect(getJsonMock).toHaveBeenCalledTimes(1);

    workbench.refreshWorkbenchIfNeeded();
    await vi.runAllTimersAsync();
    expect(getJsonMock).toHaveBeenCalledTimes(1);

    workbench.markWorkbenchDirty();
    workbench.refreshWorkbenchIfNeeded();
    await vi.runAllTimersAsync();
    expect(getJsonMock).toHaveBeenCalledTimes(2);

    vi.setSystemTime(new Date('2026-07-03T12:01:00Z'));
    workbench.refreshWorkbenchIfNeeded();
    await vi.runAllTimersAsync();
    expect(getJsonMock).toHaveBeenCalledTimes(3);
  });

  it('merges followup reminders and new leads without duplicate dashboard rows', async () => {
    const { workbench } = await freshStore();
    workbench.workbenchState.followups = [
      followup({ phone: '18800001111', reminderType: 'OVERDUE', overdueHours: 1, alertLevel: 'NORMAL' })
    ];

    const reminderPayload: FollowupReminderPayload = {
      phone: '18800001111',
      reminders: [
        { reminderType: 'OVERDUE', overdueHours: 5, alertLevel: 'HIGH' },
        { reminderType: 'OVERDUE', overdueHours: 7, alertLevel: 'HIGH' },
        { reminderType: 'DUE_TODAY', overdueHours: 0, alertLevel: 'NORMAL' }
      ]
    };
    workbench.handleWorkbenchFollowupReminder(reminderPayload);

    expect(workbench.workbenchState.followups.filter((item) => item.reminderType === 'OVERDUE')).toHaveLength(1);
    expect(workbench.workbenchState.followups.find((item) => item.reminderType === 'OVERDUE')).toMatchObject({
      overdueHours: 5,
      alertLevel: 'HIGH'
    });
    expect(workbench.workbenchState.followups.find((item) => item.reminderType === 'DUE_TODAY')).toMatchObject({
      phone: '18800001111',
      leadType: 'PENDING'
    });

    const newLeadPayload = newLead({ phone: 'mask-1111', phoneFull: '18800001111', nickname: 'Lead A' });
    workbench.handleWorkbenchNewLead(newLeadPayload);
    workbench.handleWorkbenchNewLead(newLeadPayload);

    expect(workbench.workbenchState.followups.filter((item) => item.reminderType === 'NEW_LEAD')).toHaveLength(1);
  });

  it('emits navigation events for customer, followup tabs, capture, and quick search actions', async () => {
    const { workbench, eventBus } = await freshStore();
    const seen: Array<{ event: string; payload: unknown }> = [];
    eventBus.on('customer:selected', (payload) => seen.push({ event: 'customer:selected', payload }));
    eventBus.on('followup:switch-tab', (payload) => seen.push({ event: 'followup:switch-tab', payload }));
    eventBus.on('workbench:capture-chat', (payload) => seen.push({ event: 'workbench:capture-chat', payload }));
    eventBus.on('quick-search:show', (payload) => seen.push({ event: 'quick-search:show', payload }));

    workbench.workbenchState.followups = [followup({ phone: '1', reminderType: 'OVERDUE' })];
    workbench.openWorkbenchCustomer('18800002222', 'TUAN_GOU');
    workbench.openAllFollowups();
    workbench.openAllNewLeads();
    workbench.startWorkbenchCapture();
    workbench.openWorkbenchQuickSearch();
    workbench.startWorkbenchBatchTemplate();

    expect(seen).toEqual([
      { event: 'customer:selected', payload: { phone: '18800002222', scene: 'ACTIVE_REPLY', leadType: 'TUAN_GOU', sourceFrom: 'DASHBOARD' } },
      { event: 'followup:switch-tab', payload: { tab: 'OVERDUE' } },
      { event: 'followup:switch-tab', payload: { tab: 'NEW_LEAD' } },
      { event: 'workbench:capture-chat', payload: {} },
      { event: 'quick-search:show', payload: {} },
      { event: 'followup:switch-tab', payload: { tab: 'OVERDUE' } }
    ]);
    expect(workbench.workbenchState.toast).toContain('今日跟进');
  });
});

function followup(patch: Partial<FollowupItem>): FollowupItem {
  return {
    phone: '18800000000',
    nickname: 'Customer',
    leadType: 'PENDING',
    reminderType: 'DUE_TODAY',
    nextFollowupAt: '2026-07-03T12:30:00',
    ...patch
  };
}

function newLead(patch: Partial<NewLeadAlertPayload> & { id?: string }): NewLeadAlertPayload & { id: string } {
  return {
    id: 'lead-id',
    phone: '188****0000',
    phoneFull: '18800000000',
    nickname: 'New Lead',
    leadType: 'XIAN_SUO',
    priority: 'HIGH',
    sourceTable: 'sheet',
    assignedKeeper: 'keeper',
    arrivedAt: '2026-07-03T12:00:00Z',
    ...patch
  };
}

function notice(patch: Partial<WorkbenchNoticePayload>): WorkbenchNoticePayload {
  return {
    noticeId: 'notice-id',
    title: 'Notice',
    content: 'Notice content',
    level: 'INFO',
    createdAt: '2026-07-03T12:00:00Z',
    expireAt: '2026-07-04T12:00:00Z',
    ...patch
  };
}
