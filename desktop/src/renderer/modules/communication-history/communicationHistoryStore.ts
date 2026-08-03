import { reactive } from 'vue';
import { getJson, putJson } from '../../shared/apiClient';
import type {
  ArchivedCommunicationMessage,
  CommunicationMessagePage,
  CommunicationSummaryVersion,
  CommunicationView
} from './types';

export const communicationHistoryState = reactive({
  phone: '',
  customerId: null as number | null,
  activeView: 'messages' as CommunicationView,
  platform: '',
  fromDate: '',
  toDate: '',
  keyword: '',
  messages: [] as ArchivedCommunicationMessage[],
  summaries: [] as CommunicationSummaryVersion[],
  nextBeforeId: null as number | null,
  loading: false,
  loadingOlder: false,
  error: ''
});

export async function openCommunicationHistory(
  phone: string,
  view: CommunicationView = 'messages'
): Promise<void> {
  communicationHistoryState.phone = phone.trim();
  communicationHistoryState.customerId = null;
  communicationHistoryState.activeView = view;
  communicationHistoryState.error = '';
  if (view === 'summaries') {
    await loadSummaryVersions();
    return;
  }
  await loadMessages(true);
}

export async function openCommunicationHistoryByCustomerId(
  customerId: number,
  phone = '',
  view: CommunicationView = 'messages'
): Promise<void> {
  if (!Number.isFinite(customerId) || customerId <= 0) {
    return;
  }
  communicationHistoryState.customerId = customerId;
  communicationHistoryState.phone = phone.trim();
  communicationHistoryState.activeView = view;
  communicationHistoryState.error = '';
  if (view === 'summaries') {
    await loadSummaryVersions();
    return;
  }
  await loadMessages(true);
}

export async function switchCommunicationView(view: CommunicationView): Promise<void> {
  communicationHistoryState.activeView = view;
  if (view === 'summaries') {
    await loadSummaryVersions();
  } else {
    await loadMessages(true);
  }
}

export async function applyCommunicationFilters(): Promise<void> {
  await loadMessages(true);
}

export async function loadOlderMessages(): Promise<void> {
  if (communicationHistoryState.nextBeforeId === null || communicationHistoryState.loadingOlder) {
    return;
  }
  communicationHistoryState.loadingOlder = true;
  try {
    await loadMessages(false);
  } finally {
    communicationHistoryState.loadingOlder = false;
  }
}

export async function correctCommunicationMessage(messageId: number, correctedText: string): Promise<boolean> {
  const text = correctedText.trim();
  if (!text) {
    return false;
  }
  const response = await putJson<{ corrected: boolean }>(
    `/api/v1/communications/messages/${messageId}`,
    { correctedText: text }
  );
  if (!response.success || !response.data?.corrected) {
    communicationHistoryState.error = response.message || '修正失败，请重试';
    return false;
  }
  const message = communicationHistoryState.messages.find((item) => item.id === messageId);
  if (message) {
    message.currentText = text;
  }
  return true;
}

async function loadMessages(reset: boolean): Promise<void> {
  if (!communicationHistoryState.phone && !communicationHistoryState.customerId) {
    return;
  }
  if (reset) {
    communicationHistoryState.loading = true;
    communicationHistoryState.error = '';
  }
  try {
    const params = new URLSearchParams();
    addParam(params, 'platform', communicationHistoryState.platform);
    addParam(params, 'from', communicationHistoryState.fromDate);
    addParam(params, 'to', communicationHistoryState.toDate);
    addParam(params, 'keyword', communicationHistoryState.keyword);
    if (!reset && communicationHistoryState.nextBeforeId !== null) {
      params.set('beforeId', String(communicationHistoryState.nextBeforeId));
    }
    params.set('limit', '50');
    const path = communicationHistoryState.customerId
      ? `/api/v1/communications/customers/by-id/${communicationHistoryState.customerId}/messages`
      : `/api/v1/communications/customers/${encodeURIComponent(communicationHistoryState.phone)}/messages`;
    const response = await getJson<CommunicationMessagePage>(`${path}?${params.toString()}`);
    if (!response.success || !response.data) {
      communicationHistoryState.error = response.message || '聊天记录加载失败';
      return;
    }
    const rows = reset
      ? response.data.messages
      : [...response.data.messages, ...communicationHistoryState.messages];
    communicationHistoryState.messages = uniqueChronological(rows);
    communicationHistoryState.nextBeforeId = response.data.nextBeforeId ?? null;
  } catch (error) {
    communicationHistoryState.error = error instanceof Error ? error.message : '聊天记录加载失败';
  } finally {
    if (reset) {
      communicationHistoryState.loading = false;
    }
  }
}

async function loadSummaryVersions(): Promise<void> {
  if (!communicationHistoryState.phone && !communicationHistoryState.customerId) {
    return;
  }
  communicationHistoryState.loading = true;
  communicationHistoryState.error = '';
  try {
    const path = communicationHistoryState.customerId
      ? `/api/v1/communications/customers/by-id/${communicationHistoryState.customerId}/summaries`
      : `/api/v1/communications/customers/${encodeURIComponent(communicationHistoryState.phone)}/summaries`;
    const response = await getJson<CommunicationSummaryVersion[]>(path);
    if (!response.success || !response.data) {
      communicationHistoryState.error = response.message || '历史汇总加载失败';
      return;
    }
    communicationHistoryState.summaries = [...response.data]
      .sort((left, right) => right.versionNo - left.versionNo);
  } catch (error) {
    communicationHistoryState.error = error instanceof Error ? error.message : '历史汇总加载失败';
  } finally {
    communicationHistoryState.loading = false;
  }
}

function addParam(params: URLSearchParams, key: string, value: string): void {
  const trimmed = value.trim();
  if (trimmed) {
    params.set(key, trimmed);
  }
}

function uniqueChronological(rows: ArchivedCommunicationMessage[]): ArchivedCommunicationMessage[] {
  return [...new Map(rows.map((item) => [item.id, item])).values()]
    .sort((left, right) => left.messageTime.localeCompare(right.messageTime) || left.id - right.id);
}
