import { createApp, nextTick, type App } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { HelpRequestPayload, HelpResponsePayload } from './types';

const mocks = vi.hoisted(() => ({
  postJson: vi.fn()
}));

vi.mock('../../shared/apiClient', () => ({
  postJson: mocks.postJson
}));

type MountedAgent = {
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

async function mountAgent(): Promise<MountedAgent> {
  vi.resetModules();
  localStorage.clear();
  localStorage.setItem('desktop_config', JSON.stringify({
    helpMaxReplies: 3
  }));
  const [{ default: HelpModeAgent }, { eventBus }] = await Promise.all([
    import('./HelpModeAgent.vue'),
    import('../../shared/eventBus')
  ]);
  const host = document.createElement('div');
  document.body.appendChild(host);
  const app = createApp(HelpModeAgent);
  app.mount(host);
  await flushUi();
  return { app, host, eventBus };
}

function setValue(element: HTMLTextAreaElement, value: string): void {
  element.value = value;
  element.dispatchEvent(new Event('input', { bubbles: true }));
  element.dispatchEvent(new Event('change', { bubbles: true }));
}

describe('HelpModeAgent', () => {
  beforeEach(() => {
    installMemoryLocalStorage();
    mocks.postJson.mockResolvedValue({
      success: true,
      data: { helpId: 'help-a', leaderOnline: true, targetLeaderName: 'Leader A' }
    });
  });

  afterEach(() => {
    document.body.innerHTML = '';
    localStorage.clear();
    mocks.postJson.mockReset();
  });

  it('opens the request dialog from help:request and submits keeper context to the backend', async () => {
    const { app, host, eventBus } = await mountAgent();
    const pending: unknown[] = [];
    eventBus.on('help:pending', (payload) => pending.push(payload));

    eventBus.emit('help:request', {
      phone: '18800001111',
      clientMessage: 'customer asks a hard question',
      aiSuggestions: [{ text: 'Try discount', direction: 'NEXT_STEP', reason: 'AI' }]
    });
    await flushUi();

    expect(host.querySelector('.help-dialog')).toBeTruthy();
    expect(host.textContent).toContain('customer asks a hard question');
    expect(host.textContent).toContain('Try discount');

    const note = host.querySelector('.help-dialog textarea') as HTMLTextAreaElement | null;
    setValue(note as HTMLTextAreaElement, 'Need leader approval');
    (host.querySelector('.help-dialog .primary') as HTMLButtonElement | null)?.click();
    await flushUi();

    expect(mocks.postJson).toHaveBeenCalledWith('/api/v1/help/request', {
      phone: '18800001111',
      clientMessage: 'customer asks a hard question',
      aiSuggestions: [{ text: 'Try discount', direction: 'NEXT_STEP', reason: 'AI' }],
      keeperNote: 'Need leader approval'
    }, 5000);
    expect(pending).toEqual([{ helpId: 'help-a', phone: '18800001111' }]);
    expect(host.querySelector('.help-dialog')).toBeFalsy();
    expect(host.querySelector('.help-status-notice')?.textContent ?? '').toContain('已向Leader A发送求助');
    expect(host.querySelector('.help-status-notice')?.textContent ?? '').toContain('收到回复后');

    (host.querySelector('.help-status-notice .icon-close-button') as HTMLButtonElement | null)?.click();
    await flushUi();
    expect(host.querySelector('.help-status-notice')).toBeFalsy();
    app.unmount();
  });

  it('renders leader help queue, creates draft replies from suggestions, edits them, and resolves the request', async () => {
    const { app, host, eventBus } = await mountAgent();
    mocks.postJson.mockResolvedValue({ success: true, data: {} });

    eventBus.emit('HELP_REQUEST', requestPayload());
    await flushUi();

    expect(host.querySelector('.help-leader-panel')).toBeTruthy();
    expect(host.textContent).toContain('Operator A');
    expect(host.textContent).toContain('Need human judgment');

    const suggestionActions = [...host.querySelectorAll('.help-suggestions button')] as HTMLButtonElement[];
    suggestionActions[0].click();
    suggestionActions[1].click();
    await flushUi();

    expect(host.querySelectorAll('.help-drafts article')).toHaveLength(2);
    const editable = [...host.querySelectorAll('.help-drafts textarea')] as HTMLTextAreaElement[];
    setValue(editable[1], 'Modified leader reply');

    (host.querySelector('.help-detail .primary') as HTMLButtonElement | null)?.click();
    await flushUi();

    expect(mocks.postJson).toHaveBeenCalledWith('/api/v1/help/resolve', {
      helpId: 'help-a',
      helperReplies: [
        { text: 'Use this', direction: '组长确认', source: 'CONFIRMED' },
        { text: 'Modified leader reply', direction: '组长修改', source: 'MODIFIED' }
      ]
    }, 5000);
    expect(host.querySelector('.help-leader-panel')).toBeFalsy();
    app.unmount();
  });

  it('shows received leader replies and emits reply:selected from the rendered copy button', async () => {
    const { app, host, eventBus } = await mountAgent();
    const selected: unknown[] = [];
    eventBus.on('reply:selected', (payload) => selected.push(payload));

    eventBus.emit('HELP_RESPONSE', responsePayload());
    await flushUi();

    expect(host.querySelector('.help-response-panel')).toBeTruthy();
    expect(host.querySelector('.help-status-notice')?.textContent ?? '').toContain('组长已回复你的求助');
    (host.querySelector('.help-alert.green') as HTMLButtonElement | null)?.click();
    await flushUi();

    expect(host.querySelectorAll('.reply-card')).toHaveLength(1);
    (host.querySelector('.reply-card .primary') as HTMLButtonElement | null)?.click();
    await flushUi();

    expect(selected).toEqual([{
      text: 'Leader says use this',
      direction: 'NEXT_STEP',
      reason: 'HELP_REPLY',
      phone: '18800001111',
      isFallback: false
    }]);
    expect(host.querySelector('.help-status-notice')?.textContent ?? '').toContain('组长回复已复制');
    app.unmount();
  });
});

function requestPayload(): HelpRequestPayload {
  return {
    helpId: 'help-a',
    requesterName: 'Operator A',
    phone: '18800001111',
    clientMessage: 'Need human judgment',
    keeperNote: 'VIP customer',
    aiSuggestions: [{ text: 'Use this', direction: 'NEXT_STEP', reason: 'AI' }]
  };
}

function responsePayload(): HelpResponsePayload {
  return {
    helpId: 'help-a',
    phone: '18800001111',
    helperName: 'Leader A',
    helperReplies: [{ text: 'Leader says use this', direction: 'NEXT_STEP', source: 'ORIGINAL' }]
  };
}
