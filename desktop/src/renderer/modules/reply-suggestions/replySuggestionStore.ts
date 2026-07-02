import { computed, reactive } from 'vue';
import { postJson } from '../../shared/apiClient';
import { loadDesktopConfig } from '../../shared/config';
import { eventBus } from '../../shared/eventBus';
import type {
  AbnormalAlertPayload,
  ChatResponse,
  CustomerSelectedPayload,
  ProfileSuggestion,
  ProfileSuggestionsPayload,
  RecognizeResultPayload,
  ReplyScene,
  ReplySelectedPayload,
  ReplySuggestion
} from './types';

const STAGE_TEXTS = ['正在识别聊天内容...', '正在匹配客户档案...', '正在生成回复建议...'];
const STAGE_DURATIONS = [5000, 2500, 7500];
const FALLBACK_DIRECTION = 'SYSTEM_FALLBACK';

type LoadingMode = 'NONE' | 'FULL' | 'SIMPLE';

export const replySuggestionState = reactive({
  loadingMode: 'NONE' as LoadingMode,
  currentStageIndex: 0,
  currentStageText: '',
  suggestions: [] as ReplySuggestion[],
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
  toast: ''
});

export const pendingProfileSuggestionCount = computed(() =>
  replySuggestionState.profileSuggestions.filter((item) => !item.resolved).length
);

let skeletonTimer: number | null = null;
let fallbackRetryTimer: number | null = null;

export function startRecognizeLoading(): void {
  resetForNewEntry();
  replySuggestionState.loadingMode = 'FULL';
  replySuggestionState.currentScene = 'CHAT_RECOGNIZE';
  replySuggestionState.currentStageIndex = 0;
  replySuggestionState.currentStageText = STAGE_TEXTS[0];
  scheduleNextSkeletonStage();
}

export function startGenerateLoading(payload: CustomerSelectedPayload): void {
  resetForNewEntry();
  replySuggestionState.loadingMode = 'SIMPLE';
  replySuggestionState.currentStageText = '正在生成回复...';
  replySuggestionState.currentPhone = payload.phone ?? '';
  replySuggestionState.currentLeadType = payload.leadType ?? '';
  replySuggestionState.currentScene = payload.scene ?? 'ACTIVE_REPLY';
}

export function pauseForMultipleMatch(): void {
  clearSkeletonTimer();
  replySuggestionState.currentStageText = replySuggestionState.currentStageText || '等待选择客户...';
}

export function stopForImageFailure(): void {
  clearSkeletonTimer();
  replySuggestionState.loadingMode = 'NONE';
  replySuggestionState.suggestions = [];
  replySuggestionState.toast = '图片识别失败，请使用文字通道后重新生成回复';
}

export function stopForTimeout(): void {
  clearSkeletonTimer();
  replySuggestionState.loadingMode = 'NONE';
  replySuggestionState.toast = '请求超时，回复建议已停止加载';
}

export function showRecognizeResult(payload: RecognizeResultPayload): void {
  const response = normalizeResponse(payload);
  showChatResponse(response, 'CHAT_RECOGNIZE');
}

export async function regenerateReplies(isAutomaticRetry = false): Promise<void> {
  const config = loadDesktopConfig();
  if (!replySuggestionState.currentPhone) {
    replySuggestionState.toast = '当前客户信息不足，无法换一组';
    return;
  }
  replySuggestionState.regenerating = true;
  if (!isAutomaticRetry) {
    replySuggestionState.loadingMode = 'SIMPLE';
    replySuggestionState.currentStageText = '正在生成新回复...';
  }
  try {
    const response = await postJson<ChatResponse>('/api/v1/chat/regenerate', {
      phone: replySuggestionState.currentPhone,
      leadType: replySuggestionState.currentLeadType,
      scene: 'REGENERATE',
      previousSuggestions: isAutomaticRetry ? [] : replySuggestionState.suggestions.map((item) => item.text)
    }, config.requestTotalTimeoutMs);
    if (!response.success || !response.data) {
      handleRegenerateFailure(isAutomaticRetry, response.errorCode);
      return;
    }
    if (!isAutomaticRetry) {
      replySuggestionState.regenerateCount += 1;
    }
    showChatResponse(response.data, 'REGENERATE');
    if (!isAutomaticRetry && replySuggestionState.regenerateCount >= 3) {
      replySuggestionState.showHelpHint = true;
      replySuggestionState.helpHintMessage = '连续换了 3 组还不满意，可以求助组长一起判断。';
    }
  } catch {
    handleRegenerateFailure(isAutomaticRetry);
  } finally {
    replySuggestionState.regenerating = false;
  }
}

