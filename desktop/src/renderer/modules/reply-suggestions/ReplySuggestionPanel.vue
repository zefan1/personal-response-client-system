<template>
  <section class="reply-panel">
    <header class="panel-header reply-hero">
      <div>
        <h2>回复助手</h2>
        <p>{{ summaryText }}</p>
      </div>
      <div class="reply-header-actions">
        <span class="status-pill">{{ sceneLabel(state.currentScene) }}</span>
        <button class="primary small" type="button" @click="requestGlobalRecognize">识别聊天</button>
        <button class="secondary small" type="button" @click="toggleTextMode">
          {{ recognitionFallbackState.isTwoBoxMode ? '收起文字' : '文字通道' }}
        </button>
      </div>
    </header>

    <section v-if="primarySuggestion" class="reply-primary-card reply-card" aria-label="推荐回复">
      <div class="card-head">
        <div class="reply-primary-title">
          <span class="direction">{{ directionLabel(primarySuggestion.direction) }}</span>
          <strong>推荐回复</strong>
          <span v-if="state.replySource" class="reply-source-pill" :class="replySourceClass(state.replySource.source)" :title="state.replySource.detail || replySourceLabel(state.replySource.source)">
            {{ replySourceLabel(state.replySource.source, state.replySource.label) }}
          </span>
        </div>
        <button class="primary small" @click="selectReply(primarySuggestion)">复制</button>
      </div>
      <p class="reply-text">{{ primarySuggestion.text }}</p>
      <p class="reason">{{ primarySuggestion.reason || '推荐理由暂缺' }}</p>
      <div class="reply-actions reply-primary-actions">
        <button
          v-if="state.showRegenerateButton"
          class="secondary small"
          :disabled="state.regenerating"
          @click="regenerateReplies(false)"
        >
          {{ state.regenerating ? '生成中...' : '换一组' }}
        </button>
        <button class="secondary small" :disabled="!state.currentPhone || Boolean(state.activeHelpId)" @click="requestLeaderHelp">
          {{ state.activeHelpId ? '等待组长回复...' : '求助组长' }}
        </button>
      </div>
    </section>

    <section v-if="secondarySuggestions.length" class="reply-list reply-more-list reply-alt-list" aria-label="更多建议">
      <div class="section-inline-head compact">
        <div>
          <h3>更多建议</h3>
          <p class="hint-text">保留不同语气和下一步方向，方便按客户状态切换。</p>
        </div>
      </div>
      <article v-for="(suggestion, index) in secondarySuggestions" :key="`${suggestion.direction}-${index + 1}`" class="reply-card reply-alt-card">
        <div class="card-head">
          <span class="direction">{{ directionLabel(suggestion.direction) }}</span>
          <button class="primary small" @click="selectReply(suggestion)">复制</button>
        </div>
        <p class="reply-text">{{ suggestion.text }}</p>
        <p class="reason">{{ suggestion.reason || '推荐理由暂缺' }}</p>
      </article>
    </section>

    <section v-if="activeSession" class="reply-current-task" :class="sessionStatusClass(activeSession)">
      <div>
        <span class="task-kicker">当前任务</span>
        <strong>{{ sessionLabel(activeSession) }}</strong>
        <p>{{ activeTaskText }}</p>
        <time class="reply-current-time" :datetime="sessionIsoTime(activeSession)">{{ sessionTimeText(activeSession) }}</time>
      </div>
      <div class="reply-current-actions">
        <span class="reply-task-status">{{ sessionStatusLabel(activeSession.status) }}</span>
        <span v-if="activeSession.replySource" class="reply-source-pill compact" :class="replySourceClass(activeSession.replySource.source)" :title="activeSession.replySource.detail || replySourceLabel(activeSession.replySource.source)">
          {{ replySourceLabel(activeSession.replySource.source, activeSession.replySource.label) }}
        </span>
        <button v-if="canCopySession(activeSession)" class="primary small" type="button" @click="copySessionReply(activeSession)">
          复制
        </button>
        <button v-if="activeSession.status === 'LOADING'" class="secondary small" type="button" @click="openTextChannel(activeSession.sessionId)">
          文字
        </button>
        <button v-if="activeSession.status === 'FAILED'" class="secondary small" type="button" @click="retryRecognize(activeSession.sessionId)">
          重试
        </button>
        <button v-if="activeSession.status === 'FAILED'" class="secondary small" type="button" @click="openTextChannel(activeSession.sessionId)">
          文字
        </button>
        <button
          class="icon-close-button"
          type="button"
          :aria-label="`移除${sessionLabel(activeSession)}`"
          :title="`移除${sessionLabel(activeSession)}`"
          @click="requestRemoveSession(activeSession.sessionId)"
        >
          <span aria-hidden="true">×</span>
        </button>
      </div>
      <div v-if="pendingRemovalSessionId === activeSession.sessionId" class="reply-task-remove-confirm" @click.stop>
        <span>移除这条任务？</span>
        <button class="secondary small" type="button" @click="cancelRemoveSession">取消</button>
        <button class="secondary small danger" type="button" @click="confirmRemoveSession(activeSession.sessionId)">移除</button>
      </div>
    </section>

    <section v-if="state.sessions.length" class="reply-task-queue" aria-label="待处理队列">
      <div class="section-inline-head compact">
        <div>
          <h3>待处理队列</h3>
          <p class="hint-text">{{ queueSummary }}</p>
        </div>
      </div>

      <div v-if="queuedSessions.length" class="reply-task-list">
        <article
          v-for="session in queuedSessions"
          :key="session.sessionId"
          class="reply-task-row"
          :class="sessionStatusClass(session)"
          @click="activateSession(session.sessionId)"
        >
          <div class="reply-task-copy">
            <strong>{{ sessionLabel(session) }}</strong>
            <div class="reply-task-meta">
              <time class="reply-task-time" :datetime="sessionIsoTime(session)">{{ sessionTimeText(session) }}</time>
              <span class="reply-task-message">{{ queueRowText(session) }}</span>
            </div>
          </div>
          <span class="reply-task-badge">{{ sessionStatusLabel(session.status) }}</span>
          <span v-if="session.replySource" class="reply-source-pill compact" :class="replySourceClass(session.replySource.source)" :title="session.replySource.detail || replySourceLabel(session.replySource.source)">
            {{ replySourceLabel(session.replySource.source, session.replySource.label) }}
          </span>
          <div class="reply-task-actions" @click.stop>
            <button v-if="canCopySession(session)" class="primary small" type="button" @click="copySessionReply(session)">
              复制
            </button>
            <button v-if="session.status === 'LOADING'" class="secondary small" type="button" @click="openTextChannel(session.sessionId)">
              文字
            </button>
            <button v-if="session.status === 'FAILED'" class="secondary small" type="button" @click="retryRecognize(session.sessionId)">
              重试
            </button>
            <button v-if="session.status === 'FAILED'" class="secondary small" type="button" @click="openTextChannel(session.sessionId)">
              文字
            </button>
            <button
              class="icon-close-button"
              type="button"
              :aria-label="`移除${sessionLabel(session)}`"
              :title="`移除${sessionLabel(session)}`"
              @click="requestRemoveSession(session.sessionId)"
            >
              <span aria-hidden="true">×</span>
            </button>
          </div>
          <div v-if="pendingRemovalSessionId === session.sessionId" class="reply-task-remove-confirm" @click.stop>
            <span>移除这条任务？</span>
            <button class="secondary small" type="button" @click="cancelRemoveSession">取消</button>
            <button class="secondary small danger" type="button" @click="confirmRemoveSession(session.sessionId)">移除</button>
          </div>
          <div v-if="session.status === 'MULTIPLE' && session.candidates.length" class="reply-candidate-actions" @click.stop>
            <button
              v-for="candidate in session.candidates"
              :key="`${session.sessionId}-${candidate.phone}`"
              class="secondary small"
              type="button"
              @click="selectCandidateForSession(session.sessionId, candidate)"
            >
              {{ candidateLabel(candidate) }}
            </button>
          </div>
        </article>
      </div>
      <p v-else class="reply-queue-empty">当前任务已在上方展示，暂无其他待处理任务。</p>
    </section>

    <section class="reply-detail-panel" aria-label="当前任务详情">
      <p v-if="state.abnormalAlert" class="alert-banner">
        {{ state.abnormalAlert.message || '当前客户存在异常提醒，请谨慎回复' }}
      </p>

      <section v-if="copySuggestionItems.length" class="profile-suggestions inline-profile-suggestions">
        <div class="suggestion-head">
          <button class="link-button suggestion-toggle" type="button" @click="toggleCopySuggestions">
            <span>资料更新建议（{{ pendingCopySuggestionCount }}）</span>
            <small>{{ copySuggestionSummary }}</small>
          </button>
          <div>
            <button class="secondary small" :disabled="pendingCopySuggestionCount === 0" @click="resolveToastSuggestion('CONFIRM')">全部确认</button>
            <button class="secondary small" :disabled="pendingCopySuggestionCount === 0" @click="resolveToastSuggestion('REJECT')">全部拒绝</button>
          </div>
        </div>
        <div v-if="copyBackfillState.suggestionToastVisible" class="suggestion-list">
          <article v-for="item in copySuggestionItems" :key="`${item.fieldName}-${String(item.suggestedValue)}`" class="suggestion-item">
            <div>
              <strong>{{ item.fieldName }}</strong>
              <p>{{ formatValue(item.currentValue) }} -> {{ formatValue(item.suggestedValue) }}</p>
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
      </section>

      <div v-if="state.loadingMode !== 'NONE'" class="reply-progress-panel">
        <div class="skeleton-stage">
          <span class="stage-icon"></span>
          <span>{{ state.currentStageText }}</span>
        </div>
        <div v-if="activeSession" class="reply-detail-actions">
          <button class="secondary small" type="button" @click="openTextChannel(activeSession.sessionId)">文字通道</button>
          <button
            class="icon-close-button"
            type="button"
            :aria-label="`移除${sessionLabel(activeSession)}`"
            :title="`移除${sessionLabel(activeSession)}`"
            @click="requestRemoveSession(activeSession.sessionId)"
          >
            <span aria-hidden="true">×</span>
          </button>
        </div>
      </div>

      <template v-else>
        <p v-if="state.isFallbackMode" class="fallback-banner">{{ state.fallbackBannerText }}</p>

        <div v-if="activeSession?.status === 'FAILED'" class="reply-failure-state">
          <strong>{{ state.failureReason || '识别失败' }}</strong>
          <p>可以重新截取当前聊天，或改用文字通道粘贴客户标识和聊天内容。</p>
          <div>
            <button class="primary small" type="button" @click="retryRecognize(activeSession.sessionId)">重试识别</button>
            <button class="secondary small" type="button" @click="openTextChannel(activeSession.sessionId)">改用文字通道</button>
            <button
              class="icon-close-button"
              type="button"
              :aria-label="`移除${sessionLabel(activeSession)}`"
              :title="`移除${sessionLabel(activeSession)}`"
              @click="requestRemoveSession(activeSession.sessionId)"
            >
              <span aria-hidden="true">×</span>
            </button>
          </div>
        </div>

        <div v-else-if="activeSession?.status === 'MULTIPLE'" class="reply-multiple-state">
          <strong>请选择对应客户</strong>
          <p>识别到了多个可能客户，选择后会继续生成回复。</p>
          <div class="reply-candidate-actions">
            <button
              v-for="candidate in activeSession.candidates"
              :key="`${activeSession.sessionId}-${candidate.phone}`"
              class="secondary small"
              type="button"
              @click="selectCandidateForSession(activeSession.sessionId, candidate)"
            >
              {{ candidateLabel(candidate) }}
            </button>
            <button
              class="icon-close-button"
              type="button"
              :aria-label="`移除${sessionLabel(activeSession)}`"
              :title="`移除${sessionLabel(activeSession)}`"
              @click="requestRemoveSession(activeSession.sessionId)"
            >
              <span aria-hidden="true">×</span>
            </button>
          </div>
        </div>

        <div v-if="!state.suggestions.length" class="reply-empty-state">
          <span class="reply-empty-icon" aria-hidden="true">识</span>
          <strong>还没有识别当前聊天</strong>
          <p>点击“识别聊天”后，这里会显示可复制的回复建议。</p>
          <button class="primary small" type="button" @click="requestGlobalRecognize">识别聊天</button>
        </div>

      </template>

      <section class="reply-text-channel">
        <div class="section-inline-head">
          <div>
            <h3>备用文字输入</h3>
            <p class="hint-text">截图失败或图片服务不可用时，可粘贴客户标识和聊天内容。</p>
          </div>
          <button class="secondary small" type="button" @click="toggleTextMode">
            {{ recognitionFallbackState.isTwoBoxMode ? '收起' : '文字通道' }}
          </button>
        </div>
        <form v-if="recognitionFallbackState.isTwoBoxMode" class="two-box compact" @submit.prevent="handleTextSubmit">
          <input v-model="recognitionFallbackState.customerIdentityInput" placeholder="客户手机号、昵称或微信备注" />
          <textarea v-model="recognitionFallbackState.chatContentInput" placeholder="粘贴当前聊天内容"></textarea>
          <button class="primary" type="button" @click="handleTextSubmit">发送文字</button>
        </form>
        <p v-if="recognitionFallbackState.toast" class="toast">{{ recognitionFallbackState.toast }}</p>
      </section>
    </section>

    <p v-if="state.showHelpHint" class="hint">{{ state.helpHintMessage }}</p>
    <p v-if="state.toast" class="toast">{{ state.toast }}</p>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { eventBus } from '../../shared/eventBus';
