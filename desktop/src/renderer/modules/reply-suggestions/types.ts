export type MatchType = 'EXACT' | 'FUZZY' | 'MULTIPLE' | 'NONE';
export type ReplyScene = 'CHAT_RECOGNIZE' | 'ACTIVE_REPLY' | 'REGENERATE' | 'OPENING' | 'PROFILE_EXTRACT';

export type ReplySuggestion = {
  text: string;
  direction: string;
  reason: string;
};

export type ChatResponse = {
  phone?: string | null;
  nickname?: string | null;
  needsCustomerIdentifier?: boolean;
  match?: {
    matchType?: MatchType;
    customers?: unknown[];
    matchCount?: number;
  } | null;
  skill?: {
    suggestions?: ReplySuggestion[];
    customerAnalysis?: unknown;
    followupSuggest?: unknown;
    profileUpdates?: unknown;
  } | null;
  warning?: string | null;
  replySource?: ReplySourceInfo | null;
};

export type ReplySourceInfo = {
  source?: 'LLM' | 'SKILL' | 'FALLBACK' | string;
  label?: string;
  detail?: string;
};

export type RecognizeResultPayload = ChatResponse | {
  sessionId?: string;
  source?: string;
  response?: ChatResponse;
};

export type RecognizeStartPayload = {
  sessionId?: string;
  source?: string;
  stage?: RecognizeProgressStage;
};

export type RecognizeProgressPayload = RecognizeStartPayload & {
  message?: string;
};

export type RecognizeFailurePayload = {
  sessionId?: string;
  errorCode?: string | null;
  message?: string;
};

export type CustomerSelectedPayload = {
  sessionId?: string;
  phone?: string;
  scene?: ReplyScene;
  leadType?: string;
  sourceFrom?: string;
};

export type ReplyCandidate = {
  phone: string;
  nickname?: string | null;
  leadType?: string | null;
  assignedKeeper?: string | null;
  intendedStore?: string | null;
};

export type ProfileSuggestion = {
  suggestionId?: number;
  fieldName: string;
  currentValue?: unknown;
  suggestedValue?: unknown;
  reason?: string;
  resolved?: boolean;
  resolving?: boolean;
  resolveAction?: 'CONFIRM' | 'REJECT';
};

export type ProfileSuggestionsPayload = {
  phone?: string;
  suggestionCount?: number;
  suggestions?: ProfileSuggestion[];
};

export type AbnormalAlertPayload = {
  alertId: string;
  phone: string;
  alertType: 'CUSTOMER_COMPLAINT' | 'CHURN_RISK';
  message: string;
  level: 'ERROR' | 'WARN' | 'INFO';
  occurredAt: string;
  acknowledged: boolean;
  acknowledgedAt?: string | null;
};

export type ReplySelectedPayload = {
  text: string;
  direction: string;
  reason: string;
  phone: string;
  displayPhone?: string;
  isFallback: boolean;
};

export type ReplySessionStatus = 'LOADING' | 'READY' | 'FAILED' | 'FALLBACK' | 'COPIED' | 'MULTIPLE';
export type RecognizeProgressStage = 'CAPTURED' | 'UPLOADING' | 'WAITING_MODEL' | 'GENERATING' | 'DONE' | 'FAILED';

export type ReplySession = {
  sessionId: string;
  status: ReplySessionStatus;
  source?: string;
  createdAt: number;
  updatedAt: number;
  copiedAt?: number;
  loadingMode: 'NONE' | 'FULL' | 'SIMPLE';
  currentStageIndex: number;
  currentStageText: string;
  progressStage: RecognizeProgressStage;
  failureReason: string;
  suggestions: ReplySuggestion[];
  replySource: ReplySourceInfo | null;
  candidates: ReplyCandidate[];
  currentPhone: string;
  currentNickname: string;
  currentLeadType: string;
  currentScene: ReplyScene;
  currentMatchType: string;
  regenerating: boolean;
  regenerateCount: number;
  isFallbackMode: boolean;
  fallbackText: string;
  fallbackBannerText: string;
  fallbackRetryCount: number;
  showRegenerateButton: boolean;
  showHelpHint: boolean;
  helpHintMessage: string;
  profileSuggestions: ProfileSuggestion[];
  profileSuggestionsExpanded: boolean;
  abnormalAlert: AbnormalAlertPayload | null;
  activeHelpId: string | number | '';
  toast: string;
};
