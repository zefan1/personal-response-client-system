import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { FollowupItem, FollowupReminderPayload, NewLeadAlertPayload } from './types';

const getJsonMock = vi.fn();

vi.mock('../../shared/apiClient', () => ({
  getJson: getJsonMock
}));

type FollowupModule = typeof import('./followupListStore');
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

async function freshStore(): Promise<{ followups: FollowupModule; eventBus: EventBusModule['eventBus'] }> {
  vi.resetModules();
  localStorage.clear();
  localStorage.setItem('desktop_config', JSON.stringify({
    newReminderFlashMs: 200
  }));
  getJsonMock.mockReset();
  const followups = await import('./followupListStore');
  const { eventBus } = await import('../../shared/eventBus');
  return { followups, eventBus };
}

describe('followupListStore', () => {
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

  it('loads today followups into normalized tabs and exposes active items', async () => {
    const { followups } = await freshStore();
    getJsonMock.mockResolvedValue({
      success: true,
      data: {
        keeperId: 'keeper-a',
        totalCount: 5,
        items: [
          item({ phone: 'overdue', reminderType: 'OVERDUE' }),
          item({ phone: 'today', reminderType: 'DUE_TODAY' }),
          item({ phone: 'appointment', reminderType: 'APPOINTMENT' }),
          item({ phone: 'new-lead', reminderType: 'NEW_LEAD' }),
          item({ phone: 'ignored', reminderType: 'TAG_SUGGESTION' })
        ]
      }
    });

    await followups.loadTodayFollowups();

    expect(getJsonMock).toHaveBeenCalledWith('/api/v1/followups/today', 10000);
    expect(followups.followupListState.keeperId).toBe('keeper-a');
    expect(followups.followupListState.loaded).toBe(true);
    expect(followups.followupListState.groups.OVERDUE.map((entry) => entry.phone)).toEqual(['overdue']);
    expect(followups.followupListState.groups.DUE_TODAY.map((entry) => entry.phone)).toEqual(['today']);
    expect(followups.followupListState.groups.APPOINTMENT.map((entry) => entry.phone)).toEqual(['appointment']);
    expect(followups.followupListState.groups.NEW_LEAD.map((entry) => entry.phone)).toEqual(['new-lead']);

    followups.setActiveFollowupTab('APPOINTMENT');
    expect(followups.activeFollowupItems.value.map((entry) => entry.phone)).toEqual(['appointment']);
  });

  it('marks loaded data stale on API failures while keeping existing groups', async () => {
    const { followups } = await freshStore();
    getJsonMock.mockResolvedValueOnce({ success: true, data: { keeperId: 'keeper-a', totalCount: 1, items: [item({ phone: 'cache' })] } });
    await followups.loadTodayFollowups();

    getJsonMock.mockRejectedValueOnce(new Error('network down'));
    await followups.loadTodayFollowups();

    expect(followups.followupListState.stale).toBe(true);
    expect(followups.followupListState.loading).toBe(false);
    expect(followups.followupListState.groups.DUE_TODAY.map((entry) => entry.phone)).toEqual(['cache']);
  });

  it('chooses the primary reminder, moves existing rows across tabs, and clears flash after timeout', async () => {
    const { followups } = await freshStore();
    followups.followupListState.groups.APPOINTMENT = [
      item({ phone: '18800001111', nickname: 'Existing', reminderType: 'APPOINTMENT', overdueHours: 0 })
    ];

    const payload: FollowupReminderPayload = {
      phone: '18800001111',
      reminders: [
        { reminderType: 'APPOINTMENT', alertLevel: 'NORMAL', overdueHours: 0 },
        { reminderType: 'OVERDUE', alertLevel: 'HIGH', overdueHours: 8 },
        { reminderType: 'DUE_TODAY', alertLevel: 'NORMAL', overdueHours: 1 }
      ]
    };
    followups.handleFollowupReminder(payload);

    expect(followups.followupListState.groups.APPOINTMENT).toHaveLength(0);
    expect(followups.followupListState.groups.OVERDUE[0]).toMatchObject({
      phone: '18800001111',
      nickname: 'Existing',
      reminderType: 'OVERDUE',
      overdueHours: 8,
      alertLevel: 'HIGH'
    });
    expect(followups.followupListState.newReminderCount).toBe(1);
    expect(followups.followupListState.newReminderTab).toBe('OVERDUE');
    expect(followups.followupListState.groups.OVERDUE[0].flashUntil).toBeGreaterThan(Date.now());

    vi.advanceTimersByTime(200);

    expect(followups.followupListState.groups.OVERDUE[0].flashUntil).toBeUndefined();
  });

  it('upserts new lead alerts and only schedules flash cleanup on the active new-lead tab', async () => {
    const { followups } = await freshStore();
    const payload: NewLeadAlertPayload = {
      phone: 'masked-1111',
      phoneFull: '18800001111',
      nickname: 'Lead A',
      leadType: 'XIAN_SUO',
      priority: 'HIGH',
      sourceTable: 'sheet-a',
      assignedKeeper: 'keeper-a',
      arrivedAt: '2026-07-03T12:00:00Z'
    };

    followups.handleNewLeadAlert(payload);
    followups.handleNewLeadAlert({ ...payload, nickname: 'Lead A Updated', priority: 'NORMAL' });

    expect(followups.followupListState.groups.NEW_LEAD).toHaveLength(1);
    expect(followups.followupListState.groups.NEW_LEAD[0]).toMatchObject({
      phone: '18800001111',
      nickname: 'Lead A Updated',
      alertLevel: 'NORMAL'
    });

    vi.advanceTimersByTime(200);
    expect(followups.followupListState.groups.NEW_LEAD[0].flashUntil).toBeDefined();

    followups.setActiveFollowupTab('NEW_LEAD');
    followups.handleNewLeadAlert({ ...payload, phoneFull: '18800002222' });
    vi.advanceTimersByTime(200);
    expect(followups.followupListState.groups.NEW_LEAD[0].flashUntil).toBeUndefined();
  });

  it('tracks selection across tabs and emits batch template events for selected phones', async () => {
    const { followups, eventBus } = await freshStore();
    const batchEvents: unknown[] = [];
    eventBus.on('batch:start', (payload) => batchEvents.push(payload));
    followups.followupListState.groups.OVERDUE = [
      item({ phone: 'masked-1', phoneFull: '18800000001', reminderType: 'OVERDUE' }),
      item({ phone: 'masked-2', phoneFull: '18800000002', reminderType: 'OVERDUE' })
    ];
    followups.followupListState.groups.NEW_LEAD = [
      item({ phone: '18800000003', reminderType: 'NEW_LEAD' })
    ];

    followups.setActiveFollowupTab('OVERDUE');
    followups.selectAllActiveFollowups();
    expect(followups.selectedFollowupItems.value.map((entry) => entry.phoneFull ?? entry.phone)).toEqual(['18800000001', '18800000002']);

    followups.invertActiveFollowupSelection();
    expect(followups.selectedFollowupItems.value).toHaveLength(0);

    followups.toggleFollowupSelection(followups.followupListState.groups.OVERDUE[0]);
    followups.toggleFollowupSelection(followups.followupListState.groups.NEW_LEAD[0]);
    followups.startBatchTemplate();

    expect(batchEvents).toEqual([{ phones: ['18800000001', '18800000003'], source: 'FOLLOWUP_LIST' }]);
  });

  it('emits customer navigation and opens the latest reminder tab from the banner', async () => {
    const { followups, eventBus } = await freshStore();
    const selected: unknown[] = [];
    eventBus.on('customer:selected', (payload) => selected.push(payload));

    followups.openFollowupCustomer(item({ phone: 'masked-1', phoneFull: '18800000001', leadType: 'TUAN_GOU' }));
    followups.followupListState.newReminderCount = 3;
    followups.followupListState.newReminderTab = 'APPOINTMENT';
    followups.openNewReminderBanner();

    expect(selected).toEqual([{
      phone: '18800000001',
      scene: 'ACTIVE_REPLY',
      leadType: 'TUAN_GOU',
      sourceFrom: 'FOLLOWUP_LIST'
    }]);
    expect(followups.followupListState.activeTab).toBe('APPOINTMENT');
    expect(followups.followupListState.newReminderCount).toBe(0);
  });
});

function item(patch: Partial<FollowupItem>): FollowupItem {
  return {
    phone: '18800000000',
    phoneFull: undefined,
    nickname: 'Customer',
    leadType: 'PENDING',
    reminderType: 'DUE_TODAY',
    alertLevel: 'NORMAL',
    overdueHours: null,
    ...patch
  };
}
