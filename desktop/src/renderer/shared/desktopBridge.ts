export type BridgeResult = {
  success: boolean;
  error?: string;
  imageBase64?: string;
  width?: number;
  height?: number;
  captureMode?: 'FOREGROUND_WINDOW' | 'SCREEN_FALLBACK';
  message?: string;
  url?: string;
};

export type AlwaysOnTopResult = {
  success: boolean;
  alwaysOnTop: boolean;
  error?: string;
  message?: string;
};

type ClipboardImagePayload = {
  imageBase64: string;
  md5: string;
  width: number;
  height: number;
};

export async function writeClipboardText(text: string): Promise<BridgeResult> {
  if (window.desktopBridge) {
    return window.desktopBridge.writeClipboardText(text);
  }
  if (navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(text);
      return { success: true };
    } catch {
      return { success: fallbackCopyText(text) };
    }
  }
  return { success: fallbackCopyText(text) };
}

export async function writeClipboardImage(imageUrl: string): Promise<BridgeResult> {
  if (window.desktopBridge) {
    return window.desktopBridge.writeClipboardImage(imageUrl);
  }
  return writeClipboardText(imageUrl);
}

export async function captureScreenshot(): Promise<BridgeResult> {
  if (window.desktopBridge) {
    return window.desktopBridge.captureScreenshot();
  }
  return { success: false, error: 'DESKTOP_BRIDGE_UNAVAILABLE' };
}

export async function openAdminConsole(url: string): Promise<BridgeResult> {
  if (window.desktopBridge?.openAdminConsole) {
    return window.desktopBridge.openAdminConsole(url);
  }
  if (window.desktopBridge || isElectronUserAgent()) {
    return {
      success: false,
      error: 'DESKTOP_BRIDGE_STALE',
      message: '应用桥接未更新，请重启桌面端后再打开管理后台'
    };
  }
  const opened = window.open(url, '_blank', 'noopener,noreferrer');
  return { success: Boolean(opened) };
}

export async function toggleAlwaysOnTop(): Promise<AlwaysOnTopResult> {
  if (window.desktopBridge?.toggleAlwaysOnTop) {
    return window.desktopBridge.toggleAlwaysOnTop();
  }
  return { success: false, alwaysOnTop: false, error: 'DESKTOP_BRIDGE_UNAVAILABLE' };
}

export async function getAlwaysOnTop(): Promise<AlwaysOnTopResult> {
  if (window.desktopBridge?.getAlwaysOnTop) {
    return window.desktopBridge.getAlwaysOnTop();
  }
  return { success: false, alwaysOnTop: false, error: 'DESKTOP_BRIDGE_UNAVAILABLE' };
}

function isElectronUserAgent(): boolean {
  return navigator.userAgent.includes('Electron');
}

export function onClipboardImage(listener: (payload: ClipboardImagePayload) => void): () => void {
  if (window.desktopBridge) {
    return window.desktopBridge.onClipboardImage(listener);
  }
  return () => undefined;
}

export function onQuickSearchShow(listener: () => void): () => void {
  if (window.desktopBridge) {
    return window.desktopBridge.onQuickSearchShow(listener);
  }
  return () => undefined;
}

export function onQuickSearchHide(listener: () => void): () => void {
  if (window.desktopBridge) {
    return window.desktopBridge.onQuickSearchHide(listener);
  }
  return () => undefined;
}

function fallbackCopyText(text: string): boolean {
  const textarea = document.createElement('textarea');
  textarea.value = text;
  textarea.setAttribute('readonly', 'true');
  textarea.style.position = 'fixed';
  textarea.style.opacity = '0';
  document.body.appendChild(textarea);
  textarea.select();
  try {
    return document.execCommand('copy');
  } finally {
    document.body.removeChild(textarea);
  }
}
