import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  captureScreenshot,
  getAlwaysOnTop,
  notifyReplyTask,
  onReplyTaskOpen,
  openAdminConsole,
  toggleAlwaysOnTop
} from './desktopBridge';

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

    await expect(openAdminConsole('https://ops.example.com/#/admin')).resolves.toMatchObject({ success: true });
    expect(openAdminConsoleMock).toHaveBeenCalledWith('https://ops.example.com/#/admin');
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

  it('uses the controlled Electron bridge for always-on-top state', async () => {
    const getAlwaysOnTopMock = vi.fn(async () => ({ success: true, alwaysOnTop: false }));
    const toggleAlwaysOnTopMock = vi.fn(async () => ({ success: true, alwaysOnTop: true }));
    (window as unknown as {
      desktopBridge: {
        getAlwaysOnTop: typeof getAlwaysOnTopMock;
        toggleAlwaysOnTop: typeof toggleAlwaysOnTopMock;
      };
    }).desktopBridge = {
      getAlwaysOnTop: getAlwaysOnTopMock,
      toggleAlwaysOnTop: toggleAlwaysOnTopMock
    };

    await expect(getAlwaysOnTop()).resolves.toEqual({ success: true, alwaysOnTop: false });
    await expect(toggleAlwaysOnTop()).resolves.toEqual({ success: true, alwaysOnTop: true });
    expect(getAlwaysOnTopMock).toHaveBeenCalledTimes(1);
    expect(toggleAlwaysOnTopMock).toHaveBeenCalledTimes(1);
  });

  it('reports always-on-top as unavailable in the browser preview', async () => {
    await expect(getAlwaysOnTop()).resolves.toMatchObject({
      success: false,
      alwaysOnTop: false,
      error: 'DESKTOP_BRIDGE_UNAVAILABLE'
    });
    await expect(toggleAlwaysOnTop()).resolves.toMatchObject({
      success: false,
      alwaysOnTop: false,
      error: 'DESKTOP_BRIDGE_UNAVAILABLE'
    });
  });

  it('preserves non-sensitive capture metadata from the Electron bridge', async () => {
    const captureScreenshotMock = vi.fn(async () => ({
      success: true,
      imageBase64: 'window-image',
      width: 1280,
      height: 720,
      captureMode: 'FOREGROUND_WINDOW' as const
    }));
    (window as unknown as {
      desktopBridge: { captureScreenshot: typeof captureScreenshotMock };
    }).desktopBridge = { captureScreenshot: captureScreenshotMock };

    const result = await captureScreenshot();

    expect(result).toMatchObject({
      success: true,
      width: 1280,
      height: 720,
      captureMode: 'FOREGROUND_WINDOW'
    });
    const captureMode: 'FOREGROUND_WINDOW' | 'SCREEN_FALLBACK' | undefined = result.captureMode;
    expect(captureMode).toBe('FOREGROUND_WINDOW');
  });

  it('delegates pending reply notifications and disposes the open listener', async () => {
    const notifyReplyTaskMock = vi.fn(async () => ({ success: true }));
    const dispose = vi.fn();
    let openListener: ((payload: { taskId: string }) => void) | undefined;
    const onReplyTaskOpenMock = vi.fn((listener: (payload: { taskId: string }) => void) => {
      openListener = listener;
      return dispose;
    });
    (window as unknown as {
      desktopBridge: {
        notifyReplyTask: typeof notifyReplyTaskMock;
        onReplyTaskOpen: typeof onReplyTaskOpenMock;
      };
    }).desktopBridge = { notifyReplyTask: notifyReplyTaskMock, onReplyTaskOpen: onReplyTaskOpenMock };
    const opened: string[] = [];

    await expect(notifyReplyTask({ taskId: 'task-1' })).resolves.toEqual({ success: true });
    const removeListener = onReplyTaskOpen((payload) => opened.push(payload.taskId));
    openListener?.({ taskId: 'task-1' });
    removeListener();

    expect(notifyReplyTaskMock).toHaveBeenCalledWith({ taskId: 'task-1' });
    expect(opened).toEqual(['task-1']);
    expect(dispose).toHaveBeenCalledTimes(1);
  });

  it('safely disables pending reply notifications in the browser preview', async () => {
    const opened: string[] = [];

    await expect(notifyReplyTask({ taskId: 'task-1' })).resolves.toMatchObject({
      success: false,
      error: 'DESKTOP_BRIDGE_UNAVAILABLE'
    });
    const removeListener = onReplyTaskOpen((payload) => opened.push(payload.taskId));
    removeListener();

    expect(opened).toEqual([]);
  });
});
