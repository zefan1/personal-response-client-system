<template>
  <section class="customer-panel">
    <header class="panel-header">
      <div>
        <h2>客户档案</h2>
        <p>{{ state.profile ? summaryText : '搜索或从候选列表选择客户' }}</p>
      </div>
      <button class="secondary small" :disabled="!state.profile || state.profileLoading" @click="refreshCurrent">刷新</button>
    </header>

    <div class="search-row">
      <input
        v-model="state.keyword"
        placeholder="粘贴手机号、昵称或备注搜索"
        @input="onInput"
        @paste="onPaste"
      />
      <button class="primary" :disabled="state.searchLoading" @click="searchImmediately(state.keyword)">
        {{ state.searchLoading ? '搜索中...' : '搜索' }}
      </button>
    </div>

    <p v-if="state.searchMessage" class="hint">{{ state.searchMessage }}</p>
    <div v-if="state.searchResults.length" class="search-results">
      <button v-for="customer in state.searchResults" :key="customer.phone" class="result-row" @click="openProfile(customer.phone, 'SEARCH')">
        <span>{{ customer.nickname || '-' }}</span>
        <span>{{ maskPhone(customer.phone) }}</span>
        <span>{{ leadTypeLabel(customer.leadType) }}</span>
        <span>{{ customer.assignedKeeper || '-' }}</span>
        <span>{{ formatDate(customer.lastFollowupAt) }}</span>
        <span>{{ customer.intendedStore || '-' }}</span>
      </button>
      <p v-if="state.searchTruncated" class="hint">还有更多结果，建议用手机号精确搜索</p>
    </div>

    <div v-if="state.candidateVisible" class="modal-backdrop">
      <section class="candidate-modal">
        <header class="panel-header">
          <div>
            <h2>选择客户</h2>
            <p>匹配到多个候选，请选中一个继续</p>
          </div>
          <button class="secondary small" @click="dismissCandidates">关闭</button>
        </header>
        <button v-for="candidate in state.candidates" :key="candidate.phone" class="result-row" @click="chooseCandidate(candidate)">
          <span>{{ candidate.nickname || '-' }}</span>
          <span>{{ maskPhone(candidate.phone) }}</span>
          <span>{{ leadTypeLabel(candidate.leadType) }}</span>
          <span>{{ candidate.assignedKeeper || '-' }}</span>
          <span>{{ formatDate(candidate.lastFollowupAt) }}</span>
          <span>{{ candidate.intendedStore || '-' }}</span>
        </button>
      </section>
    </div>

    <p v-if="state.offline || state.fromCache" class="banner">
      {{ state.offline ? '离线数据' : '缓存数据' }}，上次缓存于 {{ formatDate(state.cachedAt) }}
    </p>
    <p v-if="state.pendingSaveBanner" class="banner">{{ state.pendingSaveBanner }}</p>

    <article v-if="state.profile" class="profile-card">
      <div class="profile-summary">
        <div>
          <strong>{{ customer.nickname || '-' }}</strong>
          <span>{{ maskPhone(customer.phone) }}</span>
          <span>{{ leadTypeLabel(customer.leadType) }}</span>
          <span>{{ customer.customerStage || '-' }}</span>
        </div>
        <div class="profile-actions">
          <button class="primary small" :disabled="state.generating || !customer.phone" @click="generateReplyFromProfile">
            {{ state.generating ? '生成中...' : '生成回复' }}
          </button>
          <button v-if="!state.editMode" class="secondary small" @click="enterEditMode">编辑档案</button>
          <template v-else>
            <button class="primary small" :disabled="state.saving" @click="saveProfileEdits">{{ state.saving ? '保存中...' : '保存' }}</button>
            <button class="secondary small" @click="cancelEditMode">取消</button>
          </template>
        </div>
      </div>

      <ProfileSection title="意向与购买" section-key="intent">
        <FieldGrid :items="intentItems" />
      </ProfileSection>
      <ProfileSection title="身体情况" section-key="body">
        <FieldGrid :items="bodyItems" />
      </ProfileSection>
      <ProfileSection title="跟进历史" section-key="followup">
        <p class="field-value multiline">{{ customer.followupNotes || '-' }}</p>
        <p class="reason">下次跟进：{{ formatDate(customer.nextFollowupAt) }} · {{ customer.nextFollowupDir || '-' }}</p>
      </ProfileSection>
      <ProfileSection :title="`AI 更新建议 (${state.suggestions.length})`" section-key="suggestions">
        <div v-if="state.suggestions.length" class="suggestion-list">
          <article v-for="suggestion in state.suggestions" :key="suggestionKey(suggestion)" class="suggestion-item">
            <div>
              <strong>{{ suggestion.fieldName }}</strong>
              <p>{{ formatValue(suggestion.currentValue) }} → {{ formatValue(suggestion.suggestedValue) }}</p>
              <p class="reason">{{ suggestion.reason || suggestion.confidence || 'AI 建议更新该字段' }}</p>
            </div>
            <div class="suggestion-actions">
              <button class="secondary small" :disabled="suggestion.resolving || state.editMode" @click="resolveProfileSuggestion('CONFIRM', suggestion)">确认</button>
              <button class="secondary small" :disabled="suggestion.resolving || state.editMode" @click="resolveProfileSuggestion('REJECT', suggestion)">拒绝</button>
            </div>
          </article>
          <div class="reply-actions">
            <button class="secondary small" :disabled="state.editMode" @click="resolveProfileSuggestion('CONFIRM')">全部确认</button>
            <button class="secondary small" :disabled="state.editMode" @click="resolveProfileSuggestion('REJECT')">全部拒绝</button>
          </div>
        </div>
        <p v-else class="empty-panel">暂无建议</p>
      </ProfileSection>
      <ProfileSection title="预约信息" section-key="appointment">
        <FieldGrid :items="appointmentItems" />
      </ProfileSection>
    </article>

    <div v-if="state.tableSyncPrompt" class="toast table-sync-toast">
      <span>{{ state.toast }}</span>
      <button class="primary small" @click="confirmTableSync">同步</button>
      <button class="secondary small" @click="skipTableSync">暂不</button>
    </div>
    <p v-else-if="state.toast" class="toast">{{ state.toast }}</p>
  </section>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, onBeforeUnmount, onMounted, type PropType } from 'vue';
