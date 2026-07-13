<template>
  <section v-if="state.requestDialogVisible" class="modal-backdrop">
    <div class="help-dialog">
      <header class="panel-header">
        <div>
          <h2>求助组长</h2>
          <p>把当前上下文发给组长判断</p>
        </div>
        <button class="icon-close-button" type="button" aria-label="关闭求助" title="关闭求助" @click="closeHelpRequest">
          <span aria-hidden="true">×</span>
        </button>
      </header>
      <blockquote>{{ state.activeRequest?.clientMessage || '当前客户最近消息未记录' }}</blockquote>
      <div class="help-suggestions">
        <article v-for="item in state.activeRequest?.aiSuggestions ?? []" :key="`${item.direction}-${item.text}`">
          <strong>{{ item.direction }}</strong>
          <p>{{ item.text }}</p>
        </article>
      </div>
      <textarea v-model="state.keeperNote" maxlength="500" placeholder="具体卡在哪里？组长看到能更快帮你"></textarea>
      <button class="primary" :disabled="state.sendingRequest" @click="submitHelpRequest">
        {{ state.sendingRequest ? '发送中...' : '发送求助' }}
      </button>
      <p v-if="state.toast" class="toast">{{ state.toast }}</p>
    </div>
  </section>

  <section v-if="state.statusNotice" :class="['help-status-notice', `level-${state.statusNotice.level}`]">
    <div>
      <strong>{{ state.statusNotice.message }}</strong>
      <span v-if="state.statusNotice.detail">{{ state.statusNotice.detail }}</span>
    </div>
    <button class="icon-close-button" type="button" aria-label="关闭求助状态" title="关闭求助状态" @click="dismissHelpStatusNotice">
      <span aria-hidden="true">×</span>
    </button>
  </section>

  <section v-if="state.helperQueue.length" class="help-leader-panel">
    <header class="help-alert red">
      <strong>{{ state.helperQueue.length }} 条待处理求助</strong>
      <span>{{ currentRequest?.requesterName }}：{{ currentRequest?.clientMessage.slice(0, 30) }}</span>
    </header>
    <div v-if="currentRequest" class="help-detail">
      <header class="panel-header">
        <div>
          <h2>{{ currentRequest.requesterName }} 的求助</h2>
          <p>{{ currentRequest.forwardedFrom?.originalLeaderName ? `${currentRequest.forwardedFrom.originalLeaderName} 组员` : '直属求助' }}</p>
        </div>
      </header>
      <blockquote>{{ currentRequest.clientMessage }}</blockquote>
      <div class="help-suggestions">
        <article v-for="item in currentRequest.aiSuggestions" :key="`${item.direction}-${item.text}`">
          <strong>{{ item.direction }}</strong>
          <p>{{ item.text }}</p>
          <button class="secondary small" @click="addConfirmedReply(item)">确认采用</button>
          <button class="secondary small" @click="addModifiedReply(item)">修改</button>
        </article>
      </div>
      <p v-if="currentRequest.keeperNote" class="hint">同事补充：{{ currentRequest.keeperNote }}</p>
      <div class="help-drafts">
        <article v-for="(reply, index) in state.draftReplies" :key="index">
          <textarea :value="reply.text" :readonly="reply.source === 'CONFIRMED'" @input="updateDraftReply(index, ($event.target as HTMLTextAreaElement).value)"></textarea>
          <span>{{ reply.direction }}</span>
          <button class="secondary small" @click="removeDraftReply(index)">删除此条</button>
        </article>
      </div>
      <button class="secondary" @click="addOriginalReply">添加一条回复</button>
      <button class="primary" :disabled="state.resolving" @click="submitHelpResolve">
        {{ state.resolving ? '发送中...' : `发送回复给${currentRequest.requesterName}` }}
      </button>
    </div>
  </section>

  <section v-if="state.receivedResponse" class="help-response-panel">
    <button class="help-alert green" @click="toggleHelpResponseExpanded">
      组长已回复你的求助 <span>查看</span>
    </button>
    <div v-if="state.responseExpanded" class="help-response-list">
      <header class="panel-header">
        <button class="secondary small" @click="closeHelpResponse">返回</button>
        <strong>组长回复（来自{{ state.receivedResponse.helperName || '组长' }}）</strong>
      </header>
      <article v-for="reply in state.receivedResponse.helperReplies" :key="`${reply.source}-${reply.text}`" class="reply-card">
        <span class="direction">{{ reply.direction }}</span>
        <p class="reply-text">{{ reply.text }}</p>
        <button class="primary small" @click="copyHelperReply(reply)">复制</button>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue';
import { eventBus } from '../../shared/eventBus';
import {
  addConfirmedReply,
  addModifiedReply,
  addOriginalReply,
  cleanupHelpModeStore,
  closeHelpRequest,
  closeHelpResponse,
  copyHelperReply,
  currentHelperRequest,
  dismissHelpStatusNotice,
  handleHelpOfflineReplay,
  handleHelpRequest,
  handleHelpResponse,
  helpModeState as state,
  openHelpRequest,
  removeDraftReply,
  submitHelpRequest,
  submitHelpResolve,
  toggleHelpResponseExpanded,
  updateDraftReply
} from './helpModeStore';
import type { HelpRequestEvent, HelpRequestPayload, HelpResponsePayload } from './types';

const currentRequest = computed(() => currentHelperRequest());
const disposers: Array<() => void> = [];

onMounted(() => {
  disposers.push(eventBus.on<HelpRequestEvent>('help:request', openHelpRequest));
  disposers.push(eventBus.on<HelpRequestPayload>('HELP_REQUEST', handleHelpRequest));
  disposers.push(eventBus.on<HelpRequestPayload>('HELP_OFFLINE_REPLAY', handleHelpOfflineReplay));
  disposers.push(eventBus.on<HelpResponsePayload>('HELP_RESPONSE', handleHelpResponse));
});

onBeforeUnmount(() => {
  disposers.splice(0).forEach((dispose) => dispose());
  cleanupHelpModeStore();
});
</script>
