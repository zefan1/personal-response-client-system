import { createApp, nextTick, type App } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { QuickSearchItem } from './types';

const mocks = vi.hoisted(() => ({
  getJson: vi.fn(),
  writeClipboardText: vi.fn(),
  writeClipboardImage: vi.fn(),
  onQuickSearchShow: vi.fn(),
  onQuickSearchHide: vi.fn()
}));

vi.mock('../../shared/apiClient', () => ({
  getJson: mocks.getJson
}));

vi.mock('../../shared/desktopBridge', () => ({
  writeClipboardText: mocks.writeClipboardText,
  writeClipboardImage: mocks.writeClipboardImage,
  onQuickSearchShow: mocks.onQuickSearchShow,
  onQuickSearchHide: mocks.onQuickSearchHide
}));

type MountedOverlay = {
  app: App<Element>;
  host: HTMLDivElement;
  eventBus: typeof import('../../shared/eventBus')['eventBus'];
};

function installMemoryLocalStorage(): void {
  const store = new Map<string, string>();
  Object.defineProperty(globalThis, 'localStorage', {
    value: {
      getItem: vi.fn((key: string) => store.get(key) ?? null),
      setItem: vi.fn((key: string, value: string) => store.set(key, String(value))),
      removeItem: vi.fn((key: string) => store.delete(key)),
      clear: vi.fn(() => store.clear())
    },
    configurable: true
  });
}

async function flushUi(): Promise<void> {
  await Promise.resolve();
  await Promise.resolve();
  await nextTick();
}

async function mountOverlay(): Promise<MountedOverlay> {
  vi.resetModules();
  localStorage.clear();
  localStorage.setItem('desktop_config', JSON.stringify({
    quicksearchResultLimit: 5,
    quicksearchAutoCloseS: 60,
    quicksearchCacheRefreshOnStartup: false,
    searchInputDebounceMs: 50
  }));
  mocks.onQuickSearchShow.mockReturnValue(() => undefined);
  mocks.onQuickSearchHide.mockReturnValue(() => undefined);
  const [{ default: QuickSearchOverlay }, { eventBus }] = await Promise.all([
    import('./QuickSearchOverlay.vue'),
    import('../../shared/eventBus')
  ]);
  const host = document.createElement('div');
  document.body.appendChild(host);
  const app = createApp(QuickSearchOverlay);
  app.mount(host);
  await flushUi();
  return { app, host, eventBus };
}

function setValue(element: HTMLInputElement, value: string): void {
  element.value = value;
  element.dispatchEvent(new Event('input', { bubbles: true }));
}

