export type DesktopConfig = {
  apiBaseUrl: string;
  accessToken: string;
  refreshToken: string;
  accountUsername: string;
  accountRole: string;
  accountPermissions: string[];
  wsUrl: string;
  clipboardPollIntervalMs: number;
  clipboardMd5CacheSize: number;
  clipboardMinImageDimension: number;
  clipboardImageTextCoverMs: number;
  clipboardScreenshotConfirmPromptS: number;
  requestTotalTimeoutMs: number;
  fallbackRetryIntervalMs: number;
  fallbackMaxRetries: number;
  helpTimeoutS: number;
  helpOfflineExpireHours: number;
  helpMaxReplies: number;
  searchDebounceMs: number;
  searchResultLimit: number;
  customerCacheLimit: number;
  followupHistoryVisible: number;
  profileCacheOfflineBannerS: number;
  editLogRetentionDays: number;
  newReminderFlashMs: number;
  toastMaxCount: number;
  toastNewLeadDismissS: number;
  quicksearchShortcut: string;
  quicksearchResultLimit: number;
  quicksearchAutoCloseS: number;
  quicksearchCacheRefreshOnStartup: boolean;
  searchInputDebounceMs: number;
  batchMaxCustomers: number;
  batchCustomerBatchTimeoutMs: number;
  saveToTableTimeoutMs: number;
  saveRetryIntervalMs: number;
  saveMaxRetries: number;
  savePendingExpireHours: number;
  stageSuggestPendingTtlS: number;
  alertHistoryMaxCount: number;
  alertHistoryRetentionDays: number;
  alertBellRefreshIntervalS: number;
  workbenchRefreshIntervalS: number;
  workbenchFollowupListLimit: number;
  workbenchNewLeadListLimit: number;
  workbenchMaxNotices: number;
  offlineApiFailCount: number;
  offlineWsDisconnectWaitS: number;
  onlineToastDurationMs: number;
  recoverSyncTimeoutS: number;
};

const WS_PATH = '/ws/v1/desktop';

const defaultApiBaseUrl = typeof import.meta.env.VITE_DEFAULT_API_BASE_URL === 'string'
  && import.meta.env.VITE_DEFAULT_API_BASE_URL.trim()
  ? import.meta.env.VITE_DEFAULT_API_BASE_URL.trim()
  : 'http://localhost:8080';

const defaults: DesktopConfig = {
  apiBaseUrl: defaultApiBaseUrl,
  accessToken: '',
  refreshToken: '',
  accountUsername: '',
  accountRole: '',
  accountPermissions: [],
  wsUrl: '',
  clipboardPollIntervalMs: 500,
  clipboardMd5CacheSize: 5,
  clipboardMinImageDimension: 200,
  clipboardImageTextCoverMs: 2000,
  clipboardScreenshotConfirmPromptS: 10,
  requestTotalTimeoutMs: 15000,
  fallbackRetryIntervalMs: 10000,
  fallbackMaxRetries: 3,
  helpTimeoutS: 30,
  helpOfflineExpireHours: 4,
  helpMaxReplies: 3,
  searchDebounceMs: 300,
  searchResultLimit: 10,
  customerCacheLimit: 50,
  followupHistoryVisible: 3,
  profileCacheOfflineBannerS: 5,
  editLogRetentionDays: 7,
  newReminderFlashMs: 3000,
  toastMaxCount: 3,
  toastNewLeadDismissS: 15,
  quicksearchShortcut: 'CommandOrControl+Shift+F',
  quicksearchResultLimit: 10,
  quicksearchAutoCloseS: 3,
  quicksearchCacheRefreshOnStartup: true,
  searchInputDebounceMs: 100,
  batchMaxCustomers: 100,
  batchCustomerBatchTimeoutMs: 3000,
  saveToTableTimeoutMs: 15000,
  saveRetryIntervalMs: 5000,
  saveMaxRetries: 3,
  savePendingExpireHours: 24,
  stageSuggestPendingTtlS: 300,
  alertHistoryMaxCount: 50,
  alertHistoryRetentionDays: 7,
  alertBellRefreshIntervalS: 86400,
  workbenchRefreshIntervalS: 60,
  workbenchFollowupListLimit: 5,
  workbenchNewLeadListLimit: 3,
  workbenchMaxNotices: 3,
  offlineApiFailCount: 3,
  offlineWsDisconnectWaitS: 15,
  onlineToastDurationMs: 2000,
  recoverSyncTimeoutS: 30
};

export function loadDesktopConfig(): DesktopConfig {
  try {
    const raw = localStorage.getItem('desktop_config');
    const parsed = raw ? { ...defaults, ...JSON.parse(raw) } : { ...defaults };
    return normalizeConfig(parsed);
  } catch {
    return normalizeConfig({ ...defaults });
  }
}

export function saveDesktopConfig(patch: Partial<DesktopConfig>): DesktopConfig {
  const next = normalizeConfig({ ...loadDesktopConfig(), ...patch }, 'wsUrl' in patch);
  localStorage.setItem('desktop_config', JSON.stringify(next));
  return next;
}

function normalizeConfig(config: DesktopConfig, explicitWsUrl = false): DesktopConfig {
  const apiBaseUrl = trimTrailingSlash(config.apiBaseUrl || defaults.apiBaseUrl);
  const wsUrl = explicitWsUrl && config.wsUrl.trim()
    ? config.wsUrl.trim()
    : deriveWsUrl(apiBaseUrl);
  const accountPermissions = Array.isArray(config.accountPermissions)
    ? [...new Set(config.accountPermissions.map((permission) => String(permission).trim()).filter(Boolean))]
    : [];
  return { ...config, apiBaseUrl, accountPermissions, wsUrl };
}

export function deriveWsUrl(apiBaseUrl: string): string {
  const normalizedBase = trimTrailingSlash(apiBaseUrl || defaults.apiBaseUrl);
  if (!normalizedBase.startsWith('http://') && !normalizedBase.startsWith('https://')) {
    if (typeof window !== 'undefined' && /^https?:$/.test(window.location.protocol)) {
      return deriveWsUrl(window.location.origin);
    }
  }
  try {
    const url = new URL(normalizedBase);
    url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
    url.pathname = WS_PATH;
    url.search = '';
    url.hash = '';
    return url.toString();
  } catch {
    return 'ws://localhost:8080/ws/v1/desktop';
  }
}

export function resolveApiUrl(apiBaseUrl: string, path: string): string {
  const base = apiBaseUrl.trim();
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  if (!base || base === '/') return normalizedPath;
  return `${base.replace(/\/+$/, '')}${normalizedPath}`;
}

function trimTrailingSlash(value: string): string {
  let normalized = value.trim();
  while (normalized.endsWith('/') && normalized.length > 1) {
    normalized = normalized.slice(0, -1);
  }
  return normalized;
}
