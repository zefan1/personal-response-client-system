<template>
  <main v-if="!session.accessToken" class="login-shell">
    <form class="login-panel" @submit.prevent="login">
      <div>
        <h1>私域辅助系统</h1>
        <p>{{ isElectronRuntime ? '登录后进入桌面工作台。' : '网页登录后进入运营后台。' }}</p>
      </div>
      <label>
        API 地址
        <input v-model="loginForm.apiBaseUrl" autocomplete="url" />
      </label>
      <label>
        账号
        <input v-model="loginForm.username" autocomplete="username" />
      </label>
      <label>
        密码
        <input v-model="loginForm.password" autocomplete="current-password" type="password" />
      </label>
      <label v-if="isElectronRuntime">
        入口
        <select v-model="loginForm.mode">
          <option value="desktop">桌面工作台</option>
        </select>
      </label>
      <p v-if="loginError" class="admin-message error">{{ loginError }}</p>
      <button class="primary" type="submit" :disabled="loginLoading">{{ loginLoading ? '登录中' : '登录' }}</button>
    </form>
  </main>

  <AdminConsole
    v-else-if="currentMode === 'admin' && !isElectronRuntime && canOpenAdmin && !adminAccessPending"
    :account-name="session.accountName"
    :tag-management-only="effectiveRole !== 'ADMIN'"
    @logout="logout"
  />

  <main v-else-if="currentMode === 'admin' && !isElectronRuntime" class="login-shell">
    <section class="login-panel">
      <div>
        <h1>运营后台</h1>
        <p>{{ adminAccessPending ? '正在校验后台权限。' : '当前账号没有后台管理权限。' }}</p>
      </div>
      <button v-if="!adminAccessPending" class="secondary" type="button" @click="logout">退出登录</button>
    </section>
  </main>

  <AdminDevConsole
    v-else-if="currentMode === 'admin-dev' && devConsoleEnabled && !isElectronRuntime && effectiveRole === 'ADMIN'"
    :account-name="session.accountName"
    @logout="logout"
    @switch-admin="setMode('admin')"
  />

  <main v-else-if="currentMode === 'admin-dev' && !isElectronRuntime" class="login-shell">
    <section class="login-panel">
      <div>
        <h1>开发调试台</h1>
        <p>只有管理员可以使用开发调试台。</p>
      </div>
      <button class="secondary" type="button" @click="setMode('admin')">返回正式后台</button>
    </section>
  </main>

  <main v-else class="desktop-shell">
    <aside class="desktop-sidebar">
      <div class="desktop-brand">
        <div class="account-card">
          <div class="account-name-row">
            <strong>{{ displayAccountName }}</strong>
          </div>
        </div>
        <span :class="['skill-status', skillStatusClass]">{{ skillStatusCompactLabel }}</span>
      </div>
      <nav class="desktop-nav" aria-label="桌面工作台导航">
        <button
          v-for="item in desktopNavItems"
          :key="item.key"
          class="desktop-nav-button"
          :class="{ active: activeDesktopPanel === item.key }"
          type="button"
          @click="selectDesktopPanel(item.key)"
        >
          <span class="nav-icon" aria-hidden="true">{{ item.icon }}</span>
          <span class="nav-copy">
            <span class="nav-label">{{ item.title }}</span>
            <small>{{ item.description }}</small>
          </span>
        </button>
      </nav>
      <nav class="sidebar-quick-actions" aria-label="全局快捷操作">
        <button class="primary sidebar-quick-button" type="button" title="识别当前聊天" @click="recognizeFromAnywhere">
          <span class="action-icon" aria-hidden="true">识</span>
          <strong class="action-label">{{ recognitionState.isRecognizePending ? '继续识别' : '识别' }}</strong>
        </button>
        <button class="secondary sidebar-quick-button" type="button" title="打开模板" @click="openQuickSearch">
          <span class="action-icon" aria-hidden="true">模</span>
          <strong class="action-label">模板</strong>
        </button>
        <button class="secondary sidebar-quick-button" type="button" title="打开待办队列" @click="openTaskQueue()">
          <span class="action-icon" aria-hidden="true">批</span>
          <strong class="action-label">批量</strong>
        </button>
      </nav>
      <div class="desktop-sidebar-actions">
        <button v-if="canOpenAdmin" class="secondary small" type="button" title="在浏览器打开管理后台" @click="openAdmin">后台</button>
        <button class="secondary small" type="button" @click="logout">退出</button>
      </div>
    </aside>

    <section class="desktop-main">
      <header class="desktop-mode-bar">
        <span class="desktop-page-icon" aria-hidden="true">{{ activeDesktopNav.icon }}</span>
        <div>
          <strong>{{ activeDesktopNav.title }}</strong>
          <p>{{ activeDesktopNav.description }}</p>
        </div>
        <div class="desktop-mode-tools">
          <AlertBell />
          <button
            v-if="isElectronRuntime"
            class="pin-window-button"
            type="button"
            :aria-label="alwaysOnTop ? '取消置顶' : '窗口置顶'"
            :aria-pressed="alwaysOnTop"
            :title="alwaysOnTop ? '取消窗口置顶' : '窗口置顶'"
            @click="togglePinWindow"
          >
            <span aria-hidden="true">{{ alwaysOnTop ? '顶' : '置' }}</span>
          </button>
        </div>
      </header>
      <p
        v-if="topGlobalAlert"
        :class="['desktop-alert-banner', `level-${topGlobalAlert.level.toLowerCase()}`]"
      >
        <strong>{{ topGlobalAlert.title }}</strong>
        <span>{{ topGlobalAlert.message }}</span>
      </p>
      <p v-if="desktopNoticeState.message && !topGlobalAlert" class="admin-message" :class="{ error: desktopNoticeState.kind === 'error' }">
        {{ desktopNoticeState.message }}
      </p>
      <OfflineStatusBar v-if="!topGlobalAlert" />
      <CopyBackfillAgent />
      <NewLeadToastAgent />
      <QuickSearchOverlay />
      <BatchTemplateOverlay />
      <HelpModeAgent />
      <ClipboardCaptureConfirmAgent />
      <WorkbenchPanel v-show="activeDesktopPanel === 'workbench'" />
      <ChatRecognitionPanel v-show="false" class="recognition-controller" />
      <CustomerProfilePanel v-show="activeDesktopPanel === 'customer'" />
      <ReplySuggestionPanel v-show="activeDesktopPanel === 'reply'" />
      <div v-show="taskQueueOpen" class="task-queue-backdrop" @click.self="closeTaskQueue">
        <aside class="task-queue-drawer" aria-label="待办队列">
          <header>
            <div>
              <h2>待办队列</h2>
              <p>选择客户后可批量发模板，也可直接查看档案。</p>
            </div>
            <button class="icon-close-button" type="button" aria-label="关闭待办队列" title="关闭待办队列" @click="closeTaskQueue">
              <span aria-hidden="true">×</span>
            </button>
          </header>
          <FollowupListPanel />
        </aside>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, nextTick, reactive, ref, onBeforeUnmount, onMounted } from 'vue';
