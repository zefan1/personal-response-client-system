export type CommunicationView = 'messages' | 'summaries';

export type ArchivedCommunicationMessage = {
  id: number;
  batchId: number;
  customerId?: number | null;
  username: string;
  platformCode: string;
  senderRole: string;
  contentType: string;
  originalText: string;
  currentText: string;
  messageTime: string;
  timeEstimated: boolean;
  sequenceNo: number;
  dedupeFingerprint: string;
};

export type CommunicationMessagePage = {
  messages: ArchivedCommunicationMessage[];
  nextBeforeId?: number | null;
};

export type CommunicationSummaryVersion = {
  id: number;
  customerId: number;
  versionNo: number;
  summaryText: string;
  lastMessageId: number;
  generatedAt: string;
};

export type PendingCommunicationBatch = {
  id: number;
  batchId: string;
  username: string;
  platformCode: string;
  platformIdentifier?: string | null;
  recognizedNickname?: string | null;
  recognizedPhone?: string | null;
  customerId?: number | null;
  associationStatus: string;
  rawText: string;
  recognizedAt: string;
};
