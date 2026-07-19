import { computed, reactive, watch } from 'vue';
import { postJson } from '../../shared/apiClient';
import { loadDesktopConfig } from '../../shared/config';
import { eventBus } from '../../shared/eventBus';
import { getAlertsByPhone } from '../abnormal-alert/alertStore';
import type {
  AbnormalAlertPayload,
  ChatResponse,
  CustomerSelectedPayload,
  ProfileSuggestion,
  ProfileSuggestionsPayload,
  RecognizeFailurePayload,
  RecognizeProgressPayload,
  RecognizeProgressStage,
  RecognizeResultPayload,
  RecognizeStartPayload,
  ReplyCandidate,
  ReplyScene,
  ReplySelectedPayload,
  ReplySession,
  ReplySuggestion
} from './types';

const IMAGE_STAGE_TEXTS = ['已获取截图', '正在提交截图', '等待识图模型返回', '正在生成回复'];
const TEXT_STAGE_TEXTS = ['已收到文字内容', '正在匹配客户档案', '正在生成回复'];
const STAGE_DURATIONS = [1200, 1800, 5000, 7500];
const FALLBACK_DIRECTION = 'SYSTEM_FALLBACK';
const DISMISSED_SESSION_TTL_MS = 30 * 60 * 1000;
const DISMISSED_SESSION_MAX = 200;
const PERSISTED_SESSIONS_PREFIX = 'reply-suggestion-sessions-v1:';

type LoadingMode = 'NONE' | 'FULL' | 'SIMPLE';

export const replySuggestionState = reactive({
  sessions: [] as ReplySession[],
  activeSessionId: '',
  loadingMode: 'NONE' as LoadingMode,
  currentStageIndex: 0,
  currentStageText: '',
  progressStage: 'DONE' as RecognizeProgressStage,
  failureReason: '',
  suggestions: [] as ReplySuggestion[],
  replySource: null as ChatResponse['replySource'],
  candidates: [] as ReplyCandidate[],
  currentPhone: '',
  currentNickname: '',
  currentLeadType: '',
  currentScene: 'CHAT_RECOGNIZE' as ReplyScene,
  currentMatchType: 'NONE',
  regenerating: false,
  regenerateCount: 0,
  isFallbackMode: false,
  fallbackText: '',
  fallbackBannerText: '',
  fallbackRetryCount: 0,
  showRegenerateButton: true,
  showHelpHint: false,
  helpHintMessage: '',
  profileSuggestions: [] as ProfileSuggestion[],
  profileSuggestionsExpanded: true,
  abnormalAlert: null as AbnormalAlertPayload | null,
  activeHelpId: '' as string | number | '',
  toast: ''
});

export const activeReplySession = computed(() =>
  replySuggestionState.sessions.find((session) => session.sessionId === replySuggestionState.activeSessionId) ?? null
);

export const pendingProfileSuggestionCount = computed(() =>
  replySuggestionState.profileSuggestions.filter((item) => !item.resolved).length
);

let skeletonTimer: number | null = null;
let fallbackRetryTimer: number | null = null;
let fallbackRetrySessionId = '';
let generatedSessionSequence = 0;
const dismissedSessionIds = new Map<string, number>();
let hydrated = false;
let persistedStorageKey = '';
let skipNextPersistence = false;

watch(
  () => ({ sessions: replySuggestionState.sessions, activeSessionId: replySuggestionState.activeSessionId }),
  () => {
    if (skipNextPersistence) {
      skipNextPersistence = false;
      return;
    }
    persistReplySessions();
  },
  { deep: true }
);

export function hydrateReplySuggestionStore(): void {
  if (hydrated) return;
  hydrated = true;
  persistedStorageKey = storageKey();
  try {
    const raw = localStorage.getItem(persistedStorageKey);
    if (!raw) return;
    const parsed = JSON.parse(raw) as { sessions?: ReplySession[]; activeSessionId?: string };
    const sessions = Array.isArray(parsed.sessions) ? parsed.sessions.map(recoverSession) : [];
    replySuggestionState.sessions = sessions;
    replySuggestionState.activeSessionId = sessions.some((item) => item.sessionId === parsed.activeSessionId)
      ? parsed.activeSessionId ?? ''
      : sessions[0]?.sessionId ?? '';
    syncActiveSessionToState();
  } catch {
    // Ignore malformed local session data and start with an empty queue.
  }
}

