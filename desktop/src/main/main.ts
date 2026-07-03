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
const smokeAutoQuit = process.env.PDA_ELECTRON_SMOKE_AUTO_QUIT !== '0';
const rendererSmoke = process.env.PDA_RENDERER_SMOKE === '1';
const rendererSmokeApiBaseUrl = process.env.PDA_SMOKE_API_BASE_URL ?? 'http://localhost:8080';
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
      if (rendererSmoke && mainWindow) {
        void runRendererSmoke(mainWindow);
        return;
      }
      if (smokeAutoQuit) {
        setTimeout(() => app.quit(), 50);
      }
    });
  }
}

async function runRendererSmoke(window: BrowserWindow) {
  try {
    await window.webContents.executeJavaScript(`
      (async () => {
        const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
        const findButton = (text) => [...document.querySelectorAll('button')].find((item) => item.textContent.includes(text));
        const inputByLabel = (text) => {
          const label = [...document.querySelectorAll('label')].find((item) => item.textContent.includes(text));
          return label ? label.querySelector('input,select,textarea') : null;
        };
        const setValue = (element, value) => {
          element.value = value;
          element.dispatchEvent(new Event('input', { bubbles: true }));
          element.dispatchEvent(new Event('change', { bubbles: true }));
        };
        const waitForText = async (text, timeout = 15000) => {
          const started = Date.now();
          while (Date.now() - started < timeout) {
            if (document.body.innerText.includes(text)) return;
            await delay(100);
          }
          throw new Error('missing text: ' + text);
        };
        const hasLoginForm = () => Boolean(inputByLabel('API 地址') && inputByLabel('账号') && inputByLabel('密码'));
        const started = Date.now();
        while (!hasLoginForm() && !document.body.innerText.includes('健康与系统配置') && Date.now() - started < 15000) {
          await delay(100);
        }
        if (hasLoginForm()) {
          setValue(inputByLabel('API 地址'), ${JSON.stringify(rendererSmokeApiBaseUrl)});
          setValue(inputByLabel('账号'), 'admin');
          setValue(inputByLabel('密码'), 'admin123');
          const mode = inputByLabel('入口');
          if (mode) setValue(mode, 'admin');
          findButton('登录').click();
        }
        await waitForText('健康与系统配置');
        for (const section of ['技能场景绑定', 'AI 与外部环境', '数据源与字段映射', '账号权限', '公告、版本、审计']) {
          findButton(section).click();
          await waitForText(section);
          await waitForText('数据读取');
          await waitForText('操作入口');
        }
        findButton('工作台').click();
        await waitForText('桌面工作台');
        return true;
      })();
    `);
    console.log('renderer_smoke=passed');
    app.quit();
  } catch (error) {
    console.error('renderer_smoke=failed', error);
    app.exit(1);
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