import AlertBell from './modules/abnormal-alert/AlertBell.vue';
import { cleanupAbnormalAlertRouter, initializeAbnormalAlertRouter } from './modules/abnormal-alert/alertStore';
import { topGlobalAlert } from './modules/abnormal-alert/globalAlertCenter';
import AdminConsole from './modules/admin/AdminConsole.vue';
import ChatRecognitionPanel from './modules/chat-recognition/ChatRecognitionPanel.vue';
import ClipboardCaptureConfirmAgent from './modules/chat-recognition/ClipboardCaptureConfirmAgent.vue';
import BatchTemplateOverlay from './modules/batch-template/BatchTemplateOverlay.vue';
import CopyBackfillAgent from './modules/copy-backfill/CopyBackfillAgent.vue';
import CustomerProfilePanel from './modules/customer-profile/CustomerProfilePanel.vue';
import FollowupListPanel from './modules/followup-list/FollowupListPanel.vue';
import HelpModeAgent from './modules/help-mode/HelpModeAgent.vue';
import NewLeadToastAgent from './modules/new-lead-toast/NewLeadToastAgent.vue';
import OfflineStatusBar from './modules/offline/OfflineStatusBar.vue';
import QuickSearchOverlay from './modules/quick-search/QuickSearchOverlay.vue';
import ReplySuggestionPanel from './modules/reply-suggestions/ReplySuggestionPanel.vue';
import { postJson } from './shared/apiClient';
import { captureScreenshot, getAlwaysOnTop, openAdminConsole, toggleAlwaysOnTop } from './shared/desktopBridge';
import { clearDesktopNotice, desktopNoticeState, setDesktopNotice } from './shared/desktopNoticeStore';
import { desktopStatusState, loadDesktopStatus, resetDesktopStatus } from './shared/desktopStatusStore';
import { loadDesktopConfig, saveDesktopConfig } from './shared/config';
import { eventBus } from './shared/eventBus';
import { cleanupStageSuggestionHandler, initializeStageSuggestionHandler } from './modules/stage-suggestion/stageSuggestionHandler';
import WorkbenchPanel from './modules/workbench/WorkbenchPanel.vue';
import { recognitionState, triggerRecognize } from './modules/chat-recognition/recognitionStore';

