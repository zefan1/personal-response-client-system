export type DesktopConfig = {
  apiBaseUrl: string;
  accessToken: string;
  wsUrl: string;
  clipboardPollIntervalMs: number;
  clipboardMd5CacheSize: number;
  clipboardMinImageDimension: number;
  clipboardImageTextCoverMs: number;
  requestTotalTimeoutMs: number;
  fallbackRetryIntervalMs: number;
  fallbackMaxRetries: number;
  helpTimeoutS: number;
};

const defaults: DesktopConfig = {
  apiBaseUrl: 'http://127.0.0.1:8080',
  accessToken: '',
  wsUrl: 'ws://127.0.0.1:8080/ws/v1/desktop',
  clipboardPollIntervalMs: 500,
  clipboardMd5CacheSize: 5,
  clipboardMinImageDimension: 200,
  clipboardImageTextCoverMs: 2000,
  requestTotalTimeoutMs: 15000,
  fallbackRetryIntervalMs: 10000,
  fallbackMaxRetries: 3,
  helpTimeoutS: 30
};

export function loadDesktopConfig(): DesktopConfig {
  try {
    const raw = localStorage.getItem('desktop_config');
    return raw ? { ...defaults, ...JSON.parse(raw) } : defaults;
  } catch {
    return defaults;
  }
}
