export type MatchType = 'EXACT' | 'FUZZY' | 'MULTIPLE' | 'NONE';
export type ReplyScene = 'CHAT_RECOGNIZE' | 'ACTIVE_REPLY' | 'REGENERATE' | 'OPENING' | 'PROFILE_EXTRACT';

export type ReplySuggestion = {
  text: string;
  direction: string;
  reason: string;
};

export type ChatResponse = {
  customerId?: number | null;
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
  awaitingCustomerSelection?: boolean;
  recognition?: RecognitionSnapshot | null;
};

export type RecognitionSnapshot = {
  platform?: string | null;
  nickname?: string | null;
  messages?: Array<{ role?: string | null; text?: string | null }> | null;
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
  message?: string;
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
  customerId?: number | null;
  scene?: ReplyScene;
  leadType?: string;
  sourceFrom?: string;
};

export type ReplyCandidate = {
  customerId?: number | null;
  phone?: string | null;
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
  customerId?: number | null;
  nickname?: string;
  displayPhone?: string;
  taskId?: string;
  replySessionId?: string;
  replySource?: 'LLM' | 'SKILL' | 'FALLBACK';
  isFallback: boolean;
};

export type RecognitionJobStatus =
  | 'QUEUED'
  | 'RECOGNIZING'
  | 'READY'
  | 'FAILED'
  | 'CANCELLED'
  | 'EXPIRED';

export type RecognitionJobUpdate = {
  sessionId: string;
  jobId: string;
  status: RecognitionJobStatus;
  errorCode?: string | null;
};

export type ReplySessionStatus = 'LOADING' | 'READY' | 'FAILED' | 'FALLBACK' | 'COPIED' | 'CANCELLED';
export type RecognizeProgressStage = 'CAPTURING' | 'CAPTURED' | 'UPLOADING' | 'WAITING_MODEL' | 'GENERATING' | 'DONE' | 'FAILED';

export type ReplySession = {
  sessionId: string;
  status: ReplySessionStatus;
  recognitionJobId: string;
  recognitionJobStatus: RecognitionJobStatus | null;
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
  awaitingCustomerSelection: boolean;
  recognition: RecognitionSnapshot | null;
  currentPhone: string;
  currentCustomerId: number | null;
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

export type ArchivedReplySession = ReplySession & {
  archivedAt: number;
};