type LoginPayload = {
  accessToken: string;
  refreshToken?: string;
  account?: {
    username?: string;
    displayName?: string;
    role?: string;
    permissions?: string[];
  };
  userInfo?: {
    username?: string;
    displayName?: string;
    role?: string;
    permissions?: string[];
  };
};

type DesktopPanelKey = 'workbench' | 'customer' | 'reply';
type RouteMode = 'admin' | 'desktop' | 'admin-dev';
type AccountRole = 'ADMIN' | 'LEADER' | 'KEEPER' | '';

type DesktopNavItem = {
  key: DesktopPanelKey;
  title: string;
  description: string;
  icon: string;
};

const desktopNavItems: DesktopNavItem[] = [
  { key: 'workbench', title: '工作台', description: '今日任务', icon: '今' },
  { key: 'customer', title: '客户档案', description: '资料与阶段', icon: '客' },
  { key: 'reply', title: '回复助手', description: '建议与求助', icon: '回' }
];

const config = loadDesktopConfig();
const devConsoleEnabled = !import.meta.env.PROD;
const isElectronRuntime = computed(() => hasDesktopBridge());
const AdminDevConsole = devConsoleEnabled
  ? defineAsyncComponent(async () => (await import('./modules/admin/AdminDevConsole.vue')).default)
  : null;
const currentMode = ref<RouteMode>(modeFromHash());
const activeDesktopPanel = ref<DesktopPanelKey>('workbench');
const taskQueueOpen = ref(false);
const alwaysOnTop = ref(false);
const loginLoading = ref(false);
const loginError = ref('');
const loginForm = reactive({
  apiBaseUrl: config.apiBaseUrl,
  username: config.accountUsername,
  password: '',
  mode: (currentMode.value === 'desktop' ? 'desktop' : 'admin') as 'admin' | 'desktop'
});
const session = reactive({
  accessToken: config.accessToken,
  refreshToken: config.refreshToken,
  accountUsername: config.accountUsername || readJwtUsername(config.accessToken),
  accountName: config.accessToken ? '当前账号' : '',
  role: resolveInitialRole(config),
  permissions: normalizePermissions(config.accountPermissions)
});
const activeDesktopNav = computed(() => desktopNavItems.find((item) => item.key === activeDesktopPanel.value) ?? desktopNavItems[0]);
const displayAccountName = computed(() => desktopStatusState.accountName || session.accountName || '当前账号');
const effectiveRole = computed<AccountRole>(() => normalizeRole(desktopStatusState.role) || session.role);
const effectivePermissions = computed(() => desktopStatusState.loaded
  ? desktopStatusState.permissions
  : session.permissions);