export function startRecognizeLoading(payload: RecognizeStartPayload = {}): void {
  if (isDismissedSession(payload.sessionId)) return;
  const shouldKeepCurrentLoadingTask = Boolean(activeReplySession.value?.status === 'LOADING');
  const session = createSession(payload.sessionId ?? nextSessionId(), 'FULL', payload.source);
  if (!session) return;
  session.currentScene = 'CHAT_RECOGNIZE';
  session.currentStageIndex = 0;
  session.currentStageText = stageTextFor(session, 0);
  session.progressStage = stageFor(session, 0);
  session.failureReason = '';
  session.status = 'LOADING';
  if (shouldKeepCurrentLoadingTask && session.sessionId !== replySuggestionState.activeSessionId) {
    syncActiveSessionToState();
    return;
  }
  activateSession(session.sessionId);
}

export function updateRecognizeProgress(payload: RecognizeProgressPayload = {}): void {
  if (isDismissedSession(payload.sessionId)) return;
  const session = sessionForPayload(payload);
  if (!session) return;
  if (payload.stage) {
    session.progressStage = payload.stage;
    session.currentStageIndex = stageIndexFor(session, payload.stage);
  }
  if (payload.message) {
    session.currentStageText = payload.message;
  } else if (payload.stage) {
    session.currentStageText = stageTextFor(session, session.currentStageIndex);
  }
  session.updatedAt = Date.now();
  if (session.sessionId === replySuggestionState.activeSessionId) {
    syncActiveSessionToState();
  }
}

export function startGenerateLoading(payload: CustomerSelectedPayload): void {
  if (isDismissedSession(payload.sessionId)) return;
  const session = payload.sessionId
    ? createSession(payload.sessionId, 'SIMPLE', payload.sourceFrom)
    : createSession(nextSessionId(), 'SIMPLE', payload.sourceFrom);
  if (!session) return;
  stopFallbackRetry(session.sessionId);
  session.currentStageText = '正在生成回复...';
  session.progressStage = 'GENERATING';
  session.failureReason = '';
  session.suggestions = [];
  session.currentPhone = payload.phone ?? '';
  session.currentLeadType = payload.leadType ?? '';
  session.currentScene = payload.scene ?? 'ACTIVE_REPLY';
  session.status = 'LOADING';
  session.loadingMode = 'SIMPLE';
  session.source = payload.sourceFrom ?? session.source;
  session.updatedAt = Date.now();
  activateSession(session.sessionId);
}

export function pauseForMultipleMatch(payload: RecognizeStartPayload & { candidates?: ReplyCandidate[]; matchInfo?: { customers?: ReplyCandidate[] } } = {}): void {
  if (isDismissedSession(payload.sessionId)) return;
  const session = sessionForPayload(payload);
  if (!session) return;
  session.status = 'MULTIPLE';
  session.candidates = (payload.candidates ?? payload.matchInfo?.customers ?? []).slice(0, 5);
  session.loadingMode = 'NONE';
  session.progressStage = 'DONE';
  session.currentStageText = session.currentStageText || '等待选择客户...';
  session.updatedAt = Date.now();
  if (session.sessionId === replySuggestionState.activeSessionId) {
    clearSkeletonTimer();
  }
  syncActiveSessionToState();
}

export function stopForImageFailure(payload: RecognizeFailurePayload = {}): void {
  if (isDismissedSession(payload.sessionId)) return;
  markSessionFailed(payload, '图片识别失败，请使用文字通道后重新生成回复');
}

export function stopForTimeout(payload: RecognizeFailurePayload = {}): void {
  if (isDismissedSession(payload.sessionId)) return;
  markSessionFailed(payload, '请求超时，回复建议已停止加载');
}

