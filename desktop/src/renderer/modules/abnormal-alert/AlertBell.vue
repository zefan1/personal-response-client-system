<template>
  <div class="alert-bell-wrap">
    <button class="alert-bell" type="button" aria-label="提醒中心" @click="toggleAlertPanel">
      <span class="bell-mark">!</span>
      <span v-if="unconfirmedCount > 0" class="bell-badge">{{ unconfirmedCount }}</span>
    </button>

    <section v-if="state.panelOpen" class="alert-panel">
      <header class="alert-panel-head">
        <strong>提醒中心</strong>
        <button class="secondary small" type="button" @click="closeAlertPanel">收起</button>
      </header>

      <p v-if="state.historyUnavailable" class="hint">提醒历史暂不可用</p>

      <template v-if="visibleAlerts.length">
        <article
          v-for="alert in visibleAlerts"
          :key="alert.alertId"
          :class="['alert-row', `level-${alert.level.toLowerCase()}`, { acknowledged: alert.acknowledged }]"
        >
          <div>
            <strong>{{ alertTypeLabel(alert.alertType) }} · {{ maskPhone(alert.phone) }}</strong>
            <p>{{ alert.message }}</p>
            <span>{{ formatDate(alert.occurredAt) }}</span>
          </div>
          <button
            v-if="!alert.acknowledged"
            class="secondary small"
            type="button"
            @click="acknowledgeAlert(alert.alertId)"
          >
            已知晓
          </button>
        </article>
      </template>
      <p v-else class="empty-panel">暂无未确认提醒</p>

      <button class="link-button alert-history-link" type="button" @click="showAllHistory">
        查看全部历史
      </button>
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
            <span>{{ formatDate(alert.occurredAt) }}</span>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import {
  abnormalAlertState as state,
  acknowledgeAlert,
  alertStore,
  closeAlertPanel,
  showAllHistory,
  toggleAlertPanel,
  unconfirmedCount
} from './alertStore';
import type { AlertType } from './types';

defineOptions({ name: 'AlertBell' });

const visibleAlerts = computed(() =>
  Array.from(alertStore.values())
    .flat()
    .sort((a, b) => Number(a.acknowledged) - Number(b.acknowledged) || b.occurredAt.localeCompare(a.occurredAt))
    .slice(0, 10)
);

function alertTypeLabel(type: AlertType): string {
  return type === 'CUSTOMER_COMPLAINT' ? '客户不满' : '流失风险';
}

function maskPhone(phone: string): string {
  return `${phone.slice(0, 3)}****${phone.slice(-4)}`;
}

function formatDate(value: string): string {
  return value.replace('T', ' ').slice(0, 16);
}
</script>