import { eventBus } from '../../shared/eventBus';
import {
  appendProfileSuggestions,
  appendStageSuggestion,
  cancelEditMode,
  chooseCandidate,
  cleanupCustomerProfileStore,
  confirmTableSync,
  customerProfileState as state,
  dismissCandidates,
  enterEditMode,
  generateReplyFromProfile,
  handleSendConfirmed,
  handleStageUpdated,
  openProfile,
  resolveProfileSuggestion,
  saveProfileEdits,
  scheduleSearch,
  searchImmediately,
  skipTableSync,
  showCandidates
} from './customerProfileStore';
import type { Customer, ProfileSuggestion, RecognizeMultiplePayload, StageSuggestPayload } from './types';

const customer = computed(() => state.profile?.customer ?? {} as Customer);
const summaryText = computed(() => `${customer.value.nickname || '-'} · ${maskPhone(customer.value.phone || '')} · ${customer.value.customerStage || '-'}`);

const intentItems = computed<Array<[string, unknown]>>(() => [
  ['来源渠道', editField('sourceChannel')],
  ['意向门店', editField('intendedStore')],
  ['意向项目', editField('intendedProject')],
  ['已购项目', editField('purchasedProject')],
  ['意向度', editField('intentLevel')],
  ['担忧点', editField('worries')]
]);

const bodyItems = computed<Array<[string, unknown]>>(() => [
  ['产后月份', editField('postpartumMonths')],
  ['胎次', editField('parity')],
  ['分娩方式', editField('deliveryMethod')],
  ['母乳状态', editField('breastfeeding')],
  ['身体关注', editField('bodyConcerns')],
  ['运动习惯', editField('exerciseHabits')]
]);

