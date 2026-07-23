export type QuickSearchContentType = 'TEMPLATE' | 'KNOWLEDGE' | 'LOCATION' | 'IMAGE' | 'MINI_PROGRAM';
export type QuickSearchLeadType = 'TUAN_GOU' | 'XIAN_SUO' | 'GENERAL' | string;

export type QuickSearchItem = {
  id: number;
  contentType: QuickSearchContentType;
  scene?: string | null;
  leadType: QuickSearchLeadType;
  title: string;
  shortcutCode: string;
  content: string;
  imageUrl?: string | null;
  sortOrder: number;
  isEnabled: boolean;
  updatedAt?: string | null;
};

export type QuickSearchFilter = 'ALL' | 'TUAN_GOU' | 'XIAN_SUO' | 'GENERAL';

export type QuickSearchCustomerContext = {
  phone: string;
  nickname?: string | null;
  leadType?: string | null;
  sourceTable?: string | null;
  reminderType?: 'OVERDUE' | 'DUE_TODAY' | 'APPOINTMENT' | 'NEW_LEAD' | null;
  returnToFollowups: boolean;
  customer: Record<string, unknown>;
};

export type QuickSearchPendingSend = {
  itemId: number;
  title: string;
  scene: string;
  sentText: string;
};
