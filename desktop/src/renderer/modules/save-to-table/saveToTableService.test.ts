import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { SaveProfileInput } from './types';

const putJsonMock = vi.fn();
const postJsonMock = vi.fn();

vi.mock('../../shared/apiClient', () => ({
  putJson: putJsonMock,
  postJson: postJsonMock
}));

type SaveModule = typeof import('./saveToTableService');

function installMemoryLocalStorage(): void {
  const store = new Map<string, string>();
  const storage = {
    get length() {
      return store.size;
    },
    key: vi.fn((index: number) => Array.from(store.keys())[index] ?? null),
    getItem: vi.fn((key: string) => store.get(key) ?? null),
    setItem: vi.fn((key: string, value: string) => {
      store.set(key, String(value));
    }),
    removeItem: vi.fn((key: string) => {
      store.delete(key);
    }),
    clear: vi.fn(() => {
      store.clear();
    })
  };
  Object.defineProperty(globalThis, 'localStorage', {
    value: storage,
    configurable: true
  });
}

async function freshService(): Promise<SaveModule> {
  vi.resetModules();
  localStorage.clear();
  localStorage.setItem('desktop_config', JSON.stringify({
    saveToTableTimeoutMs: 1000,
    saveRetryIntervalMs: 100,
    saveMaxRetries: 2,
    savePendingExpireHours: 24
  }));
  putJsonMock.mockReset();
  postJsonMock.mockReset();
  return await import('./saveToTableService');
}

