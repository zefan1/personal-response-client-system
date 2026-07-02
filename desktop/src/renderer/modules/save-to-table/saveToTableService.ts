import { postJson, putJson } from '../../shared/apiClient';
import { loadDesktopConfig } from '../../shared/config';
import type { SaveProfileInput, SaveResult, PendingSaveRecord } from './types';

const PENDING_SAVE_PREFIX = 'pending_saves:';
const TABLE_SYNC_TIMEOUT_MS = 12000;
const activeSaves = new Set<string>();
const retryTimers = new Set<number>();

export async function saveProfile(input: SaveProfileInput): Promise<SaveResult> {
  const phone = input.phone;
  if (activeSaves.has(phone)) {
    return { status: 'BUSY', message: '正在保存中，请稍候', needRefresh: false };
  }
  activeSaves.add(phone);
  try {
    const result = await saveWithRetries(input);
    if (result.status === 'OK') {
      removePendingSave(phone);
    }
    return result;
  } finally {
    activeSaves.delete(phone);
  }
}

export async function syncProfileToTable(input: SaveProfileInput): Promise<SaveResult> {
  if (!input.hasTableRow || !input.sourceTable || !input.sourceRowId) {
    return { status: 'OK', message: '档案已保存', needRefresh: true };
  }
  try {
    const response = await postJson(`/api/v1/customers/${encodeURIComponent(input.phone)}/save-to-table`, {
      sourceTable: input.sourceTable,
      sourceRowId: input.sourceRowId,
      fields: input.editedFields
    }, TABLE_SYNC_TIMEOUT_MS);
    if (response.success) {
      return { status: 'OK', message: '已同步到表格', needRefresh: true };
    }
    return { status: 'FAILED_RETRYING', message: '表格同步失败，系统将在后台自动重试', needRefresh: true };
  } catch {
    return { status: 'FAILED_RETRYING', message: '表格同步失败，系统将在后台自动重试', needRefresh: true };
  }
}

export async function recoverPendingSave(phone: string, latestVersion: number): Promise<SaveResult | null> {
  const pending = readPendingSave(phone);
  if (!pending) {
    return null;
  }
  return saveProfile({
    phone,
    editedFields: pending.editedFields,
    version: latestVersion,
    hasTableRow: false
  });
}

export function getPendingSave(phone: string): PendingSaveRecord | null {
  return readPendingSave(phone);
}

export function cleanupExpiredPendingSaves(): void {
  const expireMs = loadDesktopConfig().savePendingExpireHours * 60 * 60 * 1000;
  const expiredBefore = Date.now() - expireMs;
  for (let index = localStorage.length - 1; index >= 0; index -= 1) {
    const key = localStorage.key(index);
    if (!key?.startsWith(PENDING_SAVE_PREFIX)) {
      continue;
    }
    try {
      const record = JSON.parse(localStorage.getItem(key) ?? '') as PendingSaveRecord;
      if (record.createdAt < expiredBefore) {
        localStorage.removeItem(key);
      }
    } catch {
      localStorage.removeItem(key);
    }
  }
}

export function cleanupSaveToTableService(): void {
  retryTimers.forEach((timer) => window.clearTimeout(timer));
  retryTimers.clear();
  activeSaves.clear();
}

async function saveWithRetries(input: SaveProfileInput): Promise<SaveResult> {
  const config = loadDesktopConfig();
  for (let attempt = 0; attempt <= config.saveMaxRetries; attempt += 1) {
    const result = await tryPutProfile(input);
    if (result.status === 'OK' || result.status === 'CONFLICT') {
      return result;
    }
    if (attempt === 1) {
      writePendingSave(input, attempt + 1);
    }
    if (attempt < config.saveMaxRetries) {
      await wait(config.saveRetryIntervalMs);
    }
  }
  writePendingSave(input, config.saveMaxRetries);
  return {
    status: 'GIVE_UP',
    message: '上次编辑内容未保存成功，系统将在稍后自动重试',
    needRefresh: false
  };
}

async function tryPutProfile(input: SaveProfileInput): Promise<SaveResult> {
  try {
    const response = await putJson(`/api/v1/customers/${encodeURIComponent(input.phone)}`, {
      version: input.version,
      fields: input.editedFields,
      operator: 'desktop'
    }, loadDesktopConfig().saveToTableTimeoutMs);
    if (response.success) {
      return {
        status: 'OK',
        message: input.hasTableRow ? '档案已保存。是否同步到企微表格？' : '档案已保存',
        needRefresh: true,
        askTableSync: input.hasTableRow
      };
    }
    if (response.errorCode === '50-10002') {
      return { status: 'CONFLICT', message: '档案已被更新，正在刷新…', needRefresh: true };
    }
    if (response.errorCode === '40-10002') {
      return { status: 'GIVE_UP', message: '客户不存在，请刷新后重试', needRefresh: true };
    }
    if (response.errorCode === '80-10003') {
      return { status: 'GIVE_UP', message: '无权编辑此客户', needRefresh: false };
    }
    if (response.errorCode === '80-10001') {
      return { status: 'GIVE_UP', message: '输入格式有误，请检查后重试', needRefresh: false };
    }
    return { status: 'FAILED_RETRYING', message: '保存失败，系统正在自动重试', needRefresh: false };
  } catch {
    return { status: 'FAILED_RETRYING', message: '保存失败，系统正在自动重试', needRefresh: false };
  }
}

function wait(ms: number): Promise<void> {
  return new Promise((resolve) => {
    const timer = window.setTimeout(() => {
      retryTimers.delete(timer);
      resolve();
    }, ms);
    retryTimers.add(timer);
  });
}

function writePendingSave(input: SaveProfileInput, retryCount: number): void {
  try {
    const record: PendingSaveRecord = {
      phone: input.phone,
      editedFields: input.editedFields,
      version: input.version,
      createdAt: Date.now(),
      retryCount
    };
    localStorage.setItem(`${PENDING_SAVE_PREFIX}${input.phone}`, JSON.stringify(record));
  } catch {
    // Pending persistence is a fallback; the live edit form remains the primary copy.
  }
}

function readPendingSave(phone: string): PendingSaveRecord | null {
  try {
    const raw = localStorage.getItem(`${PENDING_SAVE_PREFIX}${phone}`);
    return raw ? JSON.parse(raw) as PendingSaveRecord : null;
  } catch {
    return null;
  }
}

function removePendingSave(phone: string): void {
  localStorage.removeItem(`${PENDING_SAVE_PREFIX}${phone}`);
}
