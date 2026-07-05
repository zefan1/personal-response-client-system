<template>
  <main v-if="!session.accessToken" class="login-shell">
    <form class="login-panel" @submit.prevent="login">
      <div>
        <h1>私域辅助系统</h1>
        <p>登录后进入桌面工作台或管理后台。</p>
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
      <label>
        入口
        <select v-model="loginForm.mode">
          <option v-if="!isElectronRuntime" value="admin">管理后台</option>
          <option value="desktop">桌面工作台</option>
        </select>
      </label>
      <p v-if="loginError" class="admin-message error">{{ loginError }}</p>
      <button class="primary" type="submit" :disabled="loginLoading">{{ loginLoading ? '登录中' : '登录' }}</button>
    </form>
  </main>

  <AdminConsole
    v-else-if="currentMode === 'admin' && !isElectronRuntime"
    :account-name="session.accountName"
    @logout="logout"
    @switch-desktop="setMode('desktop')"
  />

  <AdminDevConsole
    v-else-if="currentMode === 'admin-dev' && devConsoleEnabled && !isElectronRuntime"
    :account-name="session.accountName"
    @logout="logout"
    @switch-admin="setMode('admin')"
    @switch-desktop="setMode('desktop')"
  />

  <main v-else class="desktop-shell">
    <aside class="desktop-sidebar">
      <div class="desktop-brand">
        <strong>私域辅助系统</strong>
        <span>{{ session.accountName }}</span>
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
          <span>{{ item.title }}</span>
          <small>{{ item.description }}</small>
        </button>
      </nav>
      <div class="desktop-sidebar-actions">
        <button v-if="canOpenAdmin" class="secondary small" type="button" @click="openAdmin">管理后台</button>
        <button class="secondary small" type="button" @click="logout">退出</button>
      </div>
    </aside>

    <section class="desktop-main">
      <header class="desktop-mode-bar">
        <div>
          <strong>{{ activeDesktopNav.title }}</strong>
          <p>{{ activeDesktopNav.description }}</p>
        </div>
        <span>{{ session.accountName }}</span>
      </header>
      <button
        class="global-recognize-button"
        type="button"
        :disabled="recognitionState.isRecognizePending"
        @click="recognizeFromAnywhere"
      >
        {{ recognitionState.isRecognizePending ? '识别中' : '识别聊天' }}
      </button>
      <p v-if="desktopNotice" class="admin-message" :class="{ error: desktopNoticeKind === 'error' }">{{ desktopNotice }}</p>
      <OfflineStatusBar />
      <AlertBell />
      <CopyBackfillAgent />
      <NewLeadToastAgent />
      <QuickSearchOverlay />
      <BatchTemplateOverlay />
      <HelpModeAgent />
      <WorkbenchPanel v-show="activeDesktopPanel === 'workbench'" />
      <ChatRecognitionPanel v-show="activeDesktopPanel === 'recognition'" />
      <FollowupListPanel v-show="activeDesktopPanel === 'followups'" />
      <CustomerProfilePanel v-show="activeDesktopPanel === 'customer'" />
      <ReplySuggestionPanel v-show="activeDesktopPanel === 'reply'" />
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, reactive, ref, onBeforeUnmount, onMounted } from 'vue';
import AlertBell from './modules/abnormal-alert/AlertBell.vue';
import { cleanupAbnormalAlertRouter, initializeAbnormalAlertRouter } from './modules/abnormal-alert/alertStore';
import AdminConsole from './modules/admin/AdminConsole.vue';
import ChatRecognitionPanel from './modules/chat-recognition/ChatRecognitionPanel.vue';
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
import { captureScreenshot, openAdminConsole } from './shared/desktopBridge';
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
  };
  userInfo?: {
    username?: string;
    displayName?: string;
    role?: string;
  };
};

type DesktopPanelKey = 'workbench' | 'recognition' | 'followups' | 'customer' | 'reply';
type RouteMode = 'admin' | 'desktop' | 'admin-dev';
type AccountRole = 'ADMIN' | 'LEADER' | 'KEEPER' | '';

type DesktopNavItem = {
  key: DesktopPanelKey;
  title: string;
  description: string;
};

const desktopNavItems: DesktopNavItem[] = [
  { key: 'workbench', title: '工作台', description: '今日待办与快捷入口' },
  { key: 'recognition', title: '聊天识别', description: '截图或文本识别客户' },
  { key: 'followups', title: '跟进列表', description: '逾期、今日与新客' },
  { key: 'customer', title: '客户档案', description: '客户字段与阶段' },
  { key: 'reply', title: '话术建议', description: '回复、复制与求助' }
];

const config = loadDesktopConfig();
const devConsoleEnabled = !import.meta.env.PROD;
const isElectronRuntime = Boolean(window.desktopBridge);
const AdminDevConsole = devConsoleEnabled
  ? defineAsyncComponent(async () => (await import('./modules/admin/AdminDevConsole.vue')).default)
  : null;