describe('saveToTableService', () => {
  beforeEach(() => {
    installMemoryLocalStorage();
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-07-03T12:00:00Z'));
  });

  afterEach(async () => {
    const service = await import('./saveToTableService');
    service.cleanupSaveToTableService();
    vi.useRealTimers();
    localStorage.clear();
    putJsonMock.mockReset();
    postJsonMock.mockReset();
  });

  it('saves customer profile fields and asks for table sync when the source row exists', async () => {
    const service = await freshService();
    putJsonMock.mockResolvedValue({ success: true, data: {} });

    const result = await service.saveProfile(input({ hasTableRow: true, sourceTable: 'sheet-a', sourceRowId: 'row-1' }));

    expect(putJsonMock).toHaveBeenCalledWith('/api/v1/customers/18800001111', {
      version: 7,
      fields: { nickname: 'Alice' },
      operator: 'desktop'
    }, 1000);
    expect(result).toMatchObject({ status: 'OK', needRefresh: true, askTableSync: true });
    expect(service.getPendingSave('18800001111')).toBeNull();
  });

  it('returns BUSY for concurrent saves to the same customer', async () => {
    const service = await freshService();
    putJsonMock.mockReturnValue(new Promise(() => undefined));

    const first = service.saveProfile(input());
    await Promise.resolve();
    const second = await service.saveProfile(input());

    expect(second).toMatchObject({ status: 'BUSY', needRefresh: false });
    expect(putJsonMock).toHaveBeenCalledTimes(1);
    void first;
  });

  it('maps profile update business errors without retrying permanent failures', async () => {
    const service = await freshService();

    putJsonMock.mockResolvedValueOnce({ success: false, errorCode: '50-10002' });
    await expect(service.saveProfile(input())).resolves.toMatchObject({ status: 'CONFLICT', needRefresh: true });
    expect(putJsonMock).toHaveBeenCalledTimes(1);

    putJsonMock.mockResolvedValueOnce({ success: false, errorCode: '80-10003' });
    await expect(service.saveProfile(input({ phone: '18800002222' }))).resolves.toMatchObject({ status: 'GIVE_UP', needRefresh: false });
    expect(putJsonMock).toHaveBeenCalledTimes(2);
  });

  it('retries transient save failures, writes a pending record, and gives up after configured attempts', async () => {
    const service = await freshService();
    putJsonMock.mockResolvedValue({ success: false, errorCode: '50-99999' });

    const saving = service.saveProfile(input());
    await vi.advanceTimersByTimeAsync(100);
    await vi.advanceTimersByTimeAsync(100);
    const result = await saving;

    expect(putJsonMock).toHaveBeenCalledTimes(3);
    expect(result).toMatchObject({ status: 'GIVE_UP', needRefresh: false });
    expect(service.getPendingSave('18800001111')).toMatchObject({
      phone: '18800001111',
      editedFields: { nickname: 'Alice' },
      version: 7,
      retryCount: 2
    });
  });

  it('recovers a pending save with the latest profile version and removes it after success', async () => {
    const service = await freshService();
    putJsonMock
      .mockResolvedValueOnce({ success: false, errorCode: '50-99999' })
      .mockResolvedValueOnce({ success: false, errorCode: '50-99999' })
      .mockResolvedValueOnce({ success: false, errorCode: '50-99999' });

    const failedSave = service.saveProfile(input());
    await vi.advanceTimersByTimeAsync(200);
    await failedSave;
    expect(service.getPendingSave('18800001111')).not.toBeNull();

    putJsonMock.mockReset();
    putJsonMock.mockResolvedValue({ success: true, data: {} });

    await expect(service.recoverPendingSave('18800001111', 9)).resolves.toMatchObject({ status: 'OK', needRefresh: true });

    expect(putJsonMock).toHaveBeenCalledWith('/api/v1/customers/18800001111', {
      version: 9,
      fields: { nickname: 'Alice' },
      operator: 'desktop'
    }, 1000);
    expect(service.getPendingSave('18800001111')).toBeNull();
  });

  it('cleans expired and malformed pending saves on startup cleanup', async () => {
    const service = await freshService();
    localStorage.setItem('pending_saves:old', JSON.stringify({
      phone: 'old',
      editedFields: {},
      version: 1,
      createdAt: Date.now() - 25 * 60 * 60 * 1000,
      retryCount: 1
    }));
    localStorage.setItem('pending_saves:bad', '{bad-json');
    localStorage.setItem('pending_saves:fresh', JSON.stringify({
      phone: 'fresh',
      editedFields: { nickname: 'Fresh' },
      version: 2,
      createdAt: Date.now(),
      retryCount: 1
    }));

    service.cleanupExpiredPendingSaves();

    expect(localStorage.getItem('pending_saves:old')).toBeNull();
    expect(localStorage.getItem('pending_saves:bad')).toBeNull();
    expect(localStorage.getItem('pending_saves:fresh')).not.toBeNull();
  });

  it('syncs profile edits to the external table only when source row metadata exists', async () => {
    const service = await freshService();

    await expect(service.syncProfileToTable(input({ hasTableRow: false, sourceTable: null, sourceRowId: null })))
      .resolves.toMatchObject({ status: 'OK', needRefresh: true });
    expect(postJsonMock).not.toHaveBeenCalled();

    postJsonMock.mockResolvedValueOnce({ success: true, data: {} });
    await expect(service.syncProfileToTable(input({ hasTableRow: true, sourceTable: 'sheet-a', sourceRowId: 'row-1' })))
      .resolves.toMatchObject({ status: 'OK', needRefresh: true });
    expect(postJsonMock).toHaveBeenCalledWith('/api/v1/customers/18800001111/save-to-table', {
      sourceTable: 'sheet-a',
      sourceRowId: 'row-1',
      fields: { nickname: 'Alice' }
    }, 12000);

    postJsonMock.mockRejectedValueOnce(new Error('network down'));
    await expect(service.syncProfileToTable(input({ phone: '18800002222', hasTableRow: true, sourceTable: 'sheet-b', sourceRowId: 'row-2' })))
      .resolves.toMatchObject({ status: 'FAILED_RETRYING', needRefresh: true });
  });
});

function input(patch: Partial<SaveProfileInput> = {}): SaveProfileInput {
  return {
    phone: '18800001111',
    editedFields: { nickname: 'Alice' },
    version: 7,
    hasTableRow: false,
    sourceTable: null,
    sourceRowId: null,
    ...patch
  };
}
