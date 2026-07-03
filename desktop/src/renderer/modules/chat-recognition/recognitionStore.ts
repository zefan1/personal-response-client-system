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
  lastRequestSource: null as RecognizeSource | null,
  lastRequestContentMd5: '',
  lastRequestTime: 0,
  imageServiceStatus: 'UNKNOWN' as ImageServiceStatus,
  isTwoBoxMode: false,
  customerIdentityInput: '',
  chatContentInput: '',
  toast: ''
});

export async function triggerRecognize(source: RecognizeSource, content: RecognizeContent): Promise<void> {
  if (recognitionState.imageServiceStatus === 'DOWN' && source !== 'CLIPBOARD_TEXT') {
    recognitionState.toast = '图片识别暂不可用，请使用文字通道';
    recognitionState.isTwoBoxMode = true;
    return;
  }
  if (recognitionState.isRecognizePending && !(source === 'BUTTON_CLICK' && recognitionState.lastRequestSource === 'CLIPBOARD_SCREENSHOT')) {
    recognitionState.toast = '上一条请求正在处理中';
    return;
  }
  recognitionState.isRecognizePending = true;
  recognitionState.lastRequestSource = source;
  const contentMd5 = await digest(JSON.stringify(content));
  if (contentMd5 === recognitionState.lastRequestContentMd5 && Date.now() - recognitionState.lastRequestTime < 1000) {
    recognitionState.isRecognizePending = false;
    recognitionState.lastRequestSource = null;
    return;
  }
  recognitionState.lastRequestContentMd5 = contentMd5;
  recognitionState.lastRequestTime = Date.now();
  eventBus.emit('recognize:start', { source });
  try {
    const response = await postJson<ChatRecognizeResponse>('/api/v1/chat/recognize', {
      imageBase64: content.imageBase64,
      textMessage: content.textMessage,
      customerIdentifier: content.customerIdentifier,
      source
    });
    if (!response.success) {
      handleError(response.errorCode);
      return;
    }
    const data = response.data as ChatRecognizeResponse;
    const matchType = data.match?.matchType ?? 'NONE';
    if (matchType === 'MULTIPLE') {
      eventBus.emit('recognize:multiple', { candidates: data.match?.customers ?? [], matchInfo: data.match });
    } else {
      eventBus.emit('recognize:result', { source, response: data });
    }
    recognitionState.isTwoBoxMode = false;
  } catch {
    recognitionState.toast = '请求超时，请检查网络后重试';
    eventBus.emit('recognize:timeout', {});
  } finally {
    recognitionState.isRecognizePending = false;
    recognitionState.lastRequestSource = null;
  }
}

export async function recognizeClipboardImage(payload: ClipboardImagePayload): Promise<void> {
  if (recognitionState.isRecognizePending || recognitionState.imageServiceStatus === 'DOWN') {
    return;
  }
  recognitionState.toast = '检测到截图，正在识别...';
  await triggerRecognize('CLIPBOARD_SCREENSHOT', { imageBase64: payload.imageBase64 });
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

function handleError(errorCode: string | null): void {
  if (errorCode === '30-10001') {
    recognitionState.toast = '图片识别失败，请粘贴客户标识和聊天内容';
    recognitionState.isTwoBoxMode = true;
    eventBus.emit('recognize:image-failed', {});
  } else if (errorCode === '30-10002') {
    recognitionState.toast = '图片格式不支持，请使用 PNG/JPG 截图';
  } else if (errorCode === '80-10002') {
    recognitionState.toast = '登录已失效，请重新登录';
  } else {
    recognitionState.toast = '识别失败，请稍后重试';
  }
}

async function digest(value: string): Promise<string> {
  const data = new TextEncoder().encode(value);
  const hash = await crypto.subtle.digest('SHA-256', data);
  return Array.from(new Uint8Array(hash)).map((byte) => byte.toString(16).padStart(2, '0')).join('');
}
