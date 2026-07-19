// @vitest-environment node
import { describe, expect, it, vi } from 'vitest';
import {
  captureForegroundWindow,
  parseElectronWindowId,
  type CaptureDependencies,
  type CaptureSource
} from './foregroundWindowCapture.js';

function image(bytes: string, width = 900, height = 700) {
  return {
    toPNG: () => Buffer.from(bytes),
    getSize: () => ({ width, height })
  };
}

function source(id: string, name: string, bytes: string): CaptureSource {
  return { id, name, thumbnail: image(bytes) };
}

function dependencies(overrides: Partial<CaptureDependencies> = {}): CaptureDependencies {
  const state = { visible: true, focused: true, alwaysOnTop: true };
  return {
    assistantWindow: {
      isVisible: () => state.visible,
      isFocused: () => state.focused,
      isAlwaysOnTop: () => state.alwaysOnTop,
      hide: vi.fn(() => { state.visible = false; state.focused = false; }),
      show: vi.fn(() => { state.visible = true; }),
      showInactive: vi.fn(() => { state.visible = true; }),
      focus: vi.fn(() => { state.focused = true; }),
      setAlwaysOnTop: vi.fn((value: boolean) => { state.alwaysOnTop = value; }),
      getNativeWindowHandle: () => Buffer.from([11, 0, 0, 0])
    },
    getActiveWindow: vi.fn()
      .mockResolvedValueOnce({ id: 11, title: '私域辅助系统', ownerName: 'electron.exe', bounds: { width: 420, height: 760 } })
      .mockResolvedValueOnce({ id: 77, title: '抖音企业号', ownerName: 'msedge.exe', bounds: { width: 1400, height: 900 } }),
    getSources: vi.fn(async (types) => types[0] === 'window'
      ? [source('window:77:0', '抖音企业号', 'window-image')]
      : [source('screen:0:0', 'Screen 1', 'screen-image')]),
    delay: vi.fn(async () => undefined),
    minImageDimension: 200,
    ...overrides
  };
}

describe('foregroundWindowCapture', () => {
  it('parses Electron native window source ids', () => {
    expect(parseElectronWindowId('window:77:0')).toBe(77);
    expect(parseElectronWindowId('screen:0:0')).toBeNull();
  });

  it('hides the assistant and captures the complete foreground window', async () => {
    const deps = dependencies();

    const result = await captureForegroundWindow(deps);

    expect(result).toMatchObject({
      success: true,
      captureMode: 'FOREGROUND_WINDOW',
      imageBase64: Buffer.from('window-image').toString('base64'),
      width: 900,
      height: 700
    });
    expect(deps.assistantWindow.hide).toHaveBeenCalledOnce();
    expect(deps.assistantWindow.show).toHaveBeenCalledOnce();
    expect(deps.assistantWindow.focus).toHaveBeenCalledOnce();
  });

  it('uses a hidden-screen fallback when no window source matches', async () => {
    const deps = dependencies({
      getSources: vi.fn(async (types) => types[0] === 'window'
        ? []
        : [source('screen:0:0', 'Screen 1', 'screen-image')])
    });

    await expect(captureForegroundWindow(deps)).resolves.toMatchObject({
      success: true,
      captureMode: 'SCREEN_FALLBACK',
      imageBase64: Buffer.from('screen-image').toString('base64')
    });
  });

  it('restores the assistant after capture throws', async () => {
    const deps = dependencies({
      getSources: vi.fn(async () => { throw new Error('capture unavailable'); })
    });

    await expect(captureForegroundWindow(deps)).resolves.toMatchObject({
      success: false,
      error: 'CAPTURE_FAILED'
    });
    expect(deps.assistantWindow.show).toHaveBeenCalledOnce();
    expect(deps.assistantWindow.focus).toHaveBeenCalledOnce();
  });
});
