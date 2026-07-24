import { createApp, nextTick, type App } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  getJson: vi.fn(),
  postJson: vi.fn(),
  writeClipboardText: vi.fn()
}));

vi.mock('../../shared/apiClient', () => ({ getJson: mocks.getJson, postJson: mocks.postJson }));
vi.mock('../../shared/desktopBridge', () => ({ writeClipboardText: mocks.writeClipboardText }));

type Mounted = { app: App<Element>; host: HTMLDivElement };

async function mountOverlay(): Promise<Mounted> {
  vi.resetModules();
  const [{ default: TemplateLibraryOverlay }, store] = await Promise.all([
    import('./TemplateLibraryOverlay.vue'),
    import('./templateLibraryStore')
  ]);
  store.templateLibraryState.visible = true;
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
  });

  afterEach(() => {
    document.body.innerHTML = '';
  });

  it('shows only personal and team tabs and does not expose candidate decisions', async () => {
    const { app, host } = await mountOverlay();

    expect(host.textContent).toContain('我的模板');
    expect(host.textContent).toContain('团队模板');
    expect(host.textContent).not.toMatch(/待审核|退回|拒绝|主管意见/);
    (host.querySelector('[data-testid="template-tab-team"]') as HTMLButtonElement).click();
    await nextTick();
    expect(host.textContent).toContain('Team opening');
    app.unmount();
  });

  it('copies a team template or opens it as a personal draft', async () => {
    const { app, host } = await mountOverlay();
    mocks.writeClipboardText.mockResolvedValue({ success: true });
    mocks.postJson.mockResolvedValue({ success: true, data: { recorded: true }, errorCode: null, message: null });
    (host.querySelector('[data-testid="template-tab-team"]') as HTMLButtonElement).click();
    await nextTick();

    (host.querySelector('[data-testid="copy-team-template-77"]') as HTMLButtonElement).click();
    await Promise.resolve();
    await Promise.resolve();
    expect(mocks.writeClipboardText).toHaveBeenCalledWith('Team body');
    expect(mocks.postJson).toHaveBeenCalledWith('/api/v1/templates/team/77/use', {});

    (host.querySelector('[data-testid="save-team-template-77"]') as HTMLButtonElement).click();
    await nextTick();
    const store = await import('./templateLibraryStore');
    expect(store.templateEditorState.visible).toBe(true);
    expect(store.templateEditorState.draft.body).toBe('Team body');
    app.unmount();
  });
});
