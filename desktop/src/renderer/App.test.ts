import { createApp, nextTick, type App as VueApp } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App.vue';

const apiMocks = vi.hoisted(() => ({
  getJson: vi.fn(),
  postJson: vi.fn()
}));

vi.mock('./shared/apiClient', () => ({
  getJson: apiMocks.getJson,
  postJson: apiMocks.postJson
}));

vi.mock('./modules/abnormal-alert/alertStore', () => ({
  cleanupAbnormalAlertRouter: vi.fn(),
  initializeAbnormalAlertRouter: vi.fn(),
  alertStore: new Map()
}));

vi.mock('./modules/stage-suggestion/stageSuggestionHandler', () => ({
  cleanupStageSuggestionHandler: vi.fn(),
  initializeStageSuggestionHandler: vi.fn()
}));

vi.mock('./modules/admin/AdminConsole.vue', () => ({
  default: {
    props: ['accountName'],
    emits: ['logout', 'switch-dev-console'],
    template: `
      <section class="ops-admin-shell">
        <button type="button" @click="$emit('switch-dev-console')">开发调试台</button>
        <button type="button" @click="$emit('logout')">退出</button>
      </section>
    `
  }
}));

vi.mock('./modules/admin/AdminDevConsole.vue', () => ({
  default: {
    props: ['accountName'],
    emits: ['logout', 'switch-admin'],
    template: `
      <section class="admin-console">
        <button type="button" @click="$emit('switch-admin')">正式后台</button>
        <button type="button" @click="$emit('logout')">退出</button>
      </section>
    `
  }
}));

vi.mock('./modules/workbench/WorkbenchPanel.vue', () => ({ default: { template: '<section class="workbench-panel">工作台内容</section>' } }));
vi.mock('./modules/chat-recognition/ChatRecognitionPanel.vue', () => ({ default: { template: '<section class="recognition">聊天识别内容</section>' } }));
vi.mock('./modules/followup-list/FollowupListPanel.vue', () => ({ default: { template: '<section class="followup-panel">跟进列表内容</section>' } }));
vi.mock('./modules/customer-profile/CustomerProfilePanel.vue', () => ({ default: { template: '<section class="customer-panel">客户档案内容</section>' } }));
vi.mock('./modules/reply-suggestions/ReplySuggestionPanel.vue', () => ({ default: { template: '<section class="reply-panel">回复助手内容</section>' } }));
vi.mock('./modules/abnormal-alert/AlertBell.vue', () => ({ default: { template: '<div class="alert-bell-wrap"></div>' } }));
vi.mock('./modules/batch-template/BatchTemplateOverlay.vue', () => ({ default: { template: '<div class="batch-template-overlay"></div>' } }));
vi.mock('./modules/copy-backfill/CopyBackfillAgent.vue', () => ({ default: { template: '<div class="copy-backfill-agent"></div>' } }));
vi.mock('./modules/help-mode/HelpModeAgent.vue', () => ({ default: { template: '<div class="help-mode-agent"></div>' } }));
vi.mock('./modules/new-lead-toast/NewLeadToastAgent.vue', () => ({ default: { template: '<div class="new-lead-toast-agent"></div>' } }));
vi.mock('./modules/offline/OfflineStatusBar.vue', () => ({ default: { template: '<div class="offline-status-bar"></div>' } }));
vi.mock('./modules/quick-search/QuickSearchOverlay.vue', () => ({ default: { template: '<div class="quick-search-overlay"></div>' } }));
vi.mock('./shared/desktopBridge', () => ({
  captureScreenshot: vi.fn(async () => ({ success: true, imageBase64: 'capture-image' })),
  openAdminConsole: vi.fn(async () => ({ success: true })),
  getAlwaysOnTop: vi.fn(async () => ({ success: true, alwaysOnTop: false })),
  toggleAlwaysOnTop: vi.fn(async () => ({ success: true, alwaysOnTop: true }))
}));
vi.mock('./modules/chat-recognition/recognitionStore', () => ({
  recognitionState: { isRecognizePending: false },
  triggerRecognize: vi.fn(async () => undefined)
}));

type MountedApp = {
  app: VueApp<Element>;
  host: HTMLDivElement;
};

function installMemoryLocalStorage(): void {
  const store = new Map<string, string>();
  Object.defineProperty(globalThis, 'localStorage', {
    value: {
      getItem: vi.fn((key: string) => store.get(key) ?? null),
      setItem: vi.fn((key: string, value: string) => store.set(key, String(value))),
      removeItem: vi.fn((key: string) => store.delete(key)),
      clear: vi.fn(() => store.clear())
    },
    configurable: true
  });
}

async function flushUi() {
  await Promise.resolve();
  await Promise.resolve();
  await nextTick();
}

async function flushAsyncComponent() {
  await flushUi();
  await Promise.resolve();
  await Promise.resolve();
  await nextTick();
}