const canOpenAdmin = computed(() => effectiveRole.value === 'ADMIN' || effectivePermissions.value.includes('TAG_MANAGEMENT'));
const adminAccessPending = computed(() => Boolean(
  session.accessToken
  && !desktopStatusState.loaded
  && effectiveRole.value !== 'ADMIN'
));
const skillStatusClass = computed(() => desktopStatusState.skillStatus.status.toLowerCase());
const skillStatusCompactLabel = computed(() => {
  const status = desktopStatusState.skillStatus.status;
  const expireAt = desktopStatusState.skillStatus.expireAt?.slice(0, 10);
  if (status === 'OK' && expireAt) return `有效至 ${expireAt}`;
  if (status === 'EXPIRING') return expireAt ? `即将到期 ${expireAt}` : '即将到期';
  if (status === 'EXPIRED') return '已到期';
  return '未配置';
});
const eventDisposers: Array<() => void> = [];
let refreshPromise: Promise<void> | null = null;

onMounted(() => {
  normalizeInitialHash();
  window.addEventListener('hashchange', syncModeFromHash);
  initializeAbnormalAlertRouter();
  initializeStageSuggestionHandler();
  if (session.accessToken) {
    void refreshDesktopStatus();
  }
  if (hasDesktopBridge()) {
    void refreshAlwaysOnTopState();
  }
  eventDisposers.push(eventBus.on('followup:switch-tab', () => {
    taskQueueOpen.value = true;
  }));
  eventDisposers.push(eventBus.on('customer:selected', () => selectDesktopPanel('customer')));
  const focusReplyAssistant = () => selectDesktopPanel('reply');
  eventDisposers.push(eventBus.on('recognize:result', focusReplyAssistant));
  eventDisposers.push(eventBus.on('recognize:image-failed', focusReplyAssistant));
  eventDisposers.push(eventBus.on('recognize:failed', focusReplyAssistant));
  eventDisposers.push(eventBus.on('recognize:timeout', focusReplyAssistant));
  eventDisposers.push(eventBus.on('recognize:multiple', focusReplyAssistant));
  eventDisposers.push(eventBus.on('suggestion:show', () => selectDesktopPanel('reply')));
  eventDisposers.push(eventBus.on('desktop:recognize-request', () => {
    void recognizeFromAnywhere();
  }));
  eventDisposers.push(eventBus.on<{ message?: string }>('auth:expired', (payload) => {
    void handleAuthExpired(payload?.message);
  }));
  eventDisposers.push(eventBus.on<{ configKey?: string; configKeys?: string[] }>('CONFIG_REFRESH', (payload) => {
    const keys = [payload?.configKey, ...(payload?.configKeys ?? [])].filter(Boolean) as string[];
    if (!keys.length || keys.some((key) => key === 'desktop.clipboard_screenshot_confirm_prompt_s' || key.startsWith('desktop.'))) {
      void refreshDesktopStatus();
    }
  }));
});
onBeforeUnmount(() => {
  window.removeEventListener('hashchange', syncModeFromHash);
  cleanupAbnormalAlertRouter();
  cleanupStageSuggestionHandler();
  eventDisposers.splice(0).forEach((dispose) => dispose());
});

async function login() {
  loginLoading.value = true;
  loginError.value = '';
  try {
    saveDesktopConfig({ apiBaseUrl: loginForm.apiBaseUrl.trim().replace(/\/$/, '') });
    const path = loginForm.mode === 'admin' && !hasDesktopBridge() ? '/admin/api/v1/auth/login' : '/api/v1/auth/login';
    const response = await postJson<LoginPayload>(path, {
      username: loginForm.username.trim(),
      password: loginForm.password
    });
    if (!response.success || !response.data?.accessToken) {
      throw new Error(response.message ?? response.errorCode ?? '登录失败');
    }
    const account = response.data.account ?? response.data.userInfo;
    session.accessToken = response.data.accessToken;
    session.refreshToken = response.data.refreshToken ?? '';
    session.accountUsername = account?.username || loginForm.username.trim();
    session.accountName = account?.displayName || account?.username || loginForm.username.trim();
    session.role = normalizeRole(account?.role);
    session.permissions = normalizePermissions(account?.permissions);
    saveDesktopConfig({
      accessToken: session.accessToken,
      refreshToken: session.refreshToken,
      accountUsername: session.accountUsername,
      accountRole: session.role,
      accountPermissions: session.permissions
    });
    await refreshDesktopStatus();
    setMode(loginForm.mode === 'admin' && !hasDesktopBridge() ? currentMode.value === 'admin-dev' && devConsoleEnabled ? 'admin-dev' : 'admin' : 'desktop');
  } catch (error) {
    loginError.value = error instanceof Error ? error.message : String(error);
  } finally {
    loginLoading.value = false;
  }
}

