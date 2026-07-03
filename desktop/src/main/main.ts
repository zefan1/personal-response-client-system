import { app, BrowserWindow, clipboard, desktopCapturer, globalShortcut, ipcMain, nativeImage, net } from 'electron';
import crypto from 'node:crypto';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

type ClipboardHistoryItem = {
  md5: string;
  width: number;
  height: number;
  timestamp: number;
};

type OnlineStatusPayload = {
  online: boolean;
  type: 'unknown';
};

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const isDev = process.env.VITE_DEV_SERVER_URL !== undefined;
const isSmoke = process.env.PDA_ELECTRON_SMOKE === '1';
const clipboardImageHistory: ClipboardHistoryItem[] = [];
let clipboardPollTimer: NodeJS.Timeout | null = null;
let onlineStatusPollTimer: NodeJS.Timeout | null = null;
let lastBroadcastOnlineStatus: boolean | null = null;
let mainWindow: BrowserWindow | null = null;

const DESKTOP_DEFAULTS = {
  clipboardPollIntervalMs: 500,
  clipboardMd5CacheSize: 5,
  clipboardMinImageDimension: 200,
  clipboardImageTextCoverMs: 2000,
  requestTotalTimeoutMs: 15000,
  quickSearchShortcut: 'CommandOrControl+Shift+F'
};

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 420,
    height: 760,
    minWidth: 360,
    minHeight: 560,
    title: '私域辅助系统',
    webPreferences: {
      preload: path.join(__dirname, '../preload/preload.js'),
      contextIsolation: true,
      nodeIntegration: false
    }
  });

  if (isDev) {
    void mainWindow.loadURL(process.env.VITE_DEV_SERVER_URL as string);
  } else {
    void mainWindow.loadFile(path.join(__dirname, '../renderer/index.html'));
  }
  if (isSmoke) {
    mainWindow.webContents.once('did-finish-load', () => {
      app.quit();
    });
  }
}

app.whenReady().then(() => {
  registerOnlineStatusIpc();
  registerScreenshotCapture();
  registerClipboardWriteText();
  registerClipboardWriteImage();
  registerQuickSearchIpc();
  createWindow();
  broadcastOnlineStatus();
  startOnlineStatusPolling();
  registerQuickSearchShortcut();
  startClipboardPolling();
});

app.on('window-all-closed', () => {
  stopClipboardPolling();
  stopOnlineStatusPolling();
  if (process.platform !== 'darwin') {
    app.quit();
  }
});

app.on('will-quit', () => {
  stopOnlineStatusPolling();
  globalShortcut.unregisterAll();
});

function getOnlineStatus(): OnlineStatusPayload {
  return {
    online: net.isOnline(),
    type: 'unknown'
  };
}

function registerOnlineStatusIpc() {
  ipcMain.handle('app:get-online-status', () => getOnlineStatus());
}

function broadcastOnlineStatus() {
  const status = getOnlineStatus();
  lastBroadcastOnlineStatus = status.online;
  mainWindow?.webContents.send('app:online-status', status);
}

function startOnlineStatusPolling() {
  stopOnlineStatusPolling();
  lastBroadcastOnlineStatus = net.isOnline();
  onlineStatusPollTimer = setInterval(() => {
    const current = net.isOnline();
    if (current !== lastBroadcastOnlineStatus) {
      broadcastOnlineStatus();
    }
  }, 1000);
}

function stopOnlineStatusPolling() {
  if (onlineStatusPollTimer) {
    clearInterval(onlineStatusPollTimer);
    onlineStatusPollTimer = null;
  }
}

