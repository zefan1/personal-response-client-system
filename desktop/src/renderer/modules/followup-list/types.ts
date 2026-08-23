export type ReminderType = 'OVERDUE' | 'DUE_TODAY' | 'APPOINTMENT' | 'NEW_LEAD' | 'TAG_SUGGESTION';
export type FollowupTab = 'OVERDUE' | 'DUE_TODAY' | 'APPOINTMENT' | 'NEW_LEAD';
export type AlertLevel = 'HIGH' | 'NORMAL' | string;

export type TagSuggestionPayload = {
  suggestionId?: number;
  tagName?: string;
  confidence?: string;
};

export type FollowupItem = {
  phone: string;
  phoneFull?: string;
  nickname?: string | null;
  leadType?: string | null;
  lastFollowupAt?: string | null;
  nextFollowupAt?: string | null;
  nextFollowupDir?: string | null;
  appointmentDate?: string | null;
  appointmentTime?: string | null;
  appointmentStore?: string | null;
  sourceTable?: string | null;
  reminderType: ReminderType;
  overdueHours?: number | null;
  alertLevel?: AlertLevel | null;
  tagSuggestion?: TagSuggestionPayload | null;
  arrivedAt?: string | null;
  contactValue?: string | null;
  contactType?: 'PHONE' | 'WECHAT' | string | null;
  leadProcessed?: boolean;
  leadInvalid?: boolean;
  leadRetainedUntil?: string | null;
  customerVersion?: number | null;
  assignedKeeper?: string | null;
  priority?: string | null;
  flashUntil?: number;
  selected?: boolean;
};

export type FollowupTodayResponse = {
  keeperId?: string;
  totalCount: number;
  items: FollowupItem[];
  pendingNewLeadCount?: number;
  retainedNewLeadCount?: number;
};

export type FollowupReminderPayload = {
  phone: string;
  phoneFull?: string;
  reminders: Array<{
    ruleId?: number;
    ruleName?: string;
    reminderType: ReminderType;
    alertLevel?: AlertLevel | null;
    overdueHours?: number | null;
    tagSuggestion?: TagSuggestionPayload | null;
  }>;
};

export type NewLeadAlertPayload = {
  phone: string;
  phoneFull?: string;
  nickname?: string;
  leadType?: string;
  priority?: string;
  sourceTable?: string;
  assignedKeeper?: string;
  arrivedAt?: string;
  contactValue?: string;
  contactType?: 'PHONE' | 'WECHAT' | string;
  customerVersion?: number | null;
  leadProcessed?: boolean;
  leadInvalid?: boolean;
  leadRetainedUntil?: string;
};
