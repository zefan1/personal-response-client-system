export type BridgeResult = {
  success: boolean;
  error?: string;
  imageBase64?: string;
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