const currentMode = ref<RouteMode>(modeFromHash());
const activeDesktopPanel = ref<DesktopPanelKey>('workbench');
const loginLoading = ref(false);
const loginError = ref('');
const desktopNotice = ref('');
const desktopNoticeKind = ref<'info' | 'error'>('info');
const loginForm = reactive({
  apiBaseUrl: config.apiBaseUrl,
  username: 'admin',
  password: 'admin123',
  mode: (currentMode.value === 'desktop' ? 'desktop' : 'admin') as 'admin' | 'desktop'
});
const session = reactive({
  accessToken: config.accessToken,
  refreshToken: '',
  accountName: config.accessToken ? '已登录账号' : '',
  role: resolveInitialRole(config)
});
const activeDesktopNav = computed(() => desktopNavItems.find((item) => item.key === activeDesktopPanel.value) ?? desktopNavItems[0]);
const canOpenAdmin = computed(() => session.role === 'ADMIN' || session.role === 'LEADER');
const eventDisposers: Array<() => void> = [];

onMounted(() => {
  normalizeInitialHash();
  window.addEventListener('hashchange', syncModeFromHash);
  initializeAbnormalAlertRouter();
  initializeStageSuggestionHandler();
  eventDisposers.push(eventBus.on('followup:switch-tab', () => selectDesktopPanel('followups')));
  eventDisposers.push(eventBus.on('customer:selected', () => selectDesktopPanel('customer')));
  eventDisposers.push(eventBus.on('recognize:start', () => selectDesktopPanel('recognition')));
  eventDisposers.push(eventBus.on('recognize:result', () => selectDesktopPanel('reply')));
  eventDisposers.push(eventBus.on('suggestion:show', () => selectDesktopPanel('reply')));
  eventDisposers.push(eventBus.on('workbench:capture-chat', () => selectDesktopPanel('recognition')));
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
    const path = loginForm.mode === 'admin' && !isElectronRuntime ? '/admin/api/v1/auth/login' : '/api/v1/auth/login';
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
    session.accountName = account?.displayName || account?.username || loginForm.username.trim();
    session.role = normalizeRole(account?.role);
    setMode(loginForm.mode === 'admin' && !isElectronRuntime ? currentMode.value === 'admin-dev' && devConsoleEnabled ? 'admin-dev' : 'admin' : 'desktop');
    saveDesktopConfig({ accessToken: session.accessToken, accountRole: session.role });
  } catch (error) {
    loginError.value = error instanceof Error ? error.message : String(error);
  } finally {
    loginLoading.value = false;
  }
}

function logout() {
  session.accessToken = '';
  session.refreshToken = '';
  session.accountName = '';
  session.role = '';
  saveDesktopConfig({ accessToken: '', accountRole: '' });
}

function setMode(mode: RouteMode) {
  const nextMode = isElectronRuntime
    ? 'desktop'
    : mode === 'admin-dev' && !devConsoleEnabled ? 'admin' : mode;
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
  if (isElectronRuntime) {
    return 'desktop';
  }
  const hash = window.location.hash || '#/desktop';
  if (hash.startsWith('#/admin/dev-console')) {
    return devConsoleEnabled ? 'admin-dev' : 'admin';
  }
  if (hash.startsWith('#/admin')) {
    return 'admin';
  }
  return 'desktop';
}

function hashForMode(mode: RouteMode) {
  if (mode === 'admin') return '#/admin';
  if (mode === 'admin-dev') return '#/admin/dev-console';
  return '#/desktop';
}

function normalizeInitialHash() {
  if (isElectronRuntime) {
    window.history.replaceState(null, '', '#/desktop');
  } else if (!window.location.hash) {
    window.history.replaceState(null, '', '#/desktop');
  } else if (window.location.hash.startsWith('#/admin/dev-console') && !devConsoleEnabled) {
    window.history.replaceState(null, '', '#/admin');
  }
  syncModeFromHash();
}

function selectDesktopPanel(panel: DesktopPanelKey) {
  activeDesktopPanel.value = panel;
  if (panel === 'workbench') {
    eventBus.emit('workbench:show', {});
  }
}

async function recognizeFromAnywhere() {
  desktopNotice.value = '';
  selectDesktopPanel('recognition');
  const result = await captureScreenshot();
  if (!result.success || !result.imageBase64) {
    desktopNoticeKind.value = 'error';
    desktopNotice.value = result.error === 'NO_WECHAT_WINDOW'
      ? '未检测到微信/企业微信窗口，请先打开聊天窗口后再识别'
      : '截图失败，请确认聊天窗口可见后重试';
    return;
  }
  await triggerRecognize('BUTTON_CLICK', { imageBase64: result.imageBase64 });
}

async function openAdmin() {
  if (!canOpenAdmin.value) {
    return;
  }
  desktopNotice.value = '';
  const adminUrl = `${window.location.origin}${window.location.pathname}#/admin`;
  const result = await openAdminConsole(adminUrl);
  if (!result.success) {
    desktopNoticeKind.value = 'error';
    desktopNotice.value = result.message ?? '管理后台打开失败，请在浏览器中访问后台地址';
  }
}

function normalizeRole(value?: string): AccountRole {
  if (value === 'ADMIN' || value === 'LEADER' || value === 'KEEPER') {
    return value;
  }
  return '';
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
</script>
