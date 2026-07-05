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
        const waitForSelector = async (selector, timeout = 15000) => {
          const started = Date.now();
          while (Date.now() - started < timeout) {
            const element = document.querySelector(selector);
            if (element) return element;
            await delay(100);
          }
          throw new Error('missing selector: ' + selector);
        };
        const clickFirst = async (selector) => {
          const element = await waitForSelector(selector);
          element.click();
          await delay(150);
          return element;
        };
        const assertActiveFollowupTab = (index) => {
          const tabs = [...document.querySelectorAll('.followup-panel .tab-button')];
          if (!tabs[index]?.classList.contains('active')) {
            throw new Error('followup active tab mismatch: ' + index);
          }
        };
        const assertDesktopSmoke = async () => {
          await waitForSelector('.workbench-panel');
          await waitForSelector('.recognition');
          await waitForSelector('.followup-panel');
          await waitForSelector('.customer-panel');
          await waitForSelector('.reply-panel');
          const refreshButtons = [...document.querySelectorAll('.workbench-panel button, .followup-panel button')]
            .filter((button) => !button.disabled);
          if (refreshButtons.length < 2) {
            throw new Error('desktop panels expose too few enabled buttons');
          }
          refreshButtons[0].click();
          refreshButtons[1].click();
          await delay(300);
          const followupTabs = [...document.querySelectorAll('.followup-panel .tab-button')];
          if (followupTabs.length !== 4) {
            throw new Error('followup tab count mismatch: ' + followupTabs.length);
          }
          for (const [index, tab] of followupTabs.entries()) {
            tab.click();
            await delay(50);
            assertActiveFollowupTab(index);
          }
          await clickFirst('.recognition .toolbar .secondary');
          const textForm = await waitForSelector('.recognition .two-box');
          if (!textForm.querySelector('input') || !textForm.querySelector('textarea')) {
            throw new Error('recognition text mode missing inputs');
          }
          const customerSearch = await waitForSelector('.customer-panel .search-row input');
          setValue(customerSearch, '18800001111');
          await clickFirst('.customer-panel .search-row button');
          await delay(500);
          const quickActionButtons = [...document.querySelectorAll('.workbench-panel .quick-actions button')];
          if (quickActionButtons.length < 3) {
            throw new Error('workbench quick action count mismatch: ' + quickActionButtons.length);
          }
          const linkButtons = [...document.querySelectorAll('.workbench-panel .section-inline-head .link-button')];
          if (linkButtons.length < 2) {
            throw new Error('workbench view-all links missing');
          }
          linkButtons[0].click();
          await delay(150);
          if (![0, 1].some((index) => [...document.querySelectorAll('.followup-panel .tab-button')][index]?.classList.contains('active'))) {
            throw new Error('workbench followup view-all did not select a due tab');
          }
          linkButtons[1].click();
          await delay(150);
          assertActiveFollowupTab(3);
          quickActionButtons[0].click();
          await delay(150);
          await waitForSelector('.recognition .loading-skeleton, .recognition .two-box');
          quickActionButtons[2].click();
          await delay(150);
          const workbenchToast = document.querySelector('.workbench-panel .banner')?.textContent ?? '';
          if (!workbenchToast.trim()) {
            throw new Error('batch template quick action did not show guidance');
          }
          quickActionButtons[1].click();
          const quickInput = await waitForSelector('.quick-search-overlay .quick-search-input');
          setValue(quickInput, 'smoke');
          await delay(350);
          const quickFilters = [...document.querySelectorAll('.quick-search-overlay .quick-filter button')];
          if (quickFilters.length < 4) {
            throw new Error('quick-search filter count mismatch: ' + quickFilters.length);
          }
          for (const [index, filter] of quickFilters.entries()) {
            filter.click();
            await delay(50);
            const currentFilters = [...document.querySelectorAll('.quick-search-overlay .quick-filter button')];
            if (!currentFilters[index]?.classList.contains('active')) {
              throw new Error('quick-search filter did not become active');
            }
          }
          return true;
        };
        const hasLoginForm = () => Boolean(inputByLabel('API 地址') && inputByLabel('账号') && inputByLabel('密码'));
        const started = Date.now();
        while (
          !hasLoginForm()
          && !document.querySelector('.ops-admin-shell')
          && !document.querySelector('.desktop-sidebar')
          && Date.now() - started < 15000
        ) {
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
        if (document.querySelector('.desktop-sidebar')) {
          findButton('管理后台').click();
        }
        await waitForSelector('.ops-admin-shell');
        await waitForText('AI 与 Skill 配置');
        const forbiddenAdminText = ['请求体 JSON', '目标 ID', 'GET /admin', 'POST /admin', 'PUT /admin', 'DELETE /admin'];
        for (const text of forbiddenAdminText) {
          if (document.querySelector('.ops-admin-shell')?.textContent.includes(text)) {
            throw new Error('production admin exposes debug text: ' + text);
          }
        }
        if (document.querySelector('.admin-read-panel') || document.querySelector('.admin-action-panel')) {
          throw new Error('production admin rendered debug console panels');
        }
        const expectedAdminSections = ['AI 与 Skill 配置', '数据源与内容', '组织、规则与标签', '分析与系统运营'];
        const navLabels = [...document.querySelectorAll('.ops-admin-nav span')]
          .map((item) => item.textContent.trim())
          .filter(Boolean);
        if (JSON.stringify(navLabels) !== JSON.stringify(expectedAdminSections)) {
          throw new Error('admin section labels mismatch: ' + JSON.stringify(navLabels));
        }
        const sectionMarkers = {
          'AI 与 Skill 配置': ['Skill 场景绑定', 'Skill 环境', '识图环境', 'Prompt 与规则'],
          '数据源与内容': ['客户数据源', '字段映射', 'CSV 导入', '速搜内容'],
          '组织、规则与标签': ['账号与权限', '跟进规则', '标签与分层'],
          '分析与系统运营': ['运营分析看板', '桌面版本', '系统公告', '审计日志', '系统健康']
        };
        for (const section of expectedAdminSections) {
          findButton(section).click();
          await waitForText(section);
          for (const marker of sectionMarkers[section]) {
            await waitForText(marker);
          }
          const panels = [...document.querySelectorAll('.ops-panel')];
          if (!panels.length) {
            throw new Error('section has no business panels: ' + section);
          }
          const emptyStates = [...document.querySelectorAll('.ops-empty')];
          if (!panels.length && !emptyStates.length) {
            throw new Error('section lacks panel or empty-state coverage: ' + section);
          }
          const filterInputs = [...document.querySelectorAll('.ops-filter-bar input, .ops-filter-bar select')];
          if ((section === '数据源与内容' || section === '组织、规则与标签' || section === '分析与系统运营') && !filterInputs.length) {
            throw new Error('section missing filters: ' + section);
          }
        }
        findButton('AI 与 Skill 配置').click();
        await waitForText('Skill 场景绑定');
        findButton('新增 Skill 绑定').click();
        const drawer = await waitForSelector('.ops-drawer');
        if (drawer.textContent.includes('请求体 JSON') || drawer.textContent.includes('目标 ID')) {
          throw new Error('admin drawer exposes debug form text');
        }
        if (!drawer.querySelector('select') || !drawer.querySelector('input')) {
          throw new Error('admin drawer missing business form controls');
        }
        findButton('关闭').click();
        await delay(100);
        findButton('桌面工作台').click();
        await waitForSelector('.desktop-sidebar');
        await waitForText('工作台');
        await assertDesktopSmoke();
        findButton('管理后台').click();
        await waitForSelector('.ops-admin-shell');
        findButton('退出').click();
        await waitForSelector('.login-panel');
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
