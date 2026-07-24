export type ReplySelectedPayload = {
  text: string;
  direction: string;
  reason: string;
  phone: string;
  displayPhone?: string;
  taskId?: string;
  replySessionId?: string;
  replySource?: 'LLM' | 'SKILL' | 'FALLBACK';
  isFallback: boolean;
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

export type SuggestionShowPayload = {
  phone: string;
  suggestions: ProfileSuggestion[];
};
