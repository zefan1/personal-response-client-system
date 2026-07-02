import { computed, reactive } from 'vue';
import { getJson } from '../../shared/apiClient';
import { loadDesktopConfig } from '../../shared/config';
import type { QuickSearchContentType, QuickSearchFilter, QuickSearchItem } from './types';

const CACHE_KEY = 'quick_search_cache';
const API_TIMEOUT_MS = 5000;
const RETRY_DELAY_MS = 3000;
const CONTENT_TYPE_ORDER: QuickSearchContentType[] = ['TEMPLATE', 'KNOWLEDGE', 'LOCATION', 'IMAGE', 'MINI_PROGRAM'];

export const quickSearchState = reactive({
  visible: false,
  loading: false,
  offline: false,
  query: '',
  filter: 'ALL' as QuickSearchFilter,
  items: [] as QuickSearchItem[],
  selectedIndex: 0,
  toast: '',
  error: ''
});

let debounceTimer: number | null = null;
let autoCloseTimer: number | null = null;

export const filteredQuickSearchItems = computed(() => {
  const config = loadDesktopConfig();
  const keyword = quickSearchState.query.trim().toLowerCase();
  const enabled = quickSearchState.items.filter((item) => item.isEnabled);
  const byFilter = quickSearchState.filter === 'ALL'
    ? enabled
    : enabled.filter((item) => item.leadType === quickSearchState.filter);
  const ranked = keyword ? rankItems(byFilter, keyword) : sortItems(byFilter);
  return ranked.slice(0, config.quicksearchResultLimit);
});

export const groupedQuickSearchItems = computed(() => {
  const groups = new Map<QuickSearchContentType, QuickSearchItem[]>();
  for (const type of CONTENT_TYPE_ORDER) {
    groups.set(type, []);
  }
  filteredQuickSearchItems.value.forEach((item) => {
    groups.get(item.contentType)?.push(item);
  });
  return CONTENT_TYPE_ORDER
    .map((type) => ({ type, items: groups.get(type) ?? [] }))
    .filter((group) => group.items.length > 0);
});

export async function initializeQuickSearch(): Promise<void> {
  readCache();
  if (loadDesktopConfig().quicksearchCacheRefreshOnStartup) {
    await refreshQuickSearchItems();
  }
}

export function showQuickSearch(): void {
  quickSearchState.visible = true;
  quickSearchState.query = '';
  quickSearchState.filter = 'ALL';
  quickSearchState.selectedIndex = 0;
  resetAutoClose();
  if (quickSearchState.items.length === 0) {
    readCache();
    if (quickSearchState.items.length === 0 && navigator.onLine) {
      void refreshQuickSearchItems();
    }
  }
}

export function hideQuickSearch(): void {
  quickSearchState.visible = false;
  clearAutoClose();
}

export function scheduleQuickSearchQuery(query: string): void {
  if (debounceTimer) {
    window.clearTimeout(debounceTimer);
  }
  debounceTimer = window.setTimeout(() => {
    quickSearchState.query = query;
    quickSearchState.selectedIndex = 0;
    resetAutoClose();
  }, loadDesktopConfig().searchInputDebounceMs);
}

export function setQuickSearchFilter(filter: QuickSearchFilter): void {
  quickSearchState.filter = filter;
  quickSearchState.selectedIndex = 0;
  resetAutoClose();
}

export async function refreshQuickSearchItems(): Promise<void> {
  quickSearchState.loading = true;
  quickSearchState.error = '';
  for (let attempt = 0; attempt < 3; attempt += 1) {
    try {
      const response = await getJson<QuickSearchItem[]>('/api/v1/quick-search/items', API_TIMEOUT_MS);
      if (response.success && response.data) {
        quickSearchState.items = sortItems(response.data);
        writeCache(quickSearchState.items);
        quickSearchState.loading = false;
        return;
      }
    } catch {
      // Retry below.
    }
    if (attempt < 2) {
      await delay(RETRY_DELAY_MS);
    }
  }
  quickSearchState.loading = false;
  quickSearchState.error = quickSearchState.items.length ? '' : '内容加载失败，请检查网络后重试';
}

