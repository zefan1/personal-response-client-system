<template>
  <div v-if="globalAlertCount > 0 || state.panelOpen" class="alert-bell-wrap">
    <button class="alert-bell" type="button" aria-label="提醒中心" @click="toggleAlertPanel">
      <span class="bell-mark">!</span>
      <span v-if="globalAlertCount > 0" class="bell-badge">{{ globalAlertCount }}</span>
    </button>

    <section v-if="state.panelOpen" class="alert-panel">
      <header class="alert-panel-head">
        <strong>提醒中心</strong>
        <button class="icon-close-button" type="button" aria-label="关闭提醒中心" title="关闭提醒中心" @click="closeAlertPanel">
          <span aria-hidden="true">×</span>
        </button>
      </header>

      <p v-if="state.historyUnavailable" class="hint">提醒历史暂不可用</p>

      <template v-if="visibleGlobalAlerts.length">
        <article
          v-for="alert in visibleGlobalAlerts"
          :key="alert.id"
          :class="['alert-row', `level-${alert.level.toLowerCase()}`]"
        >
          <div>
            <strong>{{ alert.title }}</strong>
            <p>{{ alert.message }}</p>
            <span>{{ sourceLabel(alert.source) }} · {{ levelLabel(alert.level) }} · {{ alert.abnormalAlertId ? '待处理' : '通知' }}<template v-if="alert.occurredAt"> · {{ formatDate(alert.occurredAt) }}</template></span>
          </div>
          <button
            v-if="alert.abnormalAlertId"
            class="secondary small"
            type="button"
            @click="acknowledgeAlert(alert.abnormalAlertId)"
          >
            已知晓
          </button>
        </article>
      </template>
      <p v-else class="empty-panel">暂无需要处理的提醒</p>

      <div class="alert-history-actions">
        <button v-if="!state.historyOpen" class="link-button alert-history-link" type="button" @click="showAllHistory">查看全部历史</button>
        <button v-else class="link-button" type="button" @click="hideAlertHistory">收起历史</button>
        <button v-if="state.historyOpen && state.recentHistory.length" class="link-button danger-text" type="button" @click="confirmClearHistory">清空历史</button>
      </div>
      <div v-if="state.historyOpen" class="alert-history">
        <p v-if="state.historyLoading" class="hint">正在加载历史...</p>
        <article
          v-for="alert in state.recentHistory"
          v-else
          :key="`history-${alert.alertId}`"
          :class="['alert-row', 'acknowledged', `level-${alert.level.toLowerCase()}`]"
        >
          <div>
            <strong>{{ alertTypeLabel(alert.alertType) }} · {{ maskPhone(alert.phone) }}</strong>
            <p>{{ alert.message }}</p>
            <span>{{ levelLabel(alert.level) }} · 已知晓 · {{ formatDate(alert.occurredAt) }}</span>
          </div>
        </article>
        <p v-if="!state.historyLoading && !state.recentHistory.length" class="empty-panel">暂无历史提醒</p>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import {
  abnormalAlertState as state,
  acknowledgeAlert,
  clearAlertHistory,
  closeAlertPanel,
  hideAlertHistory,
  showAllHistory,
  toggleAlertPanel
} from './alertStore';
import { globalAlertCount, globalAlertItems } from './globalAlertCenter';
import type { GlobalAlertItem, GlobalAlertLevel } from './globalAlertCenter';
import type { AlertType } from './types';

defineOptions({ name: 'AlertBell' });

const visibleGlobalAlerts = computed(() => globalAlertItems.value.slice(0, 10));

function alertTypeLabel(type: AlertType): string {
  return type === 'CUSTOMER_COMPLAINT' ? '客户不满' : '流失风险';
}

function maskPhone(phone: string): string {
  return `${phone.slice(0, 3)}****${phone.slice(-4)}`;
}

function formatDate(value: string): string {
  return value.replace('T', ' ').slice(0, 16);
}

function sourceLabel(source: GlobalAlertItem['source']): string {
  return ({
    DESKTOP: '桌面端',
    NETWORK: '网络',
    ABNORMAL: '客户异常',
    NOTICE: '系统公告',
    SKILL: 'Skill',
    LLM: 'LLM'
  } as Record<GlobalAlertItem['source'], string>)[source];
}

function levelLabel(level: GlobalAlertLevel): string {
  return ({ ERROR: '紧急', WARN: '提醒', INFO: '普通' } as Record<GlobalAlertLevel, string>)[level];
}

function confirmClearHistory(): void {
  if (window.confirm('确认清空本机保存的提醒历史？当前待处理提醒不会被清除。')) {
    void clearAlertHistory();
  }
}
</script>
