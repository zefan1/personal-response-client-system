export type MatchType = 'EXACT' | 'FUZZY' | 'MULTIPLE' | 'NONE';
export type ReplyScene = 'CHAT_RECOGNIZE' | 'ACTIVE_REPLY' | 'REGENERATE' | 'OPENING';

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
};

export type RecognizeResultPayload = ChatResponse | {
  source?: string;
  response?: ChatResponse;
};

export type CustomerSelectedPayload = {
  phone?: string;
  scene?: ReplyScene;
  leadType?: string;
  sourceFrom?: string;
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
  isFallback: boolean;
};
