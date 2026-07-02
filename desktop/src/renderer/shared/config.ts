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
  helpTimeoutS: 30,
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
  savePendingExpireHours: 24
};

export function loadDesktopConfig(): DesktopConfig {
  try {
    const raw = localStorage.getItem('desktop_config');
    return raw ? { ...defaults, ...JSON.parse(raw) } : defaults;
  } catch {
    return defaults;
  }
}
