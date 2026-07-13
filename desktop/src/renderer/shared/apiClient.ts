import { loadDesktopConfig } from './config';
import { eventBus } from './eventBus';
import { recordApiNetworkFailure, recordApiSuccess } from './offlineManager';

export type ApiResponse<T> = {
  success: boolean;
  data: T | null;
  errorCode: string | null;
  message: string | null;
};

type AuthExpiredPayload = {
  message: string;
};

export async function getJson<T>(path: string, timeoutMs = loadDesktopConfig().requestTotalTimeoutMs, signal?: AbortSignal): Promise<ApiResponse<T>> {
  return requestJson<T>('GET', path, undefined, timeoutMs, signal);
}

export async function postJson<T>(
  path: string,
  body: unknown,
  timeoutMs = loadDesktopConfig().requestTotalTimeoutMs,
  signal?: AbortSignal
): Promise<ApiResponse<T>> {
  return requestJson<T>('POST', path, body, timeoutMs, signal);
}

export async function putJson<T>(
  path: string,
  body: unknown,
  timeoutMs = loadDesktopConfig().requestTotalTimeoutMs,
  signal?: AbortSignal
): Promise<ApiResponse<T>> {
  return requestJson<T>('PUT', path, body, timeoutMs, signal);
}

export async function deleteJson<T>(
  path: string,
  timeoutMs = loadDesktopConfig().requestTotalTimeoutMs,
  signal?: AbortSignal
): Promise<ApiResponse<T>> {
  return requestJson<T>('DELETE', path, undefined, timeoutMs, signal);
}

export async function postForm<T>(
  path: string,
  body: FormData,
  timeoutMs = loadDesktopConfig().requestTotalTimeoutMs,
  signal?: AbortSignal
): Promise<ApiResponse<T>> {
  const config = loadDesktopConfig();
  const controller = new AbortController();
  const abort = () => controller.abort();
  signal?.addEventListener('abort', abort, { once: true });
  const timer = window.setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(`${config.apiBaseUrl}${path}`, {
      method: 'POST',
      headers: {
        ...(config.accessToken ? { Authorization: `Bearer ${config.accessToken}` } : {})
      },
      body,
      signal: controller.signal
    });
    recordApiSuccess();
    const payload = await response.json() as ApiResponse<T>;
    emitAuthExpiredIfNeeded(path, config.accessToken, response.status, payload);
    return payload;
  } catch (error) {
    recordApiNetworkFailure(error);
    throw toUserFacingNetworkError(error);
  } finally {
    window.clearTimeout(timer);
    signal?.removeEventListener('abort', abort);
  }
}

async function requestJson<T>(
  method: 'GET' | 'POST' | 'PUT' | 'DELETE',
  path: string,
  body: unknown,
  timeoutMs: number,
  signal?: AbortSignal
): Promise<ApiResponse<T>> {
  const config = loadDesktopConfig();
  const controller = new AbortController();
  const abort = () => controller.abort();
  signal?.addEventListener('abort', abort, { once: true });
  const timer = window.setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(`${config.apiBaseUrl}${path}`, {
      method,
      headers: {
        'Content-Type': 'application/json',
        ...(config.accessToken ? { Authorization: `Bearer ${config.accessToken}` } : {})
      },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal
    });
    recordApiSuccess();
    const payload = await response.json() as ApiResponse<T>;
    emitAuthExpiredIfNeeded(path, config.accessToken, response.status, payload);
    return payload;
  } catch (error) {
    recordApiNetworkFailure(error);
    throw toUserFacingNetworkError(error);
  } finally {
    window.clearTimeout(timer);
    signal?.removeEventListener('abort', abort);
  }
}

function emitAuthExpiredIfNeeded<T>(path: string, accessToken: string, status: number, payload: ApiResponse<T>): void {
  if (!accessToken || path.endsWith('/auth/login')) {
    return;
  }
  const authExpired = status === 401 || payload.errorCode === '80-10002' || payload.errorCode === '80-10008';
  if (!authExpired) {
    return;
  }
  eventBus.emit<AuthExpiredPayload>('auth:expired', {
    message: payload.message?.trim() || '登录已过期，请重新登录'
  });
}

function toUserFacingNetworkError(error: unknown): Error {
  if (error instanceof DOMException && error.name === 'AbortError') {
    return new Error('请求超时，请检查后端服务或稍后重试');
  }
  const message = error instanceof Error ? error.message : String(error ?? '');
  if (/failed to fetch|networkerror|load failed/i.test(message)) {
    return new Error('网络连接失败，请确认本地后端服务已启动');
  }
  return new Error(message || '请求失败，请稍后重试');
}
