import { createApp, nextTick, type App as VueApp } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App.vue';

vi.mock('./modules/abnormal-alert/alertStore', () => ({
  cleanupAbnormalAlertRouter: vi.fn(),
  initializeAbnormalAlertRouter: vi.fn()
}));

vi.mock('./modules/stage-suggestion/stageSuggestionHandler', () => ({
  cleanupStageSuggestionHandler: vi.fn(),
  initializeStageSuggestionHandler: vi.fn()
}));

vi.mock('./modules/admin/AdminConsole.vue', () => ({
  default: {
    props: ['accountName'],
    emits: ['logout', 'switch-desktop', 'switch-dev-console'],
    template: `
      <section class="ops-admin-shell">
        <button type="button" @click="$emit('switch-desktop')">桌面工作台</button>
        <button type="button" @click="$emit('switch-dev-console')">开发调试台</button>
        <button type="button" @click="$emit('logout')">退出</button>
      </section>
    `
  }
}));

vi.mock('./modules/admin/AdminDevConsole.vue', () => ({
  default: {
    props: ['accountName'],
    emits: ['logout', 'switch-admin', 'switch-desktop'],
    template: `
      <section class="admin-console">
        <button type="button" @click="$emit('switch-admin')">正式后台</button>
        <button type="button" @click="$emit('switch-desktop')">工作台</button>
        <button type="button" @click="$emit('logout')">退出</button>
      </section>
    `
  }
}));