import {
  closeTextMode,
  openTextMode,
  recognitionState as recognitionFallbackState,
  submitTextRecognition
} from '../chat-recognition/recognitionStore';
import {
  closeSuggestionToast,
  copyBackfillState,
  handleSuggestionShow,
  reopenSuggestionToast,
  resolveToastSuggestion
} from '../copy-backfill/copyBackfillStore';
import type { SuggestionShowPayload } from '../copy-backfill/types';
import {
  activateSession,
  activeReplySession,
  cleanupReplySuggestionStore,
  closeReplySession,
  handleAbnormalAlert,
  handleHelpPending,
  handleHelpResolved,
  handleHelpTimeout,
  handleProfileSuggestions,
  pauseForMultipleMatch,
  regenerateReplies,
  replySuggestionState as state,
  requestLeaderHelp,
  selectCandidateForSession,
  selectReply,
  showRecognizeResult,
  startGenerateLoading,
  startRecognizeLoading,
  stopForFailure,
  stopForImageFailure,
  stopForTimeout,
  updateRecognizeProgress
} from './replySuggestionStore';
import type {
  AbnormalAlertPayload,
  CustomerSelectedPayload,
  ProfileSuggestionsPayload,
  RecognizeFailurePayload,
  RecognizeProgressPayload,
  RecognizeResultPayload,
  ReplyCandidate,
  ReplyScene,
  ReplySourceInfo,
  ReplySession,
  ReplySessionStatus
} from './types';

