import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { AbnormalAlert } from './types';

const closeAlertDatabaseMock = vi.fn();
const deleteExpiredAlertsMock = vi.fn();
const getPersistedAlertsByPhoneMock = vi.fn();
const getPersistedRecentAlertsMock = vi.fn();
const insertAlertHistoryMock = vi.fn();
const updateAlertAcknowledgedMock = vi.fn();

vi.mock('./alertHistoryDb', () => ({
  closeAlertDatabase: closeAlertDatabaseMock,
  deleteExpiredAlerts: deleteExpiredAlertsMock,
  getAlertsByPhone: getPersistedAlertsByPhoneMock,
  getRecentAlerts: getPersistedRecentAlertsMock,
  insertAlertHistory: insertAlertHistoryMock,
  updateAlertAcknowledged: updateAlertAcknowledgedMock
}));

type AlertModule = typeof import('./alertStore');
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

async function freshStore(): Promise<{ alerts: AlertModule; eventBus: EventBusModule['eventBus'] }> {
  vi.resetModules();
  localStorage.clear();
  localStorage.setItem('desktop_config', JSON.stringify({
    alertHistoryMaxCount: 3,
    alertHistoryRetentionDays: 7,
    alertBellRefreshIntervalS: 1
  }));
  resetMocks();
  const alerts = await import('./alertStore');
  const { eventBus } = await import('../../shared/eventBus');
  return { alerts, eventBus };
}

