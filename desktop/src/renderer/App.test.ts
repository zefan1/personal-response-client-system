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
    emits: ['logout', 'switch-desktop'],
    template: `
      <section class="admin-console">
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

async function mountAppWithToken(): Promise<MountedApp> {
  localStorage.setItem('desktop_config', JSON.stringify({ apiBaseUrl: 'http://localhost:8080', accessToken: 'token-a' }));
  const host = document.createElement('div');
  document.body.appendChild(host);
  const app = createApp(App);
  app.mount(host);
  await flushUi();
  return { app, host };
}

describe('App desktop shell', () => {
  beforeEach(() => {
    installMemoryLocalStorage();
  });

  afterEach(() => {
    document.body.innerHTML = '';
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('opens authenticated users on the desktop sidebar instead of the admin console', async () => {
    const { app, host } = await mountAppWithToken();

    expect(host.querySelector('.desktop-shell')).toBeTruthy();
    expect(host.querySelector('.desktop-sidebar')).toBeTruthy();
    expect(host.querySelector('.admin-console')).toBeFalsy();
    expect([...host.querySelectorAll('.desktop-nav-button span')].map((item) => item.textContent)).toEqual([
      '工作台',
      '聊天识别',
      '跟进列表',
      '客户档案',
      '话术建议'
    ]);
    expect((host.querySelector('.desktop-nav-button.active span') as HTMLElement | null)?.textContent).toBe('工作台');

    app.unmount();
  });

  it('switches desktop panels from the sidebar and keeps the admin console as an explicit action', async () => {
    const { app, host } = await mountAppWithToken();
    const navButtons = [...host.querySelectorAll('.desktop-nav-button')] as HTMLButtonElement[];

    navButtons[2].click();
    await flushUi();
    expect((host.querySelector('.desktop-nav-button.active span') as HTMLElement | null)?.textContent).toBe('跟进列表');
    expect((host.querySelector('.followup-panel') as HTMLElement | null)?.style.display).not.toBe('none');
    expect((host.querySelector('.workbench-panel') as HTMLElement | null)?.style.display).toBe('none');

    const adminButton = [...host.querySelectorAll('.desktop-sidebar-actions button')]
      .find((button) => button.textContent?.includes('管理后台')) as HTMLButtonElement | undefined;
    expect(adminButton).toBeTruthy();
    adminButton?.click();
    await flushUi();
    expect(host.querySelector('.admin-console')).toBeTruthy();

    app.unmount();
  });
});