export async function copyQuickSearchItem(item: QuickSearchItem): Promise<void> {
  resetAutoClose();
  if (item.contentType === 'IMAGE') {
    if (!item.imageUrl) {
      quickSearchState.toast = '图片素材缺少链接';
      return;
    }
    const result = await window.desktopBridge.writeClipboardImage(item.imageUrl);
    if (!result.success) {
      quickSearchState.toast = '图片加载失败，请检查网络';
      return;
    }
  } else {
    const result = await window.desktopBridge.writeClipboardText(resolveTemplateVariables(item.content));
    if (!result.success) {
      quickSearchState.toast = '复制失败，请重试';
      return;
    }
  }
  quickSearchState.toast = '已复制';
  window.setTimeout(() => hideQuickSearch(), 120);
}

export function handleQuickSearchConfigRefresh(payload: { configKeys?: string[] }): void {
  if (payload.configKeys?.some((key) => key === 'quick_search' || key.startsWith('quicksearch') || key.startsWith('desktop.quicksearch'))) {
    void refreshQuickSearchItems();
  }
}

export function handleQuickSearchOffline(): void {
  quickSearchState.offline = true;
}

export function handleQuickSearchOnline(): void {
  quickSearchState.offline = false;
  void refreshQuickSearchItems();
}

export function cleanupQuickSearchStore(): void {
  if (debounceTimer) {
    window.clearTimeout(debounceTimer);
    debounceTimer = null;
  }
  clearAutoClose();
}

function rankItems(items: QuickSearchItem[], keyword: string): QuickSearchItem[] {
  const l1 = items.filter((item) => item.shortcutCode.toLowerCase().startsWith(keyword));
  const l2 = items.filter((item) => !l1.includes(item) && item.title.toLowerCase().includes(keyword));
  const l3 = items.filter((item) => !l1.includes(item) && !l2.includes(item) && item.content.toLowerCase().includes(keyword));
  return [...sortItems(l1), ...sortItems(l2), ...sortItems(l3)];
}

function sortItems(items: QuickSearchItem[]): QuickSearchItem[] {
  return [...items].sort((left, right) => {
    const typeDiff = CONTENT_TYPE_ORDER.indexOf(left.contentType) - CONTENT_TYPE_ORDER.indexOf(right.contentType);
    return typeDiff || left.sortOrder - right.sortOrder || left.shortcutCode.localeCompare(right.shortcutCode);
  });
}

function resolveTemplateVariables(content: string): string {
  return content
    .replaceAll('{客户昵称}', '{客户昵称}')
    .replaceAll('{预约时间}', '{预约时间}')
    .replaceAll('{预约门店}', '{预约门店}')
    .replaceAll('{预约项目}', '{预约项目}')
    .replaceAll('{管家名}', '{管家名}')
    .replaceAll('{意向门店}', '{意向门店}')
    .replaceAll('{手机后4位}', '{手机后4位}');
}

function readCache(): void {
  try {
    const raw = localStorage.getItem(CACHE_KEY);
    if (raw) {
      quickSearchState.items = sortItems(JSON.parse(raw) as QuickSearchItem[]);
    }
  } catch {
    quickSearchState.items = [];
  }
}

function writeCache(items: QuickSearchItem[]): void {
  try {
    localStorage.setItem(CACHE_KEY, JSON.stringify(items));
  } catch {
    // Cache write failure does not affect current in-memory search.
  }
}

function resetAutoClose(): void {
  clearAutoClose();
  autoCloseTimer = window.setTimeout(() => hideQuickSearch(), loadDesktopConfig().quicksearchAutoCloseS * 1000);
}

function clearAutoClose(): void {
  if (autoCloseTimer) {
    window.clearTimeout(autoCloseTimer);
    autoCloseTimer = null;
  }
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => {
    window.setTimeout(resolve, ms);
  });
}