function logout() {
  clearSession();
  loginError.value = '';
}

async function handleAuthExpired(message?: string): Promise<void> {
  if (refreshPromise) {
    return refreshPromise;
  }
  const saved = loadDesktopConfig();
  const refreshToken = session.refreshToken || saved.refreshToken;
  const username = session.accountUsername || saved.accountUsername || readJwtUsername(saved.accessToken);
  if (!refreshToken || !username) {
    expireSession(message);
    return;
  }
  refreshPromise = (async () => {
    const response = await postJson<LoginPayload>('/api/v1/auth/refresh', { refreshToken, username });
    if (!response.success || !response.data?.accessToken) {
      throw new Error(response.message ?? response.errorCode ?? 'login refresh failed');
    }
    const account = response.data.account ?? response.data.userInfo;
    session.accessToken = response.data.accessToken;
    session.refreshToken = response.data.refreshToken ?? refreshToken;
    session.accountUsername = account?.username || username;
    session.accountName = account?.displayName || account?.username || username;
    session.role = normalizeRole(account?.role) || session.role;
    session.permissions = normalizePermissions(account?.permissions);
    saveDesktopConfig({
      accessToken: session.accessToken,
      refreshToken: session.refreshToken,
      accountUsername: session.accountUsername,
      accountRole: session.role,
      accountPermissions: session.permissions
    });
    loginError.value = '';
    await refreshDesktopStatus();
  })()
    .catch(() => {
      expireSession(message);
    })
    .finally(() => {
      refreshPromise = null;
    });
  return refreshPromise;
}

function expireSession(message?: string) {
  const username = session.accountUsername || loadDesktopConfig().accountUsername || readJwtUsername(session.accessToken);
  clearSession(true);
  loginForm.username = username;
  loginError.value = message?.trim() || '登录已过期，请重新登录';
}

function clearSession(preserveIdentity = false) {
  const username = session.accountUsername;
  session.accessToken = '';
  session.refreshToken = '';
  session.accountUsername = preserveIdentity ? username : '';
  session.accountName = '';
  session.role = '';
  session.permissions = [];
  clearDesktopNotice();
  resetDesktopStatus();
  saveDesktopConfig({
    accessToken: '',
    refreshToken: '',
    accountUsername: preserveIdentity ? username : '',
    accountRole: '',
    accountPermissions: []
  });
}

function setMode(mode: RouteMode) {
  const nextMode = hasDesktopBridge()
    ? 'desktop'
    : mode === 'admin-dev' && devConsoleEnabled ? 'admin-dev' : 'admin';
  currentMode.value = nextMode;
  loginForm.mode = nextMode === 'desktop' ? 'desktop' : 'admin';
  const nextHash = hashForMode(nextMode);
  if (window.location.hash !== nextHash) {
    window.location.hash = nextHash;
  }
}

function syncModeFromHash() {
  const routeMode = modeFromHash();
  currentMode.value = routeMode;
  loginForm.mode = routeMode === 'desktop' ? 'desktop' : 'admin';
}

function modeFromHash(): RouteMode {
  if (hasDesktopBridge()) {
    return 'desktop';
  }
  const hash = window.location.hash || '#/admin';
  if (hash.startsWith('#/admin/dev-console')) {
    return devConsoleEnabled ? 'admin-dev' : 'admin';
  }
  return 'admin';
}

function hashForMode(mode: RouteMode) {
  if (mode === 'admin') return '#/admin';
  if (mode === 'admin-dev') return '#/admin/dev-console';
  return '#/desktop';
}

function normalizeInitialHash() {
  if (hasDesktopBridge()) {
    window.history.replaceState(null, '', '#/desktop');
  } else if (!window.location.hash) {
    window.history.replaceState(null, '', '#/admin');
  } else if (window.location.hash.startsWith('#/admin/dev-console') && !devConsoleEnabled) {
    window.history.replaceState(null, '', '#/admin');
  } else if (!window.location.hash.startsWith('#/admin')) {
    window.history.replaceState(null, '', '#/admin');
  }
  syncModeFromHash();
}

