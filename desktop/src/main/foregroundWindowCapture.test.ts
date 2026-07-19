// @vitest-environment node
import { describe, expect, it, vi } from 'vitest';
import {
  captureForegroundWindow,
  parseElectronWindowId,
  resolveDisplayIdFromPhysicalPoint,
  verifyForegroundCaptureNativeBinding,
  type CaptureDependencies,
  type CaptureSource
} from './foregroundWindowCapture.js';

function image(bytes: string, width = 900, height = 700) {
  return {
    toPNG: () => Buffer.from(bytes),
    getSize: () => ({ width, height })
  };
}

function source(
  id: string,
  name: string,
  bytes: string,
  options: { width?: number; height?: number; displayId?: string } = {}
): CaptureSource {
  return {
    id,
    name,
    displayId: options.displayId,
    thumbnail: image(bytes, options.width ?? 900, options.height ?? 700)
  };
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
    getDisplayId: vi.fn(() => '0'),
    delay: vi.fn(async () => undefined),
    minImageDimension: 200,
    ...overrides
  };
}

describe('foregroundWindowCapture', () => {
  it('loads the foreground capture native binding and propagates load failures', async () => {
    const loader = vi.fn().mockResolvedValue(undefined);
    await expect(verifyForegroundCaptureNativeBinding(loader)).resolves.toBeUndefined();
    expect(loader).toHaveBeenCalledOnce();

    await expect(verifyForegroundCaptureNativeBinding(async () => {
      throw new Error('native binding ABI mismatch');
    })).rejects.toThrow('native binding ABI mismatch');
  });

  it('converts physical window coordinates to DIP before resolving the display', () => {
    const screenToDipPoint = vi.fn(() => ({ x: 1600, y: 240 }));
    const getDisplayNearestPoint = vi.fn(() => ({ id: 2 }));

    expect(resolveDisplayIdFromPhysicalPoint(
      { x: 2400, y: 360 },
      { screenToDipPoint, getDisplayNearestPoint }
    )).toBe('2');
    expect(screenToDipPoint).toHaveBeenCalledWith({ x: 2400, y: 360 });
    expect(getDisplayNearestPoint).toHaveBeenCalledWith({ x: 1600, y: 240 });
  });

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

  it('selects the screen containing the active window for fallback capture', async () => {
    const deps = dependencies({
      getActiveWindow: vi.fn().mockResolvedValue({
        id: 77,
        title: '抖音企业号',
        ownerName: 'msedge.exe',
        bounds: { x: 2200, y: 120, width: 1400, height: 900 }
      }),
      getSources: vi.fn(async (types) => types[0] === 'window'
        ? []
        : [
            source('screen:0:0', 'Screen 1', 'primary-screen', { displayId: '0' }),
            source('screen:1:0', 'Screen 2', 'active-screen', { displayId: '1' })
          ]),
      getDisplayId: vi.fn(() => '1')
    });

    await expect(captureForegroundWindow(deps)).resolves.toMatchObject({
      success: true,
      captureMode: 'SCREEN_FALLBACK',
      imageBase64: Buffer.from('active-screen').toString('base64')
    });
  });

  it('does not select an ambiguous duplicate title when source ids do not match', async () => {
    const deps = dependencies({
      getSources: vi.fn(async (types) => types[0] === 'window'
        ? [
            source('window:90:0', '抖音企业号', 'wrong-a'),
            source('window:91:0', '抖音企业号', 'wrong-b')
          ]
        : [source('screen:0:0', 'Screen 1', 'screen-image')])
    });

    await expect(captureForegroundWindow(deps)).resolves.toMatchObject({
      success: true,
      captureMode: 'SCREEN_FALLBACK',
      imageBase64: Buffer.from('screen-image').toString('base64')
    });
  });

  it('uses aspect ratio to disambiguate a unique title match', async () => {
    const deps = dependencies({
      getActiveWindow: vi.fn().mockResolvedValue({
        id: 77,
        title: '抖音企业号',
        ownerName: 'msedge.exe',
        bounds: { x: 0, y: 0, width: 1400, height: 900 }
      }),
      getSources: vi.fn(async (types) => types[0] === 'window'
        ? [
            source('window:90:0', '抖音企业号', 'wrong-aspect', { width: 900, height: 900 }),
            source('window:91:0', '抖音企业号', 'matching-aspect', { width: 1400, height: 900 })
          ]
        : [source('screen:0:0', 'Screen 1', 'screen-image')])
    });

    await expect(captureForegroundWindow(deps)).resolves.toMatchObject({
      success: true,
      captureMode: 'FOREGROUND_WINDOW',
      imageBase64: Buffer.from('matching-aspect').toString('base64')
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
