import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('./offlineDb', () => ({
  initOfflineDatabase: vi.fn(() => Promise.resolve({}))
}));

type OfflineModule = typeof import('./offlineManager');

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

async function freshOfflineManager(): Promise<OfflineModule> {
  vi.resetModules();
  localStorage.clear();
  localStorage.setItem('desktop_config', JSON.stringify({
    offlineApiFailCount: 2,
    offlineWsDisconnectWaitS: 1
  }));
  return await import('./offlineManager');
}

describe('offlineManager', () => {
  beforeEach(() => {
    installMemoryLocalStorage();
    vi.useFakeTimers();
    delete (window as { desktopBridge?: unknown }).desktopBridge;
  });

  afterEach(() => {
    vi.useRealTimers();
    localStorage.clear();
  });

  it('enters offline after consecutive network-layer API failures and recovers after API success with WS connected', async () => {
    const manager = await freshOfflineManager();
    await manager.initializeOfflineManager();

    manager.recordApiNetworkFailure(new Error('Failed to fetch'));
    expect(manager.offlineState.isOnline.value).toBe(true);

    manager.recordApiNetworkFailure(new Error('ERR_NETWORK'));
    expect(manager.offlineState.isOnline.value).toBe(false);
    expect(manager.offlineState.offlineReason.value).toBe('API_CONSECUTIVE_FAIL');

    manager.recordApiSuccess();
    expect(manager.offlineState.isOnline.value).toBe(false);

    const { eventBus } = await import('./eventBus');
    eventBus.emit('ws:status-change', { connected: true });
    manager.recordApiSuccess();
    expect(manager.offlineState.isOnline.value).toBe(true);
    expect(manager.offlineState.offlineReason.value).toBeNull();

    manager.cleanupOfflineManager();
  });

  it('does not count business errors as network failures', async () => {
    const manager = await freshOfflineManager();
    await manager.initializeOfflineManager();

    manager.recordApiNetworkFailure(new Error('HTTP 400 BAD_REQUEST'));
    manager.recordApiNetworkFailure(new Error('validation failed'));

    expect(manager.offlineState.isOnline.value).toBe(true);
    expect(manager.offlineState.offlineReason.value).toBeNull();

    manager.cleanupOfflineManager();
  });

  it('marks websocket as degraded only after the configured disconnect wait', async () => {
    const manager = await freshOfflineManager();
    await manager.initializeOfflineManager();
    const { eventBus } = await import('./eventBus');

    eventBus.emit('ws:status-change', { connected: false });
    expect(manager.offlineState.hasWsDegraded.value).toBe(false);

    vi.advanceTimersByTime(1000);
    expect(manager.offlineState.hasWsDegraded.value).toBe(true);

    eventBus.emit('ws:status-change', { connected: true });
    expect(manager.offlineState.hasWsDegraded.value).toBe(false);
    expect(manager.offlineState.isWsConnected.value).toBe(true);

    manager.cleanupOfflineManager();
  });

  it('uses desktop bridge OS offline events and debounced recovery', async () => {
    let onlineHandler: ((payload: { online: boolean }) => void) | undefined;
    (window as unknown as {
      desktopBridge: {
        getOnlineStatus: () => Promise<{ online: boolean }>;
        onOnlineStatusChange: (handler: (payload: { online: boolean }) => void) => () => void;
      };
    }).desktopBridge = {
      getOnlineStatus: vi.fn(() => Promise.resolve({ online: true })),
      onOnlineStatusChange: vi.fn((handler) => {
        onlineHandler = handler;
        return vi.fn();
      })
    };
    const manager = await freshOfflineManager();
    await manager.initializeOfflineManager();

    expect(onlineHandler).toBeDefined();
    onlineHandler!({ online: false });
    vi.advanceTimersByTime(300);
    expect(manager.offlineState.isOnline.value).toBe(false);
    expect(manager.offlineState.offlineReason.value).toBe('OS_OFFLINE');

    const { eventBus } = await import('./eventBus');
    eventBus.emit('ws:status-change', { connected: true });
    onlineHandler!({ online: true });
    vi.advanceTimersByTime(300);
    expect(manager.offlineState.isOnline.value).toBe(true);
    expect(manager.offlineState.offlineReason.value).toBeNull();

    manager.cleanupOfflineManager();
  });

  it('registers each offline capability only once', async () => {
    const manager = await freshOfflineManager();

    manager.registerOfflineCapability({ module: 'quick-search', offlineBehavior: 'cache' });
    manager.registerOfflineCapability({ module: 'quick-search', offlineBehavior: 'cache again' });

    expect(manager.offlineState.capabilities.value).toHaveLength(1);
    expect(manager.offlineState.capabilities.value[0].offlineBehavior).toBe('cache');
  });
});
