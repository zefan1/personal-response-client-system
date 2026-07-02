export type RecognizeSource = 'BUTTON_CLICK' | 'CLIPBOARD_SCREENSHOT' | 'CLIPBOARD_TEXT';
export type ImageServiceStatus = 'UP' | 'DOWN' | 'UNKNOWN';

export type ClipboardImagePayload = {
  imageBase64: string;
  md5: string;
  width: number;
  height: number;
};

export type ChatRecognizeResponse = {
  phone?: string;
  nickname?: string;
  needsCustomerIdentifier?: boolean;
  match?: {
    matchType: 'EXACT' | 'FUZZY' | 'MULTIPLE' | 'NONE';
    customers?: unknown[];
    matchCount?: number;
  } | null;
  skill?: {
    suggestions?: Array<{ text: string; direction: string; reason: string }>;
    customerAnalysis?: unknown;
    followupSuggest?: unknown;
  } | null;
  warning?: string | null;
};

export type RecognizeEventPayload = {
  source: RecognizeSource;
  response: ChatRecognizeResponse;
};
