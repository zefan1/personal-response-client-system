import { computed, ref } from 'vue';
import { loadDesktopConfig } from './config';
import { eventBus } from './eventBus';
import { initOfflineDatabase } from './offlineDb';

export type OfflineReason = 'OS_OFFLINE' | 'WS_AND_API_FAILED' | 'API_CONSECUTIVE_FAIL' | null;

export type OfflineCapability = {
  module: string;
  offlineBehavior: string;
  recoveryBehavior?: string;
};

type OnlineStatusPayload = {
  online: boolean;
  type?: string;
};

type WsStatusPayload = {
  connected: boolean;
};

export const isOnline = ref(true);
export const isWsConnected = ref(false);
export const lastOnlineAt = ref(Date.now());
export const offlineReason = ref<OfflineReason>(null);
export const offlineCapabilities = ref<OfflineCapability[]>([]);
export const hasWsDegraded = ref(false);

export const offlineState = {
  isOnline,
  isWsConnected,
  lastOnlineAt,
  offlineReason,
  hasWsDegraded,
  capabilities: computed(() => offlineCapabilities.value)
};

let initialized = false;
let consecutiveApiFailures = 0;
let osOnline = true;
let offlineStartedAt: number | null = null;
let wsDisconnectedSince: number | null = null;
let wsDisconnectTimer: number | null = null;
let osStatusDebounceTimer: number | null = null;
let disposeOnlineStatus: (() => void) | null = null;
let disposeWsStatus: (() => void) | null = null;

export async function initializeOfflineManager(): Promise<void> {
  if (initialized) {
    return;
  }
  initialized = true;
  await initOfflineDatabase().catch(() => undefined);

  if (!window.desktopBridge) {
    osOnline = true;
    isOnline.value = true;
    lastOnlineAt.value = Date.now();
    disposeWsStatus = eventBus.on<WsStatusPayload>('ws:status-change', handleWsStatus);
    return;
  }

  const initialStatus = await window.desktopBridge.getOnlineStatus().catch(() => ({ online: true }));
  osOnline = initialStatus.online;
  if (!osOnline) {
    enterOffline('OS_OFFLINE');
  } else {
    isOnline.value = true;
    lastOnlineAt.value = Date.now();
  }

  disposeOnlineStatus = window.desktopBridge.onOnlineStatusChange((payload) => {
    if (osStatusDebounceTimer) {
      window.clearTimeout(osStatusDebounceTimer);
    }
    osStatusDebounceTimer = window.setTimeout(() => handleOsStatus(payload), 300);
  });
  disposeWsStatus = eventBus.on<WsStatusPayload>('ws:status-change', handleWsStatus);
}

export function cleanupOfflineManager(): void {
  disposeOnlineStatus?.();
  disposeOnlineStatus = null;
  disposeWsStatus?.();
  disposeWsStatus = null;
  if (wsDisconnectTimer) {
    window.clearTimeout(wsDisconnectTimer);
    wsDisconnectTimer = null;
  }
  if (osStatusDebounceTimer) {
    window.clearTimeout(osStatusDebounceTimer);
    osStatusDebounceTimer = null;
  }
  initialized = false;
}

export function registerOfflineCapability(capability: OfflineCapability): void {
  if (!offlineCapabilities.value.some((item) => item.module === capability.module)) {
    offlineCapabilities.value = [...offlineCapabilities.value, capability];
  }
}

export function recordApiSuccess(): void {
  consecutiveApiFailures = 0;
  if (osOnline && isWsConnected.value && !isOnline.value) {
    enterOnline();
  }
}

export function recordApiNetworkFailure(error: unknown): void {
  if (!isNetworkLayerError(error)) {
    return;
  }
  consecutiveApiFailures += 1;
  if (consecutiveApiFailures >= loadDesktopConfig().offlineApiFailCount) {
    enterOffline('API_CONSECUTIVE_FAIL');
  }
}

function handleOsStatus(payload: OnlineStatusPayload): void {
  osOnline = payload.online;
  if (!payload.online) {
    enterOffline('OS_OFFLINE');
    return;
  }
  if (isWsConnected.value || !isOnline.value) {
    maybeRecoverOnline();
  }
}

function handleWsStatus(payload: WsStatusPayload): void {
  isWsConnected.value = payload.connected;
  if (payload.connected) {
    if (wsDisconnectTimer) {
      window.clearTimeout(wsDisconnectTimer);
      wsDisconnectTimer = null;
    }
    wsDisconnectedSince = null;
    if (hasWsDegraded.value) {
      hasWsDegraded.value = false;
      eventBus.emit('ws:reconnected', {});
    }
    maybeRecoverOnline();
    return;
  }

  wsDisconnectedSince = Date.now();
  if (wsDisconnectTimer) {
    window.clearTimeout(wsDisconnectTimer);
  }
  wsDisconnectTimer = window.setTimeout(() => {
    if (!isWsConnected.value && osOnline && wsDisconnectedSince) {
      hasWsDegraded.value = true;
      eventBus.emit('ws:disconnected', { since: wsDisconnectedSince });
    }
  }, loadDesktopConfig().offlineWsDisconnectWaitS * 1000);
}

function maybeRecoverOnline(): void {
  if (!osOnline || !isWsConnected.value) {
    return;
  }
  enterOnline();
}

function enterOffline(reason: Exclude<OfflineReason, null>): void {
  const wasOnline = isOnline.value;
  if (wasOnline) {
    offlineStartedAt = Date.now();
    lastOnlineAt.value = offlineStartedAt;
  }
  isOnline.value = false;
  offlineReason.value = reason;
  if (wasOnline) {
    eventBus.emit('network:offline', { reason, lastOnlineAt: lastOnlineAt.value });
  }
}

function enterOnline(): void {
  if (isOnline.value) {
    lastOnlineAt.value = Date.now();
    return;
  }
  const offlineDurationMs = Date.now() - (offlineStartedAt ?? lastOnlineAt.value);
  isOnline.value = true;
  offlineReason.value = null;
  consecutiveApiFailures = 0;
  offlineStartedAt = null;
  lastOnlineAt.value = Date.now();
  eventBus.emit('network:online', { offlineDurationMs });
}

function isNetworkLayerError(error: unknown): boolean {
  if (error instanceof DOMException && error.name === 'AbortError') {
    return true;
  }
  if (!(error instanceof Error)) {
    return false;
  }
  const text = `${error.name} ${error.message}`.toUpperCase();
  return ['ERR_NETWORK', 'ECONNREFUSED', 'ETIMEDOUT', 'ENOTFOUND', 'FAILED TO FETCH', 'NETWORKERROR'].some((token) => text.includes(token));
}
