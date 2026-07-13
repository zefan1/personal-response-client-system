import { createApp, nextTick, type App } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { AbnormalAlert } from './types';

const mocks = vi.hoisted(() => ({
  clearAllAlertHistory: vi.fn(),
  closeAlertDatabase: vi.fn(),
  deleteExpiredAlerts: vi.fn(),
  getAlertsByPhone: vi.fn(),
  getRecentAlerts: vi.fn(),
  insertAlertHistory: vi.fn(),
  updateAlertAcknowledged: vi.fn()
}));

vi.mock('./alertHistoryDb', () => ({
  clearAllAlertHistory: mocks.clearAllAlertHistory,
  closeAlertDatabase: mocks.closeAlertDatabase,
  deleteExpiredAlerts: mocks.deleteExpiredAlerts,
  getAlertsByPhone: mocks.getAlertsByPhone,
  getRecentAlerts: mocks.getRecentAlerts,
  insertAlertHistory: mocks.insertAlertHistory,
  updateAlertAcknowledged: mocks.updateAlertAcknowledged
}));

vi.mock('../../shared/config', () => {
  const config = {
    alertBellRefreshIntervalS: 86400,
    alertHistoryMaxCount: 50,
    alertHistoryRetentionDays: 7,
    clipboardScreenshotConfirmPromptS: 10,
    offlineWsDisconnectWaitS: 15,
    workbenchMaxNotices: 3
  };
  return {
    loadDesktopConfig: vi.fn(() => config),
    saveDesktopConfig: vi.fn((patch: Partial<typeof config>) => Object.assign(config, patch))
  };
});

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

  it('hides the reminder entry when there is no actionable alert', async () => {
    const { app, host } = await mountBell();

    expect(host.querySelector('.alert-bell')).toBeFalsy();
    expect(host.querySelector('.alert-panel')).toBeFalsy();
    app.unmount();
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
    expect(host.textContent).toContain('客户异常 · 提醒 · 待处理');

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
    const { app, host, alerts } = await mountBell();
    alerts.alertStore.set('18800001111', [alert({ alertId: 'alert-a' })]);
    await flushUi();

    (host.querySelector('.alert-bell') as HTMLButtonElement | null)?.click();
    await flushUi();
    (host.querySelector('.alert-history-link') as HTMLButtonElement | null)?.click();
    await flushUi();

    expect(mocks.getRecentAlerts).toHaveBeenCalled();
    expect(host.querySelector('.alert-history')).toBeTruthy();
    expect(host.textContent).toContain('Past complaint');
    app.unmount();
  });

  it('clears persisted history without clearing active reminders', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    mocks.getRecentAlerts.mockResolvedValue([alert({ alertId: 'history-a', message: 'Past complaint', acknowledged: true })]);
    const { app, host, alerts } = await mountBell();
    alerts.alertStore.set('18800001111', [alert({ alertId: 'alert-a' })]);
    await flushUi();

    (host.querySelector('.alert-bell') as HTMLButtonElement).click();
    await flushUi();
    (host.querySelector('.alert-history-link') as HTMLButtonElement).click();
    await flushUi();
    const clearButton = [...host.querySelectorAll('button')].find((button) => button.textContent?.includes('清空历史')) as HTMLButtonElement;
    clearButton.click();
    await flushUi();

    expect(mocks.clearAllAlertHistory).toHaveBeenCalled();
    expect(alerts.alertStore.get('18800001111')).toHaveLength(1);
    expect(host.textContent).toContain('暂无历史提醒');
    app.unmount();
  });

  it('can close the panel through the rendered close button', async () => {
    const { app, host, alerts } = await mountBell();
    alerts.alertStore.set('18800001111', [alert({ alertId: 'alert-a' })]);
    await flushUi();

    (host.querySelector('.alert-bell') as HTMLButtonElement | null)?.click();
    await flushUi();

    expect(host.querySelector('.alert-panel')).toBeTruthy();

    (host.querySelector('.alert-panel-head .icon-close-button') as HTMLButtonElement | null)?.click();
    await flushUi();

    expect(host.querySelector('.alert-panel')).toBeFalsy();
    app.unmount();
  });

  it('renders LLM configuration warnings from desktop status', async () => {
    const { app, host } = await mountBell();
    const { applyDesktopStatus } = await import('../../shared/desktopStatusStore');
    applyDesktopStatus({
      llmStatus: {
        status: 'WARN',
        label: 'LLM 配置不完整',
        detail: '已启用 LLM 回复生成，但 API 地址、密钥或模型未配置完整。',
        replyGenerationEnabled: true
      }
    });
    await flushUi();

    expect(host.querySelector('.bell-badge')?.textContent).toBe('1');

    (host.querySelector('.alert-bell') as HTMLButtonElement | null)?.click();
    await flushUi();

    expect(host.textContent).toContain('LLM 配置需处理');
    expect(host.textContent).toContain('已启用 LLM 回复生成');
    app.unmount();
  });
});

function resetMocks(): void {
  Object.values(mocks).forEach((mock) => mock.mockReset());
  mocks.deleteExpiredAlerts.mockResolvedValue(undefined);
  mocks.clearAllAlertHistory.mockResolvedValue(undefined);
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