export function selectReply(suggestion: ReplySuggestion): void {
  const payload: ReplySelectedPayload = {
    text: suggestion.text,
    direction: suggestion.direction,
    reason: suggestion.reason,
    phone: maskPhone(replySuggestionState.currentPhone),
    isFallback: suggestion.direction === FALLBACK_DIRECTION || replySuggestionState.isFallbackMode
  };
  eventBus.emit('reply:selected', payload);
  replySuggestionState.toast = '已发送复制事件，等待复制回填模块处理';
}

export function requestLeaderHelp(): void {
  stopFallbackRetry();
  eventBus.emit('help:request', {
    phone: replySuggestionState.currentPhone,
    clientMessage: '',
    aiSuggestions: replySuggestionState.suggestions.map((item) => ({ text: item.text, direction: item.direction }))
  });
  replySuggestionState.toast = '已发起求助入口';
}

export function handleHelpTimeout(payload: { phone?: string; reason?: string }): void {
  if (payload.phone && replySuggestionState.currentPhone && payload.phone !== replySuggestionState.currentPhone) {
    return;
  }
  replySuggestionState.showHelpHint = true;
  replySuggestionState.helpHintMessage = '组长暂时不在线，请选择最接近的一条回复后在微信里手动调整。';
}

export function handleProfileSuggestions(payload: ProfileSuggestionsPayload): void {
  if (!payload.phone || payload.phone !== replySuggestionState.currentPhone) {
    return;
  }
  replySuggestionState.profileSuggestions = (payload.suggestions ?? []).map((item) => ({
    ...item,
    resolved: false,
    resolving: false
  }));
  replySuggestionState.profileSuggestionsExpanded = true;
  eventBus.emit('suggestion:show', {
    phone: payload.phone,
    suggestions: replySuggestionState.profileSuggestions
  });
}

export function handleAbnormalAlert(payload: AbnormalAlertPayload): void {
  if (payload.phone && replySuggestionState.currentPhone && payload.phone !== replySuggestionState.currentPhone) {
    return;
  }
  replySuggestionState.abnormalAlert = payload;
}

export async function resolveProfileSuggestion(action: 'CONFIRM' | 'REJECT', suggestion?: ProfileSuggestion): Promise<void> {
  const targets = suggestion ? [suggestion] : replySuggestionState.profileSuggestions.filter((item) => !item.resolved);
  if (!replySuggestionState.currentPhone || targets.length === 0) {
    return;
  }
  targets.forEach((item) => {
    item.resolving = true;
  });
  const suggestionIds = targets.map((item) => item.suggestionId).filter((id): id is number => typeof id === 'number');
  try {
    await postJson(`/api/v1/customers/${encodeURIComponent(replySuggestionState.currentPhone)}/suggestions/batch-resolve`, {
      action: action === 'CONFIRM' ? 'CONFIRM' : 'REJECT',
      suggestionIds,
      operator: 'desktop'
    });
    targets.forEach((item) => {
      item.resolved = true;
      item.resolving = false;
      item.resolveAction = action;
    });
    if (pendingProfileSuggestionCount.value === 0) {
      replySuggestionState.profileSuggestionsExpanded = false;
    }
  } catch {
    targets.forEach((item) => {
      item.resolving = false;
    });
    replySuggestionState.toast = 'AI 更新建议处理失败，请稍后重试';
  }
}

export function cleanupReplySuggestionStore(): void {
  clearSkeletonTimer();
  stopFallbackRetry();
}

function showChatResponse(response: ChatResponse, scene: ReplyScene): void {
  clearSkeletonTimer();
  stopFallbackRetry();
  replySuggestionState.loadingMode = 'NONE';
  replySuggestionState.currentScene = scene;
  replySuggestionState.currentPhone = response.phone ?? replySuggestionState.currentPhone;
  replySuggestionState.currentNickname = response.nickname ?? replySuggestionState.currentNickname;
  replySuggestionState.currentMatchType = response.match?.matchType ?? (response.needsCustomerIdentifier ? 'NONE' : replySuggestionState.currentMatchType);
  const suggestions = response.skill?.suggestions ?? [];
  if (suggestions.length === 0) {
    enterFallbackMode('系统返回异常，请重试');
    return;
  }
  if (suggestions[0]?.direction === FALLBACK_DIRECTION) {
    enterFallbackMode(suggestions[0].text);
    return;
  }
  replySuggestionState.isFallbackMode = false;
  replySuggestionState.fallbackText = '';
  replySuggestionState.fallbackBannerText = '';
  replySuggestionState.showRegenerateButton = true;
  replySuggestionState.suggestions = suggestions;
}

