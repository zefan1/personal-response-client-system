import { afterEach, describe, expect, it, vi } from 'vitest';
import { eventBus } from './eventBus';

class FakeWebSocket {
  static OPEN = 1;
  static instances: FakeWebSocket[] = [];

  readyState = FakeWebSocket.OPEN;
  onopen: (() => void) | null = null;
  onmessage: ((event: { data: string }) => void) | null = null;
  onclose: (() => void) | null = null;
  onerror: (() => void) | null = null;

  constructor(public readonly url: string) {
    FakeWebSocket.instances.push(this);
  }

  send = vi.fn();
}

function installConfig(): void {
  const config = {
    apiBaseUrl: 'http://localhost:8080',
    accessToken: 'token-a',
    accountRole: 'KEEPER',
    wsUrl: 'ws://localhost:8080/ws/v1/desktop'
  };
  const store = new Map<string, string>([['desktop_config', JSON.stringify(config)]]);
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

describe('wsMessageBus', () => {
  afterEach(() => {
    FakeWebSocket.instances.splice(0);
    vi.unstubAllGlobals();
  });

  it('forwards online account invalidation to the global authentication handler', async () => {
    installConfig();
    vi.stubGlobal('WebSocket', FakeWebSocket);
    const expired: Array<{ message: string }> = [];
    const dispose = eventBus.on<{ message: string }>('auth:expired', (payload) => expired.push(payload));
    const { connectWsMessageBus } = await import('./wsMessageBus');

    connectWsMessageBus();
    const socket = FakeWebSocket.instances[0];
    socket.onmessage?.({
      data: JSON.stringify({
        type: 'AUTH_INVALIDATED',
        payload: { message: '密码已重置，请使用新密码重新登录' }
      })
    });

    expect(expired).toEqual([{ message: '密码已重置，请使用新密码重新登录' }]);
    dispose();
    socket.onclose?.();
  });
});