export function stopForFailure(payload: RecognizeFailurePayload = {}): void {
  if (isDismissedSession(payload.sessionId)) return;
  markSessionFailed(payload, payload.message || messageForErrorCode(payload.errorCode));
}

export function showRecognizeResult(payload: RecognizeResultPayload): void {
  const sessionId = sessionIdFromPayload(payload);
  if (isDismissedSession(sessionId)) return;
  const response = normalizeResponse(payload);
  const session = sessionForPayload({ sessionId }) ?? createSession(sessionId ?? nextSessionId(), 'FULL', sourceFromPayload(payload));
  if (!session) return;
  const shouldKeepCurrentLoadingTask = Boolean(
    activeReplySession.value?.status === 'LOADING'
    && activeReplySession.value.sessionId !== session.sessionId
  );
  showChatResponse(session, response, 'CHAT_RECOGNIZE');
  if (!shouldKeepCurrentLoadingTask) {
    activateSession(session.sessionId);
  } else {
    syncActiveSessionToState();
  }
}

export async function regenerateReplies(isAutomaticRetry = false): Promise<void> {
  await regenerateSessionReplies(activeReplySession.value, isAutomaticRetry);
}

async function regenerateSessionReplies(session: ReplySession | null, isAutomaticRetry = false): Promise<void> {
  const config = loadDesktopConfig();
  if (!session?.currentPhone) {
    setActiveToast('当前客户信息不足，无法换一组');
    return;
  }
  session.regenerating = true;
  if (!isAutomaticRetry) {
    session.loadingMode = 'SIMPLE';
    session.currentStageText = '正在生成新回复...';
    session.status = 'LOADING';
    syncActiveSessionToState();
  }
  try {
    const response = await postJson<ChatResponse>('/api/v1/chat/regenerate', {
      phone: session.currentPhone,
      leadType: session.currentLeadType,
      scene: 'REGENERATE',
      previousSuggestions: isAutomaticRetry ? [] : session.suggestions.map((item) => item.text)
    }, config.requestTotalTimeoutMs);
    if (!response.success || !response.data) {
      handleRegenerateFailure(session, isAutomaticRetry, response.errorCode);
      return;
    }
    if (!isAutomaticRetry) {
      session.regenerateCount += 1;
    }
    showChatResponse(session, response.data, 'REGENERATE');
  } catch {
    handleRegenerateFailure(session, isAutomaticRetry);
  } finally {
    session.regenerating = false;
    syncActiveSessionToState();
  }
}

export function selectReply(suggestion: ReplySuggestion): void {
  const session = activeReplySession.value;
  const payload: ReplySelectedPayload = {
    text: suggestion.text,
    direction: suggestion.direction,
    reason: suggestion.reason,
    phone: session?.currentPhone ?? '',
    displayPhone: maskPhone(session?.currentPhone ?? ''),
    isFallback: suggestion.direction === FALLBACK_DIRECTION || Boolean(session?.isFallbackMode)
  };
  eventBus.emit('reply:selected', payload);
  if (session) {
    session.status = 'COPIED';
    session.copiedAt = Date.now();
    session.toast = '已发送复制事件，等待复制回填模块处理';
    syncActiveSessionToState();
  }
}

export function requestLeaderHelp(): void {
  const session = activeReplySession.value;
  stopFallbackRetry();
  if (!session) return;
  if (session.activeHelpId) {
    session.toast = '你已有等待中的求助，请等待组长回复后再发起新求助';
    syncActiveSessionToState();
    return;
  }
  if (!session.currentPhone) {
    session.toast = '请先识别聊天或选择客户';
    syncActiveSessionToState();
    return;
  }
  eventBus.emit('help:request', {
    phone: session.currentPhone,
    clientMessage: '',
    aiSuggestions: session.suggestions.map((item) => ({ text: item.text, direction: item.direction }))
  });
  session.toast = '已发起求助入口';
  syncActiveSessionToState();
}

