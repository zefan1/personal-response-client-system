type BridgeResult = {
  success: boolean;
  error?: string;
  imageBase64?: string;
  message?: string;
  url?: string;
};

type AlwaysOnTopResult = {
  success: boolean;
  alwaysOnTop: boolean;
  error?: string;
};

type OnlineStatusPayload = {
  online: boolean;
  type?: string;
};

type ClipboardImagePayload = {
  imageBase64: string;
  md5: string;
  width: number;
  height: number;
};

type DesktopBridge = {
  captureScreenshot: () => Promise<BridgeResult>;
  writeClipboardText: (text: string) => Promise<BridgeResult>;
  writeClipboardImage: (imageUrl: string) => Promise<BridgeResult>;
  openAdminConsole: (url?: string) => Promise<BridgeResult>;
  toggleAlwaysOnTop: () => Promise<AlwaysOnTopResult>;
  getAlwaysOnTop: () => Promise<AlwaysOnTopResult>;
  getOnlineStatus: () => Promise<OnlineStatusPayload>;
  onOnlineStatusChange: (callback: (payload: OnlineStatusPayload) => void) => () => void;
  hideQuickSearch: () => Promise<{ success: boolean }>;
  onQuickSearchShow: (callback: () => void) => () => void;
  onQuickSearchHide: (callback: () => void) => () => void;
  onClipboardImage: (callback: (payload: ClipboardImagePayload) => void) => () => void;
};

declare global {
  interface Window {
    desktopBridge?: DesktopBridge;
  }
}

export {};
