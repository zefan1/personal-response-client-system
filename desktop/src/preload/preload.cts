import { contextBridge, ipcRenderer } from 'electron';

type ScreenshotResult = {
  success: boolean;
  imageBase64?: string;
  width?: number;
  height?: number;
  screenTitle?: string;
  error?: 'CAPTURE_FAILED' | 'DESKTOP_BRIDGE_UNAVAILABLE';
  message?: string;
};

type ClipboardImagePayload = {
  imageBase64: string;
  md5: string;
  width: number;
  height: number;
};

type OnlineStatusPayload = {
  online: boolean;
  type?: string;
};

type AlwaysOnTopResult = {
  success: boolean;
  alwaysOnTop: boolean;
  error?: string;
};

const api = {
  captureScreenshot: (): Promise<ScreenshotResult> => ipcRenderer.invoke('screenshot:capture'),
  writeClipboardText: (text: string): Promise<{ success: boolean; error?: string }> => ipcRenderer.invoke('clipboard:write-text', { text }),
  writeClipboardImage: (imageUrl: string): Promise<{ success: boolean; error?: string; message?: string }> => ipcRenderer.invoke('clipboard:write-image', { imageUrl }),
  openAdminConsole: (url?: string): Promise<{ success: boolean; error?: string; message?: string; url?: string }> => ipcRenderer.invoke('admin:open-external', { url }),
  toggleAlwaysOnTop: (): Promise<AlwaysOnTopResult> => ipcRenderer.invoke('window:toggle-always-on-top'),
  getAlwaysOnTop: (): Promise<AlwaysOnTopResult> => ipcRenderer.invoke('window:get-always-on-top'),
  getOnlineStatus: (): Promise<OnlineStatusPayload> => ipcRenderer.invoke('app:get-online-status'),
  onOnlineStatusChange: (callback: (payload: OnlineStatusPayload) => void) => {
    const listener = (_: Electron.IpcRendererEvent, payload: OnlineStatusPayload) => callback(payload);
    ipcRenderer.on('app:online-status', listener);
    return () => ipcRenderer.removeListener('app:online-status', listener);
  },
  hideQuickSearch: (): Promise<{ success: boolean }> => ipcRenderer.invoke('quicksearch:hide'),
  onQuickSearchShow: (callback: () => void) => {
    const listener = () => callback();
    ipcRenderer.on('quicksearch:show', listener);
    return () => ipcRenderer.removeListener('quicksearch:show', listener);
  },
  onQuickSearchHide: (callback: () => void) => {
    const listener = () => callback();
    ipcRenderer.on('quicksearch:hide', listener);
    return () => ipcRenderer.removeListener('quicksearch:hide', listener);
  },
  onClipboardImage: (callback: (payload: ClipboardImagePayload) => void) => {
    const listener = (_: Electron.IpcRendererEvent, payload: ClipboardImagePayload) => callback(payload);
    ipcRenderer.on('clipboard:new-image', listener);
    return () => ipcRenderer.removeListener('clipboard:new-image', listener);
  }
};

contextBridge.exposeInMainWorld('desktopBridge', api);

export type DesktopBridge = typeof api;
