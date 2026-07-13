<template>
  <section class="workbench-panel">
    <header class="panel-header workbench-hero">
      <p class="workbench-sync-status">{{ state.lastFetchAt ? `上次同步 ${formatDateTime(state.lastFetchAt)}` : '先处理待跟进和新客资' }}</p>
      <button
        class="secondary small workbench-refresh-button"
        type="button"
        :disabled="state.loading"
        aria-label="刷新"
        title="刷新"
        @click="loadWorkbenchFollowups(true)"
      >
        {{ state.loading ? '...' : '↻' }}
      </button>
    </header>

    <div v-if="state.stale || state.toast" class="banner workbench-status-banner">
      <span>{{ state.toast || '当前数据可能不是最新' }}</span>
      <button
        class="secondary small"
        type="button"
        :disabled="state.loading"
        @click="loadWorkbenchFollowups(true)"
      >
        重试
      </button>
    </div>

    <div v-if="visibleWorkbenchNotices.length" class="workbench-notices" aria-label="系统公告">
      <article
        v-for="notice in visibleWorkbenchNotices"
        :key="notice.noticeId"
        :class="['workbench-notice', `level-${notice.level.toLowerCase()}`]"
      >
        <div>
          <strong>{{ notice.title }}</strong>
          <p>{{ notice.content }}</p>
          <small>{{ noticeLevelLabel(notice.level) }} · 有效至 {{ formatNoticeTime(notice.expireAt) }}</small>
        </div>
        <button class="secondary small" type="button" @click="dismissWorkbenchNotice(notice.noticeId)">知道了</button>
      </article>
    </div>

    <div class="metric-grid">
      <button v-for="card in metricCards" :key="card.key" class="metric-card" type="button" @click="openMetricQueue(card.key)">
        <span class="metric-icon" aria-hidden="true">{{ card.icon }}</span>
        <span class="metric-copy">
          <span>{{ card.label }}</span>
          <strong>{{ card.metric.total }}</strong>
          <small>团 {{ card.metric.tuanGou }} · 线 {{ card.metric.xianSuo }}</small>
        </span>
      </button>
    </div>

    <div class="workbench-actions" aria-label="工作台快捷入口">
      <button class="workbench-action" type="button" @click="startWorkbenchCapture">
        <span class="workbench-action-icon" aria-hidden="true">识</span>
        <span>
          <strong>识别聊天</strong>
          <small>截图生成回复</small>
        </span>
      </button>
      <button class="workbench-action" type="button" @click="openWorkbenchQuickSearch">
        <span class="workbench-action-icon" aria-hidden="true">模</span>
        <span>
          <strong>速搜模板</strong>
          <small>话术和素材</small>
        </span>
      </button>
      <button class="workbench-action" type="button" @click="startWorkbenchBatchTemplate">
        <span class="workbench-action-icon" aria-hidden="true">批</span>
        <span>
          <strong>批量待办</strong>
          <small>选择客户发送</small>
        </span>
      </button>
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
        <p v-else class="empty-panel visual-empty">今天没有待跟进客户</p>
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
        <p v-if="recentNewLeads.length === 0" class="empty-panel visual-empty">暂无新客资</p>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue';
import { eventBus } from '../../shared/eventBus';
import {
  handleWorkbenchFollowupReminder,
  handleWorkbenchNewLead,
  handleWorkbenchNotice,
  loadWorkbenchFollowups,
  loadWorkbenchNotices,
  markWorkbenchDirty,
  dismissWorkbenchNotice,
  noticeLevelLabel,
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

const metricCards = computed<Array<{ key: WorkbenchMetricKey; label: string; icon: string; metric: { total: number; tuanGou: number; xianSuo: number } }>>(() => [
  { key: 'pendingFollowup', label: '待跟进', icon: '跟', metric: workbenchMetrics.value.pendingFollowup },
  { key: 'appointment', label: '今日预约', icon: '约', metric: workbenchMetrics.value.appointment },
  { key: 'newLead', label: '新客资', icon: '新', metric: workbenchMetrics.value.newLead }
]);

const disposers: Array<() => void> = [];

onMounted(() => {
  void loadWorkbenchFollowups();
  void loadWorkbenchNotices();
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

function formatNoticeTime(value: string): string {
  return new Date(value).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' });
}

function openMetricQueue(key: WorkbenchMetricKey): void {
  if (key === 'newLead') {
    openAllNewLeads();
    return;
  }
  if (key === 'appointment') {
    eventBus.emit('followup:switch-tab', { tab: 'APPOINTMENT' });
    return;
  }
  openAllFollowups();
}
</script>
