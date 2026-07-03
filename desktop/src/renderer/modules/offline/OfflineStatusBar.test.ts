import { createApp, nextTick, type App } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

type MountedBar = {
  app: App<Element>;
  host: HTMLDivElement;
  eventBus: typeof import('../../shared/eventBus')['eventBus'];
  offlineManager: typeof import('../../shared/offlineManager');
};

function installMemoryLocalStorage(): void {
  const store = new Map<string, string>();
  Object.defineProperty(globalThis, 'localStorage', {
    value: {
      getItem: vi.fn((key: string) => store.get(key) ?? null),
      setItem: vi.fn((key: string, value: string) => store.set(key, String(value))),
      removeItem: vi.fn((key: string) => store.delete(key)),
      clear: vi.fn(() => store.clear())
    },
    configurable: true
  });
}

async function flushUi(): Promise<void> {
  await Promise.resolve();
  await Promise.resolve();
  await nextTick();
}

async function mountBar(): Promise<MountedBar> {
  vi.resetModules();
  localStorage.clear();
  localStorage.setItem('desktop_config', JSON.stringify({
    onlineToastDurationMs: 1000
  }));
  const [{ default: OfflineStatusBar }, { eventBus }, offlineManager] = await Promise.all([
    import('./OfflineStatusBar.vue'),
    import('../../shared/eventBus'),
    import('../../shared/offlineManager')
  ]);
  const host = document.createElement('div');
  document.body.appendChild(host);
  const app = createApp(OfflineStatusBar);
  app.mount(host);
  await flushUi();
  return { app, host, eventBus, offlineManager };
}

describe('OfflineStatusBar', () => {
  beforeEach(() => {
    installMemoryLocalStorage();
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-07-03T12:00:00Z'));
  });

  afterEach(() => {
    document.body.innerHTML = '';
    vi.useRealTimers();
    localStorage.clear();
  });

  it('renders the offline status bar for API consecutive failures and hides it after returning online', async () => {
    const { app, host, offlineManager } = await mountBar();

    offlineManager.isOnline.value = false;
    offlineManager.offlineReason.value = 'API_CONSECUTIVE_FAIL';
    await flushUi();

    expect(host.querySelector('.status-bar.offline')).toBeTruthy();
    expect(host.textContent).toContain('离线模式');
    expect(host.textContent).toContain('服务连续无法访问');

    offlineManager.isOnline.value = true;
    offlineManager.offlineReason.value = null;
    await flushUi();

    expect(host.querySelector('.status-bar.offline')).toBeFalsy();
    app.unmount();
  });

  it('renders websocket degraded status while the API is online', async () => {
    const { app, host, offlineManager } = await mountBar();

    offlineManager.isOnline.value = true;
    offlineManager.hasWsDegraded.value = true;
    await flushUi();

    expect(host.querySelector('.status-bar.ws')).toBeTruthy();
    expect(host.textContent).toContain('提醒服务暂不可用');

    offlineManager.hasWsDegraded.value = false;
    await flushUi();

    expect(host.querySelector('.status-bar.ws')).toBeFalsy();
    app.unmount();
  });

  it('shows and automatically hides the online recovery toast from network:online events', async () => {
    const { app, host, eventBus } = await mountBar();

    eventBus.emit('network:online', { offlineDurationMs: 3000 });
    await flushUi();

    expect(host.querySelector('.status-bar.online')).toBeTruthy();
    expect(host.textContent).toContain('已恢复在线');

    vi.advanceTimersByTime(1000);
    await flushUi();

    expect(host.querySelector('.status-bar.online')).toBeFalsy();
    app.unmount();
  });
});