const summaryText = computed(() => {
  if (!state.currentPhone && !state.currentNickname) {
    return '当前无会话';
  }
  return `${state.currentNickname || '-'} · ${maskPhoneForView(state.currentPhone)} · ${state.currentMatchType}`;
});
const copySuggestionItems = computed(() => copyBackfillState.suggestionToastSuggestions);
const pendingCopySuggestionCount = computed(() => copySuggestionItems.value.filter((item) => !item.resolved).length);
const activeSession = computed(() => activeReplySession.value);
const pendingRemovalSessionId = ref('');
const queuedSessions = computed(() => state.sessions.filter((session) => session.sessionId !== state.activeSessionId));
const primarySuggestion = computed(() => state.suggestions[0] ?? null);
const secondarySuggestions = computed(() => state.suggestions.slice(1));
const queueSummary = computed(() => {
  const loadingCount = queuedSessions.value.filter((session) => session.status === 'LOADING').length;
  const readyCount = queuedSessions.value.filter((session) => session.status === 'READY' || session.status === 'FALLBACK').length;
  const failedCount = queuedSessions.value.filter((session) => session.status === 'FAILED').length;
  return `${queuedSessions.value.length} 个其他任务 · ${readyCount} 个可复制 · ${loadingCount} 个处理中 · ${failedCount} 个失败`;
});
const activeTaskText = computed(() => activeSession.value ? queueRowText(activeSession.value) : '等待识别聊天');
const copySuggestionSummary = computed(() => {
  const firstPending = copySuggestionItems.value.find((item) => !item.resolved) ?? copySuggestionItems.value[0];
  if (!firstPending) {
    return '';
  }
  return copyBackfillState.suggestionToastVisible
    ? '点击收起'
    : `${firstPending.fieldName}: ${formatValue(firstPending.currentValue)} -> ${formatValue(firstPending.suggestedValue)}`;
});