async function mountAppWithToken(hash = '#/desktop'): Promise<MountedApp> {
  window.history.replaceState(null, '', hash);
  localStorage.setItem('desktop_config', JSON.stringify({ apiBaseUrl: 'http://localhost:8080', accessToken: 'token-a', accountRole: 'ADMIN' }));
  const host = document.createElement('div');
  document.body.appendChild(host);
  const app = createApp(App);
  app.mount(host);
  await flushUi();
  return { app, host };
}

function installDesktopBridge(): void {
  Object.defineProperty(window, 'desktopBridge', {
    value: {
      captureScreenshot: vi.fn(),
      writeClipboardText: vi.fn(),
      writeClipboardImage: vi.fn(),
      getAlwaysOnTop: vi.fn(async () => ({ success: true, alwaysOnTop: false })),
      toggleAlwaysOnTop: vi.fn(async () => ({ success: true, alwaysOnTop: true })),
      openAdminConsole: vi.fn(async (_url?: string) => ({ success: true })),
      onClipboardImage: vi.fn(() => undefined),
      onQuickSearchShow: vi.fn(() => undefined),
      onQuickSearchHide: vi.fn(() => undefined),
      getOnlineStatus: vi.fn(async () => ({ online: true, type: 'unknown' })),
      onOnlineStatusChange: vi.fn(() => undefined)
    },
    configurable: true
  });
}

function uninstallDesktopBridge(): void {
  delete (window as { desktopBridge?: unknown }).desktopBridge;
}

function unsignedJwt(payload: Record<string, unknown>): string {
  const json = JSON.stringify(payload);
  const base64 = btoa(json).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
  return `header.${base64}.signature`;
}

