export type DesktopConfig = {
  apiBaseUrl: string;
  accessToken: string;
  accountRole: string;
  wsUrl: string;
  clipboardPollIntervalMs: number;
  clipboardMd5CacheSize: number;
  clipboardMinImageDimension: number;
  clipboardImageTextCoverMs: number;
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

const defaults: DesktopConfig = {
  apiBaseUrl: 'http://localhost:8080',
  accessToken: '',
  accountRole: '',
  wsUrl: 'ws://localhost:8080/ws/v1/desktop',
  clipboardPollIntervalMs: 500,
  clipboardMd5CacheSize: 5,
  clipboardMinImageDimension: 200,
  clipboardImageTextCoverMs: 2000,
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
  workbenchRefreshIntervalS: 300,
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
    return raw ? { ...defaults, ...JSON.parse(raw) } : defaults;
  } catch {
    return defaults;
  }
}

export function saveDesktopConfig(patch: Partial<DesktopConfig>): DesktopConfig {
  const next = { ...loadDesktopConfig(), ...patch };
  localStorage.setItem('desktop_config', JSON.stringify(next));
  return next;
}