function hasDesktopBridge() {
  return Boolean(window.desktopBridge);
}

function selectDesktopPanel(panel: DesktopPanelKey) {
  activeDesktopPanel.value = panel;
  if (panel === 'workbench') {
    eventBus.emit('workbench:show', {});
  }
}

async function recognizeFromAnywhere() {
  clearDesktopNotice();
  selectDesktopPanel('reply');
  const result = await captureScreenshot();
  if (!result.success || !result.imageBase64) {
    setDesktopNotice(result.message ?? '屏幕截图失败，请确认系统允许桌面端录屏后重试', 'error');
    return;
  }
  await triggerRecognize('BUTTON_CLICK', { imageBase64: result.imageBase64 });
  selectDesktopPanel('reply');
}

async function refreshAlwaysOnTopState() {
  const result = await getAlwaysOnTop();
  if (result.success) {
    alwaysOnTop.value = result.alwaysOnTop;
  }
}

async function togglePinWindow() {
  clearDesktopNotice();
  const result = await toggleAlwaysOnTop();
  if (result.success) {
    alwaysOnTop.value = result.alwaysOnTop;
    return;
  }
  setDesktopNotice('窗口置顶不可用，请重启桌面端后重试', 'error');
}

function openQuickSearch() {
  eventBus.emit('quick-search:show', {});
}

function openTaskQueue(tab?: 'OVERDUE' | 'DUE_TODAY' | 'APPOINTMENT' | 'NEW_LEAD') {
  taskQueueOpen.value = true;
  if (tab) {
    void nextTick(() => eventBus.emit('followup:switch-tab', { tab }));
  }
}

function closeTaskQueue() {
  taskQueueOpen.value = false;
}

async function openAdmin() {
  if (!canOpenAdmin.value) {
    return;
  }
  clearDesktopNotice();
  const adminUrl = `${window.location.origin}${window.location.pathname}#/admin`;
  const result = await openAdminConsole(adminUrl);
  if (!result.success) {
    setDesktopNotice(result.message ?? '管理后台打开失败，请在浏览器中访问后台地址', 'error');
  }
}

async function refreshDesktopStatus() {
  await loadDesktopStatus();
  if (desktopStatusState.accountName) {
    session.accountName = desktopStatusState.accountName;
  }
  if (desktopStatusState.role) {
    session.role = normalizeRole(desktopStatusState.role);
  }
  session.permissions = [...desktopStatusState.permissions];
  saveDesktopConfig({ accountRole: session.role, accountPermissions: session.permissions });
}

function normalizeRole(value?: string): AccountRole {
  if (value === 'ADMIN' || value === 'LEADER' || value === 'KEEPER') {
    return value;
  }
  return '';
}

function normalizePermissions(value?: string[]): string[] {
  if (!Array.isArray(value)) return [];
  return [...new Set(value.map((permission) => String(permission).trim()).filter(Boolean))];
}

function resolveInitialRole(savedConfig: typeof config): AccountRole {
  const savedRole = normalizeRole(savedConfig.accountRole);
  if (savedRole) {
    return savedRole;
  }
  const tokenRole = normalizeRole(readJwtRole(savedConfig.accessToken));
  if (tokenRole) {
    saveDesktopConfig({ accountRole: tokenRole });
  }
  return tokenRole;
}

function readJwtRole(token?: string): string {
  const payload = token?.split('.')[1];
  if (!payload) {
    return '';
  }
  try {
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
    const json = atob(normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '='));
    const data = JSON.parse(json) as { role?: string };
    return data.role ?? '';
  } catch {
    return '';
  }
}

function readJwtUsername(token?: string): string {
  const payload = token?.split('.')[1];
  if (!payload) {
    return '';
  }
  try {
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
    const json = atob(normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '='));
    const data = JSON.parse(json) as { username?: string; phone?: string };
    return data.username ?? data.phone ?? '';
  } catch {
    return '';
  }
}
</script>
