import type { CustomerProfileView } from '../customer-profile/types';
import type { QuickSearchItem } from '../quick-search/types';

export type BatchStartPayload = {
  phones: string[];
  source: 'FOLLOWUP_LIST';
};

export type BatchPhase = 'IDLE' | 'SELECT_TEMPLATE' | 'SENDING' | 'PAUSED' | 'COMPLETED';

export type BatchCustomerState = {
  phone: string;
  profile: CustomerProfileView | null;
  copied: boolean;
  skipped: boolean;
};

export type BatchCustomersResponse = {
  customers: CustomerProfileView[];
};

export type BatchTemplate = QuickSearchItem;
