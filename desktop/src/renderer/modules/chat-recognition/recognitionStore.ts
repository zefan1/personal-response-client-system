import { reactive } from 'vue';
import { getJson, postJson } from '../../shared/apiClient';
import { eventBus } from '../../shared/eventBus';
import type {
  ChatRecognizeResponse,
  ClipboardImagePayload,
  ImageServiceStatus,
  RecognitionJobResponse,
  RecognitionJobStatus,
  RecognizeSource
} from './types';

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
const RECOGNITION_JOB_POLL_INTERVAL_MS = 1000;
const recognitionJobPollTimers = new Map<string, number>();

export function beginScreenshotRecognition(source: RecognizeSource): string | null {
  if (recognitionState.imageServiceStatus === 'DOWN') {
    recognitionState.toast = '图片识别暂不可用，请使用文字通道';
    recognitionState.isTwoBoxMode = true;
    return null;
  }
  const sessionId = nextReplySessionId();
  recognitionState.toast = '';
  eventBus.emit('recognize:start', {
    sessionId,
    source,
    stage: 'CAPTURING',
    message: '正在截取当前聊天'
  });
  return sessionId;
}

export function failScreenshotRecognition(sessionId: string, message: string): void {
  recognitionState.toast = message;
  eventBus.emit('recognize:image-failed', {
    sessionId,
    errorCode: 'CAPTURE_FAILED',
    message
  });
}

