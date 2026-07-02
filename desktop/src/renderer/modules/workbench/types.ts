import type { FollowupItem, FollowupReminderPayload, NewLeadAlertPayload, ReminderType } from '../followup-list/types';

export type WorkbenchMetricKey = 'pendingFollowup' | 'appointment' | 'newLead';
export type NoticeLevel = 'INFO' | 'WARN' | 'ERROR';

export type WorkbenchNoticePayload = {
  noticeId?: string;
  title?: string;
  content?: string;
  level?: NoticeLevel | string;
  createdAt?: string;
  expireAt?: string;
};

export type WorkbenchNotice = Required<Pick<WorkbenchNoticePayload, 'title' | 'content' | 'createdAt' | 'expireAt'>> & {
  noticeId: string;
  level: NoticeLevel;
};

export type WorkbenchMetrics = Record<WorkbenchMetricKey, {
  total: number;
  tuanGou: number;
  xianSuo: number;
}>;

export type WorkbenchFollowupItem = FollowupItem & {
  reminderType: Extract<ReminderType, 'OVERDUE' | 'DUE_TODAY'>;
};

export type { FollowupItem, FollowupReminderPayload, NewLeadAlertPayload };
