import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { NewLeadAlertPayload, NewLeadToastItem } from './types';

const writeClipboardTextMock = vi.fn();

vi.mock('../../shared/desktopBridge', () => ({
  writeClipboardText: writeClipboardTextMock
}));

type NewLeadModule = typeof import('./newLeadToastStore');
type EventBusModule = typeof import('../../shared/eventBus');

function installMemoryLocalStorage(): void {
  const store = new Map<string, string>();
  const storage = {
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

async function freshStore(): Promise<{ store: NewLeadModule; eventBus: EventBusModule['eventBus'] }> {
  vi.resetModules();
  localStorage.clear();
  localStorage.setItem('desktop_config', JSON.stringify({
    toastMaxCount: 2,
    toastNewLeadDismissS: 1
  }));
  writeClipboardTextMock.mockReset();
  const store = await import('./newLeadToastStore');
  const { eventBus } = await import('../../shared/eventBus');
  return { store, eventBus };
}

describe('newLeadToastStore', () => {
  beforeEach(() => {
    installMemoryLocalStorage();
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-07-03T12:00:00Z'));
    vi.spyOn(Math, 'random').mockReturnValue(0.5);
  });

  afterEach(async () => {
    const store = await import('./newLeadToastStore');
    store.cleanupNewLeadToastStore();
    vi.useRealTimers();
    vi.restoreAllMocks();
    localStorage.clear();
    writeClipboardTextMock.mockReset();
  });

  it('ignores reconnect batches and invalid payloads', async () => {
    const { store, eventBus } = await freshStore();
    const events: unknown[] = [];
    eventBus.on('toast:show', (payload) => events.push(payload));

    store.enqueueNewLeadToast(payload({ isReconnectBatch: true }));
    store.enqueueNewLeadToast(payload({ phone: '', nickname: '', phoneFull: '', assignedKeeper: '' }));

    expect(store.newLeadToastState.visibleQueue).toEqual([]);
    expect(events).toEqual([]);
  });

  it('shows valid toasts up to the visible limit and queues the rest', async () => {
    const { store, eventBus } = await freshStore();
    const events: unknown[] = [];
    eventBus.on('toast:show', (payload) => events.push(payload));

    store.enqueueNewLeadToast(payload({ phone: 'lead-1', phoneFull: '18800000001' }));
    store.enqueueNewLeadToast(payload({ phone: 'lead-2', phoneFull: '18800000002' }));
    store.enqueueNewLeadToast(payload({ phone: 'lead-3', phoneFull: '18800000003' }));

    expect(store.newLeadToastState.visibleQueue.map((item) => item.phoneFull)).toEqual(['18800000001', '18800000002']);
    expect(store.newLeadToastState.pendingQueue.map((item) => item.phoneFull)).toEqual(['18800000003']);
    expect(events).toHaveLength(3);
    expect(store.newLeadToastState.visibleQueue[0].id).toContain('18800000001-');
  });

  it('auto dismisses visible toasts and promotes pending toasts', async () => {
    const { store } = await freshStore();
    store.enqueueNewLeadToast(payload({ phoneFull: '18800000001' }));
    store.enqueueNewLeadToast(payload({ phoneFull: '18800000002' }));
    store.enqueueNewLeadToast(payload({ phoneFull: '18800000003' }));

    vi.advanceTimersByTime(1000);

    expect(store.newLeadToastState.visibleQueue.map((item) => item.phoneFull)).toEqual(['18800000003']);
    expect(store.newLeadToastState.pendingQueue).toEqual([]);
  });

  it('copies normalized full phone numbers and reports failures', async () => {
    const { store } = await freshStore();
    writeClipboardTextMock.mockResolvedValueOnce({ success: true });

    await store.copyNewLeadPhone(item({ phoneFull: '188 0000-1111' }));

    expect(writeClipboardTextMock).toHaveBeenCalledWith('18800001111');
    expect(store.newLeadToastState.toast).toBeTruthy();

    writeClipboardTextMock.mockResolvedValueOnce({ success: false, error: 'denied' });
    await store.copyNewLeadPhone(item({ phoneFull: '18800002222' }));
    expect(store.newLeadToastState.toast).toBeTruthy();

    await store.copyNewLeadPhone(item({ phoneFull: '' }));
    expect(writeClipboardTextMock).toHaveBeenCalledTimes(2);
    expect(store.newLeadToastState.toast).toBeTruthy();
  });

  it('generates opening event and removes the toast', async () => {
    const { store, eventBus } = await freshStore();
    const selected: unknown[] = [];
    eventBus.on('customer:selected', (payload) => selected.push(payload));
    store.enqueueNewLeadToast(payload({ phone: 'lead-1', phoneFull: '18800000001', leadType: 'XIAN_SUO' }));
    const toast = store.newLeadToastState.visibleQueue[0];

    store.generateOpening(toast);

    expect(store.newLeadToastState.visibleQueue).toEqual([]);
    expect(selected).toEqual([{
      phone: '18800000001',
      scene: 'OPENING',
      leadType: 'XIAN_SUO',
      sourceFrom: 'NEW_LEAD',
      wsPayload: toast
    }]);
  });

  it('switches to new lead tab and clears pending queue', async () => {
    const { store, eventBus } = await freshStore();
    const tabs: unknown[] = [];
    eventBus.on('followup:switch-tab', (payload) => tabs.push(payload));
    store.newLeadToastState.pendingQueue = [item({ phoneFull: '18800000001' })];

    store.switchToNewLeadTab();

    expect(tabs).toEqual([{ tab: 'NEW_LEAD' }]);
    expect(store.newLeadToastState.pendingQueue).toEqual([]);
  });

  it('cleanup clears visible and pending queues and cancels timers', async () => {
    const { store } = await freshStore();
    store.enqueueNewLeadToast(payload({ phoneFull: '18800000001' }));
    store.enqueueNewLeadToast(payload({ phoneFull: '18800000002' }));
    store.enqueueNewLeadToast(payload({ phoneFull: '18800000003' }));

    store.cleanupNewLeadToastStore();
    vi.advanceTimersByTime(1000);

    expect(store.newLeadToastState.visibleQueue).toEqual([]);
    expect(store.newLeadToastState.pendingQueue).toEqual([]);
  });
});

function payload(patch: Partial<NewLeadAlertPayload> = {}): NewLeadAlertPayload {
  return {
    phone: '188****1111',
    phoneFull: '18800001111',
    nickname: 'New Lead',
    leadType: 'TUAN_GOU',
    priority: 'HIGH',
    sourceTable: 'sheet',
    assignedKeeper: 'keeper',
    arrivedAt: '2026-07-03T12:00:00Z',
    ...patch
  };
}

function item(patch: Partial<NewLeadToastItem> = {}): NewLeadToastItem {
  return {
    ...payload(),
    id: 'toast-id',
    ...patch
  };
}
