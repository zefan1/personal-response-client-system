<template>
  <p v-if="state.toast" class="toast copy-toast">{{ state.toast }}</p>
  <div
    v-if="state.pendingSendDecision"
    class="send-confirm-gate"
    role="dialog"
    aria-modal="true"
    aria-labelledby="send-confirm-title"
  >
    <section class="send-confirm-dialog">
      <header>
        <p class="send-confirm-label">发送确认</p>
        <h2 id="send-confirm-title">这条回复是否已经发送？</h2>
      </header>

      <blockquote>{{ state.pendingSendDecision.text }}</blockquote>

      <div class="send-confirm-customer">
        <strong>{{ state.pendingSendDecision.nickname || '当前客户' }}</strong>
        <span v-if="state.pendingSendDecision.phone">
          {{ maskPhone(state.pendingSendDecision.phone) }}
        </span>
      </div>

      <p v-if="state.pendingSendDecision.errorMessage" class="send-confirm-error" role="alert">
        {{ state.pendingSendDecision.errorMessage }}
      </p>
      <p v-if="state.pendingSendDecision.reminderCount > 0" class="send-confirm-reminder">
        已提醒 {{ state.pendingSendDecision.reminderCount }} / 5 次
      </p>

      <div class="send-confirm-actions">
        <button
          class="secondary"
          type="button"
          :disabled="state.pendingSendDecision.status === 'SUBMITTING'"
          @click="discardPendingSendDecision"
        >未发送</button>
        <button
          class="primary send-confirm-submit"
          type="button"
          :disabled="state.pendingSendDecision.status === 'SUBMITTING'"
          @click="submitConfirmedSend"
        >
          {{ confirmButtonLabel }}
        </button>
        <button
          class="secondary"
          type="button"
          :disabled="state.pendingSendDecision.status === 'SUBMITTING'"
          @click="retryRecognition"
        >重新识别</button>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue';
import { eventBus } from '../../shared/eventBus';
import {
  confirmPendingSendDecision,
  cleanupCopyBackfillStore,
  closeSuggestionToast,
  copyBackfillState as state,
  discardPendingSendDecision,
  handleReplySelected,
  resumePendingSendReminder,
  retryRecognitionFromPending,
} from './copyBackfillStore';
import type { ReplySelectedPayload } from './types';

const disposers: Array<() => void> = [];
const confirmButtonLabel = computed(() => {
  if (state.pendingSendDecision?.status === 'SUBMITTING') return '正在确认';
  if (state.pendingSendDecision?.status === 'SUBMIT_FAILED') return '重试确认已发送';
  return '已发送';
});


onMounted(() => {
  resumePendingSendReminder();
  disposers.push(eventBus.on<ReplySelectedPayload>('reply:selected', (payload) => {
    void handleReplySelected(payload);
  }));
  disposers.push(eventBus.on('recognize:start', closeSuggestionToast));
});

onBeforeUnmount(() => {
  disposers.splice(0).forEach((dispose) => dispose());
  cleanupCopyBackfillStore();
});

async function submitConfirmedSend(): Promise<void> {
  await confirmPendingSendDecision();
}

function retryRecognition(): void {
  retryRecognitionFromPending();
}

function maskPhone(phone: string): string {
  return phone.length >= 7 ? `${phone.slice(0, 3)}****${phone.slice(-4)}` : phone;
}
</script>

<style scoped>
.send-confirm-gate {
  position: fixed;
  inset: 0;
  z-index: 1100;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(16, 20, 24, 0.72);
}

.send-confirm-dialog {
  width: min(520px, 100%);
  max-height: min(680px, calc(100vh - 48px));
  overflow: auto;
  padding: 24px;
  border: 1px solid #d7dce2;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 20px 48px rgba(0, 0, 0, 0.24);
  color: #20252b;
}

.send-confirm-dialog header,
.send-confirm-customer,
.send-confirm-actions {
  display: flex;
  align-items: center;
}

.send-confirm-dialog header {
  align-items: flex-start;
  flex-direction: column;
  gap: 6px;
}

.send-confirm-label {
  margin: 0;
  color: #52606d;
  font-size: 13px;
}

.send-confirm-dialog h2 {
  margin: 0;
  font-size: 22px;
  line-height: 1.35;
  letter-spacing: 0;
}

.send-confirm-dialog blockquote {
  margin: 20px 0;
  padding: 14px 16px;
  border-left: 3px solid #2d6a4f;
  background: #f5f7f8;
  color: #20252b;
  line-height: 1.6;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.send-confirm-customer {
  justify-content: space-between;
  gap: 16px;
  padding: 12px 0;
  border-top: 1px solid #e5e8eb;
  border-bottom: 1px solid #e5e8eb;
}

.send-confirm-customer span {
  color: #52606d;
}

.send-confirm-customer .send-confirm-warning,
.send-confirm-error,
.send-confirm-match-message {
  color: #b42318;
}

.send-confirm-error {
  margin: 14px 0 0;
  font-size: 14px;
}

.send-confirm-reminder {
  margin: 12px 0 0;
  color: #8a4b08;
  font-size: 13px;
}

.send-confirm-search {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  margin-top: 16px;
}

.send-confirm-search input {
  min-width: 0;
}

.send-confirm-match-message {
  margin: 10px 0 0;
  font-size: 14px;
}

.send-confirm-candidates {
  display: grid;
  gap: 0;
  margin-top: 12px;
  border-top: 1px solid #e5e8eb;
}

.send-confirm-candidate {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 12px 4px;
  border: 0;
  border-bottom: 1px solid #e5e8eb;
  border-radius: 0;
  background: transparent;
  color: #20252b;
  text-align: left;
  cursor: pointer;
}

.send-confirm-candidate:hover {
  background: #f5f7f8;
}

.send-confirm-candidate > span:first-child {
  display: grid;
  gap: 3px;
}

.send-confirm-candidate small,
.send-confirm-candidate > span:last-child {
  color: #52606d;
}

.send-confirm-actions {
  justify-content: flex-end;
  gap: 12px;
  margin-top: 22px;
}

@media (max-width: 600px) {
  .send-confirm-gate {
    padding: 12px;
  }

  .send-confirm-dialog {
    max-height: calc(100vh - 24px);
    padding: 18px;
  }

  .send-confirm-customer,
  .send-confirm-actions,
  .send-confirm-search {
    align-items: stretch;
    grid-template-columns: 1fr;
    flex-direction: column;
  }

  .send-confirm-actions button {
    width: 100%;
  }
}
</style>
