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

  it('starts on today followups so keepers handle todays work first', async () => {
    const { followups } = await freshStore();

    expect(followups.followupListState.activeTab).toBe('DUE_TODAY');
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

  it('upserts new lead alerts, counts new reminders, and clears flash even when another tab is active', async () => {
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
    expect(followups.followupListState.newReminderCount).toBe(2);
    expect(followups.followupListState.newReminderTab).toBe('NEW_LEAD');

    vi.advanceTimersByTime(200);
    expect(followups.followupListState.groups.NEW_LEAD[0].flashUntil).toBeUndefined();

    followups.setActiveFollowupTab('NEW_LEAD');
    followups.handleNewLeadAlert({ ...payload, phoneFull: '18800002222' });
    vi.advanceTimersByTime(200);
    expect(followups.followupListState.groups.NEW_LEAD[0].flashUntil).toBeUndefined();
  });

  it('clears selected customers when switching to a different followup tab', async () => {
    const { followups, eventBus } = await freshStore();
    const batchEvents: unknown[] = [];
    eventBus.on('batch:start', (payload) => batchEvents.push(payload));
    followups.followupListState.groups.OVERDUE = [
      item({ phone: 'masked-1', phoneFull: '18800000001', reminderType: 'OVERDUE' }),
      item({ phone: 'masked-2', phoneFull: '18800000002', reminderType: 'OVERDUE' })
    ];
    followups.followupListState.groups.DUE_TODAY = [
      item({ phone: 'today', phoneFull: '18800000003', reminderType: 'DUE_TODAY' })
    ];

    followups.setActiveFollowupTab('OVERDUE');
    followups.selectAllActiveFollowups();
    expect(followups.selectedFollowupItems.value).toHaveLength(2);

    followups.setActiveFollowupTab('DUE_TODAY');
    followups.startBatchTemplate();

    expect(followups.followupListState.selectedPhones.size).toBe(0);
    expect(followups.selectedFollowupItems.value).toHaveLength(0);
    expect(batchEvents).toEqual([]);
  });

  it('keeps current selections when the active tab is selected again', async () => {
    const { followups } = await freshStore();
    followups.followupListState.groups.DUE_TODAY = [item({ phone: 'today' })];

    followups.toggleFollowupSelection(followups.followupListState.groups.DUE_TODAY[0]);
    followups.setActiveFollowupTab('DUE_TODAY');

    expect(followups.selectedFollowupItems.value.map((entry) => entry.phone)).toEqual(['today']);
  });

  it('keeps batch selection and events limited to the active tab', async () => {
    const { followups, eventBus } = await freshStore();
    const batchEvents: unknown[] = [];
    eventBus.on('batch:start', (payload) => batchEvents.push(payload));
    followups.followupListState.groups.DUE_TODAY = [item({ phone: 'today' })];
    followups.followupListState.groups.OVERDUE = [item({ phone: 'overdue', reminderType: 'OVERDUE' })];
    followups.followupListState.selectedPhones.add('today');
    followups.followupListState.selectedPhones.add('overdue');

    followups.startBatchTemplate();

    expect(followups.selectedFollowupItems.value.map((entry) => entry.phone)).toEqual(['today']);
    expect(batchEvents).toEqual([{ phones: ['today'], source: 'FOLLOWUP_LIST' }]);
  });

  it('completes only the matching due or overdue followup and clears its selection', async () => {
    const { followups } = await freshStore();
    followups.followupListState.groups.DUE_TODAY = [
      item({ phone: '18800001111' }),
      item({ phone: '18800002222' })
    ];
    followups.followupListState.groups.OVERDUE = [
      item({ phone: '18800001111', reminderType: 'OVERDUE' }),
      item({ phone: '18800003333', reminderType: 'OVERDUE' })
    ];
    followups.followupListState.groups.APPOINTMENT = [
      item({ phone: '18800001111', reminderType: 'APPOINTMENT' })
    ];
    followups.followupListState.selectedPhones.add('18800001111');

    followups.completeFollowup('18800001111', 'DUE_TODAY');

    expect(followups.followupListState.groups.DUE_TODAY.map((entry) => entry.phone)).toEqual(['18800002222']);
    expect(followups.followupListState.groups.OVERDUE.map((entry) => entry.phone)).toEqual(['18800001111', '18800003333']);
    expect(followups.followupListState.groups.APPOINTMENT).toHaveLength(1);
    expect(followups.followupListState.selectedPhones.has('18800001111')).toBe(false);
  });

  it('emits customer navigation and opens the latest reminder tab from the banner', async () => {
    const { followups, eventBus } = await freshStore();
    const selected: unknown[] = [];
    eventBus.on('customer:selected', (payload) => selected.push(payload));

    followups.openFollowupCustomer(item({ phone: 'masked-1', phoneFull: '18800000001', leadType: 'TUAN_GOU' }));
    followups.followupListState.newReminderCount = 3;
    followups.followupListState.newReminderTab = 'APPOINTMENT';
    followups.followupListState.activeTab = 'OVERDUE';
    followups.followupListState.selectedPhones.add('18800000001');
    followups.openNewReminderBanner();

    expect(selected).toEqual([{
      phone: '18800000001',
      scene: 'ACTIVE_REPLY',
      leadType: 'TUAN_GOU',
      reminderType: 'DUE_TODAY',
      sourceFrom: 'FOLLOWUP_LIST'
    }]);
    expect(followups.followupListState.activeTab).toBe('APPOINTMENT');
    expect(followups.followupListState.selectedPhones.size).toBe(0);
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
