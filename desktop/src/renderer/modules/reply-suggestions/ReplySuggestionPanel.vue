<template>
  <section class="reply-panel">
    <header class="panel-header">
      <div>
        <h2>回复建议</h2>
        <p>{{ summaryText }}</p>
      </div>
      <span class="status-pill">{{ state.currentScene }}</span>
    </header>

    <p v-if="state.abnormalAlert" class="alert-banner">
      {{ state.abnormalAlert.message || '当前客户存在异常提醒，请谨慎回复' }}
    </p>

    <div v-if="state.loadingMode !== 'NONE'" class="loading-skeleton">
      <div class="skeleton-stage">
        <span class="stage-icon"></span>
        <span>{{ state.currentStageText }}</span>
      </div>
      <div class="skeleton-cards">
        <div class="skeleton-card"></div>
        <div class="skeleton-card"></div>
        <div class="skeleton-card"></div>
      </div>
    </div>

    <template v-else>
      <p v-if="state.isFallbackMode" class="fallback-banner">{{ state.fallbackBannerText }}</p>

      <div v-if="state.suggestions.length" class="reply-list">
        <article v-for="(suggestion, index) in state.suggestions" :key="`${suggestion.direction}-${index}`" class="reply-card">
          <div class="card-head">
            <span class="direction">{{ suggestion.direction }}</span>
            <button class="primary small" @click="selectReply(suggestion)">复制</button>
          </div>
          <p class="reply-text">{{ suggestion.text }}</p>
          <p class="reason">{{ suggestion.reason || '推荐理由暂缺' }}</p>
        </article>
      </div>

      <p v-else class="empty-panel">等待识别聊天后展示 AI 回复建议</p>

      <footer class="reply-actions">
        <button
          v-if="state.showRegenerateButton && state.suggestions.length"
          class="secondary"
          :disabled="state.regenerating"
          @click="regenerateReplies(false)"
        >
          {{ state.regenerating ? '生成中...' : '换一组' }}
        </button>
        <button class="secondary" :disabled="!state.currentPhone || Boolean(state.activeHelpId)" @click="requestLeaderHelp">
          {{ state.activeHelpId ? '等待组长回复...' : '求助组长' }}
        </button>
      </footer>
    </template>

    <p v-if="state.showHelpHint" class="hint">{{ state.helpHintMessage }}</p>

    <section v-if="state.profileSuggestions.length" class="profile-suggestions">
      <div class="suggestion-head">
        <button class="link-button" @click="state.profileSuggestionsExpanded = !state.profileSuggestionsExpanded">
          AI 更新建议 ({{ pendingProfileSuggestionCount }})
        </button>
        <div>
          <button class="secondary small" :disabled="pendingProfileSuggestionCount === 0" @click="resolveProfileSuggestion('CONFIRM')">全部确认</button>
          <button class="secondary small" :disabled="pendingProfileSuggestionCount === 0" @click="resolveProfileSuggestion('REJECT')">全部拒绝</button>
        </div>
      </div>
      <div v-if="state.profileSuggestionsExpanded" class="suggestion-list">
        <article v-for="item in state.profileSuggestions" :key="`${item.fieldName}-${String(item.suggestedValue)}`" class="suggestion-item">
          <div>
            <strong>{{ item.fieldName }}</strong>
            <p>{{ formatValue(item.currentValue) }} → {{ formatValue(item.suggestedValue) }}</p>
            <p class="reason">{{ item.reason || 'AI 建议更新该字段' }}</p>
          </div>
          <div class="suggestion-actions">
            <span v-if="item.resolved">{{ item.resolveAction === 'CONFIRM' ? '已确认' : '已拒绝' }}</span>
            <template v-else>
              <button class="secondary small" :disabled="item.resolving" @click="resolveProfileSuggestion('CONFIRM', item)">确认</button>
              <button class="secondary small" :disabled="item.resolving" @click="resolveProfileSuggestion('REJECT', item)">拒绝</button>
            </template>
          </div>
        </article>
      </div>
    </section>

    <p v-if="state.toast" class="toast">{{ state.toast }}</p>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue';
import { eventBus } from '../../shared/eventBus';
import {
  cleanupReplySuggestionStore,
  handleAbnormalAlert,
  handleHelpTimeout,
  handleHelpPending,
  handleHelpResolved,
  handleProfileSuggestions,
  pauseForMultipleMatch,
  pendingProfileSuggestionCount,
  regenerateReplies,
  replySuggestionState as state,
  requestLeaderHelp,
  resolveProfileSuggestion,
  selectReply,
  showRecognizeResult,
  startGenerateLoading,
  startRecognizeLoading,
  stopForImageFailure,
  stopForTimeout
} from './replySuggestionStore';
import type { AbnormalAlertPayload, CustomerSelectedPayload, ProfileSuggestionsPayload, RecognizeResultPayload } from './types';

const summaryText = computed(() => {
  if (!state.currentPhone && !state.currentNickname) {
    return '当前无会话';
  }
  return `${state.currentNickname || '-'} · ${maskPhoneForView(state.currentPhone)} · ${state.currentMatchType}`;
});

const disposers: Array<() => void> = [];

onMounted(() => {
  disposers.push(eventBus.on('recognize:start', startRecognizeLoading));
  disposers.push(eventBus.on<RecognizeResultPayload>('recognize:result', showRecognizeResult));
  disposers.push(eventBus.on('recognize:multiple', pauseForMultipleMatch));
  disposers.push(eventBus.on('recognize:image-failed', stopForImageFailure));
  disposers.push(eventBus.on('recognize:timeout', stopForTimeout));
  disposers.push(eventBus.on<CustomerSelectedPayload>('customer:selected', startGenerateLoading));
  disposers.push(eventBus.on<{ phone?: string; reason?: string }>('help:timeout', handleHelpTimeout));
  disposers.push(eventBus.on<{ helpId?: string | number; phone?: string }>('help:pending', handleHelpPending));
  disposers.push(eventBus.on<{ helpId?: string | number; phone?: string }>('help:resolved', handleHelpResolved));
  disposers.push(eventBus.on<ProfileSuggestionsPayload>('PROFILE_SUGGESTIONS', handleProfileSuggestions));
  disposers.push(eventBus.on<AbnormalAlertPayload>('abnormal:alert', handleAbnormalAlert));
});

onBeforeUnmount(() => {
  disposers.splice(0).forEach((dispose) => dispose());
  cleanupReplySuggestionStore();
});

function formatValue(value: unknown): string {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  return String(value);
}

function maskPhoneForView(phone: string): string {
  if (!phone) {
    return '-';
  }
  return `****${phone.slice(-4)}`;
}
</script>
