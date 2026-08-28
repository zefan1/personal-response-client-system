import { createApp, nextTick, type App as VueApp } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App.vue';
import { resetDesktopStatus } from './shared/desktopStatusStore';

const apiMocks = vi.hoisted(() => ({
  getJson: vi.fn(),
  postJson: vi.fn()
}));

const communicationMocks = vi.hoisted(() => ({
  open: vi.fn(async () => undefined),
  loadPending: vi.fn(async () => undefined)
}));

const wsMocks = vi.hoisted(() => ({
  connect: vi.fn(),
  disconnect: vi.fn()
}));

vi.mock('./shared/apiClient', () => ({
  getJson: apiMocks.getJson,
  postJson: apiMocks.postJson
}));

vi.mock('./shared/wsMessageBus', () => ({
  connectWsMessageBus: wsMocks.connect,
  disconnectWsMessageBus: wsMocks.disconnect
}));

vi.mock('./modules/communication-history/communicationHistoryStore', () => ({
  openCommunicationHistory: communicationMocks.open
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
    props: ['accountName', 'tagManagementOnly'],
    emits: ['logout', 'switch-dev-console'],
    template: `
      <section class="ops-admin-shell" :data-tag-management-only="String(tagManagementOnly)">
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
vi.mock('./modules/communication-history/CommunicationHistoryPanel.vue', () => ({
  default: { template: '<section class="communication-history-panel">聊天记录内容</section>' }
}));
vi.mock('./modules/reply-suggestions/ReplyTaskSidebar.vue', () => ({
  default: {
    emits: ['open-all'],
    template: '<section class="reply-task-sidebar">回复任务<button data-testid="open-reply-task-drawer" @click="$emit(\'open-all\')">更多</button></section>'
  }
}));
vi.mock('./modules/reply-suggestions/ReplyTaskDrawer.vue', () => ({
  default: {
    props: ['open'],
    emits: ['clear'],
    template: '<section v-if="open" class="reply-task-drawer">回复任务列表<button data-testid="clear-reply-tasks" @click="$emit(\'clear\')">清空队列</button></section>'
  }
}));
vi.mock('./modules/abnormal-alert/AlertBell.vue', () => ({ default: { template: '<div class="alert-bell-wrap"></div>' } }));
vi.mock('./modules/batch-template/BatchTemplateOverlay.vue', () => ({ default: { template: '<div class="batch-template-overlay"></div>' } }));
vi.mock('./modules/copy-backfill/CopyBackfillAgent.vue', () => ({ default: { template: '<div class="copy-backfill-agent"></div>' } }));
vi.mock('./modules/help-mode/HelpModeAgent.vue', () => ({ default: { template: '<div class="help-mode-agent"></div>' } }));
vi.mock('./modules/new-lead-toast/NewLeadToastAgent.vue', () => ({ default: { template: '<div class="new-lead-toast-agent"></div>' } }));
vi.mock('./modules/offline/OfflineStatusBar.vue', () => ({ default: { template: '<div class="offline-status-bar"></div>' } }));
vi.mock('./modules/quick-search/QuickSearchOverlay.vue', () => ({ default: { template: '<div class="quick-search-overlay"></div>' } }));
vi.mock('./modules/templates/TemplateLibraryOverlay.vue', () => ({ default: { template: '<div class="template-library-overlay"></div>' } }));
vi.mock('./modules/templates/PersonalTemplateEditor.vue', () => ({ default: { template: '<div class="personal-template-editor"></div>' } }));
vi.mock('./shared/desktopBridge', () => ({
  captureScreenshot: vi.fn(async () => ({ success: true, imageBase64: 'capture-image' })),
  openAdminConsole: vi.fn(async () => ({ success: true })),
  getAlwaysOnTop: vi.fn(async () => ({ success: true, alwaysOnTop: false })),
  toggleAlwaysOnTop: vi.fn(async () => ({ success: true, alwaysOnTop: true }))
}));
vi.mock('./modules/chat-recognition/recognitionStore', () => ({
  beginScreenshotRecognition: vi.fn(() => 'capture-session'),
  cancelRecognitionJob: vi.fn(async () => undefined),
  failScreenshotRecognition: vi.fn(),
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

async function mountAppWithToken(hash = '#/desktop', configPatch: Record<string, unknown> = {}): Promise<MountedApp> {
  window.history.replaceState(null, '', hash);
  localStorage.setItem('desktop_config', JSON.stringify({
    apiBaseUrl: 'http://localhost:8080',
    accessToken: 'token-a',
    accountRole: 'ADMIN',
    ...configPatch
  }));
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
    resetDesktopStatus();
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
      '回复助手',
      '聊天记录'
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

  it('places the compact reply task area between batch actions and the admin entry', async () => {
    installDesktopBridge();
    const { app, host } = await mountAppWithToken('#/desktop');
    const batchButton = [...host.querySelectorAll('.sidebar-quick-button')]
      .find((button) => button.textContent?.includes('批量')) as HTMLElement | undefined;
    const replyTasks = host.querySelector('.reply-task-sidebar') as HTMLElement;
    const adminButton = host.querySelector('.desktop-sidebar-actions button') as HTMLElement | null;

    expect(batchButton).toBeTruthy();
    expect(replyTasks).toBeTruthy();
    expect(adminButton).toBeTruthy();
    if (!batchButton || !replyTasks || !adminButton) throw new Error('desktop sidebar controls are missing');
    expect(batchButton.compareDocumentPosition(replyTasks) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(replyTasks.compareDocumentPosition(adminButton) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    app.unmount();
  });

  it('clears the local reply queue and cancels tasks still being recognized', async () => {
    installDesktopBridge();
    const replies = await import('./modules/reply-suggestions/replySuggestionStore');
    const recognition = await import('./modules/chat-recognition/recognitionStore');
    replies.cleanupReplySuggestionStore();
    replies.hydrateReplySuggestionStore();
    replies.startRecognizeLoading({ sessionId: 'reply-running', source: 'BUTTON_CLICK' });
    replies.syncRecognitionJobIntoSession({
      sessionId: 'reply-running',
      jobId: 'job-running',
      status: 'RECOGNIZING'
    });

    const { app, host } = await mountAppWithToken('#/desktop');
    try {
      (host.querySelector('[data-testid="open-reply-task-drawer"]') as HTMLButtonElement).click();
      await flushUi();
      (host.querySelector('[data-testid="clear-reply-tasks"]') as HTMLButtonElement).click();
      await flushUi();

      expect(replies.replySuggestionState.sessions).toEqual([]);
      expect(replies.replySuggestionState.archivedSessions).toEqual([]);
      expect(recognition.cancelRecognitionJob).toHaveBeenCalledWith('job-running', 'reply-running');
    } finally {
      app.unmount();
      replies.cleanupReplySuggestionStore();
    }
  });

  it('opens the personal template library from the sidebar instead of the legacy quick-search flow', async () => {
    installDesktopBridge();
    const { eventBus } = await import('./shared/eventBus');
    const openings: unknown[] = [];
    eventBus.on('template-library:show', (payload) => openings.push(payload));
    const { app, host } = await mountAppWithToken('#/desktop');

    (host.querySelectorAll('.sidebar-quick-button').item(1) as HTMLButtonElement).click();
    await flushUi();

    app.unmount();
    expect(openings).toEqual([undefined]);
  });

  it('hides the Skill status chip when no subscription expiry is configured', async () => {
    installDesktopBridge();
    apiMocks.getJson.mockResolvedValueOnce({
      success: true,
      data: {
        accountName: 'Admin',
        role: 'ADMIN',
        skillStatus: { status: 'UNKNOWN', expireAt: null, daysLeft: null, label: '技能有效期未配置' }
      },
      errorCode: null,
      message: null
    });

    const { app, host } = await mountAppWithToken('#/desktop');

    expect(host.querySelector('.skill-status')).toBeFalsy();
    expect(host.textContent).not.toContain('未配置');
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

  it('refreshes an expired session before showing the login page', async () => {
    installDesktopBridge();
    const { eventBus } = await import('./shared/eventBus');
    const { app, host } = await mountAppWithToken('#/desktop', {
      refreshToken: 'refresh-a',
      accountUsername: 'admin'
    });
    apiMocks.postJson.mockImplementation(async (path: string) => path === '/api/v1/auth/refresh'
      ? {
          success: true,
          data: {
            accessToken: 'token-b',
            refreshToken: 'refresh-a',
            account: { username: 'admin', displayName: 'Admin', role: 'ADMIN', permissions: [] }
          },
          errorCode: null,
          message: null
        }
      : { success: true, data: null, errorCode: null, message: null });

    eventBus.emit('auth:expired', { message: '登录已过期，请重新登录' });
    await flushUi();
    await flushUi();

    expect(host.querySelector('.login-shell')).toBeFalsy();
    expect(apiMocks.postJson).toHaveBeenCalledWith('/api/v1/auth/refresh', {
      refreshToken: 'refresh-a',
      username: 'admin'
    });
    expect(JSON.parse(localStorage.getItem('desktop_config') ?? '{}')).toMatchObject({
      accessToken: 'token-b',
      refreshToken: 'refresh-a',
      accountUsername: 'admin'
    });
    app.unmount();
  });

  it('silently refreshes an already expired saved access token during startup', async () => {
    installDesktopBridge();
    apiMocks.postJson.mockImplementation(async (path: string) => path === '/api/v1/auth/refresh'
      ? {
          success: true,
          data: {
            accessToken: unsignedJwt({ exp: Math.floor(Date.now() / 1000) + 7200, role: 'ADMIN' }),
            refreshToken: 'refresh-b',
            account: { username: 'admin', displayName: 'Admin', role: 'ADMIN', permissions: [] }
          },
          errorCode: null,
          message: null
        }
      : { success: true, data: null, errorCode: null, message: null });

    const { app, host } = await mountAppWithToken('#/desktop', {
      accessToken: unsignedJwt({ exp: Math.floor(Date.now() / 1000) - 60, role: 'ADMIN' }),
      refreshToken: 'refresh-a',
      accountUsername: 'admin'
    });
    await flushUi();
    await flushUi();

    expect(apiMocks.postJson).toHaveBeenCalledWith('/api/v1/auth/refresh', {
      refreshToken: 'refresh-a',
      username: 'admin'
    });
    expect(host.querySelector('.login-shell')).toBeFalsy();
    expect(JSON.parse(localStorage.getItem('desktop_config') ?? '{}')).toMatchObject({
      refreshToken: 'refresh-b',
      accountUsername: 'admin'
    });
    app.unmount();
  });

  it('opens the desktop after a successful login', async () => {
    installDesktopBridge();
    apiMocks.postJson.mockResolvedValueOnce({
      success: true,
      data: {
        accessToken: 'token-b',
        refreshToken: 'refresh-b',
        account: { username: 'admin', displayName: 'Admin', role: 'ADMIN', permissions: [] }
      },
      errorCode: null,
      message: null
    });
    const { app, host } = await mountAppWithToken('#/desktop', { accessToken: '' });
    const username = host.querySelector('input[autocomplete="username"]') as HTMLInputElement;
    const password = host.querySelector('input[autocomplete="current-password"]') as HTMLInputElement;
    username.value = 'admin';
    username.dispatchEvent(new Event('input'));
    password.value = 'secret';
    password.dispatchEvent(new Event('input'));

    (host.querySelector('.login-panel button[type="submit"]') as HTMLButtonElement).click();
    await flushUi();
    await flushUi();

    expect(host.querySelector('.desktop-shell')).toBeTruthy();
    app.unmount();
  });

  it('opens the desktop for an existing valid session', async () => {
    installDesktopBridge();

    const { app, host } = await mountAppWithToken('#/desktop', {
      accessToken: unsignedJwt({ exp: Math.floor(Date.now() / 1000) + 7200, role: 'ADMIN' })
    });
    await flushUi();

    expect(host.querySelector('.desktop-shell')).toBeTruthy();
    app.unmount();
  });

  it('does not open the desktop while logged out', async () => {
    installDesktopBridge();

    const { app, host } = await mountAppWithToken('#/desktop', { accessToken: '' });

    expect(host.querySelector('.login-shell')).toBeTruthy();
    app.unmount();
  });

  it('revokes the current refresh session on logout and clears local state even when logout fails', async () => {
    installDesktopBridge();
    apiMocks.postJson.mockRejectedValueOnce(new Error('backend unavailable'));
    const { app, host } = await mountAppWithToken('#/desktop', {
      refreshToken: 'desktop-refresh',
      accountUsername: 'admin'
    });

    const logoutButton = [...host.querySelectorAll('.desktop-sidebar-actions button')]
      .find((button) => button.textContent?.trim() === '退出') as HTMLButtonElement;
    logoutButton.click();
    await flushUi();
    await flushUi();

    expect(apiMocks.postJson).toHaveBeenCalledWith('/api/v1/auth/logout', {
      refreshToken: 'desktop-refresh',
      username: 'admin'
    });
    expect(host.querySelector('.login-shell')).toBeTruthy();
    expect(JSON.parse(localStorage.getItem('desktop_config') ?? '{}')).toMatchObject({
      accessToken: '',
      refreshToken: ''
    });

    app.unmount();
  });

  it('does not let an old account refresh overwrite a logged-out or newly logged-in account', async () => {
    installDesktopBridge();
    const refreshA = deferred<unknown>();
    const refreshB = deferred<unknown>();
    let refreshCalls = 0;
    apiMocks.postJson.mockImplementation((path: string) => {
      if (path === '/api/v1/auth/refresh') {
        refreshCalls += 1;
        return refreshCalls === 1 ? refreshA.promise : refreshB.promise;
      }
      if (path === '/api/v1/auth/login') {
        return Promise.resolve({
          success: true,
          data: {
            accessToken: 'token-b',
            refreshToken: 'refresh-b',
            account: { username: 'account-b', displayName: 'Account B', role: 'ADMIN', permissions: [] }
          },
          errorCode: null,
          message: null
        });
      }
      return Promise.resolve({ success: true, data: null, errorCode: null, message: null });
    });
    const { eventBus } = await import('./shared/eventBus');
    const { app, host } = await mountAppWithToken('#/desktop', {
      refreshToken: 'refresh-a',
      accountUsername: 'account-a'
    });

    eventBus.emit('auth:expired', { message: 'expired A' });
    await flushUi();
    expect(refreshCalls).toBe(1);

    const logoutButton = [...host.querySelectorAll('.desktop-sidebar-actions button')]
      .find((button) => button.textContent?.trim() === '退出') as HTMLButtonElement;
    logoutButton.click();
    await flushUi();

    const username = host.querySelector('input[autocomplete="username"]') as HTMLInputElement;
    const password = host.querySelector('input[autocomplete="current-password"]') as HTMLInputElement;
    username.value = 'account-b';
    username.dispatchEvent(new Event('input'));
    password.value = 'secret';
    password.dispatchEvent(new Event('input'));
    (host.querySelector('.login-panel button[type="submit"]') as HTMLButtonElement).click();
    await flushUi();
    await flushUi();

    eventBus.emit('auth:expired', { message: 'expired B' });
    await flushUi();
    expect(refreshCalls).toBe(2);

    refreshA.resolve({
      success: true,
      data: {
        accessToken: 'stale-token-a',
        refreshToken: 'stale-refresh-a',
        account: { username: 'account-a', displayName: 'Account A', role: 'ADMIN', permissions: [] }
      }
    });
    await flushUi();
    eventBus.emit('auth:expired', { message: 'duplicate B expiry' });
    await flushUi();
    expect(refreshCalls).toBe(2);

    refreshB.resolve({
      success: true,
      data: {
        accessToken: 'refreshed-token-b',
        refreshToken: 'refreshed-refresh-b',
        account: { username: 'account-b', displayName: 'Account B', role: 'ADMIN', permissions: [] }
      }
    });
    await flushUi();
    await flushUi();

    expect(JSON.parse(localStorage.getItem('desktop_config') ?? '{}')).toMatchObject({
      accessToken: 'refreshed-token-b',
      refreshToken: 'refreshed-refresh-b',
      accountUsername: 'account-b'
    });
    expect(host.querySelector('.login-shell')).toBeFalsy();
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
    const [{ captureScreenshot, openAdminConsole }, { triggerRecognize }, { eventBus }, { customerProfileState }] = await Promise.all([
      import('./shared/desktopBridge'),
      import('./modules/chat-recognition/recognitionStore'),
      import('./shared/eventBus'),
      import('./modules/customer-profile/customerProfileStore')
    ]);
    const openedTabs: unknown[] = [];
    const templateLibraryEvents: unknown[] = [];
    eventBus.on('followup:switch-tab', (payload) => openedTabs.push(payload));
    eventBus.on('template-library:show', (payload) => templateLibraryEvents.push(payload));
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
      '话术库',
      '批量',
      '预约'
    ]);
    const recognizeButton = actionButtons[0];
    expect(recognizeButton).toBeTruthy();
    recognizeButton?.click();
    await flushUi();
    expect(captureScreenshot).toHaveBeenCalled();
    expect(triggerRecognize).toHaveBeenCalledWith('BUTTON_CLICK', { imageBase64: 'capture-image' }, 'capture-session');
    expect((host.querySelector('.desktop-nav-button.active .nav-label') as HTMLElement | null)?.textContent).toBe('回复助手');

    navButtons[0].click();
    await flushUi();
    expect((host.querySelector('.desktop-nav-button.active .nav-label') as HTMLElement | null)?.textContent).toBe('工作台');

    actionButtons[2].click();
    await flushUi();
    expect((host.querySelector('.task-queue-backdrop') as HTMLElement | null)?.style.display).not.toBe('none');
    expect(host.querySelector('.task-queue-drawer .followup-panel')).toBeTruthy();
    expect(openedTabs.at(-1)).toEqual({ tab: 'DUE_TODAY' });

    (host.querySelector('.task-queue-drawer .icon-close-button') as HTMLButtonElement | null)?.click();
    actionButtons[2].click();
    await flushUi();
    expect(openedTabs).toEqual([{ tab: 'DUE_TODAY' }, { tab: 'DUE_TODAY' }]);

    eventBus.emit('customer:selected', {
      phone: '18800002222',
      scene: 'ACTIVE_REPLY',
      leadType: 'XIAN_SUO',
      reminderType: 'DUE_TODAY',
      sourceFrom: 'FOLLOWUP_LIST'
    });
    await flushUi();
    expect((host.querySelector('.task-queue-backdrop') as HTMLElement | null)?.style.display).toBe('none');
    expect((host.querySelector('.desktop-nav-button.active .nav-label') as HTMLElement | null)?.textContent).toBe('客户档案');
    expect(host.querySelector('.profile-return-button')).toBeTruthy();
    customerProfileState.profile = {
      phoneFull: '18800002222',
      customer: {
        phone: '188****2222',
        phoneFull: '18800002222',
        nickname: '今日待跟进客户',
        leadType: 'XIAN_SUO',
        sourceTable: '私域客资管理表'
      }
    };

    actionButtons[1].click();
    await flushUi();
    expect(templateLibraryEvents.at(-1)).toMatchObject({
      phone: '18800002222',
      nickname: '今日待跟进客户',
      reminderType: 'DUE_TODAY',
      returnToFollowups: true
    });

    eventBus.emit('customer:selected', {
      phone: '18800002222',
      scene: 'CHAT_RECOGNIZE',
      leadType: 'XIAN_SUO',
      reminderType: 'DUE_TODAY',
      sourceFrom: 'FOLLOWUP_LIST'
    });
    await flushUi();

    (host.querySelector('.profile-return-button') as HTMLButtonElement | null)?.click();
    await flushUi();
    expect((host.querySelector('.desktop-nav-button.active .nav-label') as HTMLElement | null)?.textContent).toBe('工作台');
    expect((host.querySelector('.task-queue-backdrop') as HTMLElement | null)?.style.display).not.toBe('none');
    expect(openedTabs.at(-1)).toEqual({ tab: 'DUE_TODAY' });

    actionButtons[1].click();
    await flushUi();
    expect(templateLibraryEvents.at(-1)).toEqual(undefined);

    (host.querySelector('.task-queue-drawer .icon-close-button') as HTMLButtonElement | null)?.click();

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

  it('opens communication history from a profile', async () => {
    const { eventBus } = await import('./shared/eventBus');
    installDesktopBridge();
    const { app, host } = await mountAppWithToken('#/desktop');

    eventBus.emit('communication:open', { phone: '18800001111', view: 'summaries' });
    await flushUi();

    expect(communicationMocks.open).toHaveBeenCalledWith('18800001111', 'summaries');
    expect((host.querySelector('.communication-history-panel') as HTMLElement).style.display).not.toBe('none');
    expect((host.querySelector('.desktop-nav-button.active .nav-label') as HTMLElement).textContent)
      .toBe('聊天记录');

    eventBus.emit('communication:return-profile', { phone: '18800001111' });
    await flushUi();
    expect((host.querySelector('.customer-panel') as HTMLElement).style.display).not.toBe('none');
    app.unmount();
  });

  it('shows the current desktop download link on the web login page', async () => {
    apiMocks.getJson.mockResolvedValueOnce({
      success: true,
      data: { version: '2.0.0', fileSize: 1234, changelog: 'new release' },
      errorCode: null,
      message: null
    });

    const { app, host } = await mountAppWithToken('#/admin', { accessToken: '' });
    await flushUi();

    expect(apiMocks.getJson).toHaveBeenCalledWith('/api/v1/desktop/latest?platform=WINDOWS');
    const link = host.querySelector('.desktop-download-link') as HTMLAnchorElement | null;
    expect(link?.textContent).toContain('下载桌面工作台');
    expect(link?.href).toBe('http://localhost:8080/api/v1/desktop/download?platform=WINDOWS');
    expect(host.textContent).toContain('最新版 2.0.0');
    app.unmount();
  });

  it('connects the session message channel as soon as an authenticated desktop starts', async () => {
    installDesktopBridge();
    const { app } = await mountAppWithToken('#/desktop');

    expect(wsMocks.connect).toHaveBeenCalledTimes(1);

    app.unmount();
  });

  it('opens the customer panel when a reply candidate requests a full profile preview', async () => {
    const { eventBus } = await import('./shared/eventBus');
    installDesktopBridge();
    const { app, host } = await mountAppWithToken('#/desktop');

    const replyNav = [...host.querySelectorAll('.desktop-nav-button')]
      .find((button) => button.textContent?.includes('回复助手')) as HTMLButtonElement;
    replyNav.click();
    await flushUi();
    expect((host.querySelector('.desktop-nav-button.active .nav-label') as HTMLElement | null)?.textContent).toBe('回复助手');

    eventBus.emit('candidate:preview', {
      sessionId: 'session-a',
      taskId: 'task-a',
      candidate: { phone: '18800001111', nickname: 'Alice' }
    });
    await flushUi();

    expect((host.querySelector('.desktop-nav-button.active .nav-label') as HTMLElement | null)?.textContent).toBe('客户档案');
    expect((host.querySelector('.customer-panel') as HTMLElement | null)?.style.display).not.toBe('none');
    expect((host.querySelector('.reply-panel') as HTMLElement | null)?.style.display).toBe('none');
    app.unmount();
  });

  it('returns to the originating followup list after a template is confirmed as sent', async () => {
    const { eventBus } = await import('./shared/eventBus');
    installDesktopBridge();
    const { app, host } = await mountAppWithToken('#/desktop');

    eventBus.emit('followup:switch-tab', { tab: 'DUE_TODAY' });
    await flushUi();
    eventBus.emit('customer:selected', {
      phone: '18800002222',
      scene: 'ACTIVE_REPLY',
      leadType: 'XIAN_SUO',
      reminderType: 'DUE_TODAY',
      sourceFrom: 'FOLLOWUP_LIST'
    });
    await flushUi();

    expect((host.querySelector('.task-queue-backdrop') as HTMLElement | null)?.style.display).toBe('none');
    expect(host.querySelector('.profile-return-button')).toBeTruthy();

    eventBus.emit('quick-search:sent', { returnToFollowups: true });
    await flushUi();

    expect((host.querySelector('.desktop-nav-button.active .nav-label') as HTMLElement | null)?.textContent).toBe('工作台');
    expect((host.querySelector('.task-queue-backdrop') as HTMLElement | null)?.style.display).not.toBe('none');
    expect(host.querySelector('.profile-return-button')).toBeFalsy();
    app.unmount();
  });

  it('focuses the reply assistant when the global screenshot capture fails', async () => {
    const [{ captureScreenshot }, { triggerRecognize }] = await Promise.all([
      import('./shared/desktopBridge'),
      import('./modules/chat-recognition/recognitionStore')
    ]);
    installDesktopBridge();
    vi.mocked(captureScreenshot).mockResolvedValueOnce({
      success: false,
      error: 'CAPTURE_FAILED',
      message: '当前窗口未显示可识别的主聊天会话'
    });
    const { app, host } = await mountAppWithToken('#/desktop');

    (host.querySelector('.sidebar-quick-actions button') as HTMLButtonElement).click();
    await flushUi();

    expect((host.querySelector('.desktop-nav-button.active .nav-label') as HTMLElement | null)?.textContent)
      .toBe('回复助手');
    expect(host.textContent).toContain('当前窗口未显示可识别的主聊天会话');
    expect(triggerRecognize).not.toHaveBeenCalled();
    app.unmount();
  });

  it('focuses the reply assistant for recognition failure events', async () => {
    const { eventBus } = await import('./shared/eventBus');
    installDesktopBridge();
    const { app, host } = await mountAppWithToken('#/desktop');

    eventBus.emit('recognize:image-failed', {
      sessionId: 'failure-session',
      errorCode: '30-10001',
      message: '未能从图片中识别到聊天内容，请确认截图中包含聊天窗口'
    });
    await flushUi();

    expect((host.querySelector('.desktop-nav-button.active .nav-label') as HTMLElement | null)?.textContent)
      .toBe('回复助手');
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

  it('allows delegated tag managers into the browser admin with a restricted console', async () => {
    apiMocks.getJson.mockResolvedValueOnce({
      success: true,
      data: {
        accountName: 'Leader',
        role: 'LEADER',
        permissions: ['TAG_MANAGEMENT'],
        skillStatus: { status: 'UNKNOWN', expireAt: null, daysLeft: null, label: '技能有效期未配置' }
      },
      errorCode: null,
      message: null
    });
    window.history.replaceState(null, '', '#/admin');
    localStorage.setItem('desktop_config', JSON.stringify({
      apiBaseUrl: 'http://localhost:8080',
      accessToken: 'token-a',
      accountRole: 'LEADER',
      accountPermissions: []
    }));
    const host = document.createElement('div');
    document.body.appendChild(host);
    const app = createApp(App);
    app.mount(host);
    await flushUi();

    const console = host.querySelector('.ops-admin-shell') as HTMLElement | null;
    expect(console).toBeTruthy();
    expect(console?.dataset.tagManagementOnly).toBe('true');

    app.unmount();
  });

  it('keeps accounts without admin or tag permission out of the browser admin', async () => {
    apiMocks.getJson.mockResolvedValueOnce({
      success: true,
      data: {
        accountName: 'Keeper',
        role: 'KEEPER',
        permissions: [],
        skillStatus: { status: 'UNKNOWN', expireAt: null, daysLeft: null, label: '技能有效期未配置' }
      },
      errorCode: null,
      message: null
    });
    window.history.replaceState(null, '', '#/admin');
    localStorage.setItem('desktop_config', JSON.stringify({
      apiBaseUrl: 'http://localhost:8080',
      accessToken: 'token-a',
      accountRole: 'KEEPER',
      accountPermissions: []
    }));
    const host = document.createElement('div');
    document.body.appendChild(host);
    const app = createApp(App);
    app.mount(host);
    await flushUi();

    expect(host.querySelector('.ops-admin-shell')).toBeFalsy();
    expect(host.textContent).toContain('当前账号没有后台管理权限');

    app.unmount();
  });
});

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}