export function handleHelpPending(payload: { helpId?: string | number; phone?: string }): void {
  const session = sessionForPhone(payload.phone);
  if (!session) return;
  session.activeHelpId = payload.helpId ?? 'pending';
  session.toast = '等待组长回复...';
  syncActiveSessionToState();
}

export function handleHelpResolved(payload: { helpId?: string | number; phone?: string }): void {
  const session = sessionForPhone(payload.phone);
  if (!session) return;
  session.activeHelpId = '';
  session.toast = '组长已回复你的求助';
  syncActiveSessionToState();
}

export function handleHelpTimeout(payload: { phone?: string; reason?: string }): void {
  const session = sessionForPhone(payload.phone);
  if (!session) return;
  session.showHelpHint = true;
  session.activeHelpId = '';
  session.helpHintMessage = '当前所有组长均不在线，建议参考已有回复选择最接近的手动调整后发送';
  syncActiveSessionToState();
}

export function handleProfileSuggestions(payload: ProfileSuggestionsPayload): void {
  const session = sessionForPhone(payload.phone);
  if (!session || !payload.phone) return;
  session.profileSuggestions = (payload.suggestions ?? []).map((item) => ({
    ...item,
    resolved: false,
    resolving: false
  }));
  session.profileSuggestionsExpanded = true;
  eventBus.emit('suggestion:show', {
    phone: payload.phone,
    suggestions: session.profileSuggestions
  });
  syncActiveSessionToState();
}

export function handleAbnormalAlert(payload: AbnormalAlertPayload): void {
  const session = sessionForPhone(payload.phone);
  if (!session) return;
  session.abnormalAlert = payload.acknowledged ? null : payload;
  syncActiveSessionToState();
}

export async function resolveProfileSuggestion(action: 'CONFIRM' | 'REJECT', suggestion?: ProfileSuggestion): Promise<void> {
  const session = activeReplySession.value;
  const targets = suggestion ? [suggestion] : (session?.profileSuggestions ?? []).filter((item) => !item.resolved);
  if (!session?.currentPhone || targets.length === 0) {
    return;
  }
  targets.forEach((item) => {
    item.resolving = true;
  });
  const suggestionIds = targets.map((item) => item.suggestionId).filter((id): id is number => typeof id === 'number');
  try {
    await postJson(`/api/v1/customers/${encodeURIComponent(session.currentPhone)}/suggestions/batch-resolve`, {
      action: action === 'CONFIRM' ? 'CONFIRM' : 'REJECT',
      suggestionIds,
      operator: 'desktop'
    });
    targets.forEach((item) => {
      item.resolved = true;
      item.resolving = false;
      item.resolveAction = action;
    });
    if (session.profileSuggestions.filter((item) => !item.resolved).length === 0) {
      session.profileSuggestionsExpanded = false;
    }
  } catch {
    targets.forEach((item) => {
      item.resolving = false;
    });
    session.toast = 'AI 更新建议处理失败，请稍后重试';
  } finally {
    syncActiveSessionToState();
  }
}

export function activateSession(sessionId: string): void {
  const session = replySuggestionState.sessions.find((item) => item.sessionId === sessionId);
  if (!session) return;
  replySuggestionState.activeSessionId = sessionId;
  if (session.loadingMode === 'FULL') {
    scheduleNextSkeletonStage();
  } else {
    clearSkeletonTimer();
  }
  syncActiveSessionToState();
}

export function closeReplySession(sessionId: string): void {
  const index = replySuggestionState.sessions.findIndex((item) => item.sessionId === sessionId);
  rememberDismissedSession(sessionId);
  stopFallbackRetry(sessionId);
  if (index < 0) return;
  const wasActive = replySuggestionState.activeSessionId === sessionId;
  replySuggestionState.sessions.splice(index, 1);
  if (wasActive) {
    const nextSession = replySuggestionState.sessions[Math.max(0, index - 1)] ?? replySuggestionState.sessions[0];
    replySuggestionState.activeSessionId = nextSession?.sessionId ?? '';
  }
  if (wasActive && activeReplySession.value?.loadingMode !== 'FULL') {
    clearSkeletonTimer();
  }
  syncActiveSessionToState();
  if (wasActive && activeReplySession.value?.loadingMode === 'FULL') {
    scheduleNextSkeletonStage();
  }
}

