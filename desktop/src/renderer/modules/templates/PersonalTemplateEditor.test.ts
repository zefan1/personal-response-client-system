import { createApp, nextTick, type App } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({ getJson: vi.fn(), postJson: vi.fn(), writeClipboardText: vi.fn() }));
vi.mock('../../shared/apiClient', () => ({ getJson: mocks.getJson, postJson: mocks.postJson }));
vi.mock('../../shared/desktopBridge', () => ({ writeClipboardText: mocks.writeClipboardText }));

type Mounted = { app: App<Element>; host: HTMLDivElement };

async function mountEditor(): Promise<Mounted> {
  vi.resetModules();
  const [{ default: PersonalTemplateEditor }, store] = await Promise.all([
    import('./PersonalTemplateEditor.vue'),
    import('./templateLibraryStore')
  ]);
  store.openPersonalTemplateEditor({
    body: 'AI original response',
    originalAiReply: 'AI original response',
    metadata: { leadType: 'LEAD', labels: ['warm'] },
    sourceReplySessionId: 'reply-1'
  });
  const host = document.createElement('div');
  document.body.append(host);
  const app = createApp(PersonalTemplateEditor);
  app.mount(host);
  await nextTick();
  return { app, host };
}

describe('PersonalTemplateEditor', () => {
  beforeEach(() => {
    mocks.getJson.mockReset();
    mocks.postJson.mockReset();
  });

  afterEach(() => {
    document.body.innerHTML = '';
  });

  it('lets an employee edit an AI reply before saving it as a personal template', async () => {
    const { app, host } = await mountEditor();
    mocks.postJson.mockResolvedValue({
      success: true,
      data: { id: 41, title: 'Opening', body: 'Employee adjusted response', metadata: { labels: ['warm'] }, usageCount: 0 },
      errorCode: null,
      message: null
    });
    const title = host.querySelector('input[name="title"]') as HTMLInputElement;
    const body = host.querySelector('textarea[name="body"]') as HTMLTextAreaElement;
    title.value = 'Opening';
    title.dispatchEvent(new Event('input'));
    body.value = 'Employee adjusted response';
    body.dispatchEvent(new Event('input'));
    (host.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    await Promise.resolve();
    await Promise.resolve();

    expect(mocks.postJson).toHaveBeenCalledWith('/api/v1/templates/personal', expect.objectContaining({
      title: 'Opening',
      body: 'Employee adjusted response',
      originalAiReply: 'AI original response'
    }));
    const store = await import('./templateLibraryStore');
    expect(store.templateEditorState.visible).toBe(false);
    expect(store.templateLibraryState.visible).toBe(true);
    app.unmount();
  });

  it('opens from the AI reply event with the generated text prefilled', async () => {
    vi.resetModules();
    const [{ default: PersonalTemplateEditor }, { eventBus }] = await Promise.all([
      import('./PersonalTemplateEditor.vue'),
      import('../../shared/eventBus')
    ]);
    const host = document.createElement('div');
    document.body.append(host);
    const app = createApp(PersonalTemplateEditor);
    app.mount(host);

    eventBus.emit('template-editor:show', {
      body: 'Generated reply',
      originalAiReply: 'Generated reply',
      sourceReplySessionId: 'reply-2',
      metadata: { leadType: 'LEAD', labels: [] }
    });
    await nextTick();

    expect((host.querySelector('textarea[name="body"]') as HTMLTextAreaElement).value).toBe('Generated reply');
    app.unmount();
  });
});
