<template>
  <section class="workbench-panel">
    <header class="panel-header">
      <div>
        <h2>工作台</h2>
        <p>{{ state.lastFetchAt ? `上次同步 ${formatDateTime(state.lastFetchAt)}` : '今日事项总览' }}</p>
      </div>
      <button class="secondary small" :disabled="state.loading" @click="loadWorkbenchFollowups(true)">
        {{ state.loading ? '加载中...' : '刷新' }}
      </button>
    </header>

    <p v-if="state.stale || state.toast" class="banner">
      {{ state.toast || '当前数据可能不是最新' }}
    </p>

    <div v-if="visibleWorkbenchNotices.length" class="workbench-notices">
      <article
        v-for="notice in visibleWorkbenchNotices"
        :key="notice.noticeId"
        :class="['workbench-notice', `level-${notice.level.toLowerCase()}`]"
      >
        <span><strong>{{ notice.title }}</strong> {{ notice.content }}</span>
        <button class="secondary small" @click="dismissWorkbenchNotice(notice.noticeId)">关闭</button>
      </article>
    </div>

    <div class="metric-grid">
      <article v-for="card in metricCards" :key="card.key" class="metric-card">
        <span>{{ card.label }}</span>
        <strong>{{ card.metric.total }}</strong>
        <small>团 {{ card.metric.tuanGou }} · 线 {{ card.metric.xianSuo }}</small>
      </article>
    </div>

    <div class="workbench-columns">
      <section class="workbench-section">
        <header class="section-inline-head">
          <h3>今日跟进</h3>
          <button class="link-button" @click="openAllFollowups">查看全部</button>
        </header>
        <div v-if="state.loading && !state.loaded" class="loading-skeleton">
          <div class="skeleton-card"></div>
          <div class="skeleton-card"></div>
        </div>
        <article
          v-for="item in urgentFollowups"
          v-else-if="urgentFollowups.length"
          :key="`${item.reminderType}-${item.phone}`"
          :class="['workbench-row', item.reminderType.toLowerCase()]"
        >
          <button class="workbench-row-main" @click="openWorkbenchCustomer(item.phoneFull ?? item.phone, item.leadType)">
            <strong>{{ item.nickname || `客户 ${item.phone.slice(-4)}` }}</strong>
            <span>{{ maskPhone(item.phoneFull ?? item.phone) }} · {{ leadTypeLabel(item.leadType) }} · {{ followupText(item) }}</span>
          </button>
          <button class="secondary small" @click="openWorkbenchCustomer(item.phoneFull ?? item.phone, item.leadType)">查看</button>
        </article>
        <p v-else class="empty-panel">今天没有待跟进客户</p>
      </section>

      <section class="workbench-section">
        <header class="section-inline-head">
          <h3>新客资</h3>
          <button class="link-button" @click="openAllNewLeads">查看全部</button>
        </header>
        <article
          v-for="lead in recentNewLeads"
          :key="lead.phoneFull ?? lead.phone"
          class="workbench-row new-lead"
        >
          <button class="workbench-row-main" @click="openWorkbenchCustomer(lead.phoneFull ?? lead.phone, lead.leadType)">
            <strong>{{ lead.nickname || `客户 ${lead.phone.slice(-4)}` }}</strong>
            <span>{{ maskPhone(lead.phoneFull ?? lead.phone) }} · {{ leadTypeLabel(lead.leadType) }} · {{ lead.sourceTable || '-' }}</span>
          </button>
          <button class="secondary small" @click="openWorkbenchCustomer(lead.phoneFull ?? lead.phone, lead.leadType)">查看</button>
        </article>
        <p v-if="recentNewLeads.length === 0" class="empty-panel">暂无新客资</p>
      </section>
    </div>

    <nav class="quick-actions">
      <button class="primary" @click="startWorkbenchCapture">识别聊天</button>
      <button class="secondary" @click="openWorkbenchQuickSearch">快线模板</button>
      <button class="secondary" @click="startWorkbenchBatchTemplate">批量发模板</button>
    </nav>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue';
import { eventBus } from '../../shared/eventBus';
import {
  dismissWorkbenchNotice,
  handleWorkbenchFollowupReminder,
  handleWorkbenchNewLead,
  handleWorkbenchNotice,
  loadWorkbenchFollowups,
  markWorkbenchDirty,
  openAllFollowups,
  openAllNewLeads,
  openWorkbenchCustomer,
  openWorkbenchQuickSearch,
  recentNewLeads,
  refreshWorkbenchIfNeeded,
  startWorkbenchBatchTemplate,
  startWorkbenchCapture,
  urgentFollowups,
  visibleWorkbenchNotices,
  workbenchMetrics,
  workbenchState as state
} from './workbenchStore';
import type { FollowupReminderPayload, NewLeadAlertPayload, WorkbenchNoticePayload } from './types';
import type { WorkbenchMetricKey } from './types';

const metricCards = computed<Array<{ key: WorkbenchMetricKey; label: string; metric: { total: number; tuanGou: number; xianSuo: number } }>>(() => [
  { key: 'pendingFollowup', label: '待跟进', metric: workbenchMetrics.value.pendingFollowup },
  { key: 'appointment', label: '今日预约', metric: workbenchMetrics.value.appointment },
  { key: 'newLead', label: '新客资', metric: workbenchMetrics.value.newLead }
]);

const disposers: Array<() => void> = [];

onMounted(() => {
  void loadWorkbenchFollowups();
  disposers.push(eventBus.on<FollowupReminderPayload>('FOLLOWUP_REMIND', handleWorkbenchFollowupReminder));
  disposers.push(eventBus.on<NewLeadAlertPayload>('NEW_LEAD_ALERT', handleWorkbenchNewLead));
  disposers.push(eventBus.on<WorkbenchNoticePayload>('SYSTEM_NOTICE', handleWorkbenchNotice));
  disposers.push(eventBus.on('stage:updated', markWorkbenchDirty));
  disposers.push(eventBus.on('workbench:show', refreshWorkbenchIfNeeded));
  window.addEventListener('focus', refreshWorkbenchIfNeeded);
});

onBeforeUnmount(() => {
  disposers.splice(0).forEach((dispose) => dispose());
  window.removeEventListener('focus', refreshWorkbenchIfNeeded);
});

function leadTypeLabel(value?: string | null): string {
  if (value === 'TUAN_GOU') return '团购';
  if (value === 'XIAN_SUO') return '线索';
  if (value === 'PENDING') return '待确认';
  return value || '-';
}

function followupText(item: { reminderType: string; overdueHours?: number | null; nextFollowupDir?: string | null }): string {
  if (item.reminderType === 'OVERDUE') {
    const hours = item.overdueHours ?? 0;
    return hours > 24 ? `逾期 ${Math.ceil(hours / 24)} 天` : `逾期 ${hours || 1} 小时`;
  }
  return `今天跟进${item.nextFollowupDir ? ` · ${item.nextFollowupDir}` : ''}`;
}

function maskPhone(phone: string): string {
  return phone.length >= 7 ? `${phone.slice(0, 3)}****${phone.slice(-4)}` : `****${phone.slice(-4)}`;
}

function formatDateTime(timestamp: number): string {
  return new Date(timestamp).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
}
</script>
