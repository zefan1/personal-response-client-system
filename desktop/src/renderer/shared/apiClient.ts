import { loadDesktopConfig } from './config';
import { recordApiNetworkFailure, recordApiSuccess } from './offlineManager';

export type ApiResponse<T> = {
  success: boolean;
  data: T | null;
  errorCode: string | null;
  message: string | null;
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
    return await response.json() as ApiResponse<T>;
  } catch (error) {
    recordApiNetworkFailure(error);
    throw error;
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
    return await response.json() as ApiResponse<T>;
  } catch (error) {
    recordApiNetworkFailure(error);
    throw error;
  } finally {
    window.clearTimeout(timer);
    signal?.removeEventListener('abort', abort);
  }
}
