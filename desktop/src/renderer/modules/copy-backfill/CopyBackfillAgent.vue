<template>
  <section v-if="state.suggestionToastVisible" class="suggestion-toast">
    <div class="suggestion-toast-header">
      <strong>AI 更新建议 ({{ pendingCount }})</strong>
      <button class="secondary small" @click="closeSuggestionToast">收起</button>
    </div>
    <div class="suggestion-list">
      <article v-for="item in state.suggestionToastSuggestions" :key="`${item.fieldName}-${String(item.suggestedValue)}`" class="suggestion-item">
        <div>
          <strong>{{ item.fieldName }}</strong>
          <p>{{ formatValue(item.currentValue) }} → {{ formatValue(item.suggestedValue) }}</p>
          <p class="reason">{{ item.reason || 'AI 建议更新该字段' }}</p>
        </div>
        <div class="suggestion-actions">
          <span v-if="item.resolved">{{ item.resolveAction === 'CONFIRM' ? '已确认' : '已拒绝' }}</span>
          <template v-else>
            <button class="secondary small" :disabled="item.resolving" @click="resolveToastSuggestion('CONFIRM', item)">确认</button>
            <button class="secondary small" :disabled="item.resolving" @click="resolveToastSuggestion('REJECT', item)">拒绝</button>
          </template>
        </div>
      </article>
    </div>
    <div class="reply-actions">
      <button class="secondary small" :disabled="pendingCount === 0" @click="resolveToastSuggestion('CONFIRM')">全部确认</button>
      <button class="secondary small" :disabled="pendingCount === 0" @click="resolveToastSuggestion('REJECT')">全部拒绝</button>
    </div>
  </section>

  <button v-else-if="state.suggestionToastCollapsed" class="suggestion-reopen" @click="reopenSuggestionToast">
    AI 更新建议
  </button>

  <p v-if="state.toast" class="toast copy-toast">{{ state.toast }}</p>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue';
import { eventBus } from '../../shared/eventBus';
import {
  cleanupCopyBackfillStore,
  closeSuggestionToast,
  copyBackfillState as state,
  handleReplySelected,
  handleSuggestionShow,
  reopenSuggestionToast,
  resolveToastSuggestion
} from './copyBackfillStore';
import type { ReplySelectedPayload, SuggestionShowPayload } from './types';

const disposers: Array<() => void> = [];
const pendingCount = computed(() => state.suggestionToastSuggestions.filter((item) => !item.resolved).length);

onMounted(() => {
  disposers.push(eventBus.on<ReplySelectedPayload>('reply:selected', (payload) => {
    void handleReplySelected(payload);
  }));
  disposers.push(eventBus.on<SuggestionShowPayload>('suggestion:show', handleSuggestionShow));
  disposers.push(eventBus.on('recognize:start', closeSuggestionToast));
});

onBeforeUnmount(() => {
  disposers.splice(0).forEach((dispose) => dispose());
  cleanupCopyBackfillStore();
});

function formatValue(value: unknown): string {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  return String(value);
}
</script>
