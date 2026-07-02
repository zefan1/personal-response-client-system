import type { ReplySuggestion } from '../reply-suggestions/types';

export type HelpReplySource = 'CONFIRMED' | 'MODIFIED' | 'ORIGINAL';

export type HelpRequestEvent = {
  phone: string;
  clientMessage: string;
  aiSuggestions: ReplySuggestion[];
};

export type HelpRequestResponse = {
  helpId: string | number;
  requestId?: string | number;
  leaderOnline: boolean;
  targetLeaderName?: string;
  forwarded?: boolean;
  noFallbackAvailable?: boolean;
};

export type HelpRequestPayload = {
  helpId: string | number;
  requesterName: string;
  phone?: string;
  clientMessage: string;
  aiSuggestions: ReplySuggestion[];
  keeperNote?: string;
  requestedAt?: string;
  offlineAt?: string;
  forwardedFrom?: {
    originalLeaderId?: string | number;
    originalLeaderName?: string;
  };
};

export type HelperReply = {
  text: string;
  direction: string;
  source: HelpReplySource;
};

export type HelpResponsePayload = {
  helpId: string | number;
  phone?: string;
  helperReplies: HelperReply[];
  helperName?: string;
  resolvedAt?: string;
};
