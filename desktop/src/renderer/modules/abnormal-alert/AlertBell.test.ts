import { createApp, nextTick, type App } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { AbnormalAlert } from './types';

const mocks = vi.hoisted(() => ({
  closeAlertDatabase: vi.fn(),
  deleteExpiredAlerts: vi.fn(),
  getAlertsByPhone: vi.fn(),
  getRecentAlerts: vi.fn(),
  insertAlertHistory: vi.fn(),
  updateAlertAcknowledged: vi.fn()
}));

vi.mock('./alertHistoryDb', () => ({
  closeAlertDatabase: mocks.closeAlertDatabase,
  deleteExpiredAlerts: mocks.deleteExpiredAlerts,
  getAlertsByPhone: mocks.getAlertsByPhone,
  getRecentAlerts: mocks.getRecentAlerts,
  insertAlertHistory: mocks.insertAlertHistory,
  updateAlertAcknowledged: mocks.updateAlertAcknowledged
}));

type MountedBell = {
  app: App<Element>;
  host: HTMLDivElement;
  alerts: typeof import('./alertStore');
};

async function flushUi(): Promise<void> {
  await Promise.resolve();
  await Promise.resolve();
  await nextTick();
}

async function mountBell(): Promise<MountedBell> {
  vi.resetModules();
  const [{ default: AlertBell }, alerts] = await Promise.all([
    import('./AlertBell.vue'),
    import('./alertStore')
  ]);
  const host = document.createElement('div');
  document.body.appendChild(host);
  const app = createApp(AlertBell);
  app.mount(host);
  await flushUi();
  return { app, host, alerts };
}

describe('AlertBell', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-07-03T12:00:00Z'));
    resetMocks();
  });

  afterEach(() => {
    document.body.innerHTML = '';
    vi.useRealTimers();
    resetMocks();
  });

  it('renders the unconfirmed badge, alert rows, and acknowledges alerts from the panel button', async () => {
    const { app, host, alerts } = await mountBell();
    alerts.alertStore.set('18800001111', [alert({ alertId: 'alert-a', message: 'High churn risk' })]);
    await flushUi();

    expect(host.querySelector('.bell-badge')?.textContent).toBe('1');

    (host.querySelector('.alert-bell') as HTMLButtonElement | null)?.click();
    await flushUi();

    expect(host.querySelector('.alert-panel')).toBeTruthy();
    expect(host.textContent).toContain('High churn risk');
    expect(host.textContent).toContain('188****1111');

    const acknowledge = host.querySelector('.alert-row .secondary') as HTMLButtonElement | null;
    acknowledge?.click();
    await flushUi();

    expect(mocks.updateAlertAcknowledged).toHaveBeenCalledWith(expect.objectContaining({
      alertId: 'alert-a',
      acknowledged: true,
      acknowledgedAt: '2026-07-03T12:00:00.000Z'
    }));
    expect(host.querySelector('.bell-badge')).toBeFalsy();
    app.unmount();
  });

  it('loads and renders full history from the rendered history link', async () => {
    mocks.getRecentAlerts.mockResolvedValue([alert({ alertId: 'history-a', message: 'Past complaint', acknowledged: true })]);
    const { app, host } = await mountBell();

    (host.querySelector('.alert-bell') as HTMLButtonElement | null)?.click();
    await flushUi();
    (host.querySelector('.alert-history-link') as HTMLButtonElement | null)?.click();
    await flushUi();

    expect(mocks.getRecentAlerts).toHaveBeenCalled();
    expect(host.querySelector('.alert-history')).toBeTruthy();
    expect(host.textContent).toContain('Past complaint');
    app.unmount();
  });

  it('shows an empty panel and can close the panel through the rendered close button', async () => {
    const { app, host } = await mountBell();

    (host.querySelector('.alert-bell') as HTMLButtonElement | null)?.click();
    await flushUi();

    expect(host.querySelector('.empty-panel')).toBeTruthy();

    (host.querySelector('.alert-panel-head .secondary') as HTMLButtonElement | null)?.click();
    await flushUi();

    expect(host.querySelector('.alert-panel')).toBeFalsy();
    app.unmount();
  });
});

function resetMocks(): void {
  Object.values(mocks).forEach((mock) => mock.mockReset());
  mocks.deleteExpiredAlerts.mockResolvedValue(undefined);
  mocks.getAlertsByPhone.mockResolvedValue([]);
  mocks.getRecentAlerts.mockResolvedValue([]);
  mocks.insertAlertHistory.mockResolvedValue(undefined);
  mocks.updateAlertAcknowledged.mockResolvedValue(undefined);
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
