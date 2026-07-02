<template>
  <section class="followup-panel">
    <header class="panel-header">
      <div>
        <h2>今日跟进</h2>
        <p>{{ totalCount }} 个待办客户</p>
      </div>
      <button class="secondary small" :disabled="state.loading" @click="loadTodayFollowups">
        {{ state.loading ? '加载中...' : '刷新' }}
      </button>
    </header>

    <button v-if="state.newReminderCount > 0" class="new-reminder-banner" @click="openNewReminderBanner">
      {{ state.newReminderCount }} 条新提醒
    </button>

    <p v-if="state.stale" class="banner">当前展示的是上次成功拉取的数据，可能不是最新</p>
    <p v-if="state.error" class="hint">
      {{ state.error }}
      <button class="secondary small" @click="loadTodayFollowups">重试</button>
    </p>

    <nav class="tab-bar">
      <button
        v-for="tab in tabs"
        :key="tab.value"
        :class="['tab-button', { active: state.activeTab === tab.value }]"
        @click="setActiveFollowupTab(tab.value)"
      >
        {{ tab.label }} <span>{{ state.groups[tab.value].length }}</span>
      </button>
    </nav>

    <div v-if="state.loading && !state.loaded" class="loading-skeleton">
      <div class="skeleton-card"></div>
      <div class="skeleton-card"></div>
      <div class="skeleton-card"></div>
    </div>

    <div v-else-if="activeFollowupItems.length" class="followup-list">
      <article
        v-for="item in activeFollowupItems"
        :key="`${item.reminderType}-${item.phone}`"
        :class="['followup-row', rowClass(item), { flash: Boolean(item.flashUntil) }]"
      >
        <input type="checkbox" :checked="state.selectedPhones.has(item.phoneFull ?? item.phone)" @change="toggleFollowupSelection(item)" />
        <button class="followup-main" @click="openFollowupCustomer(item)">
          <span>
            <strong>{{ item.nickname || `客户 ${item.phone.slice(-4)}` }}</strong>
            <i v-if="item.leadType === 'TUAN_GOU'" title="团购客户"></i>
            <em>{{ leadTypeLabel(item.leadType) }}</em>
          </span>
          <span>{{ rowDescription(item) }}</span>
        </button>
        <span class="keeper">{{ item.assignedKeeper || '-' }}</span>
      </article>
    </div>

    <div v-else class="empty-panel">
      <strong>{{ emptyText.title }}</strong>
      <p>{{ emptyText.subtitle }}</p>
    </div>

    <footer v-if="selectedFollowupItems.length" class="batch-bar">
      <span>已选 {{ selectedFollowupItems.length }} 个</span>
      <button class="secondary small" @click="selectAllActiveFollowups">全选</button>
      <button class="secondary small" @click="invertActiveFollowupSelection">反选</button>
      <button class="primary small" @click="startBatchTemplate">批量发模板</button>
    </footer>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue';
import { eventBus } from '../../shared/eventBus';
import {
  activeFollowupItems,
  followupListState as state,
  handleFollowupReminder,
  handleNewLeadAlert,
  invertActiveFollowupSelection,
  loadTodayFollowups,
  openFollowupCustomer,
  openNewReminderBanner,
  selectAllActiveFollowups,
  selectedFollowupItems,
  setActiveFollowupTab,
  startBatchTemplate,
  toggleFollowupSelection
} from './followupListStore';
import type { FollowupItem, FollowupReminderPayload, FollowupTab, NewLeadAlertPayload } from './types';

const tabs: Array<{ value: FollowupTab; label: string }> = [
  { value: 'OVERDUE', label: '逾期跟进' },
  { value: 'DUE_TODAY', label: '今日待跟进' },
  { value: 'APPOINTMENT', label: '今日预约' },
  { value: 'NEW_LEAD', label: '新客资' }
];

const emptyMap = {
  OVERDUE: { title: '没有逾期客户', subtitle: '太棒了，所有客户都在按时跟进' },
  DUE_TODAY: { title: '今天没有待跟进客户', subtitle: '稍后可能有新的跟进建议' },
  APPOINTMENT: { title: '今天没有预约', subtitle: '' },
  NEW_LEAD: { title: '今天没有新客资', subtitle: '新客资到达时会有实时提醒' }
};

const totalCount = computed(() => tabs.reduce((sum, tab) => sum + state.groups[tab.value].length, 0));
const emptyText = computed(() => emptyMap[state.activeTab]);
const disposers: Array<() => void> = [];

onMounted(() => {
  void loadTodayFollowups();
  disposers.push(eventBus.on<FollowupReminderPayload>('FOLLOWUP_REMIND', handleFollowupReminder));
  disposers.push(eventBus.on<NewLeadAlertPayload>('NEW_LEAD_ALERT', handleNewLeadAlert));
});

onBeforeUnmount(() => {
  disposers.splice(0).forEach((dispose) => dispose());
});

function rowDescription(item: FollowupItem): string {
  if (item.reminderType === 'OVERDUE') {
    return `${formatOverdue(item.overdueHours)} · ${item.nextFollowupDir || '-'}`;
  }
  if (item.reminderType === 'DUE_TODAY') {
    return `建议跟进 · ${item.nextFollowupDir || '-'}`;
  }
  if (item.reminderType === 'APPOINTMENT') {
    return `${item.appointmentTime || formatDate(item.appointmentDate)} · ${item.appointmentStore || '-'}`;
  }
  return `${item.sourceTable || '-'} · ${formatDate(item.arrivedAt)}`;
}

function rowClass(item: FollowupItem): string {
  if (item.reminderType === 'OVERDUE' && (item.overdueHours ?? 0) > 24) {
    return 'danger';
  }
  return item.alertLevel === 'HIGH' ? 'warning' : 'normal';
}

function formatOverdue(hours?: number | null): string {
  if (!hours) {
    return '逾期';
  }
  return hours > 24 ? `逾期 ${Math.ceil(hours / 24)}天` : `逾期 ${hours}小时`;
}

function formatDate(value?: string | null): string {
  if (!value) {
    return '-';
  }
  return value.replace('T', ' ').slice(0, 16);
}

function leadTypeLabel(value?: string | null): string {
  if (value === 'TUAN_GOU') return '团购';
  if (value === 'XIAN_SUO') return '线索';
  if (value === 'PENDING') return '待确认';
  return value || '-';
}
</script>