describe('App route shell', () => {
  beforeEach(() => {
    installMemoryLocalStorage();
    uninstallDesktopBridge();
    window.history.replaceState(null, '', '#/desktop');
    apiMocks.getJson.mockResolvedValue({
      success: true,
      data: {
        accountName: 'Admin',
        role: 'ADMIN',
        skillStatus: {
          status: 'OK',
          expireAt: '2026-08-01',
          daysLeft: 27,
          label: '有效至 2026-08-01'
        }
      },
      errorCode: null,
      message: null
    });
    apiMocks.postJson.mockResolvedValue({ success: true, data: null, errorCode: null, message: null });
  });

  afterEach(() => {
    document.body.innerHTML = '';
    localStorage.clear();
    uninstallDesktopBridge();
    vi.clearAllMocks();
  });

  it('opens authenticated users on the Electron sidebar preview at #/desktop', async () => {
    installDesktopBridge();
    const { app, host } = await mountAppWithToken('#/desktop');

    expect(window.location.hash).toBe('#/desktop');
    expect(host.querySelector('.desktop-shell')).toBeTruthy();
    expect(host.querySelector('.desktop-sidebar')).toBeTruthy();
    expect(host.querySelector('.ops-admin-shell')).toBeFalsy();
    expect([...host.querySelectorAll('.desktop-nav-button .nav-label')].map((item) => item.textContent)).toEqual([
      '工作台',
      '客户档案',
      '回复助手'
    ]);
    expect((host.querySelector('.desktop-nav-button.active .nav-label') as HTMLElement | null)?.textContent).toBe('工作台');
    expect(host.querySelectorAll('.desktop-sidebar-actions button').length).toBe(2);
    expect(host.querySelector('.sidebar-quick-actions')).toBeTruthy();
    expect(host.querySelector('.desktop-mode-tools .alert-bell-wrap')).toBeTruthy();
    expect(host.querySelector('.global-action-bar')).toBeFalsy();
    expect(host.querySelector('.global-recognize-button')).toBeFalsy();
    expect(host.textContent).toContain('有效至 2026-08-01');

    app.unmount();
  });

  it('keeps browser users inside the operations admin and blocks the web desktop route', async () => {
    const { app, host } = await mountAppWithToken('#/desktop');

    expect(window.location.hash).toBe('#/admin');
    expect(host.querySelector('.ops-admin-shell')).toBeTruthy();
    expect(host.querySelector('.desktop-shell')).toBeFalsy();
    expect(host.textContent).not.toContain('桌面工作台');

    app.unmount();
  });

  it('returns to the login page when the API reports an expired session', async () => {
    const { eventBus } = await import('./shared/eventBus');
    const { app, host } = await mountAppWithToken('#/admin');

    eventBus.emit('auth:expired', { message: '登录已过期，请重新登录' });
    await flushUi();

    expect(host.querySelector('.login-shell')).toBeTruthy();
    expect(host.querySelector('.ops-admin-shell')).toBeFalsy();
    expect(host.textContent).toContain('登录已过期，请重新登录');
    const saved = JSON.parse(localStorage.getItem('desktop_config') ?? '{}');
    expect(saved.accessToken).toBe('');
    expect(saved.accountRole).toBe('');

    app.unmount();
  });

  it('keeps the development console route out of the default desktop preview', async () => {
    const { app, host } = await mountAppWithToken('#/admin/dev-console');
    await flushAsyncComponent();

    expect(window.location.hash).toBe('#/admin/dev-console');
    expect(host.querySelector('.desktop-shell')).toBeFalsy();

    app.unmount();
  });

  it('switches desktop panels, triggers global actions, and opens admin externally', async () => {
    const [{ captureScreenshot, openAdminConsole }, { triggerRecognize }] = await Promise.all([
      import('./shared/desktopBridge'),
      import('./modules/chat-recognition/recognitionStore')
    ]);
    installDesktopBridge();
    const { app, host } = await mountAppWithToken('#/desktop');
    const navButtons = [...host.querySelectorAll('.desktop-nav-button')] as HTMLButtonElement[];

    navButtons[1].click();
    await flushUi();
    expect((host.querySelector('.desktop-nav-button.active .nav-label') as HTMLElement | null)?.textContent).toBe('客户档案');
    expect((host.querySelector('.customer-panel') as HTMLElement | null)?.style.display).not.toBe('none');
    expect((host.querySelector('.workbench-panel') as HTMLElement | null)?.style.display).toBe('none');

    const actionButtons = [...host.querySelectorAll('.sidebar-quick-actions button')] as HTMLButtonElement[];
    expect([...host.querySelectorAll('.sidebar-quick-actions .action-label')].map((item) => item.textContent)).toEqual([
      '识别',
      '模板',
      '批量'
    ]);
    const recognizeButton = actionButtons[0];
    expect(recognizeButton).toBeTruthy();
    recognizeButton?.click();
    await flushUi();
    expect(captureScreenshot).toHaveBeenCalled();
    expect(triggerRecognize).toHaveBeenCalledWith('BUTTON_CLICK', { imageBase64: 'capture-image' });
    expect((host.querySelector('.desktop-nav-button.active .nav-label') as HTMLElement | null)?.textContent).toBe('回复助手');

    actionButtons[2].click();
    await flushUi();
    expect((host.querySelector('.task-queue-backdrop') as HTMLElement | null)?.style.display).not.toBe('none');
    expect(host.querySelector('.task-queue-drawer .followup-panel')).toBeTruthy();

    const adminButton = [...host.querySelectorAll('.desktop-sidebar-actions button')]
      .find((button) => button.textContent?.includes('后台')) as HTMLButtonElement | undefined;
    expect(adminButton).toBeTruthy();
    adminButton?.click();
    await flushUi();
    expect(openAdminConsole).toHaveBeenCalledWith('http://localhost:3000/#/admin');
    expect(window.location.hash).toBe('#/desktop');
    expect(host.querySelector('.desktop-sidebar')).toBeTruthy();

    app.unmount();
  });

  it('toggles the pinned window button through the desktop bridge', async () => {
    const { toggleAlwaysOnTop } = await import('./shared/desktopBridge');
    installDesktopBridge();
    const { app, host } = await mountAppWithToken('#/desktop');

    const pinButton = host.querySelector('.pin-window-button') as HTMLButtonElement | null;
    expect(pinButton).toBeTruthy();
    expect(pinButton?.getAttribute('aria-pressed')).toBe('false');
    expect(pinButton?.textContent).toContain('置');

    pinButton?.click();
    await flushUi();

    expect(toggleAlwaysOnTop).toHaveBeenCalledTimes(1);
    expect(pinButton?.getAttribute('aria-pressed')).toBe('true');
    expect(pinButton?.textContent).toContain('顶');

    app.unmount();
  });

  it('hides the admin shortcut for keeper accounts', async () => {
    installDesktopBridge();
    apiMocks.getJson.mockResolvedValueOnce({
      success: true,
      data: {
        accountName: 'Keeper',
        role: 'KEEPER',
        skillStatus: { status: 'UNKNOWN', expireAt: null, daysLeft: null, label: '技能有效期未配置' }
      },
      errorCode: null,
      message: null
    });
    window.history.replaceState(null, '', '#/desktop');
    localStorage.setItem('desktop_config', JSON.stringify({ apiBaseUrl: 'http://localhost:8080', accessToken: 'token-a', accountRole: 'KEEPER' }));
    const host = document.createElement('div');
    document.body.appendChild(host);
    const app = createApp(App);
    app.mount(host);
    await flushUi();

    expect([...host.querySelectorAll('.desktop-sidebar-actions button')]
      .some((button) => button.textContent?.includes('后台'))).toBe(false);

    app.unmount();
  });

  it('does not expose the admin shortcut for leader cached tokens', async () => {
    installDesktopBridge();
    apiMocks.getJson.mockResolvedValueOnce({
      success: true,
      data: {
        accountName: 'Leader',
        role: 'LEADER',
        skillStatus: { status: 'UNKNOWN', expireAt: null, daysLeft: null, label: '技能有效期未配置' }
      },
      errorCode: null,
      message: null
    });
    window.history.replaceState(null, '', '#/desktop');
    localStorage.setItem('desktop_config', JSON.stringify({
      apiBaseUrl: 'http://localhost:8080',
      accessToken: unsignedJwt({ role: 'LEADER' })
    }));
    const host = document.createElement('div');
    document.body.appendChild(host);
    const app = createApp(App);
    app.mount(host);
    await flushUi();

    expect([...host.querySelectorAll('.desktop-sidebar-actions button')]
      .some((button) => button.textContent?.includes('后台'))).toBe(false);

    app.unmount();
  });
});
