import { reactive } from 'vue';
import { postJson } from '../../shared/apiClient';
import { loadDesktopConfig } from '../../shared/config';
import { eventBus } from '../../shared/eventBus';
import type { HelpReplySource, HelpRequestEvent, HelpRequestPayload, HelpRequestResponse, HelpResponsePayload, HelperReply } from './types';

const HELP_TIMEOUT_MS = 5000;

export const helpModeState = reactive({
  requestDialogVisible: false,
  sendingRequest: false,
  resolving: false,
  activeRequest: null as HelpRequestEvent | null,
  keeperNote: '',
  activeHelpId: '' as string | number | '',
  helperQueue: [] as HelpRequestPayload[],
  currentHelperIndex: 0,
  draftReplies: [] as HelperReply[],
  receivedResponse: null as HelpResponsePayload | null,
  responseExpanded: false,
  statusNotice: null as { level: 'info' | 'success' | 'warning' | 'error'; message: string; detail?: string } | null,
  toast: ''
});

export function openHelpRequest(payload: HelpRequestEvent): void {
  if (helpModeState.activeHelpId) {
    showStatusNotice('warning', '你已有等待中的求助', '请等待组长回复后再发起新求助');
    return;
  }
  if (!payload.phone) {
    showStatusNotice('warning', '请先识别聊天或选择客户', '当前求助缺少客户上下文');
    return;
  }
  helpModeState.activeRequest = payload;
  helpModeState.keeperNote = '';
  helpModeState.requestDialogVisible = true;
  helpModeState.toast = '';
}

export function closeHelpRequest(): void {
  helpModeState.requestDialogVisible = false;
  helpModeState.sendingRequest = false;
}

export async function submitHelpRequest(): Promise<void> {
  const request = helpModeState.activeRequest;
  if (!request || helpModeState.sendingRequest) {
    return;
  }
  helpModeState.sendingRequest = true;
  try {
    const response = await postJson<HelpRequestResponse>('/api/v1/help/request', {
      phone: request.phone,
      clientMessage: request.clientMessage || '当前客户最近消息未记录',
      aiSuggestions: request.aiSuggestions,
      keeperNote: helpModeState.keeperNote.slice(0, 500)
    }, HELP_TIMEOUT_MS);
    if (!response.success || !response.data) {
      helpModeState.toast = response.errorCode === '80-10003' ? '当前账号无权限发起求助' : '求助发送失败，请稍后重试';
      return;
    }
    const data = response.data;
    closeHelpRequest();
    if (data.noFallbackAvailable) {
      helpModeState.activeHelpId = '';
      eventBus.emit('help:timeout', { phone: request.phone, reason: 'NO_LEADER_ONLINE' });
      showStatusNotice('warning', '暂时没有组长在线', '本次求助未发送成功，可稍后重试或电话联系组长');
      return;
    }
    helpModeState.activeHelpId = data.helpId ?? data.requestId ?? '';
    eventBus.emit('help:pending', { helpId: helpModeState.activeHelpId, phone: request.phone });
    const target = data.targetLeaderName ?? '组长';
    showStatusNotice(
      'success',
      data.forwarded ? `已转给${target}` : `已向${target}发送求助`,
      '收到回复后会在侧边栏顶部提醒，可继续处理其他客户'
    );
  } catch {
    helpModeState.toast = '网络异常，请稍后重试';
    showStatusNotice('error', '求助发送失败', '网络异常，请稍后重试');
  } finally {
    helpModeState.sendingRequest = false;
  }
}

export function handleHelpRequest(payload: HelpRequestPayload): void {
  upsertHelpRequest(payload);
}

export function handleHelpOfflineReplay(payload: HelpRequestPayload): void {
  upsertHelpRequest(payload);
}

export function handleHelpResponse(payload: HelpResponsePayload): void {
  helpModeState.receivedResponse = payload;
  helpModeState.responseExpanded = false;
  helpModeState.activeHelpId = '';
  showStatusNotice('success', '组长已回复你的求助', '点击顶部回复卡片查看并复制建议');
  eventBus.emit('help:resolved', { helpId: payload.helpId, phone: payload.phone });
}

export function addConfirmedReply(suggestion: { text: string; direction: string }): void {
  addDraftReply(suggestion.text, '组长确认', 'CONFIRMED');
}