export function selectCandidateForSession(sessionId: string, candidate: ReplyCandidate): void {
  if (isDismissedSession(sessionId)) return;
  const session = replySuggestionState.sessions.find((item) => item.sessionId === sessionId);
  if (!session || !candidate.phone) return;
  activateSession(sessionId);
  eventBus.emit('customer:selected', {
    sessionId,
    phone: candidate.phone,
    scene: 'CHAT_RECOGNIZE',
    leadType: candidate.leadType ?? '',
    sourceFrom: 'CANDIDATE_LIST'
  });
}

export function cleanupReplySuggestionStore(): void {
  const snapshot = serializeSessions();
  clearSkeletonTimer();
  stopFallbackRetry();
  dismissedSessionIds.clear();
  skipNextPersistence = true;
  replySuggestionState.sessions = [];
  replySuggestionState.activeSessionId = '';
  syncActiveSessionToState();
  persistSnapshot(snapshot);
  hydrated = false;
  persistedStorageKey = '';
}

function storageKey(): string {
  const username = loadDesktopConfig().accountUsername || 'anonymous';
  return `${PERSISTED_SESSIONS_PREFIX}${username}`;
}

function persistReplySessions(): void {
  if (!hydrated && replySuggestionState.sessions.length === 0) return;
  if (!persistedStorageKey) persistedStorageKey = storageKey();
  persistSnapshot(serializeSessions());
}

function serializeSessions(): { sessions: ReplySession[]; activeSessionId: string } {
  return {
    sessions: replySuggestionState.sessions,
    activeSessionId: replySuggestionState.activeSessionId
  };
}

function persistSnapshot(snapshot: { sessions: ReplySession[]; activeSessionId: string }): void {
  try {
    localStorage.setItem(persistedStorageKey || storageKey(), JSON.stringify(snapshot));
  } catch {
    // Local persistence is best effort; the live queue remains usable.
  }
}

function recoverSession(session: ReplySession): ReplySession {
  if (session.status !== 'LOADING') return session;
  return {
    ...session,
    status: 'FAILED',
    loadingMode: 'NONE',
    progressStage: 'FAILED',
    failureReason: '上次识别被中断，请重新识别'
  };
}

function showChatResponse(session: ReplySession, response: ChatResponse, scene: ReplyScene): void {
  stopFallbackRetry(session.sessionId);
  session.loadingMode = 'NONE';
  session.progressStage = 'DONE';
  session.currentStageText = '回复已生成，可直接复制';
  session.failureReason = '';
  session.currentScene = scene;
  session.currentPhone = response.phone ?? session.currentPhone;
  session.abnormalAlert = session.currentPhone ? getAlertsByPhone(session.currentPhone)[0] ?? null : null;
  session.currentNickname = response.nickname ?? session.currentNickname;
  session.currentMatchType = response.match?.matchType ?? (response.needsCustomerIdentifier ? 'NONE' : session.currentMatchType);
  const suggestions = response.skill?.suggestions ?? [];
  session.replySource = response.replySource ?? null;
  if (suggestions.length === 0) {
    enterFallbackMode(session, '系统返回异常，请重试');
    return;
  }
  if (suggestions[0]?.direction === FALLBACK_DIRECTION) {
    enterFallbackMode(session, suggestions[0].text);
    return;
  }
  session.status = 'READY';
  session.isFallbackMode = false;
  session.fallbackText = '';
  session.fallbackBannerText = '';
  session.showRegenerateButton = true;
  session.showHelpHint = Boolean(response.warning);
  session.helpHintMessage = response.warning ?? '';
  session.suggestions = suggestions;
  session.updatedAt = Date.now();
}

