export type NewLeadAlertPayload = {
  phone: string;
  phoneFull?: string;
  nickname?: string;
  leadType?: string;
  priority?: string;
  sourceTable?: string;
  assignedKeeper?: string;
  arrivedAt?: string;
  isReconnectBatch?: boolean;
};

export type NewLeadToastItem = NewLeadAlertPayload & {
  id: string;
  timerId?: number;
};
