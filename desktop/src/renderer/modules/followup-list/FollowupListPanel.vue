<template>
  <section class="followup-panel">
    <header class="panel-header">
      <div>
        <h2>今日跟进</h2>
        <p>{{ totalCount }} 个待办客户<span v-if="state.lastLoadedAt"> · {{ lastLoadedText }}</span></p>
      </div>
      <button
        class="secondary small icon-refresh-button"
        type="button"
        :disabled="state.loading"
        aria-label="刷新今日跟进"
        title="刷新"
        @click="loadTodayFollowups"
      >
        {{ state.loading ? '…' : '↻' }}
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
        {{ tab.label }} <span>{{ tab.value === 'NEW_LEAD' ? state.pendingNewLeadCount : state.groups[tab.value].length }}</span>
      </button>
    </nav>

    <div v-if="state.loading && !state.loaded" class="loading-skeleton">
      <div class="skeleton-card"></div>
      <div class="skeleton-card"></div>
      <div class="skeleton-card"></div>
    </div>

    <div v-else-if="activeFollowupItems.length" class="followup-list">
      <template v-for="group in displayGroups" :key="group.key">
        <button
          v-if="group.collapsible && group.items.length"
          class="followup-group-toggle"
          type="button"
          :aria-expanded="!isLeadGroupCollapsed(group.key)"
          @click="toggleLeadGroup(group.key)"
        >
          <span>{{ group.label }} {{ group.items.length }}</span>
          <span aria-hidden="true">{{ isLeadGroupCollapsed(group.key) ? '展开' : '收起' }}</span>
        </button>
        <template v-if="!group.collapsible || !isLeadGroupCollapsed(group.key)">
          <article
            v-for="(item, index) in group.items"
            :key="itemKey(item, index)"
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
        <div class="followup-row-actions">
          <button
            v-if="item.reminderType === 'NEW_LEAD'"
            class="secondary small followup-copy-button"
            type="button"
            :aria-label="`复制${item.contactType === 'WECHAT' ? '微信号' : '手机号'}`"
            :title="`复制${item.contactType === 'WECHAT' ? '微信号' : '手机号'}`"
            @click.stop="requestLeadContact(item)"
          >⧉</button>
          <button
            class="secondary small followup-profile-button"
            type="button"
            :aria-label="`查看 ${item.nickname || `客户 ${item.phone.slice(-4)}`} 的客户档案`"
            title="查看客户档案"
            @click="openFollowupCustomer(item)"
          >档案</button>
          <button
            v-if="item.reminderType === 'NEW_LEAD'"
            class="secondary small followup-invalid-button"
            type="button"
            :disabled="isLeadValidityUpdating(item)"
            @click.stop="void toggleLeadInvalid(item)"
          >{{ item.leadInvalid ? '有效' : '无效' }}</button>
        </div>
          </article>
        </template>
      </template>
    </div>

    <div v-else class="empty-panel">
      <strong>{{ emptyText.title }}</strong>
      <p>{{ emptyText.subtitle }}</p>
    </div>

    <footer v-if="selectedFollowupItems.length" class="batch-bar">
      <span class="batch-selection-count">已选 {{ selectedFollowupItems.length }} 个</span>
      <div class="batch-secondary-actions">
        <button class="secondary small" @click="selectAllActiveFollowups">全选</button>
        <button class="secondary small" @click="invertActiveFollowupSelection">反选</button>
      </div>
      <button class="primary small batch-primary-action" @click="startBatchTemplate">批量发模板</button>
    </footer>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { eventBus } from '../../shared/eventBus';
import {
  activeFollowupItems,
  applyLeadValidityChange,
  completeFollowup,
  followupListState as state,
  handleFollowupReminder,
  handleNewLeadAlert,
  invertActiveFollowupSelection,
  isLeadValidityUpdating,
  loadTodayFollowups,
  markNewLeadProcessed,
  openFollowupCustomer,
  openNewReminderBanner,
  selectAllActiveFollowups,
  selectedFollowupItems,
  setActiveFollowupTab,
  startBatchTemplate,
  toggleLeadInvalid,
  toggleFollowupSelection
} from './followupListStore';
import type { FollowupItem, FollowupReminderPayload, FollowupTab, NewLeadAlertPayload } from './types';
import { requestLeadContact } from '../new-lead-flow/newLeadFlowStore';

