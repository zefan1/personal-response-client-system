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
          <option value="admin">管理后台</option>
          <option value="desktop">桌面工作台</option>
        </select>
      </label>
      <p v-if="loginError" class="admin-message error">{{ loginError }}</p>
      <button class="primary" type="submit" :disabled="loginLoading">{{ loginLoading ? '登录中' : '登录' }}</button>
    </form>
  </main>

  <AdminConsole
    v-else-if="currentMode === 'admin'"
    :account-name="session.accountName"
    @logout="logout"
    @switch-desktop="currentMode = 'desktop'"
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
        <button class="secondary small" type="button" @click="currentMode = 'admin'">管理后台</button>
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
import { computed, reactive, ref, onBeforeUnmount, onMounted } from 'vue';
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
import { loadDesktopConfig, saveDesktopConfig } from './shared/config';
import { eventBus } from './shared/eventBus';
import { cleanupStageSuggestionHandler, initializeStageSuggestionHandler } from './modules/stage-suggestion/stageSuggestionHandler';
import WorkbenchPanel from './modules/workbench/WorkbenchPanel.vue';

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
const currentMode = ref<'admin' | 'desktop'>('desktop');
const activeDesktopPanel = ref<DesktopPanelKey>('workbench');
const loginLoading = ref(false);
const loginError = ref('');
const loginForm = reactive({
  apiBaseUrl: config.apiBaseUrl,
  username: 'admin',
  password: 'admin123',
  mode: 'desktop' as 'admin' | 'desktop'
});
const session = reactive({
  accessToken: config.accessToken,
  refreshToken: '',
  accountName: config.accessToken ? '已登录账号' : ''
});
const activeDesktopNav = computed(() => desktopNavItems.find((item) => item.key === activeDesktopPanel.value) ?? desktopNavItems[0]);
const eventDisposers: Array<() => void> = [];

onMounted(() => {
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
  cleanupAbnormalAlertRouter();
  cleanupStageSuggestionHandler();
  eventDisposers.splice(0).forEach((dispose) => dispose());
});

async function login() {
  loginLoading.value = true;
  loginError.value = '';
  try {
    saveDesktopConfig({ apiBaseUrl: loginForm.apiBaseUrl.trim().replace(/\/$/, '') });
    const path = loginForm.mode === 'admin' ? '/admin/api/v1/auth/login' : '/api/v1/auth/login';
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
    currentMode.value = loginForm.mode;
    saveDesktopConfig({ accessToken: session.accessToken });
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
  saveDesktopConfig({ accessToken: '' });
}

function selectDesktopPanel(panel: DesktopPanelKey) {
  activeDesktopPanel.value = panel;
  if (panel === 'workbench') {
    eventBus.emit('workbench:show', {});
  }
}
</script>
