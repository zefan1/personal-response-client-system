export type CaptureMode = 'FOREGROUND_WINDOW' | 'SCREEN_FALLBACK';

export type ScreenshotCaptureResult = {
  success: boolean;
  imageBase64?: string;
  width?: number;
  height?: number;
  captureMode?: CaptureMode;
  error?: 'CAPTURE_FAILED';
  message?: string;
};

export type ActiveWindowInfo = {
  id: number;
  title: string;
  ownerName: string;
  bounds: { x?: number; y?: number; width: number; height: number };
};

export type CaptureSource = {
  id: string;
  name: string;
  displayId?: string;
  thumbnail: {
    toPNG(): Buffer;
    getSize(): { width: number; height: number };
  };
};

export type CaptureDependencies = {
  assistantWindow: {
    isVisible(): boolean;
    isFocused(): boolean;
    isAlwaysOnTop(): boolean;
    hide(): void;
    show(): void;
    showInactive(): void;
    focus(): void;
    setAlwaysOnTop(value: boolean): void;
    getNativeWindowHandle(): Buffer;
  };
  getActiveWindow(): Promise<ActiveWindowInfo | undefined>;
  getSources(types: Array<'window' | 'screen'>, size: { width: number; height: number }): Promise<CaptureSource[]>;
  getDisplayId?: (point: { x: number; y: number }) => string | undefined;
  delay(ms: number): Promise<void>;
  minImageDimension: number;
};

const FOREGROUND_WAIT_ATTEMPTS = 10;
const FOREGROUND_WAIT_INTERVAL_MS = 50;
const SCREEN_THUMBNAIL_SIZE = { width: 1920, height: 1080 };

export function parseElectronWindowId(sourceId: string): number | null {
  const match = /^window:(\d+):/.exec(sourceId);
  return match ? Number(match[1]) : null;
}

export async function captureForegroundWindow(deps: CaptureDependencies): Promise<ScreenshotCaptureResult> {
  const previous = {
    visible: deps.assistantWindow.isVisible(),
    focused: deps.assistantWindow.isFocused(),
    alwaysOnTop: deps.assistantWindow.isAlwaysOnTop()
  };
  const ownId = nativeWindowId(deps.assistantWindow.getNativeWindowHandle());
  deps.assistantWindow.hide();

  try {
    const active = await waitForTargetWindow(deps, ownId);
    if (active) {
      try {
        const sources = await deps.getSources(['window'], boundedSize(active.bounds));
        const matched = matchWindowSource(sources, active);
        const result = matched && imageResult(matched, 'FOREGROUND_WINDOW', deps.minImageDimension);
        if (result) return result;
      } catch {
        // A source enumeration failure can still use the screen fallback.
      }
    }

    const screenSources = await deps.getSources(['screen'], SCREEN_THUMBNAIL_SIZE);
    const screen = selectScreenSource(screenSources, active, deps.getDisplayId);
    return screen && imageResult(screen, 'SCREEN_FALLBACK', deps.minImageDimension)
      || { success: false, error: 'CAPTURE_FAILED', message: 'No usable foreground window or screen source detected' };
  } catch {
    return { success: false, error: 'CAPTURE_FAILED', message: 'Screenshot capture failed' };
  } finally {
    restoreAssistantWindow(deps, previous);
  }
}

async function waitForTargetWindow(
  deps: CaptureDependencies,
  ownId: number | null
): Promise<ActiveWindowInfo | undefined> {
  for (let attempt = 0; attempt < FOREGROUND_WAIT_ATTEMPTS; attempt += 1) {
    const active = await deps.getActiveWindow();
    if (active && active.id !== ownId) {
      return active;
    }
    if (attempt < FOREGROUND_WAIT_ATTEMPTS - 1) {
      await deps.delay(FOREGROUND_WAIT_INTERVAL_MS);
    }
  }
  return undefined;
}

