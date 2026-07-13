import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { eventBus } from './eventBus';

vi.mock('./offlineManager', () => ({
  recordApiNetworkFailure: vi.fn(),
  recordApiSuccess: vi.fn()
}));

function installConfig(accessToken = 'token-a'): void {
  const config = {
    apiBaseUrl: 'http://localhost:8080',
    accessToken,
    accountRole: 'ADMIN'
  };
  Object.defineProperty(globalThis, 'localStorage', {
    value: {
      getItem: vi.fn(() => JSON.stringify(config)),
      setItem: vi.fn(),
      removeItem: vi.fn(),
      clear: vi.fn()
    },
    configurable: true
  });
}

describe('apiClient authentication expiry', () => {
  beforeEach(() => {
    installConfig();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('emits a global expiry event for authenticated 401 responses', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({
      status: 401,
      json: async () => ({
        success: false,
        data: null,
        errorCode: '80-10002',
        message: '登录已过期，请重新登录'
      })
    })));
    const events: Array<{ message: string }> = [];
    const dispose = eventBus.on<{ message: string }>('auth:expired', (payload) => events.push(payload));
    const { getJson } = await import('./apiClient');

    await getJson('/admin/api/v1/accounts');

    expect(events).toEqual([{ message: '登录已过期，请重新登录' }]);
    dispose();
  });

  it('does not emit expiry events for the login endpoint itself', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({
      status: 401,
      json: async () => ({
        success: false,
        data: null,
        errorCode: '80-10002',
        message: '手机号或密码不正确'
      })
    })));
    const events: Array<{ message: string }> = [];
    const dispose = eventBus.on<{ message: string }>('auth:expired', (payload) => events.push(payload));
    const { postJson } = await import('./apiClient');

    await postJson('/admin/api/v1/auth/login', { username: 'admin', password: 'wrong' });

    expect(events).toEqual([]);
    dispose();
  });
});