function enterFallbackMode(session: ReplySession, text: string): void {
  session.status = 'FALLBACK';
  session.loadingMode = 'NONE';
  session.progressStage = 'DONE';
  session.currentStageText = '已生成降级回复';
  session.isFallbackMode = true;
  if (session.replySource?.source !== 'FALLBACK') {
    session.replySource = { source: 'FALLBACK', label: '系统兜底', detail: 'AI 服务不可用，已使用降级回复' };
  }
  session.fallbackText = text;
  session.fallbackBannerText = 'AI 助手暂时不可用，正在自动重试恢复...';
  session.suggestions = [{ text, direction: FALLBACK_DIRECTION, reason: '系统降级回复' }];
  session.showRegenerateButton = false;
  session.updatedAt = Date.now();
  startFallbackRetry(session);
}

function startFallbackRetry(session: ReplySession): void {
  stopFallbackRetry();
  const config = loadDesktopConfig();
  session.fallbackRetryCount = 0;
  fallbackRetrySessionId = session.sessionId;
  const run = () => {
    const current = replySuggestionState.sessions.find((item) => item.sessionId === fallbackRetrySessionId);
    if (!current) return;
    if (current.fallbackRetryCount >= config.fallbackMaxRetries) {
      current.fallbackBannerText = '暂时无法生成回复，请手动回复';
      current.showRegenerateButton = true;
      stopFallbackRetry();
      syncActiveSessionToState();
      return;
    }
    current.fallbackRetryCount += 1;
    void regenerateSessionReplies(current, true).then(() => {
      const latest = replySuggestionState.sessions.find((item) => item.sessionId === fallbackRetrySessionId);
      if (latest?.isFallbackMode) {
        fallbackRetryTimer = window.setTimeout(run, config.fallbackRetryIntervalMs);
      }
    });
  };
  fallbackRetryTimer = window.setTimeout(run, config.fallbackRetryIntervalMs);
}

function stopFallbackRetry(sessionId?: string): void {
  if (sessionId && fallbackRetrySessionId && fallbackRetrySessionId !== sessionId) {
    return;
  }
  if (fallbackRetryTimer) {
    window.clearTimeout(fallbackRetryTimer);
    fallbackRetryTimer = null;
  }
  fallbackRetrySessionId = '';
}

function rememberDismissedSession(sessionId: string): void {
  dismissedSessionIds.set(sessionId, Date.now());
  pruneDismissedSessions();
}

function isDismissedSession(sessionId?: string): boolean {
  if (!sessionId) {
    return false;
  }
  pruneDismissedSessions();
  return dismissedSessionIds.has(sessionId);
}

function pruneDismissedSessions(): void {
  if (dismissedSessionIds.size === 0) {
    return;
  }
  const now = Date.now();
  for (const [sessionId, dismissedAt] of dismissedSessionIds) {
    if (now - dismissedAt > DISMISSED_SESSION_TTL_MS) {
      dismissedSessionIds.delete(sessionId);
    }
  }
  while (dismissedSessionIds.size > DISMISSED_SESSION_MAX) {
    const oldest = dismissedSessionIds.keys().next().value;
    if (!oldest) break;
    dismissedSessionIds.delete(oldest);
  }
}

function handleRegenerateFailure(session: ReplySession, isAutomaticRetry: boolean, errorCode?: string | null): void {
  if (isAutomaticRetry) {
    return;
  }
  session.loadingMode = 'NONE';
  session.status = 'FAILED';
  session.progressStage = 'FAILED';
  session.failureReason = errorCode === '80-10002' ? '登录已失效，请重新登录' : '换一组失败，请重试';
  session.currentStageText = session.failureReason;
  session.toast = session.failureReason;
}

