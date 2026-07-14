import { afterEach, describe, expect, it, vi } from 'vitest';
import { deriveWsUrl, loadDesktopConfig, saveDesktopConfig } from './config';

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

describe('desktop config', () => {
  afterEach(() => {
    localStorage.clear();
  });

  it('derives websocket URL from the configured API base URL', () => {
    installMemoryLocalStorage();

    expect(deriveWsUrl('https://ops.example.com/api')).toBe('wss://ops.example.com/ws/v1/desktop');

    const config = saveDesktopConfig({ apiBaseUrl: 'https://ops.example.com/' });

    expect(config.apiBaseUrl).toBe('https://ops.example.com');
    expect(config.wsUrl).toBe('wss://ops.example.com/ws/v1/desktop');
    expect(loadDesktopConfig().wsUrl).toBe('wss://ops.example.com/ws/v1/desktop');
  });

  it('keeps an explicit websocket URL when one is configured', () => {
    installMemoryLocalStorage();

    const config = saveDesktopConfig({
      apiBaseUrl: 'https://ops.example.com',
      wsUrl: 'wss://events.example.com/ws/v1/desktop'
    });

    expect(config.wsUrl).toBe('wss://events.example.com/ws/v1/desktop');
  });

  it('defaults and persists the clipboard screenshot confirm prompt seconds', () => {
    installMemoryLocalStorage();

    expect(loadDesktopConfig().clipboardScreenshotConfirmPromptS).toBe(10);

    saveDesktopConfig({ clipboardScreenshotConfirmPromptS: 15 });

    expect(loadDesktopConfig().clipboardScreenshotConfirmPromptS).toBe(15);
  });

  it('persists normalized account permissions for admin access gating', () => {
    installMemoryLocalStorage();

    expect(loadDesktopConfig().accountPermissions).toEqual([]);

    saveDesktopConfig({ accountPermissions: ['TAG_MANAGEMENT', ' TAG_MANAGEMENT ', ''] });

    expect(loadDesktopConfig().accountPermissions).toEqual(['TAG_MANAGEMENT']);
  });
});