function enterFallbackMode(text: string): void {
  replySuggestionState.isFallbackMode = true;
  replySuggestionState.fallbackText = text;
  replySuggestionState.fallbackBannerText = 'AI 助手暂时不可用，正在自动重试恢复...';
  replySuggestionState.suggestions = [{ text, direction: FALLBACK_DIRECTION, reason: '系统降级回复' }];
  replySuggestionState.showRegenerateButton = false;
  startFallbackRetry();
}

function startFallbackRetry(): void {
  stopFallbackRetry();
  const config = loadDesktopConfig();
  replySuggestionState.fallbackRetryCount = 0;
  const run = () => {
    if (replySuggestionState.fallbackRetryCount >= config.fallbackMaxRetries) {
      replySuggestionState.fallbackBannerText = '暂时无法生成回复，请手动回复';
      replySuggestionState.showRegenerateButton = true;
      stopFallbackRetry();
      return;
    }
    replySuggestionState.fallbackRetryCount += 1;
    void regenerateReplies(true).then(() => {
      if (replySuggestionState.isFallbackMode) {
        fallbackRetryTimer = window.setTimeout(run, config.fallbackRetryIntervalMs);
      }
    });
  };
  fallbackRetryTimer = window.setTimeout(run, config.fallbackRetryIntervalMs);
}

function stopFallbackRetry(): void {
  if (fallbackRetryTimer) {
    window.clearTimeout(fallbackRetryTimer);
    fallbackRetryTimer = null;
  }
}

function handleRegenerateFailure(isAutomaticRetry: boolean, errorCode?: string | null): void {
  if (isAutomaticRetry) {
    return;
  }
  replySuggestionState.loadingMode = 'NONE';
  replySuggestionState.toast = errorCode === '80-10002' ? '登录已失效，请重新登录' : '换一组失败，请重试';
}

function scheduleNextSkeletonStage(): void {
  clearSkeletonTimer();
  if (replySuggestionState.currentStageIndex >= STAGE_TEXTS.length - 1) {
    return;
  }
  skeletonTimer = window.setTimeout(() => {
    replySuggestionState.currentStageIndex += 1;
    replySuggestionState.currentStageText = STAGE_TEXTS[replySuggestionState.currentStageIndex];
    scheduleNextSkeletonStage();
  }, STAGE_DURATIONS[replySuggestionState.currentStageIndex]);
}

function clearSkeletonTimer(): void {
  if (skeletonTimer) {
    window.clearTimeout(skeletonTimer);
    skeletonTimer = null;
  }
}

function resetForNewEntry(): void {
  clearSkeletonTimer();
  stopFallbackRetry();
  replySuggestionState.loadingMode = 'NONE';
  replySuggestionState.currentStageIndex = 0;
  replySuggestionState.currentStageText = '';
  replySuggestionState.suggestions = [];
  replySuggestionState.currentPhone = '';
  replySuggestionState.currentNickname = '';
  replySuggestionState.currentLeadType = '';
  replySuggestionState.currentScene = 'CHAT_RECOGNIZE';
  replySuggestionState.currentMatchType = 'NONE';
  replySuggestionState.regenerating = false;
  replySuggestionState.regenerateCount = 0;
  replySuggestionState.isFallbackMode = false;
  replySuggestionState.fallbackText = '';
  replySuggestionState.fallbackBannerText = '';
  replySuggestionState.fallbackRetryCount = 0;
  replySuggestionState.showRegenerateButton = true;
  replySuggestionState.showHelpHint = false;
  replySuggestionState.helpHintMessage = '';
  replySuggestionState.profileSuggestions = [];
  replySuggestionState.profileSuggestionsExpanded = true;
  replySuggestionState.abnormalAlert = null;
  replySuggestionState.toast = '';
}

function normalizeResponse(payload: RecognizeResultPayload): ChatResponse {
  if ('response' in payload && payload.response) {
    return payload.response;
  }
  return payload as ChatResponse;
}

function maskPhone(phone: string): string {
  if (!phone) {
    return '';
  }
  const tail = phone.slice(-4);
  return tail ? `****${tail}` : '';
}
