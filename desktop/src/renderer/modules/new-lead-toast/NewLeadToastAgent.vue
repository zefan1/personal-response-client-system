<template>
  <section class="new-lead-stack" aria-live="polite">
    <article v-for="item in state.visibleQueue" :key="item.id" :class="['new-lead-toast', leadTypeClass(item.leadType)]">
      <header class="new-lead-head">
        <strong>{{ item.nickname || '新客户' }}</strong>
        <span>{{ leadTypeLabel(item.leadType) }} · {{ relativeTime(item.arrivedAt) }}</span>
      </header>
      <p>{{ item.phone }}</p>
      <p>{{ item.sourceTable || '来源未填写' }}</p>
      <div class="reply-actions">
        <button class="secondary small" @click="copyNewLeadPhone(item)">复制手机号</button>
        <button class="primary small" @click="generateOpening(item)">生成开场白</button>
      </div>
    </article>
    <button v-if="state.pendingQueue.length" class="new-lead-collapsed" @click="switchToNewLeadTab">
      还有 {{ state.pendingQueue.length }} 条新客资
    </button>
  </section>
  <p v-if="state.toast" class="toast new-lead-light-toast">{{ state.toast }}</p>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue';
import { eventBus } from '../../shared/eventBus';
import {
  cleanupNewLeadToastStore,
  copyNewLeadPhone,
  enqueueNewLeadToast,
  generateOpening,
  newLeadToastState as state,
  switchToNewLeadTab
} from './newLeadToastStore';
import type { NewLeadAlertPayload } from './types';

let dispose: (() => void) | null = null;

onMounted(() => {
  dispose = eventBus.on<NewLeadAlertPayload>('NEW_LEAD_ALERT', enqueueNewLeadToast);
});

onBeforeUnmount(() => {
  dispose?.();
  cleanupNewLeadToastStore();
});

function leadTypeLabel(value?: string): string {
  if (value === 'TUAN_GOU') return '团购';
  if (value === 'XIAN_SUO') return '线索';
  return '待确认';
}

function leadTypeClass(value?: string): string {
  if (value === 'TUAN_GOU') return 'lead-red';
  if (value === 'XIAN_SUO') return 'lead-blue';
  return 'lead-gray';
}

function relativeTime(value?: string): string {
  if (!value) {
    return '刚刚';
  }
  const diffMs = Math.max(0, Date.now() - new Date(value).getTime());
  const minutes = Math.floor(diffMs / 60000);
  return minutes <= 0 ? '刚刚' : `${minutes}分钟前`;
}
</script>