const tabs: Array<{ value: FollowupTab; label: string }> = [
  { value: 'DUE_TODAY', label: '今日待跟进' },
  { value: 'OVERDUE', label: '逾期跟进' },
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
type LeadGroupKey = 'pending' | 'processed';
type DisplayGroup = {
  key: 'all' | LeadGroupKey;
  label: string;
  items: FollowupItem[];
  collapsible: boolean;
};
const pendingLeadItems = computed(() => activeFollowupItems.value.filter((item) => !item.leadProcessed));
const processedLeadItems = computed(() => activeFollowupItems.value.filter((item) => Boolean(item.leadProcessed)));
const displayGroups = computed<DisplayGroup[]>(() => {
  if (state.activeTab !== 'NEW_LEAD') {
    return [{ key: 'all', label: '', items: activeFollowupItems.value, collapsible: false }];
  }
  return [
    { key: 'pending', label: '未处理', items: pendingLeadItems.value, collapsible: true },
    { key: 'processed', label: '已处理', items: processedLeadItems.value, collapsible: true }
  ];
});
const lastLoadedText = computed(() => {
  if (!state.lastLoadedAt) {
    return '';
  }
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).format(new Date(state.lastLoadedAt));
});
const disposers: Array<() => void> = [];
const collapsedLeadGroups = ref(new Set<'pending' | 'processed'>(['processed']));

onMounted(() => {
  void loadTodayFollowups();
  disposers.push(eventBus.on<FollowupReminderPayload>('FOLLOWUP_REMIND', handleFollowupReminder));
  disposers.push(eventBus.on<NewLeadAlertPayload>('NEW_LEAD_ALERT', handleNewLeadAlert));
  disposers.push(eventBus.on<{ tab?: FollowupTab }>('followup:switch-tab', (payload) => {
    if (payload.tab && tabs.some((tab) => tab.value === payload.tab)) {
      setActiveFollowupTab(payload.tab);
    }
  }));
  disposers.push(eventBus.on<{ phone: string; reminderType?: FollowupTab | null }>('followup:completed', (payload) => {
    completeFollowup(payload.phone, payload.reminderType);
  }));
  disposers.push(eventBus.on<{ phone: string; nickname?: string }>('new-lead:processed', (payload) => {
    const item = state.groups.NEW_LEAD.find((candidate) => (candidate.phoneFull ?? candidate.phone) === payload.phone);
    if (item) {
      markNewLeadProcessed(item, payload.nickname || item.nickname || '');
    }
  }));
  disposers.push(eventBus.on<{ phone: string; invalid: boolean; retainedUntil?: string | null; version?: number | null }>('new-lead:validity-changed', applyLeadValidityChange));
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
  return `${item.contactType === 'WECHAT' ? '微信号' : '手机号'} ${formatContact(item)} · 来源 ${sourceLabel(item.sourceTable)} · ${formatDate(item.arrivedAt)}`;
}

function itemKey(item: FollowupItem, index: number): string {
  return `${item.reminderType}-${item.phoneFull ?? item.phone}-${index}`;
}

function isLeadGroupCollapsed(key: 'all' | LeadGroupKey): boolean {
  return key !== 'all' && collapsedLeadGroups.value.has(key);
}

function toggleLeadGroup(key: 'all' | LeadGroupKey): void {
  if (key === 'all') return;
  const next = new Set(collapsedLeadGroups.value);
  if (next.has(key)) {
    next.delete(key);
  } else {
    next.add(key);
  }
  collapsedLeadGroups.value = next;
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

function formatPhone(value?: string | null): string {
  return value || '-';
}

function formatContact(item: FollowupItem): string {
  const value = item.contactValue || item.phoneFull || item.phone || '';
  if (item.contactType === 'WECHAT') return value || '-';
  return value.length >= 7 ? `${value.slice(0, 3)}****${value.slice(-4)}` : value || '-';
}

function sourceLabel(value?: string | null): string {
  if (!value) return '-';
  if (value.startsWith('ASSIGNMENT:')) return '分配表';
  if (value.startsWith('ARRIVAL:')) return '到店表';
  if (value === 'th1zyU') return '客户主表';
  return value;
}

function leadTypeLabel(value?: string | null): string {
  if (value === 'TUAN_GOU') return '团购';
  if (value === 'XIAN_SUO') return '线索';
  if (value === 'PENDING') return '待确认';
  return value || '-';
}
</script>