function scheduleNextSkeletonStage(): void {
  clearSkeletonTimer();
  const session = activeReplySession.value;
  if (!session || session.loadingMode !== 'FULL' || session.currentStageIndex >= stageTextsFor(session).length - 1) {
    return;
  }
  skeletonTimer = window.setTimeout(() => {
    const active = activeReplySession.value;
    if (!active || active.loadingMode !== 'FULL') return;
    active.currentStageIndex += 1;
    active.currentStageText = stageTextFor(active, active.currentStageIndex);
    active.progressStage = stageFor(active, active.currentStageIndex);
    syncActiveSessionToState();
    scheduleNextSkeletonStage();
  }, STAGE_DURATIONS[session.currentStageIndex]);
}

function clearSkeletonTimer(): void {
  if (skeletonTimer) {
    window.clearTimeout(skeletonTimer);
    skeletonTimer = null;
  }
}

function createSession(sessionId: string, loadingMode: LoadingMode, source?: string): ReplySession | null {
  if (isDismissedSession(sessionId)) {
    return null;
  }
  const existing = replySuggestionState.sessions.find((session) => session.sessionId === sessionId);
  if (existing) return existing;
  const now = Date.now();
  const session: ReplySession = {
    sessionId,
    status: loadingMode === 'NONE' ? 'READY' : 'LOADING',
    source,
    createdAt: now,
    updatedAt: now,
    loadingMode,
    currentStageIndex: 0,
    currentStageText: '',
    progressStage: loadingMode === 'NONE' ? 'DONE' : 'CAPTURED',
    failureReason: '',
    suggestions: [],
    replySource: null,
    candidates: [],
    currentPhone: '',
    currentNickname: '',
    currentLeadType: '',
    currentScene: 'CHAT_RECOGNIZE',
    currentMatchType: 'NONE',
    regenerating: false,
    regenerateCount: 0,
    isFallbackMode: false,
    fallbackText: '',
    fallbackBannerText: '',
    fallbackRetryCount: 0,
    showRegenerateButton: true,
    showHelpHint: false,
    helpHintMessage: '',
    profileSuggestions: [],
    profileSuggestionsExpanded: true,
    abnormalAlert: null,
    activeHelpId: '',
    toast: ''
  };
  session.currentStageText = loadingMode === 'FULL' ? stageTextFor(session, 0) : '';
  replySuggestionState.sessions.unshift(session);
  return session;
}

function sessionForPayload(payload: { sessionId?: string }): ReplySession | null {
  if (payload.sessionId) {
    return replySuggestionState.sessions.find((session) => session.sessionId === payload.sessionId) ?? null;
  }
  return activeReplySession.value;
}

function sessionForPhone(phone?: string): ReplySession | null {
  if (!phone) {
    return activeReplySession.value;
  }
  return replySuggestionState.sessions.find((session) => session.currentPhone === phone) ?? null;
}

function syncActiveSessionToState(): void {
  const session = activeReplySession.value;
  replySuggestionState.loadingMode = session?.loadingMode ?? 'NONE';
  replySuggestionState.currentStageIndex = session?.currentStageIndex ?? 0;
  replySuggestionState.currentStageText = session?.currentStageText ?? '';
  replySuggestionState.progressStage = session?.progressStage ?? 'DONE';
  replySuggestionState.failureReason = session?.failureReason ?? '';
  replySuggestionState.suggestions = session?.suggestions ?? [];
  replySuggestionState.replySource = session?.replySource ?? null;
  replySuggestionState.currentPhone = session?.currentPhone ?? '';
  replySuggestionState.currentNickname = session?.currentNickname ?? '';
  replySuggestionState.currentLeadType = session?.currentLeadType ?? '';
  replySuggestionState.currentScene = session?.currentScene ?? 'CHAT_RECOGNIZE';
  replySuggestionState.currentMatchType = session?.currentMatchType ?? 'NONE';
  replySuggestionState.regenerating = session?.regenerating ?? false;
  replySuggestionState.regenerateCount = session?.regenerateCount ?? 0;
  replySuggestionState.isFallbackMode = session?.isFallbackMode ?? false;
  replySuggestionState.fallbackText = session?.fallbackText ?? '';
  replySuggestionState.fallbackBannerText = session?.fallbackBannerText ?? '';
  replySuggestionState.fallbackRetryCount = session?.fallbackRetryCount ?? 0;
  replySuggestionState.showRegenerateButton = session?.showRegenerateButton ?? true;
  replySuggestionState.showHelpHint = session?.showHelpHint ?? false;
  replySuggestionState.helpHintMessage = session?.helpHintMessage ?? '';
  replySuggestionState.profileSuggestions = session?.profileSuggestions ?? [];
  replySuggestionState.profileSuggestionsExpanded = session?.profileSuggestionsExpanded ?? true;
  replySuggestionState.abnormalAlert = session?.abnormalAlert ?? null;
  replySuggestionState.activeHelpId = session?.activeHelpId ?? '';
  replySuggestionState.toast = session?.toast ?? '';
}

