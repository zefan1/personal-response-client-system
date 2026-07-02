import { computed, reactive } from 'vue';
import { loadDesktopConfig } from '../../shared/config';
import { eventBus } from '../../shared/eventBus';
import {
  closeAlertDatabase,
  deleteExpiredAlerts,
  getAlertsByPhone as getPersistedAlertsByPhone,
  getRecentAlerts as getPersistedRecentAlerts,
  insertAlertHistory,
  updateAlertAcknowledged
} from './alertHistoryDb';
import type { AbnormalAlert, AbnormalAlertInboundPayload, AlertLevel, AlertType } from './types';

const validTypes = new Set<AlertType>(['CUSTOMER_COMPLAINT', 'CHURN_RISK']);
const validLevels = new Set<AlertLevel>(['ERROR', 'WARN', 'INFO']);

export const alertStore = reactive(new Map<string, AbnormalAlert[]>());

export const abnormalAlertState = reactive({
  panelOpen: false,
  historyOpen: false,
  historyLoading: false,
  historyUnavailable: false,
  recentHistory: [] as AbnormalAlert[],
  message: ''
});

export const unconfirmedCount = computed(() =>
  Array.from(alertStore.values()).flat().filter((alert) => !alert.acknowledged).length
);

let cleanupTimer: number | null = null;
let disposeWsListener: (() => void) | null = null;

export function initializeAbnormalAlertRouter(): void {
  if (disposeWsListener) {
    return;
  }
  disposeWsListener = eventBus.on<AbnormalAlertInboundPayload>('ABNORMAL_ALERT', handleIncomingAlert);
  runHistoryCleanup();
  const intervalMs = loadDesktopConfig().alertBellRefreshIntervalS * 1000;
  cleanupTimer = window.setInterval(runHistoryCleanup, intervalMs);
}

export function cleanupAbnormalAlertRouter(): void {
  disposeWsListener?.();
  disposeWsListener = null;
  if (cleanupTimer) {
    window.clearInterval(cleanupTimer);
    cleanupTimer = null;
  }
  closeAlertDatabase();
  alertStore.clear();
}

export function handleIncomingAlert(payload: AbnormalAlertInboundPayload): void {
  if (!validateAlertPayload(payload)) {
    warnK('K_PAYLOAD_INVALID', payload);
    return;
  }
  const alert: AbnormalAlert = {
    alertId: createAlertId(payload.occurredAt),
    phone: payload.phone,
    alertType: payload.alertType as AlertType,
    message: payload.message.trim(),
    level: payload.level as AlertLevel,
    occurredAt: payload.occurredAt,
    acknowledged: false,
    acknowledgedAt: null
  };
  addAlert(alert);
  emitAbnormalAlert(alert);
  void insertAlertHistory(alert, loadDesktopConfig().alertHistoryMaxCount).catch((error) => {
    warnK('K_INDEXEDDB_WRITE_FAILED', error);
  });
}

export function getAlertsByPhone(phone: string): AbnormalAlert[] {
  return sortAlerts(alertStore.get(phone) ?? []).filter((alert) => !alert.acknowledged);
}

export async function loadAlertsByPhone(phone: string): Promise<AbnormalAlert[]> {
  const memoryAlerts = getAlertsByPhone(phone);
  if (memoryAlerts.length > 0) {
    return memoryAlerts;
  }
  try {
    const persisted = await getPersistedAlertsByPhone(phone);
    mergePersistedAlerts(persisted);
    abnormalAlertState.historyUnavailable = false;
    return persisted.filter((alert) => !alert.acknowledged);
  } catch (error) {
    abnormalAlertState.historyUnavailable = true;
    warnK('K_INDEXEDDB_READ_FAILED', error);
    return [];
  }
}

export async function loadRecentAlertHistory(limit = loadDesktopConfig().alertHistoryMaxCount): Promise<void> {
  abnormalAlertState.historyLoading = true;
  abnormalAlertState.historyUnavailable = false;
  try {
    abnormalAlertState.recentHistory = await getPersistedRecentAlerts(limit);
  } catch (error) {
    abnormalAlertState.recentHistory = [];
    abnormalAlertState.historyUnavailable = true;
    warnK('K_INDEXEDDB_READ_FAILED', error);
  } finally {
    abnormalAlertState.historyLoading = false;
  }
}

export function acknowledgeAlert(alertId: string): void {
  const alert = findAlert(alertId);
  if (!alert || alert.acknowledged) {
    return;
  }
  alert.acknowledged = true;
  alert.acknowledgedAt = new Date().toISOString();
  const alerts = alertStore.get(alert.phone);
  if (alerts) {
    alertStore.set(alert.phone, [...alerts]);
  }
  emitAbnormalAlert(alert);
  void updateAlertAcknowledged(alert).catch((error) => {
    warnK('K_INDEXEDDB_WRITE_FAILED', error);
  });
}

export function toggleAlertPanel(): void {
  abnormalAlertState.panelOpen = !abnormalAlertState.panelOpen;
  if (abnormalAlertState.panelOpen) {
    abnormalAlertState.historyOpen = false;
  }
}

export function closeAlertPanel(): void {
  abnormalAlertState.panelOpen = false;
}

export async function showAllHistory(): Promise<void> {
  abnormalAlertState.historyOpen = true;
  await loadRecentAlertHistory();
}

function addAlert(alert: AbnormalAlert): void {
  const existing = alertStore.get(alert.phone) ?? [];
  alertStore.set(alert.phone, sortAlerts([alert, ...existing]));
}

function mergePersistedAlerts(alerts: AbnormalAlert[]): void {
  alerts.forEach((alert) => {
    const existing = alertStore.get(alert.phone) ?? [];
    if (!existing.some((item) => item.alertId === alert.alertId)) {
      alertStore.set(alert.phone, sortAlerts([alert, ...existing]));
    }
  });
}

function validateAlertPayload(payload: AbnormalAlertInboundPayload): payload is Required<AbnormalAlertInboundPayload> {
  return Boolean(
    payload.phone &&
    /^\d{11}$/.test(payload.phone) &&
    payload.alertType &&
    validTypes.has(payload.alertType as AlertType) &&
    payload.message &&
    payload.message.trim().length > 0 &&
    payload.level &&
    validLevels.has(payload.level as AlertLevel) &&
    payload.occurredAt
  );
}

function createAlertId(occurredAt: string): string {
  const timestamp = Number.isNaN(Date.parse(occurredAt)) ? Date.now() : Date.parse(occurredAt);
  const random4 = Math.floor(1000 + Math.random() * 9000);
  return `k_alert_${timestamp}_${random4}`;
}

function emitAbnormalAlert(alert: AbnormalAlert): void {
  try {
    eventBus.emit<AbnormalAlert>('abnormal:alert', alert);
  } catch (error) {
    warnK('K_EVENT_EMIT_FAILED', error);
  }
}

function runHistoryCleanup(): void {
  void deleteExpiredAlerts(loadDesktopConfig().alertHistoryRetentionDays).catch((error) => {
    warnK('K_INDEXEDDB_CLEANUP_FAILED', error);
  });
}

function findAlert(alertId: string): AbnormalAlert | null {
  for (const alerts of alertStore.values()) {
    const found = alerts.find((alert) => alert.alertId === alertId);
    if (found) {
      return found;
    }
  }
  return null;
}

function sortAlerts(alerts: AbnormalAlert[]): AbnormalAlert[] {
  return [...alerts].sort((a, b) => b.occurredAt.localeCompare(a.occurredAt));
}

function warnK(code: string, detail: unknown): void {
  console.warn(code, detail);
}
