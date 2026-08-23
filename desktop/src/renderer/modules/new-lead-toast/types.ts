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
  isReconnectBatch?: boolean;
};

export type NewLeadToastItem = NewLeadAlertPayload & {
  id: string;
  timerId?: number;
};
