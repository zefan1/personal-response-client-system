import { reactive } from 'vue';
import { postJson } from '../../shared/apiClient';
import { eventBus } from '../../shared/eventBus';
import type { ChatRecognizeResponse, ClipboardImagePayload, ImageServiceStatus, RecognizeSource } from './types';

type RecognizeContent = {
  imageBase64?: string;
  customerIdentifier?: string;
  textMessage?: string;
};

export const recognitionState = reactive({
  isRecognizePending: false,
  pendingCount: 0,
  lastRequestSource: null as RecognizeSource | null,
  lastRequestContentMd5: '',
  lastRequestTime: 0,
  imageServiceStatus: 'UNKNOWN' as ImageServiceStatus,
  pendingClipboardImage: null as ClipboardImagePayload | null,
  pendingClipboardImageToken: '',
  pendingClipboardImageDetectedAt: 0,
  isTwoBoxMode: false,
  customerIdentityInput: '',
  chatContentInput: '',
  toast: ''
});

let requestSequence = 0;

export async function triggerRecognize(source: RecognizeSource, content: RecognizeContent): Promise<void> {
  if (recognitionState.imageServiceStatus === 'DOWN' && source !== 'CLIPBOARD_TEXT') {
    recognitionState.toast = '图片识别暂不可用，请使用文字通道';
    recognitionState.isTwoBoxMode = true;
    return;
  }
  recognitionState.pendingCount += 1;
  recognitionState.isRecognizePending = recognitionState.pendingCount > 0;
  recognitionState.lastRequestSource = source;
  const sessionId = `reply-${Date.now()}-${requestSequence += 1}`;
  const contentMd5 = await digest(JSON.stringify(content));
  if (contentMd5 === recognitionState.lastRequestContentMd5 && Date.now() - recognitionState.lastRequestTime < 1000) {
    recognitionState.pendingCount = Math.max(0, recognitionState.pendingCount - 1);
    recognitionState.isRecognizePending = recognitionState.pendingCount > 0;
    recognitionState.lastRequestSource = null;
    return;
  }
  recognitionState.lastRequestContentMd5 = contentMd5;
  recognitionState.lastRequestTime = Date.now();
  eventBus.emit('recognize:start', { sessionId, source, stage: content.imageBase64 ? 'CAPTURED' : 'UPLOADING' });
  try {
    if (content.imageBase64) {
      eventBus.emit('recognize:progress', { sessionId, source, stage: 'UPLOADING', message: '正在提交截图' });
    }
    const response = await postJson<ChatRecognizeResponse>('/api/v1/chat/recognize', {
      imageBase64: content.imageBase64,
      textMessage: content.textMessage,
      customerIdentifier: content.customerIdentifier,
      source
    });
    if (!response.success) {
      handleError(response.errorCode, sessionId, response.message);
      return;
    }
    eventBus.emit('recognize:progress', { sessionId, source, stage: 'GENERATING', message: '正在生成回复' });
    const data = response.data as ChatRecognizeResponse;
    const matchType = data.match?.matchType ?? 'NONE';
    if (matchType === 'MULTIPLE') {
      eventBus.emit('recognize:multiple', { sessionId, candidates: data.match?.customers ?? [], matchInfo: data.match });
    } else {
      eventBus.emit('recognize:result', { sessionId, source, response: data });
    }
    recognitionState.isTwoBoxMode = false;
  } catch {
    recognitionState.toast = '请求超时，请检查网络后重试';
    eventBus.emit('recognize:timeout', { sessionId, message: recognitionState.toast });
  } finally {
    recognitionState.pendingCount = Math.max(0, recognitionState.pendingCount - 1);
    recognitionState.isRecognizePending = recognitionState.pendingCount > 0;
    recognitionState.lastRequestSource = null;
  }
}

export async function recognizeClipboardImage(payload: ClipboardImagePayload): Promise<void> {
  if (recognitionState.imageServiceStatus === 'DOWN') {
    recognitionState.pendingClipboardImage = null;
    recognitionState.pendingClipboardImageToken = '';
    recognitionState.pendingClipboardImageDetectedAt = 0;
    recognitionState.toast = '检测到截图，但图片识别暂不可用，请使用文字通道';
    recognitionState.isTwoBoxMode = true;
    return;
  }
  recognitionState.pendingClipboardImage = payload;
  recognitionState.pendingClipboardImageToken = `${payload.md5}:${Date.now()}`;
  recognitionState.pendingClipboardImageDetectedAt = Date.now();
  recognitionState.toast = '检测到新截图，确认是客户聊天后再识别';
}

export async function recognizePendingClipboardImage(): Promise<void> {
  const payload = recognitionState.pendingClipboardImage;
  if (!payload) {
    recognitionState.toast = '暂无待识别截图';
    return;
  }
  recognitionState.pendingClipboardImage = null;
  recognitionState.pendingClipboardImageToken = '';
  recognitionState.pendingClipboardImageDetectedAt = 0;
  await triggerRecognize('CLIPBOARD_SCREENSHOT', { imageBase64: payload.imageBase64 });
}

export function dismissPendingClipboardImage(): void {
  recognitionState.pendingClipboardImage = null;
  recognitionState.pendingClipboardImageToken = '';
  recognitionState.pendingClipboardImageDetectedAt = 0;
  recognitionState.toast = '已忽略这张截图';
}

export function openTextMode(): void {
  recognitionState.isTwoBoxMode = true;
}

export function closeTextMode(): void {
  recognitionState.isTwoBoxMode = false;
}

export async function submitTextRecognition(): Promise<void> {
  await triggerRecognize('CLIPBOARD_TEXT', {
    customerIdentifier: recognitionState.customerIdentityInput,
    textMessage: recognitionState.chatContentInput
  });
}

export function handleImageServiceStatus(payload: { status?: string; message?: string }): void {
  if (payload.status === 'DOWN') {
    recognitionState.imageServiceStatus = 'DOWN';
    recognitionState.toast = '图片识别服务暂不可用，已切换至文字通道';
    recognitionState.isTwoBoxMode = true;
  } else if (payload.status === 'UP') {
    recognitionState.imageServiceStatus = 'UP';
    recognitionState.toast = '图片识别服务已恢复';
  }
}

function handleError(errorCode: string | null, sessionId: string, message?: string | null): void {
  if (errorCode === '30-10001') {
    const detail = message?.trim() || '图片识别失败，请粘贴客户标识和聊天内容';
    recognitionState.toast = detail;
    recognitionState.isTwoBoxMode = true;
    eventBus.emit('recognize:image-failed', { sessionId, errorCode, message: detail });
    return;
  }
  const fallback = errorCode === '30-10002'
    ? '图片格式不支持，请使用 PNG/JPG 截图'
    : errorCode === '80-10002'
      ? '登录已失效，请重新登录'
      : '识别失败，请稍后重试';
  const detail = message?.trim() || fallback;
  recognitionState.toast = detail;
  eventBus.emit('recognize:failed', { sessionId, errorCode, message: detail });
}

async function digest(value: string): Promise<string> {
  const data = new TextEncoder().encode(value);
  const hash = await crypto.subtle.digest('SHA-256', data);
  return Array.from(new Uint8Array(hash)).map((byte) => byte.toString(16).padStart(2, '0')).join('');
}