describe('QuickSearchOverlay', () => {
  beforeEach(() => {
    installMemoryLocalStorage();
    vi.useFakeTimers();
    Object.defineProperty(navigator, 'onLine', {
      value: true,
      configurable: true
    });
    mocks.getJson.mockResolvedValue({ success: true, data: items() });
    mocks.writeClipboardText.mockResolvedValue({ success: true });
    mocks.writeClipboardImage.mockResolvedValue({ success: true });
    mocks.onQuickSearchShow.mockReturnValue(() => undefined);
    mocks.onQuickSearchHide.mockReturnValue(() => undefined);
  });

  afterEach(() => {
    document.body.innerHTML = '';
    vi.useRealTimers();
    localStorage.clear();
    Object.values(mocks).forEach((mock) => mock.mockReset());
  });

  it('opens from the event bus, loads API content, filters by typed query and lead type, then copies the clicked result', async () => {
    const { app, host, eventBus } = await mountOverlay();

    eventBus.emit('quick-search:show', {});
    await flushUi();

    expect(mocks.getJson).toHaveBeenCalledWith('/api/v1/quick-search/items', 5000);
    expect(host.querySelector('.quick-search-overlay')).toBeTruthy();
    expect(host.querySelector('.quick-search-box')?.getAttribute('aria-label')).toBe('模板');
    expect(host.textContent).toContain('Opening template');
    expect(host.textContent).toContain('FAQ answer');
    expect(host.textContent).not.toContain('KNOWLEDGE');

    const input = host.querySelector('.quick-search-input') as HTMLInputElement | null;
    expect(input).toBeTruthy();
    setValue(input as HTMLInputElement, 'FAQ');
    await vi.advanceTimersByTimeAsync(50);
    await flushUi();

    expect(host.textContent).not.toContain('Opening template');
    expect(host.textContent).toContain('FAQ answer');

    const filters = [...host.querySelectorAll('.quick-filter button')] as HTMLButtonElement[];
    filters.find((button) => button.textContent?.includes('线索') || button.textContent?.includes('绾跨储'))?.click();
    await flushUi();

    expect(host.textContent).toContain('FAQ answer');
    expect(host.querySelector('.quick-filter .active')?.textContent ?? '').not.toEqual('');

    const copyButton = host.querySelector('.quick-item .primary') as HTMLButtonElement | null;
    copyButton?.click();
    await flushUi();

    expect(mocks.writeClipboardText).toHaveBeenCalledWith('FAQ content');
    expect(host.textContent).toContain('已复制');
    app.unmount();
  });

  it('copies the first filtered result with Enter and stays open until explicitly closed', async () => {
    const { app, host, eventBus } = await mountOverlay();

    eventBus.emit('quick-search:show', {});
    await flushUi();

    const input = host.querySelector('.quick-search-input') as HTMLInputElement | null;
    expect(input).toBeTruthy();
    setValue(input as HTMLInputElement, 'img');
    await vi.advanceTimersByTimeAsync(50);
    expect(host.querySelector('.quick-item-thumb')).toBeTruthy();
    input?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    await flushUi();

    expect(mocks.writeClipboardImage).toHaveBeenCalledWith('https://example.test/image.png');
    expect(host.textContent).toContain('图片已复制');

    await vi.advanceTimersByTimeAsync(3000);
    await flushUi();

    expect(host.querySelector('.quick-search-overlay')).toBeTruthy();
    expect(host.textContent).not.toContain('图片已复制');

    (host.querySelector('.icon-close-button') as HTMLButtonElement | null)?.click();
    await flushUi();
    expect(host.querySelector('.quick-search-overlay')).toBeFalsy();
    app.unmount();
  });

  it('shows offline and refresh failure states, then retries through the rendered retry button', async () => {
    mocks.getJson.mockRejectedValue(new Error('network down'));
    const { app, host, eventBus } = await mountOverlay();

    eventBus.emit('network:offline', {});
    eventBus.emit('quick-search:show', {});
    await flushUi();

    const refresh = vi.advanceTimersByTimeAsync(6000);
    await refresh;
    await flushUi();

    expect(host.textContent).toContain('离线');
    expect(host.textContent).toContain('失败');
    expect(mocks.getJson).toHaveBeenCalledTimes(3);

    mocks.getJson.mockResolvedValue({ success: true, data: [item({ id: 9, title: 'Recovered answer', shortcutCode: 'recovered' })] });
    const retry = host.querySelector('.hint .secondary') as HTMLButtonElement | null;
    retry?.click();
    await flushUi();

    expect(mocks.getJson).toHaveBeenCalledTimes(4);
    expect(host.textContent).toContain('Recovered answer');
    app.unmount();
  });

  it('moves keyboard selection and shows a scoped empty result message', async () => {
    const { app, host, eventBus } = await mountOverlay();

    eventBus.emit('quick-search:show', {});
    await flushUi();

    const input = host.querySelector('.quick-search-input') as HTMLInputElement | null;
    expect(input).toBeTruthy();
    input?.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }));
    await flushUi();
    expect(host.querySelectorAll('.quick-item')[1]?.classList.contains('selected')).toBe(true);

    input?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    await flushUi();
    expect(mocks.writeClipboardText).toHaveBeenCalledWith('FAQ content');

    setValue(input as HTMLInputElement, 'no-match');
    await vi.advanceTimersByTimeAsync(50);
    await flushUi();
    expect(host.textContent).toContain('没有匹配的内容');

    app.unmount();
  });
});

function items(): QuickSearchItem[] {
  return [
    item({ id: 1, title: 'Opening template', shortcutCode: 'open', content: 'Opening content', leadType: 'TUAN_GOU', sortOrder: 1 }),
    item({ id: 2, title: 'FAQ answer', shortcutCode: 'faq', content: 'FAQ content', leadType: 'XIAN_SUO', contentType: 'KNOWLEDGE', sortOrder: 2 }),
    item({ id: 3, title: 'Image asset', shortcutCode: 'img', content: 'Image content', contentType: 'IMAGE', imageUrl: 'https://example.test/image.png', sortOrder: 3 })
  ];
}

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