function matchWindowSource(sources: CaptureSource[], active: ActiveWindowInfo): CaptureSource | undefined {
  const exact = sources.find((source) => parseElectronWindowId(source.id) === active.id);
  if (exact) return exact;

  const title = normalizeTitle(active.title);
  if (!title) return undefined;
  const titleMatches = sources.filter((source) => normalizeTitle(source.name) === title);
  if (titleMatches.length === 0) return undefined;
  const targetAspect = aspectRatio(active.bounds.width, active.bounds.height);
  if (targetAspect === undefined) {
    return titleMatches.length === 1 ? titleMatches[0] : undefined;
  }
  const aspectMatches = titleMatches.filter((source) => {
    const size = source.thumbnail.getSize();
    const sourceAspect = aspectRatio(size.width, size.height);
    return sourceAspect !== undefined && Math.abs(sourceAspect - targetAspect) / targetAspect <= 0.08;
  });
  return aspectMatches.length === 1 ? aspectMatches[0] : undefined;
}

function selectScreenSource(
  sources: CaptureSource[],
  active: ActiveWindowInfo | undefined,
  getDisplayId: CaptureDependencies['getDisplayId']
): CaptureSource | undefined {
  if (!sources.length) return undefined;
  const point = active && {
    x: (active.bounds.x ?? 0) + active.bounds.width / 2,
    y: (active.bounds.y ?? 0) + active.bounds.height / 2
  };
  const displayId = point && getDisplayId?.(point);
  if (displayId) {
    const matching = sources.find((source) => sourceDisplayId(source) === displayId);
    if (matching) return matching;
  }
  return sources.find((item) => sourceDisplayId(item) === '0') ?? sources[0];
}

function sourceDisplayId(source: CaptureSource): string | undefined {
  if (source.displayId) return source.displayId;
  return /^screen:([^:]+):/.exec(source.id)?.[1];
}

function imageResult(
  source: CaptureSource,
  captureMode: CaptureMode,
  minImageDimension: number
): ScreenshotCaptureResult | undefined {
  const png = source.thumbnail.toPNG();
  const size = source.thumbnail.getSize();
  if (!png.length || size.width < minImageDimension || size.height < minImageDimension) {
    return undefined;
  }
  return {
    success: true,
    imageBase64: png.toString('base64'),
    width: size.width,
    height: size.height,
    captureMode
  };
}

function restoreAssistantWindow(
  deps: CaptureDependencies,
  previous: { visible: boolean; focused: boolean; alwaysOnTop: boolean }
): void {
  if (deps.assistantWindow.isAlwaysOnTop() !== previous.alwaysOnTop) {
    deps.assistantWindow.setAlwaysOnTop(previous.alwaysOnTop);
  }
  if (!previous.visible) return;
  if (previous.focused) {
    deps.assistantWindow.show();
    deps.assistantWindow.focus();
    return;
  }
  deps.assistantWindow.showInactive();
}

function nativeWindowId(handle: Buffer): number | null {
  if (!handle.length) return null;
  if (handle.length >= 8) {
    return Number(handle.readBigUInt64LE(0));
  }
  return handle.readUInt32LE(0);
}

function boundedSize(bounds: { width: number; height: number }): { width: number; height: number } {
  const maxDimension = Math.max(bounds.width, bounds.height);
  if (!Number.isFinite(maxDimension) || maxDimension <= 0) return SCREEN_THUMBNAIL_SIZE;
  const scale = Math.min(1, 1920 / maxDimension);
  return {
    width: Math.max(1, Math.round(bounds.width * scale)),
    height: Math.max(1, Math.round(bounds.height * scale))
  };
}

function aspectRatio(width: number, height: number): number | undefined {
  if (!Number.isFinite(width) || !Number.isFinite(height) || width <= 0 || height <= 0) return undefined;
  return width / height;
}

function normalizeTitle(value: string): string {
  return value.trim().replace(/\s+/g, ' ').toLocaleLowerCase();
}