export function addModifiedReply(suggestion: { text: string; direction: string }): void {
  addDraftReply(suggestion.text, '组长修改', 'MODIFIED');
}

export function addOriginalReply(): void {
  addDraftReply('', '组长建议', 'ORIGINAL');
}

export function updateDraftReply(index: number, text: string): void {
  if (helpModeState.draftReplies[index]) {
    helpModeState.draftReplies[index].text = text;
  }
}

export function removeDraftReply(index: number): void {
  helpModeState.draftReplies.splice(index, 1);
}

export async function submitHelpResolve(): Promise<void> {
  const request = currentHelperRequest();
  const replies = helpModeState.draftReplies.filter((reply) => reply.text.trim()).slice(0, loadDesktopConfig().helpMaxReplies);
  if (!request || replies.length === 0 || helpModeState.resolving) {
    helpModeState.toast = '回复内容不完整';
    return;
  }
  helpModeState.resolving = true;
  try {
    const response = await postJson('/api/v1/help/resolve', {
      helpId: request.helpId,
      helperReplies: replies
    }, HELP_TIMEOUT_MS);
    if (!response.success) {
      helpModeState.toast = '发送失败，请重试';
      showStatusNotice('error', '组长回复发送失败', '请检查网络后重试');
      return;
    }
    helpModeState.toast = `已回复${request.requesterName}的求助`;
    showStatusNotice('success', `已回复${request.requesterName}的求助`, '该求助已从待处理列表移除');
    helpModeState.helperQueue.splice(helpModeState.currentHelperIndex, 1);
    helpModeState.currentHelperIndex = Math.max(0, Math.min(helpModeState.currentHelperIndex, helpModeState.helperQueue.length - 1));
    helpModeState.draftReplies = [];
  } catch {
    helpModeState.toast = '发送失败，请重试';
    showStatusNotice('error', '组长回复发送失败', '请检查网络后重试');
  } finally {
    helpModeState.resolving = false;
  }
}

export function copyHelperReply(reply: HelperReply): void {
  const phone = helpModeState.receivedResponse?.phone ?? '';
  eventBus.emit('reply:selected', {
    text: reply.text,
    direction: reply.direction || sourceLabel(reply.source),
    reason: 'HELP_REPLY',
    phone,
    isFallback: false
  });
  helpModeState.toast = '已复制，请粘贴到微信发送';
  showStatusNotice('success', '组长回复已复制', '请粘贴到微信发送，并按正常流程确认发送');
}

export function toggleHelpResponseExpanded(): void {
  helpModeState.responseExpanded = !helpModeState.responseExpanded;
}

export function closeHelpResponse(): void {
  helpModeState.responseExpanded = false;
}

export function dismissHelpStatusNotice(): void {
  helpModeState.statusNotice = null;
}

export function currentHelperRequest(): HelpRequestPayload | null {
  return helpModeState.helperQueue[helpModeState.currentHelperIndex] ?? null;
}

export function cleanupHelpModeStore(): void {
  helpModeState.helperQueue = [];
  helpModeState.draftReplies = [];
}

function upsertHelpRequest(payload: HelpRequestPayload): void {
  const existing = helpModeState.helperQueue.findIndex((item) => String(item.helpId) === String(payload.helpId));
  if (existing >= 0) {
    helpModeState.helperQueue[existing] = payload;
  } else {
    helpModeState.helperQueue.push(payload);
  }
  if (helpModeState.draftReplies.length === 0) {
    helpModeState.draftReplies = [];
  }
}

function addDraftReply(text: string, direction: string, source: HelpReplySource): void {
  if (helpModeState.draftReplies.length >= loadDesktopConfig().helpMaxReplies) {
    helpModeState.toast = `最多回复 ${loadDesktopConfig().helpMaxReplies} 条`;
    return;
  }
  helpModeState.draftReplies.push({ text, direction, source });
}

function showStatusNotice(level: 'info' | 'success' | 'warning' | 'error', message: string, detail?: string): void {
  helpModeState.statusNotice = { level, message, detail };
}

function sourceLabel(source: HelpReplySource): string {
  if (source === 'CONFIRMED') return '组长确认';
  if (source === 'MODIFIED') return '组长修改';
  return '组长建议';
}
