import { afterEach, describe, expect, it, vi } from 'vitest';

import { openAdminConsole } from './desktopBridge';

describe('desktopBridge admin console launcher', () => {
  const originalUserAgent = navigator.userAgent;

  afterEach(() => {
    delete (window as { desktopBridge?: unknown }).desktopBridge;
    Object.defineProperty(navigator, 'userAgent', {
      value: originalUserAgent,
      configurable: true
    });
    vi.restoreAllMocks();
  });

  it('uses the controlled Electron bridge when available', async () => {
    const openAdminConsoleMock = vi.fn(async () => ({ success: true, url: 'http://127.0.0.1:5173/#/admin' }));
    (window as unknown as { desktopBridge: { openAdminConsole: typeof openAdminConsoleMock } }).desktopBridge = {
      openAdminConsole: openAdminConsoleMock
    };

    await expect(openAdminConsole('http://127.0.0.1:5173/#/admin')).resolves.toMatchObject({ success: true });
    expect(openAdminConsoleMock).toHaveBeenCalledTimes(1);
  });

  it('does not open an Electron child window when the preload bridge is stale', async () => {
    const windowOpenSpy = vi.spyOn(window, 'open');
    (window as unknown as { desktopBridge: Record<string, unknown> }).desktopBridge = {};

    await expect(openAdminConsole('http://127.0.0.1:5173/#/admin')).resolves.toMatchObject({
      success: false,
      error: 'DESKTOP_BRIDGE_STALE'
    });
    expect(windowOpenSpy).not.toHaveBeenCalled();
  });

  it('does not open an Electron child window when preload injection is missing', async () => {
    const windowOpenSpy = vi.spyOn(window, 'open');
    Object.defineProperty(navigator, 'userAgent', {
      value: `${originalUserAgent} Electron/39.0.0`,
      configurable: true
    });

    await expect(openAdminConsole('http://127.0.0.1:5173/#/admin')).resolves.toMatchObject({
      success: false,
      error: 'DESKTOP_BRIDGE_STALE'
    });
    expect(windowOpenSpy).not.toHaveBeenCalled();
  });

  it('opens a new tab only in the browser preview', async () => {
    const windowOpenSpy = vi.spyOn(window, 'open').mockReturnValue({} as Window);

    await expect(openAdminConsole('http://127.0.0.1:5173/#/admin')).resolves.toMatchObject({ success: true });
    expect(windowOpenSpy).toHaveBeenCalledWith('http://127.0.0.1:5173/#/admin', '_blank', 'noopener,noreferrer');
  });
});
