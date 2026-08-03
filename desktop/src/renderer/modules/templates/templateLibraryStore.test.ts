import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  getJson: vi.fn(),
  postJson: vi.fn(),
  writeClipboardText: vi.fn()
}));

vi.mock('../../shared/apiClient', () => ({
  getJson: mocks.getJson,
  postJson: mocks.postJson
}));

vi.mock('../../shared/desktopBridge', () => ({
  writeClipboardText: mocks.writeClipboardText
}));

function installMemoryLocalStorage(): void {
  const data = new Map<string, string>();
  Object.defineProperty(globalThis, 'localStorage', {
    value: {
      getItem: vi.fn((key: string) => data.get(key) ?? null),
      setItem: vi.fn((key: string, value: string) => data.set(key, String(value))),
      removeItem: vi.fn((key: string) => data.delete(key)),
      clear: vi.fn(() => data.clear())
    },
    configurable: true
  });
}

describe('templateLibraryStore', () => {
  beforeEach(() => {
    installMemoryLocalStorage();
    mocks.getJson.mockReset();
    mocks.postJson.mockReset();
    mocks.writeClipboardText.mockReset();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('loads personal, published team, and shortcut speech without exposing candidate decisions', async () => {
    const store = await import('./templateLibraryStore');
    mocks.getJson.mockImplementation((path: string) => Promise.resolve({
      success: true,
      data: path.endsWith('/personal')
        ? [{ id: 41, title: 'My opening', body: 'Personal body', metadata: { labels: ['warm'] }, usageCount: 2 }]
        : path.endsWith('/team')
          ? [{ quickSearchItemId: 77, promotionCandidateId: 42, title: 'Team opening', body: 'Team body', shortcutCode: 'TM42', metadata: { labels: ['shared'] } }]
          : [{ id: 99, title: 'Shortcut opening', content: 'Shortcut body', contentType: 'TEMPLATE', shortcutCode: 'KS01', leadType: 'GENERAL', sortOrder: 1, isEnabled: true }],
      errorCode: null,
      message: null
    }));

    await store.openTemplateLibrary();

    expect(store.templateLibraryState.visible).toBe(true);
    expect(store.templateLibraryState.personal).toHaveLength(1);
    expect(store.templateLibraryState.team).toHaveLength(1);
    expect(store.templateLibraryState.shortcuts).toHaveLength(1);
    expect(JSON.stringify(store.templateLibraryState)).not.toMatch(/CANDIDATE|NOT_PUBLISHED|decidedBy/);
  });

  it('copies a team template and records template usage without sending confirmation', async () => {
    const store = await import('./templateLibraryStore');
    mocks.writeClipboardText.mockResolvedValue({ success: true });
    mocks.postJson.mockResolvedValue({ success: true, data: { recorded: true, source: 'TEAM' }, errorCode: null, message: null });
    const template = {
      quickSearchItemId: 77,
      promotionCandidateId: 42,
      title: 'Team opening',
      body: 'Team body',
      shortcutCode: 'TM42',
      metadata: { labels: ['shared'] }
    };

    await store.copyTeamTemplate(template);

    expect(mocks.writeClipboardText).toHaveBeenCalledWith('Team body');
    expect(mocks.postJson).toHaveBeenCalledWith('/api/v1/templates/team/77/use', {});
    expect(mocks.postJson).not.toHaveBeenCalledWith('/api/v1/chat/send-confirm', expect.anything());
  });
});