describe('alertStore', () => {
  beforeEach(() => {
    installMemoryLocalStorage();
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-07-03T12:00:00Z'));
    vi.spyOn(Math, 'random').mockReturnValue(0.2345);
    vi.spyOn(console, 'warn').mockImplementation(() => undefined);
  });

  afterEach(async () => {
    const alerts = await import('./alertStore');
    alerts.cleanupAbnormalAlertRouter();
    vi.useRealTimers();
    vi.restoreAllMocks();
    localStorage.clear();
    resetMocks();
  });

  it('validates incoming alert payloads and emits normalized alerts', async () => {
    const { alerts, eventBus } = await freshStore();
    const emitted: AbnormalAlert[] = [];
    eventBus.on<AbnormalAlert>('abnormal:alert', (payload) => emitted.push(payload));

    alerts.handleIncomingAlert({ phone: 'bad', alertType: 'CHURN_RISK', message: 'risk', level: 'WARN', occurredAt: '2026-07-03T12:00:00Z' });
    expect(alerts.unconfirmedCount.value).toBe(0);

    alerts.handleIncomingAlert({
      phone: '18800001111',
      alertType: 'CHURN_RISK',
      message: ' risk ',
      level: 'WARN',
      occurredAt: '2026-07-03T12:00:00Z'
    });

    expect(alerts.unconfirmedCount.value).toBe(1);
    expect(emitted[0]).toMatchObject({
      alertId: 'k_alert_1783080000000_3110',
      phone: '18800001111',
      message: 'risk',
      acknowledged: false
    });
    expect(insertAlertHistoryMock).toHaveBeenCalledWith(emitted[0], 3);
  });

  it('sorts memory alerts by occurredAt and filters acknowledged alerts', async () => {
    const { alerts } = await freshStore();
    alerts.alertStore.set('18800001111', [
      alert({ alertId: 'old', occurredAt: '2026-07-03T10:00:00Z' }),
      alert({ alertId: 'ack', occurredAt: '2026-07-03T12:00:00Z', acknowledged: true }),
      alert({ alertId: 'new', occurredAt: '2026-07-03T11:00:00Z' })
    ]);

    expect(alerts.getAlertsByPhone('18800001111').map((item) => item.alertId)).toEqual(['new', 'old']);
  });

  it('loads persisted alerts when memory is empty and marks history unavailable on failure', async () => {
    const { alerts } = await freshStore();
    getPersistedAlertsByPhoneMock.mockResolvedValueOnce([
      alert({ alertId: 'persisted-new', occurredAt: '2026-07-03T11:00:00Z' }),
      alert({ alertId: 'persisted-ack', acknowledged: true })
    ]);

    await expect(alerts.loadAlertsByPhone('18800001111')).resolves.toEqual([
      expect.objectContaining({ alertId: 'persisted-new' })
    ]);
    expect(alerts.abnormalAlertState.historyUnavailable).toBe(false);
    expect(alerts.getAlertsByPhone('18800001111').map((item) => item.alertId)).toEqual(['persisted-new']);

    getPersistedAlertsByPhoneMock.mockRejectedValueOnce(new Error('indexeddb down'));
    await expect(alerts.loadAlertsByPhone('18800002222')).resolves.toEqual([]);
    expect(alerts.abnormalAlertState.historyUnavailable).toBe(true);
  });

  it('loads recent history and handles history read failures', async () => {
    const { alerts } = await freshStore();
    getPersistedRecentAlertsMock.mockResolvedValueOnce([alert({ alertId: 'recent' })]);

    await alerts.loadRecentAlertHistory(1);

    expect(getPersistedRecentAlertsMock).toHaveBeenCalledWith(1);
    expect(alerts.abnormalAlertState.recentHistory.map((item) => item.alertId)).toEqual(['recent']);
    expect(alerts.abnormalAlertState.historyLoading).toBe(false);

    getPersistedRecentAlertsMock.mockRejectedValueOnce(new Error('indexeddb down'));
    await alerts.loadRecentAlertHistory();

    expect(alerts.abnormalAlertState.recentHistory).toEqual([]);
    expect(alerts.abnormalAlertState.historyUnavailable).toBe(true);
  });

  it('acknowledges alerts, emits the updated alert, and persists acknowledgement', async () => {
    const { alerts, eventBus } = await freshStore();
    const emitted: AbnormalAlert[] = [];
    eventBus.on<AbnormalAlert>('abnormal:alert', (payload) => emitted.push(payload));
    alerts.alertStore.set('18800001111', [alert({ alertId: 'alert-a' })]);

    alerts.acknowledgeAlert('alert-a');

    expect(alerts.unconfirmedCount.value).toBe(0);
    expect(emitted[0]).toMatchObject({ alertId: 'alert-a', acknowledged: true, acknowledgedAt: '2026-07-03T12:00:00.000Z' });
    expect(updateAlertAcknowledgedMock).toHaveBeenCalledWith(emitted[0]);

    alerts.acknowledgeAlert('missing');
    expect(updateAlertAcknowledgedMock).toHaveBeenCalledTimes(1);
  });

  it('toggles alert panels and opens full history', async () => {
    const { alerts } = await freshStore();
    getPersistedRecentAlertsMock.mockResolvedValueOnce([alert({ alertId: 'history' })]);

    alerts.toggleAlertPanel();
    expect(alerts.abnormalAlertState.panelOpen).toBe(true);
    expect(alerts.abnormalAlertState.historyOpen).toBe(false);
    alerts.closeAlertPanel();
    expect(alerts.abnormalAlertState.panelOpen).toBe(false);

    await alerts.showAllHistory();
    expect(alerts.abnormalAlertState.historyOpen).toBe(true);
    expect(alerts.abnormalAlertState.recentHistory.map((item) => item.alertId)).toEqual(['history']);
  });

  it('initializes and cleans up router listeners and periodic history cleanup', async () => {
    const { alerts, eventBus } = await freshStore();

    alerts.initializeAbnormalAlertRouter();
    alerts.initializeAbnormalAlertRouter();
    expect(deleteExpiredAlertsMock).toHaveBeenCalledTimes(1);

    eventBus.emit('ABNORMAL_ALERT', {
      phone: '18800001111',
      alertType: 'CUSTOMER_COMPLAINT',
      message: 'complaint',
      level: 'ERROR',
      occurredAt: '2026-07-03T12:00:00Z'
    });
    expect(alerts.unconfirmedCount.value).toBe(1);

    vi.advanceTimersByTime(1000);
    expect(deleteExpiredAlertsMock).toHaveBeenCalledTimes(2);

    alerts.cleanupAbnormalAlertRouter();
    expect(closeAlertDatabaseMock).toHaveBeenCalled();
    expect(alerts.alertStore.size).toBe(0);
  });
});

function resetMocks(): void {
  closeAlertDatabaseMock.mockReset();
  deleteExpiredAlertsMock.mockReset();
  getPersistedAlertsByPhoneMock.mockReset();
  getPersistedRecentAlertsMock.mockReset();
  insertAlertHistoryMock.mockReset();
  updateAlertAcknowledgedMock.mockReset();
  deleteExpiredAlertsMock.mockResolvedValue(undefined);
  getPersistedAlertsByPhoneMock.mockResolvedValue([]);
  getPersistedRecentAlertsMock.mockResolvedValue([]);
  insertAlertHistoryMock.mockResolvedValue(undefined);
  updateAlertAcknowledgedMock.mockResolvedValue(undefined);
}

function alert(patch: Partial<AbnormalAlert> = {}): AbnormalAlert {
  return {
    alertId: 'alert-id',
    phone: '18800001111',
    alertType: 'CHURN_RISK',
    message: 'risk',
    level: 'WARN',
    occurredAt: '2026-07-03T12:00:00Z',
    acknowledged: false,
    acknowledgedAt: null,
    ...patch
  };
}