const disposers: Array<() => void> = [];

onMounted(() => {
  disposers.push(eventBus.on('recognize:start', startRecognizeLoading));
  disposers.push(eventBus.on<RecognizeProgressPayload>('recognize:progress', updateRecognizeProgress));
  disposers.push(eventBus.on<RecognizeResultPayload>('recognize:result', showRecognizeResult));
  disposers.push(eventBus.on('recognize:multiple', pauseForMultipleMatch));
  disposers.push(eventBus.on<RecognizeFailurePayload>('recognize:image-failed', stopForImageFailure));
  disposers.push(eventBus.on<RecognizeFailurePayload>('recognize:failed', stopForFailure));
  disposers.push(eventBus.on<RecognizeFailurePayload>('recognize:timeout', stopForTimeout));
  disposers.push(eventBus.on<CustomerSelectedPayload>('customer:selected', startGenerateLoading));
  disposers.push(eventBus.on<{ phone?: string; reason?: string }>('help:timeout', handleHelpTimeout));
  disposers.push(eventBus.on<{ helpId?: string | number; phone?: string }>('help:pending', handleHelpPending));
  disposers.push(eventBus.on<{ helpId?: string | number; phone?: string }>('help:resolved', handleHelpResolved));
  disposers.push(eventBus.on<ProfileSuggestionsPayload>('PROFILE_SUGGESTIONS', handleProfileSuggestions));
  disposers.push(eventBus.on<SuggestionShowPayload>('suggestion:show', handleSuggestionShow));
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

function toggleTextMode(): void {
  if (recognitionFallbackState.isTwoBoxMode) {
    closeTextMode();
    return;
  }
  openTextMode();
}

function openTextChannel(sessionId?: string): void {
  if (sessionId) {
    activateSession(sessionId);
  }
  openTextMode();
}

async function handleTextSubmit(): Promise<void> {
  await submitTextRecognition();
}

function requestGlobalRecognize(): void {
  eventBus.emit('desktop:recognize-request', {});
}

function retryRecognize(sessionId?: string): void {
  if (sessionId) {
    activateSession(sessionId);
  }
  requestGlobalRecognize();
}

function requestRemoveSession(sessionId: string): void {
  pendingRemovalSessionId.value = pendingRemovalSessionId.value === sessionId ? '' : sessionId;
}

function cancelRemoveSession(): void {
  pendingRemovalSessionId.value = '';
}

function confirmRemoveSession(sessionId: string): void {
  closeReplySession(sessionId);
  if (pendingRemovalSessionId.value === sessionId) {
    pendingRemovalSessionId.value = '';
  }
}

function copySessionReply(session: ReplySession): void {
  activateSession(session.sessionId);
  const firstSuggestion = session.suggestions[0];
  if (firstSuggestion) {
    selectReply(firstSuggestion);
  }
}

function sessionLabel(session: ReplySession): string {
  if (session.currentNickname && session.currentPhone) {
    return `${session.currentNickname} · ${maskPhoneForView(session.currentPhone)}`;
  }
  if (session.currentNickname) {
    return session.currentNickname;
  }
  if (session.currentPhone) {
    return maskPhoneForView(session.currentPhone);
  }
  return '识别中';
}

function candidateLabel(candidate: ReplyCandidate): string {
  return `${candidate.nickname || maskPhoneForView(candidate.phone)} · ${leadTypeLabel(candidate.leadType)}`;
}

function leadTypeLabel(value?: string | null): string {
  if (value === 'TUAN_GOU') return '团购';
  if (value === 'XIAN_SUO') return '线索';
  if (value === 'PENDING') return '待确认';
  return value || '-';
}

function sceneLabel(value?: ReplyScene): string {
  if (value === 'CHAT_RECOGNIZE') return '聊天识别';
  if (value === 'ACTIVE_REPLY') return '主动回复';
  if (value === 'REGENERATE') return '换一组';
  if (value === 'OPENING') return '开场白';
  if (value === 'PROFILE_EXTRACT') return '档案提取';
  return value || '-';
}

function directionLabel(value: string): string {
  if (value === 'SYSTEM_FALLBACK') return '降级回复';
  if (value === 'NEXT_STEP') return '下一步';
  return value;
}

function replySourceLabel(source?: ReplySourceInfo['source'], fallbackLabel?: string): string {
  if (fallbackLabel) return fallbackLabel;
  if (source === 'LLM') return 'LLM 生成';
  if (source === 'SKILL') return 'Skill 生成';
  if (source === 'FALLBACK') return '系统兜底';
  return source || '来源未知';
}

function replySourceClass(source?: ReplySourceInfo['source']): string {
  if (source === 'LLM') return 'source-llm';
  if (source === 'SKILL') return 'source-skill';
  if (source === 'FALLBACK') return 'source-fallback';
  return 'source-unknown';
}

function sessionStatusLabel(status: ReplySessionStatus): string {
  if (status === 'LOADING') return '识别中';
  if (status === 'READY') return '可复制';
  if (status === 'FAILED') return '失败';
  if (status === 'FALLBACK') return '降级';
  if (status === 'COPIED') return '已复制';
  if (status === 'MULTIPLE') return '待选择';
  return status;
}

function queueRowText(session: ReplySession): string {
  if (session.status === 'FAILED') {
    return session.failureReason || session.currentStageText || '识别失败';
  }
  if (session.status === 'READY') {
    return session.suggestions[0]?.text || '回复已生成，可直接复制';
  }
  if (session.status === 'COPIED') {
    return session.suggestions[0]?.text || '已复制回复';
  }
  if (session.status === 'FALLBACK') {
    return session.fallbackBannerText || session.suggestions[0]?.text || '已生成降级回复';
  }
  if (session.status === 'MULTIPLE') {
    return session.candidates.length ? '匹配到多个客户，请选择一个继续' : '匹配到多个客户，请到客户档案选择';
  }
  return session.currentStageText || '正在处理';
}

function sessionTimeText(session: ReplySession): string {
  const timestamp = session.updatedAt || session.createdAt;
  const elapsedMs = Math.max(0, Date.now() - timestamp);
  const elapsedMinutes = Math.floor(elapsedMs / 60000);
  if (elapsedMinutes < 1) {
    return '刚刚';
  }
  if (elapsedMinutes < 60) {
    return `${elapsedMinutes} 分钟前`;
  }
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).format(new Date(timestamp));
}

function sessionIsoTime(session: ReplySession): string {
  return new Date(session.updatedAt || session.createdAt).toISOString();
}

function sessionStatusClass(session: ReplySession): string {
  return `status-${session.status.toLowerCase()}`;
}

function canCopySession(session: ReplySession): boolean {
  return (session.status === 'READY' || session.status === 'FALLBACK' || session.status === 'COPIED') && session.suggestions.length > 0;
}

function toggleCopySuggestions(): void {
  if (copyBackfillState.suggestionToastVisible) {
    closeSuggestionToast();
    return;
  }
  reopenSuggestionToast();
}
</script>