export async function triggerRecognize(
  source: RecognizeSource,
  content: RecognizeContent,
  existingSessionId?: string
): Promise<void> {
  if (recognitionState.imageServiceStatus === 'DOWN' && source !== 'CLIPBOARD_TEXT') {
    recognitionState.toast = '图片识别暂不可用，请使用文字通道';
    recognitionState.isTwoBoxMode = true;
    if (existingSessionId) {
      eventBus.emit('recognize:failed', { sessionId: existingSessionId, message: recognitionState.toast });
    }
    return;
  }
  recognitionState.pendingCount += 1;
  recognitionState.isRecognizePending = recognitionState.pendingCount > 0;
  recognitionState.lastRequestSource = source;
  const sessionId = existingSessionId ?? nextReplySessionId();
  const contentMd5 = await digest(JSON.stringify(content));
  if (contentMd5 === recognitionState.lastRequestContentMd5 && Date.now() - recognitionState.lastRequestTime < 1000) {
    recognitionState.pendingCount = Math.max(0, recognitionState.pendingCount - 1);
    recognitionState.isRecognizePending = recognitionState.pendingCount > 0;
    recognitionState.lastRequestSource = null;
    if (existingSessionId) {
      eventBus.emit('recognize:failed', { sessionId, message: '该聊天刚刚已提交识别，无需重复操作' });
    }
    return;
  }
  recognitionState.lastRequestContentMd5 = contentMd5;
  recognitionState.lastRequestTime = Date.now();
  if (existingSessionId) {
    eventBus.emit('recognize:progress', { sessionId, source, stage: 'CAPTURED', message: '已获取截图' });
  } else {
    eventBus.emit('recognize:start', { sessionId, source, stage: content.imageBase64 ? 'CAPTURED' : 'UPLOADING' });
  }
  try {
    if (content.imageBase64) {
      eventBus.emit('recognize:progress', { sessionId, source, stage: 'UPLOADING', message: '正在提交截图' });
    }
    if (content.imageBase64) {
      const response = await postJson<RecognitionJobResponse>('/api/v1/chat/recognition-jobs', {
        imageBase64: content.imageBase64,
        textMessage: content.textMessage,
        customerIdentifier: content.customerIdentifier,
        replySessionId: sessionId
      }, 0);
      if (!response.success || !response.data) {
        handleError(response.errorCode, sessionId, response.message);
        return;
      }
      handleRecognitionJob(sessionId, source, response.data);
      recognitionState.isTwoBoxMode = false;
      return;
    }
    const response = await postJson<ChatRecognizeResponse>('/api/v1/chat/recognize', {
      imageBase64: content.imageBase64,
      textMessage: content.textMessage,
      customerIdentifier: content.customerIdentifier,
      source,
      replySessionId: sessionId
    }, 0);
    if (!response.success) {
      handleError(response.errorCode, sessionId, response.message);
      return;
    }
    const data = response.data as ChatRecognizeResponse;
    if (!data) {
      handleError('CLIENT_PROTOCOL_ERROR', sessionId, '识别服务返回的数据不完整，请重新识别聊天');
      return;
    }
    eventBus.emit('recognize:progress', { sessionId, source, stage: 'GENERATING', message: '正在生成回复' });
    eventBus.emit('recognize:result', { sessionId, source, response: data });
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

function nextReplySessionId(): string {
  return `reply-${Date.now()}-${requestSequence += 1}`;
}

export async function cancelRecognitionJob(jobId: string, sessionId: string): Promise<void> {
  if (!jobId || !sessionId) return;
  clearRecognitionJobPoll(jobId);
  try {
    const response = await postJson<RecognitionJobResponse>(
      `/api/v1/chat/recognition-jobs/${encodeURIComponent(jobId)}/cancel`,
      {},
      5000
    );
    if (!response.success || !response.data) {
      handleError(response.errorCode, sessionId, response.message);
      return;
    }
    handleRecognitionJob(sessionId, 'BUTTON_CLICK', response.data);
  } catch (error) {
    handleError(null, sessionId, error instanceof Error ? error.message : undefined);
  }
}

export function resumeRecognitionJobPolling(
  jobId: string,
  sessionId: string,
  source: RecognizeSource
): void {
  if (!jobId || !sessionId) return;
  scheduleRecognitionJobPoll(jobId, sessionId, source);
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
  if (errorCode === 'RECOGNITION_BACKEND_RESTARTED') {
    const detail = '后端重启导致识图任务失败，请重新识别聊天后重试';
    recognitionState.toast = detail;
    eventBus.emit('recognize:failed', { sessionId, errorCode, message: detail });
    return;
  }
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

function handleRecognitionJob(
  sessionId: string,
  source: RecognizeSource,
  job: RecognitionJobResponse
): void {
  eventBus.emit('recognize:job', {
    sessionId,
    source,
    jobId: job.jobId,
    status: job.status,
    errorCode: job.errorCode ?? null
  });
  if (job.status === 'QUEUED' || job.status === 'RECOGNIZING') {
    eventBus.emit('recognize:progress', {
      sessionId,
      source,
      stage: 'WAITING_MODEL',
      message: job.status === 'QUEUED' ? '正在排队识图' : '正在识图并生成回复'
    });
    scheduleRecognitionJobPoll(job.jobId, sessionId, source);
    return;
  }
  clearRecognitionJobPoll(job.jobId);
  if (job.status === 'READY') {
    if (!job.response) {
      handleError('CLIENT_PROTOCOL_ERROR', sessionId, '识图任务未返回回复结果，请重新识别聊天');
      return;
    }
    eventBus.emit('recognize:result', { sessionId, source, response: job.response });
    return;
  }
  if (job.status === 'CANCELLED') {
    return;
  }
  handleError(job.errorCode ?? errorCodeForJobStatus(job.status), sessionId, errorMessageForJobStatus(job.status));
}

function scheduleRecognitionJobPoll(jobId: string, sessionId: string, source: RecognizeSource): void {
  clearRecognitionJobPoll(jobId);
  const timer = window.setTimeout(() => {
    recognitionJobPollTimers.delete(jobId);
    void pollRecognitionJob(jobId, sessionId, source);
  }, RECOGNITION_JOB_POLL_INTERVAL_MS);
  recognitionJobPollTimers.set(jobId, timer);
}

async function pollRecognitionJob(jobId: string, sessionId: string, source: RecognizeSource): Promise<void> {
  try {
    const response = await getJson<RecognitionJobResponse>(
      `/api/v1/chat/recognition-jobs/${encodeURIComponent(jobId)}`,
      5000
    );
    if (!response.success || !response.data) {
      handleError(response.errorCode, sessionId, response.message);
      return;
    }
    handleRecognitionJob(sessionId, source, response.data);
  } catch (error) {
    recognitionState.toast = '任务状态同步失败，正在重试';
    scheduleRecognitionJobPoll(jobId, sessionId, source);
  }
}

function clearRecognitionJobPoll(jobId: string): void {
  const timer = recognitionJobPollTimers.get(jobId);
  if (timer !== undefined) {
    window.clearTimeout(timer);
    recognitionJobPollTimers.delete(jobId);
  }
}

function errorCodeForJobStatus(status: RecognitionJobStatus): string {
  return status === 'EXPIRED' ? 'RECOGNITION_IMAGE_EXPIRED' : 'RECOGNITION_PROCESSING_FAILED';
}

function errorMessageForJobStatus(status: RecognitionJobStatus): string {
  if (status === 'EXPIRED') {
    return '识图任务已过期，请重新识别聊天';
  }
  return '识图任务处理失败，请稍后重试';
}

async function digest(value: string): Promise<string> {
  const data = new TextEncoder().encode(value);
  const hash = await crypto.subtle.digest('SHA-256', data);
  return Array.from(new Uint8Array(hash)).map((byte) => byte.toString(16).padStart(2, '0')).join('');
}
