import { createApp, nextTick, type App } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  getJson: vi.fn(),
  postJson: vi.fn(),
  writeClipboardText: vi.fn(),
  onQuickSearchShow: vi.fn(),
  onQuickSearchHide: vi.fn()
}));

vi.mock('../../shared/apiClient', () => ({ getJson: mocks.getJson, postJson: mocks.postJson }));
vi.mock('../../shared/desktopBridge', () => ({
  writeClipboardText: mocks.writeClipboardText,
  onQuickSearchShow: mocks.onQuickSearchShow,
  onQuickSearchHide: mocks.onQuickSearchHide
}));

type Mounted = { app: App<Element>; host: HTMLDivElement };

async function mountOverlay(visible = true): Promise<Mounted> {
  vi.resetModules();
  const [{ default: TemplateLibraryOverlay }, store] = await Promise.all([
    import('./TemplateLibraryOverlay.vue'),
    import('./templateLibraryStore')
  ]);
  store.templateLibraryState.visible = visible;
  store.templateLibraryState.tab = 'PERSONAL';
  store.templateLibraryState.personal = [{
    id: 41,
    title: 'My opening',
    body: 'Personal body',
    metadata: { labels: ['warm'] },
    usageCount: 2
  }];
  store.templateLibraryState.team = [{
    quickSearchItemId: 77,
    promotionCandidateId: 42,
    title: 'Team opening',
    body: 'Team body',
    shortcutCode: 'TM42',
    metadata: { labels: ['shared'] }
  }];
  store.templateLibraryState.shortcuts = [
    { id: 88, title: 'Shared text', content: 'Text body', contentType: 'TEMPLATE', shortcutCode: 'TX01', leadType: 'GENERAL', sortOrder: 1, isEnabled: true },
    { id: 89, title: 'Shared image', content: 'Image body', contentType: 'IMAGE', imageUrl: 'https://example.com/image.png', shortcutCode: 'IM01', leadType: 'GENERAL', sortOrder: 2, isEnabled: true },
    { id: 90, title: '西平定位', content: '这是西平店的地址', contentType: 'LOCATION', imageUrl: 'https://surl.amap.com/shop', shortcutCode: 'xp', leadType: 'GENERAL', sortOrder: 3, isEnabled: true }
  ];
  const host = document.createElement('div');
  document.body.append(host);
  const app = createApp(TemplateLibraryOverlay);
  app.mount(host);
  await nextTick();
  return { app, host };
}

describe('TemplateLibraryOverlay', () => {
  beforeEach(() => {
    mocks.getJson.mockReset();
    mocks.postJson.mockReset();
    mocks.writeClipboardText.mockReset();
    mocks.onQuickSearchShow.mockReset();
    mocks.onQuickSearchHide.mockReset();
    mocks.onQuickSearchShow.mockReturnValue(() => undefined);
    mocks.onQuickSearchHide.mockReturnValue(() => undefined);
  });

  afterEach(() => {
    document.body.innerHTML = '';
  });

  it('shows ownership filters and does not expose candidate decisions', async () => {
    const { app, host } = await mountOverlay();

    expect(host.textContent).toContain('全部');
    expect(host.textContent).toContain('团队');
    expect(host.textContent).toContain('我的');
    expect(host.textContent).not.toMatch(/待审核|退回|拒绝|主管意见/);
    expect(host.textContent).toContain('Team opening');
    app.unmount();
  });

  it('renders personal and team speech in one scrollable flow without the old template tabs', async () => {
    const { app, host } = await mountOverlay();

    expect(host.querySelector('[data-testid="template-library-flow"]')).toBeTruthy();
    expect(host.querySelector('.template-library-tabs')).toBeFalsy();
    expect(host.textContent).toContain('My opening');
    expect(host.textContent).toContain('Team opening');
    expect(host.querySelector('[data-testid="copy-personal-template-41"]')).toBeTruthy();
    expect(host.querySelector('[data-testid="copy-team-template-77"]')).toBeTruthy();

    app.unmount();
  });

  it('filters the same speech flow between text and image content', async () => {
    const { app, host } = await mountOverlay();

    expect(host.textContent).toContain('文本');
    expect(host.textContent).toContain('图片');
    const imageFilter = [...host.querySelectorAll('.template-library-filters button')]
      .find((button) => button.textContent?.includes('图片')) as HTMLButtonElement;
    imageFilter.click();
    await nextTick();

    expect(host.textContent).toContain('Shared image');
    expect(host.textContent).not.toContain('Shared text');
    expect(host.textContent).not.toContain('Team opening');

    imageFilter.click();
    await nextTick();

    expect(host.textContent).toContain('Shared image');
    expect(host.textContent).toContain('Shared text');
    expect(host.textContent).toContain('Team opening');
    app.unmount();
  });

  it('opens the unified library from the legacy global quick-search shortcut event', async () => {
    let openFromShortcut: (() => void) | undefined;
    mocks.onQuickSearchShow.mockImplementation((callback: () => void) => {
      openFromShortcut = callback;
      return () => undefined;
    });
    mocks.onQuickSearchHide.mockReturnValue(() => undefined);
    const { app } = await mountOverlay(false);
    const store = await import('./templateLibraryStore');

    openFromShortcut?.();

    expect(store.templateLibraryState.visible).toBe(true);
    app.unmount();
  });

  it('shows a location entry as text and exposes its saved map link', async () => {
    const { app, host } = await mountOverlay();
    const locationArticle = [...host.querySelectorAll('.template-library-item')]
      .find((article) => article.querySelector('h3')?.textContent === '西平定位');

    expect(locationArticle).toBeTruthy();
    expect(locationArticle?.querySelector('.template-library-image')).toBeNull();
    expect(locationArticle?.querySelector('.template-library-entry-link')?.textContent).toContain(
      'https://surl.amap.com/shop'
    );
    app.unmount();
  });

  it('copies a team template or opens it as a personal draft', async () => {
    const { app, host } = await mountOverlay();
    mocks.writeClipboardText.mockResolvedValue({ success: true });
    mocks.postJson.mockResolvedValue({ success: true, data: { recorded: true }, errorCode: null, message: null });

    (host.querySelector('[data-testid="copy-team-template-77"]') as HTMLButtonElement).click();
    await Promise.resolve();
    await Promise.resolve();
    expect(mocks.writeClipboardText).toHaveBeenCalledWith('Team body');
    expect(mocks.postJson).toHaveBeenCalledWith('/api/v1/templates/team/77/use', {});

    (host.querySelector('[data-testid="edit-team-template-77"]') as HTMLButtonElement).click();
    await nextTick();
    const store = await import('./templateLibraryStore');
    expect(store.templateEditorState.visible).toBe(true);
    expect(store.templateEditorState.draft.body).toBe('Team body');
    app.unmount();
  });
});
