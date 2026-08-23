export type LeadContactItem = {
  phone: string;
  phoneFull?: string | null;
  nickname?: string | null;
  contactValue?: string | null;
  contactType?: 'PHONE' | 'WECHAT' | string | null;
  customerVersion?: number | null;
  leadProcessed?: boolean;
  leadInvalid?: boolean;
  leadRetainedUntil?: string | null;
  leadType?: string | null;
  sourceTable?: string | null;
};

export type FriendRequestTemplate = {
  id: string;
  name: string;
  text: string;
  enabled: boolean;
};
