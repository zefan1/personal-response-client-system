export type TemplateMetadata = {
  channelCode?: string | null;
  scene?: string | null;
  leadType?: string | null;
  labels?: string[];
};

export type PersonalTemplate = {
  id: number;
  title: string;
  body: string;
  metadata: TemplateMetadata;
  sourceReplySessionId?: string | null;
  usageCount: number;
  createdAt?: string;
  updatedAt?: string;
};

export type TeamTemplate = {
  quickSearchItemId: number;
  promotionCandidateId: number;
  title: string;
  body: string;
  shortcutCode: string;
  metadata: TemplateMetadata;
  publishedAt?: string;
};

export type PersonalTemplateDraft = {
  title: string;
  body: string;
  originalAiReply: string;
  metadata: TemplateMetadata;
  sourceReplySessionId?: string | null;
};

export type TemplateLibraryTab = 'PERSONAL' | 'TEAM';
