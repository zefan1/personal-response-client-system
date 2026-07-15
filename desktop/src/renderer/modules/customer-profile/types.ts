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
  currentTags?: CustomerTag[];
  tagLocks?: CustomerTagLock[];
  editableTagCategories?: CustomerTagCategory[];
};

export type CustomerTag = {
  assignmentId: number;
  customerId: number;
  customerVersion: number;
  categoryId: number;
  categoryKey: string;
  categoryName: string;
  categorySelectionMode: 'SINGLE' | 'MULTI';
  tagValueId: number;
  tagValue: string;
  tagDisplayName: string;
  sourceType: string;
  confidence?: number | null;
  evidenceText?: string | null;
  manualLocked: boolean;
  lockedBy?: string | null;
  lockedAt?: string | null;
};

export type CustomerTagLock = {
  id: number;
  customerId: number;
  categoryId: number;
  locked: boolean;
  lockedBy: string;
  lockReason?: string | null;
  lockedAt?: string | null;
  unlockedBy?: string | null;
  unlockedAt?: string | null;
  version: number;
};

export type CustomerTagValue = {
  id: number;
  categoryId: number;
  tagValue: string;
  displayName: string;
  meaning?: string | null;
  manualSelectable: boolean;
  isEnabled: boolean;
};

export type CustomerTagCategory = {
  id: number;
  categoryKey: string;
  categoryName: string;
  selectionMode: 'SINGLE' | 'MULTI';
  manualEditEnabled: boolean;
  values: CustomerTagValue[];
};

export type CustomerTagUpdateResult = {
  customerVersion: number;
  updated: boolean;
  decisions: Array<{
    categoryId: number;
    categoryKey: string;
    action: string;
    updated: boolean;
    reason: string;
  }>;
};

export type CustomerTagsUpdatedPayload = {
  phone: string;
  customerId: number;
  customerVersion: number;
  source: string;
  decisions: CustomerTagUpdateResult['decisions'];
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