const appointmentItems = computed<Array<[string, unknown]>>(() => [
  ['预约日期', editField('appointmentDate')],
  ['预约门店', editField('appointmentStore')],
  ['预约项目', editField('appointmentItem')],
  ['是否到店', editField('arrived')],
  ['最近跟进', formatDate(customer.value.lastFollowupAt)],
  ['同步时间', formatDate(customer.value.syncedAt)]
]);

const disposers: Array<() => void> = [];
let skipNextInput = false;

onMounted(() => {
  disposers.push(eventBus.on<RecognizeMultiplePayload>('recognize:multiple', showCandidates));
  disposers.push(eventBus.on<{ phone?: string; suggestions?: ProfileSuggestion[] }>('suggestion:show', appendProfileSuggestions));
  disposers.push(eventBus.on<StageSuggestPayload>('stage:suggest', appendStageSuggestion));
  disposers.push(eventBus.on<{ phone?: string; newStage?: string }>('stage:updated', handleStageUpdated));
  disposers.push(eventBus.on<{ phone?: string }>('reply:send-confirmed', handleSendConfirmed));
});

onBeforeUnmount(() => {
  disposers.splice(0).forEach((dispose) => dispose());
  cleanupCustomerProfileStore();
});

function onPaste(event: ClipboardEvent) {
  const text = event.clipboardData?.getData('text')?.trim();
  if (text) {
    skipNextInput = true;
    searchImmediately(text);
  }
}

function onInput() {
  if (skipNextInput) {
    skipNextInput = false;
    return;
  }
  scheduleSearch(state.keyword);
}

function refreshCurrent() {
  if (customer.value.phone) {
    void openProfile(customer.value.phone, 'PROFILE_CARD');
  }
}

function editField(key: keyof Customer): unknown {
  if (!state.editMode) {
    return customer.value[key] ?? '-';
  }
  return h('input', {
    value: String(state.editFields[key as string] ?? ''),
    onInput: (event: Event) => {
      state.editFields[key as string] = (event.target as HTMLInputElement).value;
    }
  });
}

function leadTypeLabel(value?: string | null): string {
  if (value === 'TUAN_GOU') return '团购客资';
  if (value === 'XIAN_SUO') return '线索客资';
  if (value === 'PENDING') return '待确认';
  return value || '-';
}

function maskPhone(phone: string): string {
  if (!phone) return '-';
  return phone.length >= 7 ? `${phone.slice(0, 3)}****${phone.slice(-4)}` : `****${phone.slice(-4)}`;
}

function formatDate(value?: string | null): string {
  if (!value) return '-';
  return value.replace('T', ' ').slice(0, 16);
}

function formatValue(value: unknown): string {
  if (value === null || value === undefined || value === '') return '-';
  return String(value);
}

function suggestionKey(suggestion: ProfileSuggestion): string {
  return `${suggestion.id ?? suggestion.suggestionId ?? suggestion.fieldName}-${String(suggestion.suggestedValue)}`;
}

const FieldGrid = defineComponent({
  props: {
    items: {
      type: Array as PropType<Array<[string, unknown]>>,
      required: true
    }
  },
  setup(props) {
    return () => h('div', { class: 'field-grid' }, props.items.map(([label, value]) =>
      h('div', { class: 'field-item' }, [
        h('span', { class: 'field-label' }, label),
        h('span', { class: 'field-value' }, [typeof value === 'object' && value !== null ? value as ReturnType<typeof h> : String(value ?? '-')])
      ])
    ));
  }
});

const ProfileSection = defineComponent({
  props: {
    title: {
      type: String,
      required: true
    },
    sectionKey: {
      type: String as PropType<'intent' | 'body' | 'followup' | 'suggestions' | 'appointment'>,
      required: true
    }
  },
  setup(props, { slots }) {
    return () => h('section', { class: 'profile-section' }, [
      h('button', {
        class: 'section-title',
        onClick: () => {
          state.sectionCollapsed[props.sectionKey] = !state.sectionCollapsed[props.sectionKey];
        }
      }, props.title),
      !state.sectionCollapsed[props.sectionKey] ? h('div', { class: 'section-body' }, slots.default?.()) : null
    ]);
  }
});
</script>