function registerScreenshotCapture() {
  ipcMain.handle('screenshot:capture', async () => {
    try {
      const sources = await desktopCapturer.getSources({
        types: ['window'],
        thumbnailSize: { width: 1920, height: 1080 },
        fetchWindowIcons: true
      });
      const candidates = sources.filter((source) => {
        const name = source.name.toLowerCase();
        const size = source.thumbnail.getSize();
        return (name.includes('wechat') || name.includes('weixin') || name.includes('微信') || name.includes('企业微信') || name.includes('wxwork'))
          && size.width >= DESKTOP_DEFAULTS.clipboardMinImageDimension
          && size.height >= DESKTOP_DEFAULTS.clipboardMinImageDimension;
      });
      const selected = candidates[0];
      if (!selected) {
        return { success: false, error: 'NO_WECHAT_WINDOW', message: 'No WeChat or WeCom window detected' };
      }
      const png = selected.thumbnail.toPNG();
      const size = selected.thumbnail.getSize();
      if (!png.length || size.width < DESKTOP_DEFAULTS.clipboardMinImageDimension || size.height < DESKTOP_DEFAULTS.clipboardMinImageDimension) {
        return { success: false, error: 'CAPTURE_FAILED', message: 'Captured window image is empty' };
      }
      return {
        success: true,
        imageBase64: png.toString('base64'),
        width: size.width,
        height: size.height,
        windowTitle: selected.name
      };
    } catch (error) {
      return { success: false, error: 'CAPTURE_FAILED', message: 'Screenshot capture failed' };
    }
  });
}

function registerClipboardWriteText() {
  ipcMain.handle('clipboard:write-text', (_event, payload: { text?: string }) => {
    const text = payload.text ?? '';
    if (!text.trim()) {
      return { success: false, error: 'EMPTY_TEXT' };
    }
    clipboard.writeText(text);
    return { success: true };
  });
}

function registerClipboardWriteImage() {
  ipcMain.handle('clipboard:write-image', async (_event, payload: { imageUrl?: string }) => {
    try {
      if (!payload.imageUrl) {
        return { success: false, error: 'IMAGE_LOAD_FAILED', message: 'Missing imageUrl' };
      }
      const response = await net.fetch(payload.imageUrl);
      if (!response.ok) {
        return { success: false, error: 'IMAGE_LOAD_FAILED', message: 'Image download failed' };
      }
      const buffer = Buffer.from(await response.arrayBuffer());
      const image = nativeImage.createFromBuffer(buffer);
      if (image.isEmpty()) {
        return { success: false, error: 'IMAGE_LOAD_FAILED', message: 'Image decode failed' };
      }
      clipboard.writeImage(image);
      return { success: true };
    } catch {
      return { success: false, error: 'IMAGE_LOAD_FAILED', message: 'Image load failed' };
    }
  });
}

function registerQuickSearchIpc() {
  ipcMain.handle('quicksearch:hide', () => {
    mainWindow?.webContents.send('quicksearch:hide');
    return { success: true };
  });
}

function registerQuickSearchShortcut() {
  globalShortcut.register(DESKTOP_DEFAULTS.quickSearchShortcut, () => {
    if (mainWindow?.isMinimized()) {
      mainWindow.restore();
    }
    mainWindow?.show();
    mainWindow?.focus();
    mainWindow?.webContents.send('quicksearch:show');
  });
}

function startClipboardPolling() {
  stopClipboardPolling();
  clipboardPollTimer = setInterval(() => {
    const image = clipboard.readImage();
    if (image.isEmpty()) {
      return;
    }
    const size = image.getSize();
    if (size.width < DESKTOP_DEFAULTS.clipboardMinImageDimension || size.height < DESKTOP_DEFAULTS.clipboardMinImageDimension) {
      return;
    }
    const png = image.toPNG();
    const md5 = crypto.createHash('md5').update(png).digest('hex');
    if (clipboardImageHistory.some((item) => item.md5 === md5)) {
      return;
    }
    clipboardImageHistory.unshift({ md5, width: size.width, height: size.height, timestamp: Date.now() });
    clipboardImageHistory.splice(DESKTOP_DEFAULTS.clipboardMd5CacheSize);
    mainWindow?.webContents.send('clipboard:new-image', {
      imageBase64: png.toString('base64'),
      md5,
      width: size.width,
      height: size.height
    });
  }, DESKTOP_DEFAULTS.clipboardPollIntervalMs);
}

function stopClipboardPolling() {
  if (clipboardPollTimer) {
    clearInterval(clipboardPollTimer);
    clipboardPollTimer = null;
  }
}