vi.mock('./modules/workbench/WorkbenchPanel.vue', () => ({ default: { template: '<section class="workbench-panel">工作台内容</section>' } }));
vi.mock('./modules/chat-recognition/ChatRecognitionPanel.vue', () => ({ default: { template: '<section class="recognition">聊天识别内容</section>' } }));
vi.mock('./modules/followup-list/FollowupListPanel.vue', () => ({ default: { template: '<section class="followup-panel">跟进列表内容</section>' } }));
vi.mock('./modules/customer-profile/CustomerProfilePanel.vue', () => ({ default: { template: '<section class="customer-panel">客户档案内容</section>' } }));
vi.mock('./modules/reply-suggestions/ReplySuggestionPanel.vue', () => ({ default: { template: '<section class="reply-panel">话术建议内容</section>' } }));
vi.mock('./modules/abnormal-alert/AlertBell.vue', () => ({ default: { template: '<div class="alert-bell-wrap"></div>' } }));
vi.mock('./modules/batch-template/BatchTemplateOverlay.vue', () => ({ default: { template: '<div class="batch-template-overlay"></div>' } }));
vi.mock('./modules/copy-backfill/CopyBackfillAgent.vue', () => ({ default: { template: '<div class="copy-backfill-agent"></div>' } }));
vi.mock('./modules/help-mode/HelpModeAgent.vue', () => ({ default: { template: '<div class="help-mode-agent"></div>' } }));
vi.mock('./modules/new-lead-toast/NewLeadToastAgent.vue', () => ({ default: { template: '<div class="new-lead-toast-agent"></div>' } }));
vi.mock('./modules/offline/OfflineStatusBar.vue', () => ({ default: { template: '<div class="offline-status-bar"></div>' } }));
vi.mock('./modules/quick-search/QuickSearchOverlay.vue', () => ({ default: { template: '<div class="quick-search-overlay"></div>' } }));
vi.mock('./shared/desktopBridge', () => ({
  captureScreenshot: vi.fn(async () => ({ success: true, imageBase64: 'capture-image' })),
  openAdminConsole: vi.fn(async () => ({ success: true }))
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

function unsignedJwt(payload: Record<string, unknown>): string {
  const json = JSON.stringify(payload);
  const base64 = btoa(json).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
  return `header.${base64}.signature`;
}

describe('App route shell', () => {
  beforeEach(() => {
    installMemoryLocalStorage();
    window.history.replaceState(null, '', '#/desktop');
  });

  afterEach(() => {
    document.body.innerHTML = '';
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('opens authenticated users on the Electron sidebar preview at #/desktop', async () => {
    const { app, host } = await mountAppWithToken('#/desktop');

    expect(window.location.hash).toBe('#/desktop');
    expect(host.querySelector('.desktop-shell')).toBeTruthy();
    expect(host.querySelector('.desktop-sidebar')).toBeTruthy();
    expect(host.querySelector('.ops-admin-shell')).toBeFalsy();
    expect([...host.querySelectorAll('.desktop-nav-button span')].map((item) => item.textContent)).toEqual([
      '工作台',
      '聊天识别',
      '跟进列表',
      '客户档案',
      '话术建议'
    ]);
    expect((host.querySelector('.desktop-nav-button.active span') as HTMLElement | null)?.textContent).toBe('工作台');
    expect(host.querySelectorAll('.desktop-sidebar-actions button').length).toBe(2);
    expect(host.querySelector('.global-recognize-button')).toBeTruthy();

    app.unmount();
  });

  it('opens the full-screen operations admin at #/admin and can return to the desktop route', async () => {
    const { app, host } = await mountAppWithToken('#/admin');

    expect(host.querySelector('.ops-admin-shell')).toBeTruthy();
    expect(host.querySelector('.desktop-shell')).toBeFalsy();

    const desktopButton = [...host.querySelectorAll('.ops-admin-shell button')]
      .find((button) => button.textContent?.includes('桌面工作台')) as HTMLButtonElement | undefined;
    expect(desktopButton).toBeTruthy();
    desktopButton?.click();
    await flushUi();

    expect(window.location.hash).toBe('#/desktop');
    expect(host.querySelector('.desktop-sidebar')).toBeTruthy();

    app.unmount();
  });

  it('keeps the development console route out of the default desktop preview', async () => {
    const { app, host } = await mountAppWithToken('#/admin/dev-console');
    await flushAsyncComponent();

    expect(window.location.hash).toBe('#/admin/dev-console');
    expect(host.querySelector('.desktop-shell')).toBeFalsy();

    app.unmount();
  });

  it('switches desktop panels, triggers global recognition, and opens admin externally', async () => {
    const [{ captureScreenshot, openAdminConsole }, { triggerRecognize }] = await Promise.all([
      import('./shared/desktopBridge'),
      import('./modules/chat-recognition/recognitionStore')
    ]);
    const { app, host } = await mountAppWithToken('#/desktop');
    const navButtons = [...host.querySelectorAll('.desktop-nav-button')] as HTMLButtonElement[];

    navButtons[2].click();
    await flushUi();
    expect((host.querySelector('.desktop-nav-button.active span') as HTMLElement | null)?.textContent).toBe('跟进列表');
    expect((host.querySelector('.followup-panel') as HTMLElement | null)?.style.display).not.toBe('none');
    expect((host.querySelector('.workbench-panel') as HTMLElement | null)?.style.display).toBe('none');

    const recognizeButton = host.querySelector('.global-recognize-button') as HTMLButtonElement | null;
    expect(recognizeButton).toBeTruthy();
    recognizeButton?.click();
    await flushUi();
    expect(captureScreenshot).toHaveBeenCalled();
    expect(triggerRecognize).toHaveBeenCalledWith('BUTTON_CLICK', { imageBase64: 'capture-image' });
    expect((host.querySelector('.desktop-nav-button.active span') as HTMLElement | null)?.textContent).toBe('聊天识别');

    const adminButton = [...host.querySelectorAll('.desktop-sidebar-actions button')]
      .find((button) => button.textContent?.includes('管理后台')) as HTMLButtonElement | undefined;
    expect(adminButton).toBeTruthy();
    adminButton?.click();
    await flushUi();
    expect(openAdminConsole).toHaveBeenCalledWith('http://localhost:3000/#/admin');
    expect(window.location.hash).toBe('#/desktop');
    expect(host.querySelector('.desktop-sidebar')).toBeTruthy();

    app.unmount();
  });

  it('hides the admin shortcut for keeper accounts', async () => {
    window.history.replaceState(null, '', '#/desktop');
    localStorage.setItem('desktop_config', JSON.stringify({ apiBaseUrl: 'http://localhost:8080', accessToken: 'token-a', accountRole: 'KEEPER' }));
    const host = document.createElement('div');
    document.body.appendChild(host);
    const app = createApp(App);
    app.mount(host);
    await flushUi();

    expect([...host.querySelectorAll('.desktop-sidebar-actions button')]
      .some((button) => button.textContent?.includes('管理后台'))).toBe(false);

    app.unmount();
  });

  it('recovers the admin shortcut role from legacy cached tokens', async () => {
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
      .some((button) => button.textContent?.includes('管理后台'))).toBe(true);

    app.unmount();
  });
});
