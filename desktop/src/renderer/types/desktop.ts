type BridgeResult = {
  success: boolean;
  error?: string;
  imageBase64?: string;
  width?: number;
  height?: number;
  captureMode?: 'FOREGROUND_WINDOW' | 'SCREEN_FALLBACK';
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

type ReplyTaskNotificationPayload = {
  taskId: string;
  title?: string;
  body?: string;
};

type ReplyTaskOpenPayload = {
  taskId: string;
};

type DesktopBridge = {
  captureScreenshot: () => Promise<BridgeResult>;
  writeClipboardText: (text: string) => Promise<BridgeResult>;
  writeClipboardImage: (imageUrl: string) => Promise<BridgeResult>;
  openAdminConsole: (url?: string) => Promise<BridgeResult>;
  openAssignmentTable: (url: string) => Promise<BridgeResult>;
  toggleAlwaysOnTop: () => Promise<AlwaysOnTopResult>;
  getAlwaysOnTop: () => Promise<AlwaysOnTopResult>;
  getOnlineStatus: () => Promise<OnlineStatusPayload>;
  onOnlineStatusChange: (callback: (payload: OnlineStatusPayload) => void) => () => void;
  hideQuickSearch: () => Promise<{ success: boolean }>;
  onQuickSearchShow: (callback: () => void) => () => void;
  onQuickSearchHide: (callback: () => void) => () => void;
  onClipboardImage: (callback: (payload: ClipboardImagePayload) => void) => () => void;
  notifyReplyTask: (payload: ReplyTaskNotificationPayload) => Promise<BridgeResult>;
  onReplyTaskOpen: (callback: (payload: ReplyTaskOpenPayload) => void) => () => void;
};

declare global {
  interface Window {
    desktopBridge?: DesktopBridge;
  }
}

export {};
