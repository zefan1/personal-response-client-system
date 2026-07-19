import { app, BrowserWindow, clipboard, desktopCapturer, globalShortcut, ipcMain, nativeImage, net, screen, shell } from 'electron';
import { activeWindow } from 'get-windows';
import crypto from 'node:crypto';
import { mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { captureForegroundWindow, resolveDisplayIdFromPhysicalPoint } from './foregroundWindowCapture.js';

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
const rendererSmokeTarget = process.env.PDA_RENDERER_SMOKE_TARGET ?? 'desktop';
const rendererSmokeApiBaseUrl = process.env.PDA_SMOKE_API_BASE_URL ?? 'http://localhost:8080';
const rendererSmokeAccessToken = process.env.PDA_RENDERER_SMOKE_ACCESS_TOKEN ?? '';
const rendererSmokeScreenshotDir = process.env.PDA_RENDERER_SMOKE_SCREENSHOT_DIR ?? '';
const smokeUserDataDir = process.env.PDA_ELECTRON_SMOKE_USER_DATA_DIR;
const clipboardImageHistory: ClipboardHistoryItem[] = [];
let clipboardPollTimer: NodeJS.Timeout | null = null;
let onlineStatusPollTimer: NodeJS.Timeout | null = null;
let lastBroadcastOnlineStatus: boolean | null = null;
let mainWindow: BrowserWindow | null = null;

if (isSmoke && smokeUserDataDir) {
  mkdirSync(smokeUserDataDir, { recursive: true });
  app.setPath('userData', smokeUserDataDir);
}

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
      ...(rendererSmoke && ['admin', 'tag-admin'].includes(rendererSmokeTarget)
        ? {}
        : { preload: path.join(__dirname, '../preload/preload.cjs') }),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  });
  mainWindow.setMenu(null);
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (isAllowedAdminConsoleUrl(url)) {
      void openExternalBrowser(adminConsoleUrl());
    }
    return { action: 'deny' };
  });

  if (isDev) {
    void mainWindow.loadURL(process.env.VITE_DEV_SERVER_URL as string);
  } else {
    void mainWindow.loadFile(path.join(__dirname, '../renderer/index.html'));
  }
  if (isSmoke) {
    mainWindow.webContents.on('did-finish-load', () => {
      if (rendererSmoke && mainWindow) {
        void (rendererSmokeTarget === 'admin'
          ? runAdminRendererSmoke(mainWindow)
          : rendererSmokeTarget === 'tag-admin'
            ? runTagManagementRendererSmoke(mainWindow)
            : runRendererSmoke(mainWindow));
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
    const result = await window.webContents.executeJavaScript(`
      (async () => {
        const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
        const findButton = (text) => [...document.querySelectorAll('button')].find((item) => item.textContent.includes(text));
        const findButtonByLabel = (text) => [...document.querySelectorAll('button')]
          .find((item) => (item.getAttribute('aria-label') || item.getAttribute('title') || '').includes(text));
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
        const waitForCondition = async (predicate, label, timeout = 15000) => {
          const started = Date.now();
          while (Date.now() - started < timeout) {
            if (await predicate()) return;
            await delay(100);
          }
          throw new Error('condition timeout: ' + label);
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
          const assertReplyCurrentTaskLayout = () => {
            const card = document.querySelector('.reply-current-task');
            if (!card) return;
            const copy = card.children[0];
            const actions = card.querySelector('.reply-current-actions');
            if (!copy || !actions) {
              throw new Error('reply current task layout missing copy or actions');
            }
            const cardRect = card.getBoundingClientRect();
            const copyRect = copy.getBoundingClientRect();
            const actionsRect = actions.getBoundingClientRect();
            if (copyRect.width < cardRect.width * 0.75) {
              throw new Error('reply current task copy column is too narrow: ' + JSON.stringify({
                cardWidth: cardRect.width,
                copyWidth: copyRect.width
              }));
            }
            if (actionsRect.top < copyRect.bottom - 1) {
              throw new Error('reply current task actions still share the copy row');
            }
          };
          const assertDesktopSmoke = async () => {
          await waitForSelector('.workbench-panel');
          await waitForSelector('.customer-panel');
          await waitForSelector('.reply-panel');
          const navLabels = [...document.querySelectorAll('.desktop-nav-button .nav-label')]
            .map((item) => item.textContent.trim());
          if (navLabels.join('|') !== '工作台|客户档案|回复助手') {
            throw new Error('desktop nav labels mismatch: ' + navLabels.join('|'));
          }
          if (document.querySelector('.global-recognize-button')) {
            throw new Error('legacy global recognize button is still rendered');
          }
          if (document.querySelector('.global-action-bar')) {
            throw new Error('legacy global action bar is still rendered');
          }
          const actionButtons = [...document.querySelectorAll('.sidebar-quick-actions button')];
          const actionLabels = [...document.querySelectorAll('.sidebar-quick-actions .action-label')]
            .map((item) => item.textContent.trim());
          if (actionLabels.join('|') !== '识别|模板|批量') {
            throw new Error('sidebar quick actions mismatch: ' + actionLabels.join('|'));
          }
          if (document.documentElement.scrollWidth > window.innerWidth + 1) {
            throw new Error('desktop has horizontal overflow');
          }
          actionButtons[2].click();
          const drawer = await waitForSelector('.task-queue-backdrop');
          if (getComputedStyle(drawer).display === 'none') {
            throw new Error('task queue drawer did not open');
          }
          const followupTabs = [...document.querySelectorAll('.followup-panel .tab-button')];
          if (followupTabs.length !== 4) {
            throw new Error('followup tab count mismatch: ' + followupTabs.length);
          }
          for (const [index, tab] of followupTabs.entries()) {
            tab.click();
            await delay(50);
            assertActiveFollowupTab(index);
          }
          const closeDrawer = [...document.querySelectorAll('.task-queue-drawer button')]
            .find((button) => (button.getAttribute('aria-label') || '').includes('关闭待办队列'));
          closeDrawer?.click();
          await delay(150);
          const replyNav = [...document.querySelectorAll('.desktop-nav-button')]
            .find((button) => button.textContent.includes('回复'));
          replyNav.click();
          await delay(150);
          const textModeButton = [...document.querySelectorAll('.reply-text-channel button')]
            .find((button) => button.textContent.includes('文字通道'));
          textModeButton.click();
          const textForm = await waitForSelector('.reply-text-channel .two-box');
          if (!textForm.querySelector('input') || !textForm.querySelector('textarea')) {
            throw new Error('reply assistant text mode missing inputs');
          }
          const customerNav = [...document.querySelectorAll('.desktop-nav-button')]
            .find((button) => button.textContent.includes('客户'));
          customerNav.click();
          await delay(150);
          const customerSearch = await waitForSelector('.customer-panel .search-row input');
          setValue(customerSearch, '18800001111');
          await clickFirst('.customer-panel .search-row button');
          await delay(500);
          const workbenchNav = [...document.querySelectorAll('.desktop-nav-button')]
            .find((button) => button.textContent.includes('工作台'));
          workbenchNav.click();
          await delay(150);
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
          actionButtons[0].click();
          await waitForCondition(() => {
            const activeNav = [...document.querySelectorAll('.desktop-nav-button')]
              .find((button) => button.classList.contains('active'));
            const activeLabel = activeNav?.querySelector('.nav-label')?.textContent ?? '';
            return document.body.innerText.includes('屏幕截图失败')
              || document.body.innerText.includes('截图失败')
              || document.body.innerText.includes('识别失败')
              || document.body.innerText.includes('请求超时')
              || activeLabel.includes('回复助手');
          }, 'global recognize completed or failed', 22000);
          const activeNav = [...document.querySelectorAll('.desktop-nav-button')]
            .find((button) => button.classList.contains('active'));
          const activeLabel = activeNav?.querySelector('.nav-label')?.textContent ?? '';
          const hasRecognizeFailure = document.body.innerText.includes('屏幕截图失败')
            || document.body.innerText.includes('截图失败')
            || document.body.innerText.includes('识别失败')
            || document.body.innerText.includes('请求超时');
          if (!activeLabel.includes('回复助手') && !hasRecognizeFailure) {
            throw new Error('global screen recognize neither focused reply assistant nor showed a failure');
          }
          assertReplyCurrentTaskLayout();
          actionButtons[1].click();
          const quickInput = await waitForSelector('.quick-search-overlay .quick-search-input');
          const quickDrawer = await waitForSelector('.quick-search-box');
          if (quickDrawer.getAttribute('aria-label') !== '模板') {
            throw new Error('quick-search drawer label mismatch: ' + quickDrawer.getAttribute('aria-label'));
          }
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
          await delay(3200);
          if (!document.querySelector('.quick-search-overlay')) {
            throw new Error('quick-search drawer auto closed unexpectedly');
          }
          const closeQuickSearch = [...document.querySelectorAll('.quick-search-box button')]
            .find((button) => (button.getAttribute('aria-label') || '').includes('关闭模板'));
          closeQuickSearch?.click();
          await waitForCondition(() => !document.querySelector('.quick-search-overlay'), 'quick-search drawer closed by icon');
          return true;
        };
        const hasLoginForm = () => Boolean(inputByLabel('API 地址') && inputByLabel('账号') && inputByLabel('密码'));
        const started = Date.now();
        while (
          !hasLoginForm()
          && !document.querySelector('.desktop-sidebar')
          && Date.now() - started < 15000
        ) {
          await delay(100);
        }
        if (hasLoginForm()) {
          localStorage.setItem('desktop_config', JSON.stringify({
            apiBaseUrl: ${JSON.stringify(rendererSmokeApiBaseUrl)},
            accessToken: ${JSON.stringify(rendererSmokeAccessToken)},
            accountRole: 'ADMIN',
            quicksearchResultLimit: 5,
            quicksearchCacheRefreshOnStartup: false,
            searchInputDebounceMs: 50
          }));
          window.location.hash = '#/desktop';
          window.location.reload();
          return 'renderer_smoke_reloaded';
        }
        await waitForSelector('.desktop-sidebar');
        await waitForText('工作台');
        await assertDesktopSmoke();
          await waitForSelector('.sidebar-quick-actions');
          await waitForSelector('.desktop-mode-tools');
          const alertBell = document.querySelector('.alert-bell');
          if (alertBell) {
            alertBell.click();
            const alertPanel = await waitForSelector('.alert-panel');
            const sidebarRect = document.querySelector('.desktop-sidebar').getBoundingClientRect();
            const panelRect = alertPanel.getBoundingClientRect();
            if (panelRect.left < sidebarRect.right || panelRect.right > window.innerWidth + 1 || panelRect.width < 240) {
              throw new Error('alert panel geometry invalid: ' + JSON.stringify({
                sidebarRight: sidebarRect.right,
                panelLeft: panelRect.left,
                panelRight: panelRect.right,
                panelWidth: panelRect.width,
                viewportWidth: window.innerWidth
              }));
            }
            if (!alertPanel.querySelector('.alert-row') || !alertPanel.textContent.trim()) {
              throw new Error('alert panel content is not visible');
            }
            findButtonByLabel('关闭提醒中心')?.click();
            await waitForCondition(() => !document.querySelector('.alert-panel'), 'alert panel closed');
          }
          const pinButton = document.querySelector('.desktop-mode-bar .pin-window-button');
        if (!pinButton) {
          throw new Error('pin window button missing in Electron: ' + JSON.stringify({
            href: window.location.href,
            hasBridge: Boolean(window.desktopBridge),
            bridgeKeys: Object.keys(window.desktopBridge ?? {}),
            header: document.querySelector('.desktop-mode-bar')?.innerHTML ?? ''
          }));
        }
        if (!window.desktopBridge?.getAlwaysOnTop || !window.desktopBridge?.toggleAlwaysOnTop) {
          throw new Error('pin window bridge methods missing');
        }
        const pinState = await window.desktopBridge.getAlwaysOnTop();
        if (!pinState.success) {
          throw new Error('pin window bridge state unavailable: ' + JSON.stringify(pinState));
        }
        if (!['true', 'false'].includes(pinButton.getAttribute('aria-pressed') || '')) {
          throw new Error('pin window button missing pressed state');
        }
        if ((pinButton.getAttribute('aria-label') || pinButton.getAttribute('title') || '').length === 0) {
          throw new Error('pin window button missing accessible label');
        }
        if (document.querySelector('.ops-admin-shell')) {
          throw new Error('Electron desktop rendered admin shell inline');
        }
        const adminButton = findButton('后台');
        if (!adminButton) {
          throw new Error('admin shortcut missing for admin account');
        }
        adminButton.click();
        await delay(200);
        if (document.querySelector('.ops-admin-shell')) {
          throw new Error('admin shortcut rendered admin shell inline');
        }
        findButton('退出').click();
        await waitForSelector('.login-panel');
        return true;
      })();
    `);
    if (result === 'renderer_smoke_reloaded') {
      return;
    }
    console.log('renderer_smoke=passed');
    app.quit();
  } catch (error) {
    console.error('renderer_smoke=failed', error);
    app.exit(1);
  }
}

async function runAdminRendererSmoke(window: BrowserWindow) {
  try {
    window.setSize(1440, 900);
    await new Promise((resolve) => setTimeout(resolve, 150));
    const result = await window.webContents.executeJavaScript(`
      (async () => {
        const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
        const waitForSelector = async (selector, timeout = 15000) => {
          const started = Date.now();
          while (Date.now() - started < timeout) {
            const element = document.querySelector(selector);
            if (element) return element;
            await delay(100);
          }
          throw new Error('missing selector: ' + selector);
        };
        const waitForCondition = async (predicate, label, timeout = 15000) => {
          const started = Date.now();
          while (Date.now() - started < timeout) {
            if (await predicate()) return;
            await delay(100);
          }
          throw new Error('condition timeout: ' + label);
        };
        const setValue = (element, value) => {
          element.value = value;
          element.dispatchEvent(new Event('input', { bubbles: true }));
          element.dispatchEvent(new Event('change', { bubbles: true }));
        };
        const buttonByText = (text, root = document) => [...root.querySelectorAll('button')]
          .find((button) => button.textContent.includes(text));
        const subnavByText = (text) => [...document.querySelectorAll('.ops-admin-subnav-button')]
          .find((button) => button.textContent.includes(text));
        const inputByLabel = (text) => {
          const label = [...document.querySelectorAll('label')].find((item) => item.textContent.includes(text));
          return label ? label.querySelector('input,select,textarea') : null;
        };
        const hasLoginForm = () => Boolean(inputByLabel('API 地址') && inputByLabel('账号') && inputByLabel('密码'));
        const loadAdminData = async (path) => {
          const response = await fetch(${JSON.stringify(rendererSmokeApiBaseUrl)} + path, {
            headers: { Authorization: 'Bearer ' + ${JSON.stringify(rendererSmokeAccessToken)} }
          });
          const payload = await response.json();
          if (!response.ok || payload.success === false) {
            throw new Error('tag API failed: ' + path + ' ' + (payload.message || response.status));
          }
          return payload.data || payload;
        };
        const pageItems = (data) => Array.isArray(data.items)
          ? data.items
          : (Array.isArray(data.categories) ? data.categories : []);
        const assertTagPage = (data, rows, nameKey, noun) => {
          const items = pageItems(data);
          if (rows.length !== items.length) {
            throw new Error(noun + ' page row count mismatch: api=' + items.length + ' ui=' + rows.length);
          }
          items.forEach((item, index) => {
            const name = String(item[nameKey] || '').trim();
            if (!name || !rows[index].textContent.includes(name)) {
              throw new Error(noun + ' name not displayed verbatim: ' + name);
            }
          });
          const total = Number(data.total ?? items.length);
          const page = Number(data.page ?? 1);
          const size = Number(data.size ?? 20);
          const totalPages = Number(data.totalPages ?? Math.max(1, Math.ceil(total / Math.max(1, size))));
          const paginationText = document.querySelector('.ops-pagination')?.textContent || '';
          if (!paginationText.includes('当前筛选：' + total + ' 个' + noun)
              || !paginationText.includes('第 ' + page + ' / ' + totalPages + ' 页，每页 ' + size + ' 条')) {
            throw new Error(noun + ' pagination does not match API page');
          }
          return items;
        };

        const started = Date.now();
        while (!hasLoginForm() && !document.querySelector('.ops-admin-shell') && Date.now() - started < 15000) {
          await delay(100);
        }
        if (hasLoginForm()) {
          localStorage.setItem('desktop_config', JSON.stringify({
            apiBaseUrl: ${JSON.stringify(rendererSmokeApiBaseUrl)},
            accessToken: ${JSON.stringify(rendererSmokeAccessToken)},
            accountRole: 'ADMIN'
          }));
          window.location.hash = '#/admin';
          window.location.reload();
          return 'renderer_smoke_reloaded';
        }

        await waitForSelector('.ops-admin-shell');

        subnavByText('客户数据对接')?.click();
        await waitForSelector('.ops-table-row.customer-search');
        const customerInput = [...document.querySelectorAll('input')]
          .find((input) => (input.getAttribute('placeholder') || '').includes('1111'));
        if (!customerInput) throw new Error('admin customer search input missing');
        setValue(customerInput, '1111');
        buttonByText('查询客户')?.click();
        await waitForCondition(
          () => [...document.querySelectorAll('.ops-table-row.customer-search:not(.head)')]
            .some((row) => row.textContent.includes('1111')),
          'admin customer 1111 search result'
        );
        const customerRow = document.querySelector('.ops-table-row.customer-search:not(.head)');
        buttonByText('查看档案', customerRow)?.click();
        await waitForSelector('.customer-search-detail');

        subnavByText('账号与权限')?.click();
        await waitForCondition(() => document.querySelectorAll('.ops-table-row.accounts').length > 1, 'account rows loaded');
        const accountRow = document.querySelector('.ops-table-row.accounts:not(.head)');
        const accountCells = [...accountRow.children];
        if (accountCells.length !== 7 || !accountCells[6].classList.contains('ops-row-actions')) {
          throw new Error('account table does not have a dedicated seventh action column');
        }
        const statusRect = accountCells[5].getBoundingClientRect();
        const actionRect = accountCells[6].getBoundingClientRect();
        const verticallyOverlaps = actionRect.top < statusRect.bottom && statusRect.top < actionRect.bottom;
        if (actionRect.left <= statusRect.left || !verticallyOverlaps) {
          throw new Error('account action column wrapped below status');
        }

        subnavByText('速搜内容管理')?.click();
        await waitForCondition(() => Boolean(buttonByText('新增内容')), 'quick-search admin page loaded');
        buttonByText('新增内容')?.click();
        const drawer = await waitForSelector('.ops-drawer');
        const labels = [...drawer.querySelectorAll('.ops-variable-bar button')]
          .map((button) => button.textContent.trim());
        const expected = ['客户昵称', '手机号', '意向门店', '意向项目', '客户阶段', '意向等级', '下次跟进时间', '预约日期', '预约项目', '预约门店', '是否到店', '分配管家'];
        if (labels.join('|') !== expected.join('|')) {
          throw new Error('quick-search variable labels mismatch: ' + labels.join('|'));
        }
        buttonByText('意向等级', drawer)?.click();
        await delay(50);
        if (drawer.querySelector('textarea')?.value !== '{{意向等级}}') {
          throw new Error('quick-search Chinese placeholder was not inserted');
        }
        buttonByText('取消', drawer)?.click();

        subnavByText('客户标签与分层')?.click();
        const categoryPage = await loadAdminData('/admin/api/v1/tags/categories?merged=false&page=1&size=20&sortBy=sortOrder&sortDirection=ASC');
        const expectedCategoryRows = pageItems(categoryPage).length;
        await waitForCondition(
          () => document.querySelectorAll('.tag-category-row:not(.head)').length === expectedCategoryRows,
          'tag category rows loaded'
        );
        const categoryRows = [...document.querySelectorAll('.tag-category-row:not(.head)')];
        const categoryItems = assertTagPage(categoryPage, categoryRows, 'categoryName', '分类');
        if (!categoryItems.length) {
          throw new Error('tag category API returned no rows for detail smoke');
        }
        const categoryRow = categoryRows[0];
        const categoryCells = [...categoryRow.children];
        if (categoryCells.length !== 6 || !categoryCells[5].classList.contains('ops-row-actions')) {
          throw new Error('tag category table does not have a dedicated sixth action column');
        }
        const categoryStatusRect = categoryCells[4].getBoundingClientRect();
        const categoryActionRect = categoryCells[5].getBoundingClientRect();
        const categoryVerticallyOverlaps = categoryActionRect.top < categoryStatusRect.bottom
          && categoryStatusRect.top < categoryActionRect.bottom;
        if (categoryActionRect.left <= categoryStatusRect.left || !categoryVerticallyOverlaps) {
          throw new Error('tag category action column wrapped below the data columns');
        }

        buttonByText('详情', categoryRow)?.click();
        const categoryDetail = await waitForSelector('.ops-tag-detail-drawer');
        await waitForCondition(() => categoryDetail.textContent.includes('影响范围'), 'tag category detail loaded');
        buttonByText('关闭', categoryDetail)?.click();
        await waitForCondition(() => !document.querySelector('.ops-tag-detail-drawer'), 'tag category detail closed');

        buttonByText('新增分类')?.click();
        const categoryForm = await waitForSelector('.ops-drawer');
        const categoryFormLabels = [...categoryForm.querySelectorAll('.ops-label-title')]
          .map((label) => label.textContent.trim());
        if (categoryFormLabels.some((label) => label.includes('绑定客户档案字段'))) {
          throw new Error('new tag category still exposes the legacy customer field binding');
        }
        if (categoryFormLabels.some((label) => label.includes('系统编号'))) {
          throw new Error('new tag category asks operators to enter an internal code');
        }
        buttonByText('取消', categoryForm)?.click();
        await waitForCondition(() => !document.querySelector('.ops-drawer'), 'tag category form closed');

        buttonByText('标签值')?.click();
        const valuePage = await loadAdminData('/admin/api/v1/tags/values?merged=false&page=1&size=20&sortBy=sortOrder&sortDirection=ASC');
        const expectedValueRows = pageItems(valuePage).length;
        await waitForCondition(
          () => document.querySelectorAll('.tag-value-row:not(.head)').length === expectedValueRows,
          'tag value rows loaded'
        );
        const valueRows = [...document.querySelectorAll('.tag-value-row:not(.head)')];
        const valueItems = assertTagPage(valuePage, valueRows, 'displayName', '标签值');
        if (!valueItems.length) {
          throw new Error('tag value API returned no rows for detail and merge smoke');
        }

        buttonByText('新增标签值')?.click();
        const valueForm = await waitForSelector('.ops-drawer');
        const valueFormLabels = [...valueForm.querySelectorAll('.ops-label-title')]
          .map((label) => label.textContent.trim());
        if (valueFormLabels.some((label) => label.includes('系统编号'))) {
          throw new Error('new tag value asks operators to enter an internal code');
        }
        buttonByText('取消', valueForm)?.click();
        await waitForCondition(() => !document.querySelector('.ops-drawer'), 'tag value form closed');

        const valueRow = valueRows[0];
        buttonByText('详情', valueRow)?.click();
        const valueDetail = await waitForSelector('.ops-tag-detail-drawer');
        await waitForCondition(() => valueDetail.textContent.includes('标签含义'), 'tag value detail loaded');
        buttonByText('关闭', valueDetail)?.click();
        await waitForCondition(() => !document.querySelector('.ops-tag-detail-drawer'), 'tag value detail closed');

        buttonByText('合并', valueRow)?.click();
        const mergeDrawer = await waitForSelector('.ops-tag-merge-drawer');
        await waitForCondition(() => {
          const select = mergeDrawer.querySelector('select');
          return Boolean(select && select.options.length >= 2) || Boolean(mergeDrawer.querySelector('.admin-message.error'));
        }, 'tag merge targets loaded');
        const mergeTarget = mergeDrawer.querySelector('select');
        if (!mergeTarget || mergeTarget.options.length < 2) {
          throw new Error('tag merge target list is empty: ' + (mergeDrawer.querySelector('.admin-message.error')?.textContent || 'unknown'));
        }
        setValue(mergeTarget, mergeTarget.options[1].value);
        await waitForCondition(
          () => Boolean(buttonByText('生成合并预览', mergeDrawer) && !buttonByText('生成合并预览', mergeDrawer).disabled),
          'tag merge preview enabled'
        );
        buttonByText('生成合并预览', mergeDrawer)?.click();
        await waitForCondition(
          () => Boolean(mergeDrawer.querySelector('.ops-detail-box.warning')) || Boolean(mergeDrawer.querySelector('.admin-message.error')),
          'tag merge preview loaded'
        );
        if (mergeDrawer.querySelector('.admin-message.error')) {
          throw new Error('tag merge preview failed: ' + mergeDrawer.querySelector('.admin-message.error').textContent);
        }
        const confirmMerge = buttonByText('确认合并', mergeDrawer);
        if (!confirmMerge || confirmMerge.disabled) {
          throw new Error('tag merge preview did not enable confirmation');
        }
        buttonByText('取消', mergeDrawer)?.click();
        await waitForCondition(() => !document.querySelector('.ops-tag-merge-drawer'), 'tag merge drawer closed');
        return true;
      })();
    `);
    if (result === 'renderer_smoke_reloaded') {
      return;
    }
    await captureRendererSmokeScreenshot(window, 'tag-management-admin-desktop.png');
    window.setSize(390, 844);
    await new Promise((resolve) => setTimeout(resolve, 250));
    await window.webContents.executeJavaScript(`
      (async () => {
        const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
        const waitForCondition = async (predicate, label, timeout = 15000) => {
          const started = Date.now();
          while (Date.now() - started < timeout) {
            if (await predicate()) return;
            await delay(100);
          }
          throw new Error('condition timeout: ' + label);
        };
        const buttonByText = (text, root = document) => [...root.querySelectorAll('button')]
          .find((button) => button.textContent.includes(text));

        if (document.documentElement.scrollWidth > window.innerWidth + 2) {
          throw new Error('admin page overflows the mobile viewport');
        }
        const tagTable = document.querySelector('.ops-table');
        if (!tagTable || tagTable.scrollWidth <= tagTable.clientWidth) {
          throw new Error('mobile tag table does not provide contained horizontal scrolling');
        }
        buttonByText('标签分类')?.click();
        await waitForCondition(
          () => document.querySelectorAll('.tag-category-row:not(.head)').length > 0,
          'mobile tag categories loaded'
        );
        const categoryRow = document.querySelector('.tag-category-row:not(.head)');
        buttonByText('详情', categoryRow)?.click();
        await waitForCondition(() => Boolean(document.querySelector('.ops-tag-detail-drawer')), 'mobile detail drawer loaded');
        const drawer = document.querySelector('.ops-tag-detail-drawer');
        const drawerRect = drawer.getBoundingClientRect();
        if (drawerRect.left < -1 || drawerRect.right > window.innerWidth + 1 || drawerRect.width > window.innerWidth + 1) {
          throw new Error('tag detail drawer overflows the mobile viewport');
        }
        for (const element of drawer.querySelectorAll('button,input,select,textarea,strong')) {
          const rect = element.getBoundingClientRect();
          if (rect.width > 0 && (rect.left < drawerRect.left - 1 || rect.right > drawerRect.right + 1)) {
            throw new Error('tag detail content overflows its mobile drawer');
          }
        }
        return true;
      })();
    `);
    await captureRendererSmokeScreenshot(window, 'tag-management-admin-mobile.png');
    console.log('renderer_smoke=passed target=admin');
    app.quit();
  } catch (error) {
    console.error('renderer_smoke=failed target=admin', error);
    app.exit(1);
  }
}

async function runTagManagementRendererSmoke(window: BrowserWindow) {
  try {
    window.setSize(1180, 820);
    await new Promise((resolve) => setTimeout(resolve, 150));
    const result = await window.webContents.executeJavaScript(`
      (async () => {
        const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
        const waitForSelector = async (selector, timeout = 15000) => {
          const started = Date.now();
          while (Date.now() - started < timeout) {
            const element = document.querySelector(selector);
            if (element) return element;
            await delay(100);
          }
          throw new Error('missing selector: ' + selector);
        };
        const waitForCondition = async (predicate, label, timeout = 15000) => {
          const started = Date.now();
          while (Date.now() - started < timeout) {
            if (await predicate()) return;
            await delay(100);
          }
          throw new Error('condition timeout: ' + label);
        };
        const inputByLabel = (text) => {
          const label = [...document.querySelectorAll('label')].find((item) => item.textContent.includes(text));
          return label ? label.querySelector('input,select,textarea') : null;
        };
        const buttonByText = (text, root = document) => [...root.querySelectorAll('button')]
          .find((button) => button.textContent.includes(text));
        const hasLoginForm = () => Boolean(inputByLabel('API 地址') && inputByLabel('账号') && inputByLabel('密码'));
        const loadAdminData = async (path) => {
          const response = await fetch(${JSON.stringify(rendererSmokeApiBaseUrl)} + path, {
            headers: { Authorization: 'Bearer ' + ${JSON.stringify(rendererSmokeAccessToken)} }
          });
          const payload = await response.json();
          if (!response.ok || payload.success === false) {
            throw new Error('delegated tag API failed: ' + path + ' ' + (payload.message || response.status));
          }
          return payload.data || payload;
        };
        const pageItems = (data) => Array.isArray(data.items)
          ? data.items
          : (Array.isArray(data.categories) ? data.categories : []);

        const started = Date.now();
        while (!hasLoginForm() && !document.querySelector('.ops-admin-shell') && Date.now() - started < 15000) {
          await delay(100);
        }
        if (hasLoginForm()) {
          localStorage.setItem('desktop_config', JSON.stringify({
            apiBaseUrl: ${JSON.stringify(rendererSmokeApiBaseUrl)},
            accessToken: ${JSON.stringify(rendererSmokeAccessToken)},
            accountRole: 'KEEPER',
            accountPermissions: ['TAG_MANAGEMENT']
          }));
          window.location.hash = '#/admin';
          window.location.reload();
          return 'renderer_smoke_reloaded';
        }

        await waitForSelector('.ops-admin-shell');
        const categoryPage = await loadAdminData('/admin/api/v1/tags/categories?merged=false&page=1&size=20&sortBy=sortOrder&sortDirection=ASC');
        const categoryItems = pageItems(categoryPage);
        await waitForCondition(
          () => document.querySelectorAll('.tag-category-row:not(.head)').length === categoryItems.length,
          'delegated tag category rows loaded'
        );
        const categoryRows = [...document.querySelectorAll('.tag-category-row:not(.head)')];
        categoryItems.forEach((item, index) => {
          const categoryName = String(item.categoryName || '').trim();
          if (!categoryName || !categoryRows[index].textContent.includes(categoryName)) {
            throw new Error('delegated category name not displayed verbatim: ' + categoryName);
          }
        });
        const subnav = [...document.querySelectorAll('.ops-admin-subnav-button')];
        if (subnav.length !== 1 || !subnav[0].textContent.includes('客户标签与分层')) {
          throw new Error('delegated tag manager can see navigation outside tag management');
        }
        const pageText = document.querySelector('.ops-admin-shell').textContent;
        for (const forbiddenText of ['账号与权限', '跟进规则引擎配置', '配置中心', '运营分析看板']) {
          if (pageText.includes(forbiddenText)) {
            throw new Error('delegated tag manager can see forbidden section: ' + forbiddenText);
          }
        }
        if (document.querySelector('.admin-message.error')) {
          throw new Error('delegated tag manager page contains an API error');
        }
        const valueTab = [...document.querySelectorAll('.ops-segmented button')]
          .find((button) => button.textContent.trim() === '标签值');
        if (!valueTab) {
          throw new Error('delegated tag value tab is missing');
        }
        valueTab.click();
        const valuePage = await loadAdminData('/admin/api/v1/tags/values?merged=false&page=1&size=20&sortBy=sortOrder&sortDirection=ASC');
        const valueItems = pageItems(valuePage);
        await waitForCondition(
          () => (valueTab.classList.contains('active') && document.querySelectorAll('.tag-value-row:not(.head)').length === valueItems.length)
            || Boolean(document.querySelector('.admin-message.error')),
          'delegated tag value rows loaded'
        );
        if (document.querySelector('.admin-message.error')) {
          throw new Error('delegated tag value page failed: ' + document.querySelector('.admin-message.error').textContent);
        }
        const valueRows = [...document.querySelectorAll('.tag-value-row:not(.head)')];
        valueItems.forEach((item, index) => {
          const displayName = String(item.displayName || '').trim();
          if (!displayName || !valueRows[index].textContent.includes(displayName)) {
            throw new Error('delegated tag display name not displayed verbatim: ' + displayName);
          }
        });
        const valueTotal = Number(valuePage.total ?? valueItems.length);
        const paginationText = document.querySelector('.ops-pagination')?.textContent || '';
        if (!paginationText.includes('当前筛选：' + valueTotal + ' 个标签值')) {
          throw new Error('delegated tag value total does not match API page');
        }
        return true;
      })();
    `);
    if (result === 'renderer_smoke_reloaded') {
      return;
    }
    await captureRendererSmokeScreenshot(window, 'tag-management-delegated.png');
    console.log('renderer_smoke=passed target=tag-admin');
    app.quit();
  } catch (error) {
    await captureRendererSmokeScreenshot(window, 'tag-management-delegated-failed.png');
    console.error('renderer_smoke=failed target=tag-admin', error);
    app.exit(1);
  }
}

async function captureRendererSmokeScreenshot(window: BrowserWindow, fileName: string) {
  if (!rendererSmokeScreenshotDir) return;
  mkdirSync(rendererSmokeScreenshotDir, { recursive: true });
  const image = await window.webContents.capturePage();
  writeFileSync(path.join(rendererSmokeScreenshotDir, fileName), image.toPNG());
}

app.whenReady().then(() => {
  registerOnlineStatusIpc();
  registerWindowControlIpc();
  registerAdminOpenExternal();
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

function registerWindowControlIpc() {
  ipcMain.handle('window:get-always-on-top', (event) => {
    const window = BrowserWindow.fromWebContents(event.sender);
    return { success: Boolean(window), alwaysOnTop: window?.isAlwaysOnTop() ?? false };
  });
  ipcMain.handle('window:toggle-always-on-top', (event) => {
    const window = BrowserWindow.fromWebContents(event.sender);
    if (!window) {
      return { success: false, alwaysOnTop: false, error: 'WINDOW_NOT_FOUND' };
    }
    const next = !window.isAlwaysOnTop();
    window.setAlwaysOnTop(next);
    return { success: true, alwaysOnTop: window.isAlwaysOnTop() };
  });
}

function registerAdminOpenExternal() {
  ipcMain.handle('admin:open-external', async (_event, payload?: { url?: string }) => {
    try {
      const url = adminConsoleUrl(payload?.url);
      if (isSmoke) {
        return { success: true, url };
      }
      await openExternalBrowser(url);
      return { success: true, url };
    } catch (error) {
      return {
        success: false,
        error: 'ADMIN_OPEN_FAILED',
        message: error instanceof Error ? error.message : 'Failed to open admin console'
      };
    }
  });
}

function adminConsoleUrl(requestedUrl?: string): string {
  const configured = process.env.PDA_ADMIN_CONSOLE_URL;
  const base = requestedUrl && requestedUrl.trim()
    ? requestedUrl.trim()
    : configured && configured.trim()
    ? configured.trim()
    : process.env.VITE_DEV_SERVER_URL
      ? `${process.env.VITE_DEV_SERVER_URL}/#/admin`
      : '';
  if (!base) {
    throw new Error('PDA_ADMIN_CONSOLE_URL is required to open admin console in packaged builds');
  }
  const url = new URL(base);
  if (url.protocol !== 'http:' && url.protocol !== 'https:') {
    throw new Error('Admin console URL must use http or https');
  }
  url.hash = '#/admin';
  return url.toString();
}

function isAllowedAdminConsoleUrl(rawUrl: string): boolean {
  try {
    const requested = new URL(rawUrl);
    const allowed = new URL(adminConsoleUrl());
    return requested.protocol === allowed.protocol
      && requested.origin === allowed.origin
      && requested.pathname === allowed.pathname
      && (requested.hash === '#/admin' || requested.hash.startsWith('#/admin/'));
  } catch {
    return false;
  }
}

async function openExternalBrowser(url: string): Promise<void> {
  if (isSmoke) {
    return;
  }
  await shell.openExternal(url);
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
    const assistantWindow = mainWindow;
    if (!assistantWindow) {
      return { success: false, error: 'CAPTURE_FAILED', message: 'Assistant window is unavailable' };
    }
    return captureForegroundWindow({
      assistantWindow,
      getActiveWindow: async () => {
        const current = await activeWindow();
        if (!current) return undefined;
        return {
          id: current.id,
          title: current.title,
          ownerName: current.owner?.name ?? '',
          bounds: {
            x: current.bounds.x,
            y: current.bounds.y,
            width: current.bounds.width,
            height: current.bounds.height
          }
        };
      },
      getSources: async (types, thumbnailSize) => desktopCapturer.getSources({
        types,
        thumbnailSize,
        fetchWindowIcons: false
      }).then((sources) => sources.map((source) => ({
        id: source.id,
        name: source.name,
        displayId: source.display_id,
        thumbnail: source.thumbnail
      }))),
      getDisplayId: (point) => resolveDisplayIdFromPhysicalPoint(point, screen),
      delay: (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
      minImageDimension: DESKTOP_DEFAULTS.clipboardMinImageDimension
    });
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
