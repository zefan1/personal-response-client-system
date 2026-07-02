import { loadDesktopConfig } from './config';

export type ApiResponse<T> = {
  success: boolean;
  data: T | null;
  errorCode: string | null;
  message: string | null;
};

export async function postJson<T>(path: string, body: unknown, timeoutMs = loadDesktopConfig().requestTotalTimeoutMs): Promise<ApiResponse<T>> {
  const config = loadDesktopConfig();
  const controller = new AbortController();
  const timer = window.setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(`${config.apiBaseUrl}${path}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(config.accessToken ? { Authorization: `Bearer ${config.accessToken}` } : {})
      },
      body: JSON.stringify(body),
      signal: controller.signal
    });
    return await response.json() as ApiResponse<T>;
  } finally {
    window.clearTimeout(timer);
  }
}
