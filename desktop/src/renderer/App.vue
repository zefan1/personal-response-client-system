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

  <main v-else class="shell">
    <header class="desktop-mode-bar">
      <strong>桌面工作台</strong>
      <span>{{ session.accountName }}</span>
      <button class="secondary small" type="button" @click="currentMode = 'admin'">管理后台</button>
      <button class="secondary small" type="button" @click="logout">退出</button>
    </header>
    <OfflineStatusBar />
    <AlertBell />
    <CopyBackfillAgent />
    <NewLeadToastAgent />
    <QuickSearchOverlay />
    <BatchTemplateOverlay />
    <HelpModeAgent />
    <WorkbenchPanel />
    <ChatRecognitionPanel />
    <FollowupListPanel />
    <CustomerProfilePanel />
    <ReplySuggestionPanel />
  </main>
</template>

<script setup lang="ts">
import { reactive, ref, onBeforeUnmount, onMounted } from 'vue';
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

const config = loadDesktopConfig();
const currentMode = ref<'admin' | 'desktop'>('admin');
const loginLoading = ref(false);
const loginError = ref('');
const loginForm = reactive({
  apiBaseUrl: config.apiBaseUrl,
  username: 'admin',
  password: 'admin123',
  mode: 'admin' as 'admin' | 'desktop'
});
const session = reactive({
  accessToken: config.accessToken,
  refreshToken: '',
  accountName: config.accessToken ? '已登录账号' : ''
});

onMounted(() => {
  initializeAbnormalAlertRouter();
  initializeStageSuggestionHandler();
});
onBeforeUnmount(() => {
  cleanupAbnormalAlertRouter();
  cleanupStageSuggestionHandler();
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
</script>
