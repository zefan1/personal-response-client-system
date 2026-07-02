import { contextBridge, ipcRenderer } from 'electron';

type ScreenshotResult = {
  success: boolean;
  imageBase64?: string;
  width?: number;
  height?: number;
  windowTitle?: string;
  error?: 'NO_WECHAT_WINDOW' | 'CAPTURE_FAILED';
  message?: string;
};

type ClipboardImagePayload = {
  imageBase64: string;
  md5: string;
  width: number;
  height: number;
};

const api = {
  captureScreenshot: (): Promise<ScreenshotResult> => ipcRenderer.invoke('screenshot:capture'),
  writeClipboardText: (text: string): Promise<{ success: boolean; error?: string }> => ipcRenderer.invoke('clipboard:write-text', { text }),
  onClipboardImage: (callback: (payload: ClipboardImagePayload) => void) => {
    const listener = (_: Electron.IpcRendererEvent, payload: ClipboardImagePayload) => callback(payload);
    ipcRenderer.on('clipboard:new-image', listener);
    return () => ipcRenderer.removeListener('clipboard:new-image', listener);
  }
};

contextBridge.exposeInMainWorld('desktopBridge', api);

export type DesktopBridge = typeof api;