function setActiveToast(message: string): void {
  const session = activeReplySession.value;
  if (session) {
    session.toast = message;
  }
  replySuggestionState.toast = message;
}

function normalizeResponse(payload: RecognizeResultPayload): ChatResponse {
  if ('response' in payload && payload.response) {
    return payload.response;
  }
  return payload as ChatResponse;
}

function sessionIdFromPayload(payload: RecognizeResultPayload): string | undefined {
  return 'sessionId' in payload ? payload.sessionId : undefined;
}

function sourceFromPayload(payload: RecognizeResultPayload): string | undefined {
  return 'source' in payload ? payload.source : undefined;
}

function nextSessionId(): string {
  generatedSessionSequence += 1;
  return `reply-local-${Date.now()}-${generatedSessionSequence}`;
}

function maskPhone(phone: string): string {
  if (!phone) {
    return '';
  }
  const tail = phone.slice(-4);
  return tail ? `****${tail}` : '';
}

function markSessionFailed(payload: RecognizeFailurePayload, fallbackMessage: string): void {
  const session = sessionForPayload(payload);
  if (!session) return;
  const message = payload.message || fallbackMessage;
  session.loadingMode = 'NONE';
  session.status = 'FAILED';
  session.progressStage = 'FAILED';
  session.currentStageText = message;
  session.failureReason = message;
  session.suggestions = [];
  session.toast = message;
  session.updatedAt = Date.now();
  if (session.sessionId === replySuggestionState.activeSessionId) {
    clearSkeletonTimer();
  }
  syncActiveSessionToState();
}

function messageForErrorCode(errorCode?: string | null): string {
  if (errorCode === '30-10001') return '图片识别失败，请使用文字通道后重新生成回复';
  if (errorCode === '30-10002') return '图片格式不支持，请重新截图或使用 PNG/JPG';
  if (errorCode === '80-10002') return '登录已失效，请重新登录';
  return '识别失败，请稍后重试';
}

function stageTextsFor(session: ReplySession): string[] {
  return session.source === 'CLIPBOARD_TEXT' ? TEXT_STAGE_TEXTS : IMAGE_STAGE_TEXTS;
}

function stageTextFor(session: ReplySession, index: number): string {
  return stageTextsFor(session)[index] ?? '正在处理';
}

function stageFor(session: ReplySession, index: number): RecognizeProgressStage {
  if (session.source === 'CLIPBOARD_TEXT') {
    if (index <= 0) return 'UPLOADING';
    if (index === 1) return 'WAITING_MODEL';
    return 'GENERATING';
  }
  if (index <= 0) return 'CAPTURED';
  if (index === 1) return 'UPLOADING';
  if (index === 2) return 'WAITING_MODEL';
  return 'GENERATING';
}

function stageIndexFor(session: ReplySession, stage: RecognizeProgressStage): number {
  if (stage === 'FAILED' || stage === 'DONE') {
    return session.currentStageIndex;
  }
  const stages: RecognizeProgressStage[] = session.source === 'CLIPBOARD_TEXT'
    ? ['UPLOADING', 'WAITING_MODEL', 'GENERATING']
    : ['CAPTURED', 'UPLOADING', 'WAITING_MODEL', 'GENERATING'];
  return Math.max(0, stages.indexOf(stage));
}
