import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { QuickSearchItem } from './types';

const getJsonMock = vi.fn();
const writeClipboardTextMock = vi.fn();
const writeClipboardImageMock = vi.fn();

vi.mock('../../shared/apiClient', () => ({
  getJson: getJsonMock
}));

vi.mock('../../shared/desktopBridge', () => ({
  writeClipboardText: writeClipboardTextMock,
  writeClipboardImage: writeClipboardImageMock
}));

type QuickSearchModule = typeof import('./quickSearchStore');

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

async function freshStore(): Promise<QuickSearchModule> {
  vi.resetModules();
  localStorage.clear();
  localStorage.setItem('desktop_config', JSON.stringify({
    quicksearchResultLimit: 3,
    quicksearchAutoCloseS: 3,
    quicksearchCacheRefreshOnStartup: false,
    searchInputDebounceMs: 100
  }));
  getJsonMock.mockReset();
  writeClipboardTextMock.mockReset();
  writeClipboardImageMock.mockReset();
  return await import('./quickSearchStore');
}

describe('quickSearchStore', () => {
  beforeEach(() => {
    installMemoryLocalStorage();
    vi.useFakeTimers();
    Object.defineProperty(navigator, 'onLine', {
      value: true,
      configurable: true
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    localStorage.clear();
    getJsonMock.mockReset();
    writeClipboardTextMock.mockReset();
    writeClipboardImageMock.mockReset();
  });

  it('refreshes from API, sorts enabled items by content type and order, and writes cache', async () => {
    const store = await freshStore();
    getJsonMock.mockResolvedValue({ success: true, data: [
      item({ id: 3, contentType: 'IMAGE', shortcutCode: 'img', sortOrder: 1 }),
      item({ id: 2, contentType: 'TEMPLATE', shortcutCode: 'b', sortOrder: 2 }),
      item({ id: 1, contentType: 'TEMPLATE', shortcutCode: 'a', sortOrder: 1 })
    ], errorCode: null, message: null });

    await store.refreshQuickSearchItems();

    expect(store.quickSearchState.loading).toBe(false);
    expect(store.filteredQuickSearchItems.value.map((entry) => entry.id)).toEqual([1, 2, 3]);
    expect(JSON.parse(localStorage.getItem('quick_search_cache') ?? '[]').map((entry: QuickSearchItem) => entry.id)).toEqual([1, 2, 3]);
  });

  it('filters by lead type and ranks shortcut matches before title and content matches', async () => {
    const store = await freshStore();
    store.quickSearchState.items = [
      item({ id: 1, leadType: 'TUAN_GOU', title: 'Not first', shortcutCode: 'zz', content: 'alpha content', sortOrder: 1 }),
      item({ id: 2, leadType: 'TUAN_GOU', title: 'Alpha title', shortcutCode: 'bb', content: 'body', sortOrder: 2 }),
      item({ id: 3, leadType: 'TUAN_GOU', title: 'Other', shortcutCode: 'al', content: 'body', sortOrder: 3 }),
      item({ id: 4, leadType: 'XIAN_SUO', title: 'Alpha hidden', shortcutCode: 'aa', content: 'body', sortOrder: 1 })
    ];

    store.setQuickSearchFilter('TUAN_GOU');
    store.scheduleQuickSearchQuery('al');
    vi.advanceTimersByTime(100);

    expect(store.filteredQuickSearchItems.value.map((entry) => entry.id)).toEqual([3, 2, 1]);
    expect(store.quickSearchState.selectedIndex).toBe(0);
  });

  it('keeps cached items when refresh fails three times', async () => {
    const store = await freshStore();
    store.quickSearchState.items = [item({ id: 9, shortcutCode: 'cache' })];
    getJsonMock.mockRejectedValue(new Error('network down'));

    const refresh = store.refreshQuickSearchItems();
    await vi.advanceTimersByTimeAsync(6000);
    await refresh;

    expect(getJsonMock).toHaveBeenCalledTimes(3);
    expect(store.quickSearchState.items.map((entry) => entry.id)).toEqual([9]);
    expect(store.quickSearchState.error).toBe('');
  });

  it('shows an error toast when an image item has no image URL', async () => {
    const store = await freshStore();

    await store.copyQuickSearchItem(item({ id: 5, contentType: 'IMAGE', imageUrl: null }));

    expect(writeClipboardImageMock).not.toHaveBeenCalled();
    expect(store.quickSearchState.toast).toContain('图片');
  });

  it('copies text items and keeps the drawer open after success', async () => {
    const store = await freshStore();
    writeClipboardTextMock.mockResolvedValue({ success: true });
    store.quickSearchState.visible = true;

    await store.copyQuickSearchItem(item({ id: 6, contentType: 'TEMPLATE', content: 'hello' }));
    expect(writeClipboardTextMock).toHaveBeenCalledWith('hello');
    expect(store.quickSearchState.toast).toContain('已复制');

    vi.advanceTimersByTime(3000);
    expect(store.quickSearchState.visible).toBe(true);
  });

  it('copies templates with both Chinese and legacy English customer variables resolved', async () => {
    const store = await freshStore();
    const { customerProfileState } = await import('../customer-profile/customerProfileStore');
    customerProfileState.profile = {
      phoneFull: '13800001111',
      customer: {
        nickname: '王女士',
        intendedStore: '万江店',
        intentLevel: 'HIGH',
        appointmentItem: '产后修复'
      }
    } as typeof customerProfileState.profile;
    writeClipboardTextMock.mockResolvedValue({ success: true });

    await store.copyQuickSearchItem(item({
      id: 7,
      contentType: 'TEMPLATE',
      content: '{{客户昵称}} {{nickname}} {{意向门店}} {{intentLevel}} {{预约项目}} {{手机号}}'
    }));

    expect(writeClipboardTextMock).toHaveBeenCalledWith('王女士 王女士 万江店 HIGH 产后修复 13800001111');
    customerProfileState.profile = null;
  });
});

function item(patch: Partial<QuickSearchItem>): QuickSearchItem {
  return {
    id: 1,
    contentType: 'TEMPLATE',
    scene: 'OPENING',
    leadType: 'GENERAL',
    title: 'Default title',
    shortcutCode: 'default',
    content: 'Default content',
    imageUrl: null,
    sortOrder: 1,
    isEnabled: true,
    updatedAt: '2026-07-03T12:00:00',
    ...patch
  };
}
