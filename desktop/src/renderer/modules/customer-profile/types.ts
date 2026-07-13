export type LeadType = 'TUAN_GOU' | 'XIAN_SUO' | 'PENDING' | string;
export type MatchType = 'EXACT' | 'FUZZY' | 'MULTIPLE' | 'NONE';
export type SourceFrom = 'SEARCH' | 'CANDIDATE_LIST' | 'FOLLOWUP_LIST' | 'NEW_LEAD' | 'PROFILE_CARD' | 'CANDIDATE_DISMISSED' | 'DASHBOARD';

export type CustomerSummary = {
  phone: string;
  phoneFull?: string | null;
  nickname?: string | null;
  leadType?: LeadType | null;
  assignedKeeper?: string | null;
  lastFollowupAt?: string | null;
  intendedStore?: string | null;
  confidence?: string | null;
};

export type Customer = {
  phone: string;
  phoneFull?: string | null;
  nickname?: string | null;
  sourceChannel?: string | null;
  leadType?: LeadType | null;
  personalityType?: string | null;
  assignedKeeper?: string | null;
  intendedStore?: string | null;
  intendedProject?: string | null;
  purchasedProject?: string | null;
  postpartumMonths?: number | null;
  parity?: string | null;
  deliveryMethod?: string | null;
  breastfeeding?: string | null;
  lochiaPeriod?: string | null;
  pregnancyWeight?: number | null;
  currentWeight?: number | null;
  bodyConcerns?: string | null;
  diastasisRecti?: string | null;
  urineLeakage?: string | null;
  pubicLumbago?: string | null;
  prevRepairExp?: string | null;
  postpartumCheck?: string | null;
  exerciseHabits?: string | null;
  intentLevel?: string | null;
  worries?: string | null;
  customerStage?: string | null;
  lastFollowupAt?: string | null;
  followupNotes?: string | null;
  nextFollowupAt?: string | null;
  nextFollowupDir?: string | null;
  appointmentDate?: string | null;
  appointmentStore?: string | null;
  appointmentItem?: string | null;
  arrived?: string | null;
  sourceTable?: string | null;
  sourceRowId?: string | null;
  syncedAt?: string | null;
  version?: number | null;
};

export type ProfileSuggestion = {
  id?: number;
  suggestionId?: number;
  phone?: string;
  fieldName: string;
  currentValue?: unknown;
  suggestedValue?: unknown;
  confidence?: string;
  status?: string;
  reason?: string;
  suggestionType?: 'FIELD_UPDATE' | 'STAGE_CHANGE';
  fromStage?: string;
  toStage?: string;
  stageOptionMatch?: boolean;
  validOptions?: string[];
  createdAt?: string;
  resolved?: boolean;
  resolving?: boolean;
};

export type CustomerProfileView = {
  customer: Customer;
  phoneFull?: string | null;
  pendingSuggestions?: ProfileSuggestion[];
};

export type CustomerSearchResult = {
  customers: CustomerSummary[];
  total: number;
};

export type RecognizeMultiplePayload = {
  sessionId?: string;
  candidates?: CustomerSummary[];
  matchInfo?: {
    customers?: CustomerSummary[];
    matchCount?: number;
    matchType?: MatchType;
  };
};

export type StageSuggestPayload = {
  phone: string;
  suggestionId?: number;
  fromStage?: string;
  toStage?: string;
  reason?: string;
  stageOptionMatch?: boolean;
  validOptions?: string[];
  createdAt?: string;
  suggestionType?: 'STAGE_CHANGE';
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
