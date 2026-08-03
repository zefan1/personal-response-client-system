import type { ReplySession } from './types';

export type ActiveReplySessionProjection = Pick<
  ReplySession,
  | 'loadingMode'
  | 'currentStageIndex'
  | 'currentStageText'
  | 'progressStage'
  | 'failureReason'
  | 'suggestions'
  | 'replySource'
  | 'currentPhone'
  | 'currentNickname'
  | 'currentLeadType'
  | 'currentScene'
  | 'currentMatchType'
  | 'regenerating'
  | 'regenerateCount'
  | 'isFallbackMode'
  | 'fallbackText'
  | 'fallbackBannerText'
  | 'fallbackRetryCount'
  | 'showRegenerateButton'
  | 'showHelpHint'
  | 'helpHintMessage'
  | 'profileSuggestions'
  | 'profileSuggestionsExpanded'
  | 'abnormalAlert'
  | 'activeHelpId'
  | 'toast'
>;

export function projectActiveReplySession(session: ReplySession | null): ActiveReplySessionProjection {
  return {
    loadingMode: session?.loadingMode ?? 'NONE',
    currentStageIndex: session?.currentStageIndex ?? 0,
    currentStageText: session?.currentStageText ?? '',
    progressStage: session?.progressStage ?? 'DONE',
    failureReason: session?.failureReason ?? '',
    suggestions: session?.suggestions ?? [],
    replySource: session?.replySource ?? null,
    currentPhone: session?.currentPhone ?? '',
    currentNickname: session?.currentNickname ?? '',
    currentLeadType: session?.currentLeadType ?? '',
    currentScene: session?.currentScene ?? 'CHAT_RECOGNIZE',
    currentMatchType: session?.currentMatchType ?? 'NONE',
    regenerating: session?.regenerating ?? false,
    regenerateCount: session?.regenerateCount ?? 0,
    isFallbackMode: session?.isFallbackMode ?? false,
    fallbackText: session?.fallbackText ?? '',
    fallbackBannerText: session?.fallbackBannerText ?? '',
    fallbackRetryCount: session?.fallbackRetryCount ?? 0,
    showRegenerateButton: session?.showRegenerateButton ?? true,
    showHelpHint: session?.showHelpHint ?? false,
    helpHintMessage: session?.helpHintMessage ?? '',
    profileSuggestions: session?.profileSuggestions ?? [],
    profileSuggestionsExpanded: session?.profileSuggestionsExpanded ?? true,
    abnormalAlert: session?.abnormalAlert ?? null,
    activeHelpId: session?.activeHelpId ?? '',
    toast: session?.toast ?? ''
  };
}
