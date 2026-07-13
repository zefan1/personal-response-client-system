<template>
  <p v-if="state.toast" class="toast copy-toast">{{ state.toast }}</p>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue';
import { eventBus } from '../../shared/eventBus';
import {
  cleanupCopyBackfillStore,
  closeSuggestionToast,
  copyBackfillState as state,
  handleReplySelected
} from './copyBackfillStore';
import type { ReplySelectedPayload } from './types';

const disposers: Array<() => void> = [];

onMounted(() => {
  disposers.push(eventBus.on<ReplySelectedPayload>('reply:selected', (payload) => {
    void handleReplySelected(payload);
  }));
  disposers.push(eventBus.on('recognize:start', closeSuggestionToast));
});

onBeforeUnmount(() => {
  disposers.splice(0).forEach((dispose) => dispose());
  cleanupCopyBackfillStore();
});
</script>
