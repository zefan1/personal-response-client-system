import { createApp, nextTick, type App } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ProfileSuggestion, ReplySelectedPayload } from './types';

const mocks = vi.hoisted(() => ({
  postJson: vi.fn(),
  writeClipboardText: vi.fn()
}));

vi.mock('../../shared/apiClient', () => ({
  postJson: mocks.postJson
}));

vi.mock('../../shared/desktopBridge', () => ({
  writeClipboardText: mocks.writeClipboardText
}));

type MountedAgent = {
  app: App<Element>;
  host: HTMLDivElement;
  eventBus: typeof import('../../shared/eventBus')['eventBus'];
};

async function flushUi(): Promise<void> {
  await Promise.resolve();
  await Promise.resolve();
  await nextTick();
}

async function mountAgent(): Promise<MountedAgent> {
  vi.resetModules();
  const [{ default: CopyBackfillAgent }, { eventBus }] = await Promise.all([
    import('./CopyBackfillAgent.vue'),
    import('../../shared/eventBus')
  ]);
  const host = document.createElement('div');
  document.body.appendChild(host);
  const app = createApp(CopyBackfillAgent);
  app.mount(host);
  await flushUi();
  return { app, host, eventBus };
}

describe('CopyBackfillAgent', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-07-03T12:00:00Z'));
    mocks.writeClipboardText.mockResolvedValue({ success: true });
    mocks.postJson.mockResolvedValue({ success: true, data: {} });
  });

  afterEach(() => {
    document.body.innerHTML = '';
    vi.useRealTimers();
    Object.values(mocks).forEach((mock) => mock.mockReset());
  });

  it('copies selected replies and posts send-confirm from the event-bus listener', async () => {
    const { app, eventBus } = await mountAgent();

    eventBus.emit('reply:selected', reply({ text: 'Use this reply', direction: 'NEXT_STEP' }));
    await flushUi();
    await vi.runAllTimersAsync();

    expect(mocks.writeClipboardText).toHaveBeenCalledWith('Use this reply');
    expect(mocks.postJson).toHaveBeenCalledWith('/api/v1/chat/send-confirm', {
      phone: '18800001111',
      conversationSummary: '',
      isNewCustomer: false,
      sentText: 'Use this reply',
      selectedDirection: 'NEXT_STEP'
    }, undefined, expect.any(AbortSignal));
    app.unmount();
  });

  it('renders profile suggestion toast and resolves a single suggestion from actual buttons', async () => {
    const { app, host, eventBus } = await mountAgent();

    eventBus.emit('suggestion:show', {
      phone: '18800001111',
      suggestions: [suggestion(1, 'nickname'), suggestion(2, 'intentLevel')]
    });
    await flushUi();

    expect(host.querySelector('.suggestion-toast')).toBeTruthy();
    expect(host.querySelectorAll('.suggestion-item')).toHaveLength(2);
    expect(host.textContent).toContain('nickname');
    expect(host.textContent).toContain('intentLevel');

    const firstConfirm = host.querySelector('.suggestion-item .suggestion-actions .secondary') as HTMLButtonElement | null;
    firstConfirm?.click();
    await flushUi();

    expect(mocks.postJson).toHaveBeenCalledWith('/api/v1/customers/18800001111/suggestions/batch-resolve', {
      action: 'CONFIRM',
      suggestionIds: [1],
      operator: 'desktop'
    });
    expect(host.querySelector('.suggestion-toast')).toBeTruthy();
    app.unmount();
  });

  it('supports collapse, reopen, batch reject, and recognize-start auto collapse through DOM and events', async () => {
    const { app, host, eventBus } = await mountAgent();

    eventBus.emit('suggestion:show', {
      phone: '18800002222',
      suggestions: [suggestion(11, 'stage'), suggestion(12, 'store')]
    });
    await flushUi();

    const close = host.querySelector('.suggestion-toast-header .secondary') as HTMLButtonElement | null;
    close?.click();
    await flushUi();

    expect(host.querySelector('.suggestion-toast')).toBeFalsy();
    expect(host.querySelector('.suggestion-reopen')).toBeTruthy();

    (host.querySelector('.suggestion-reopen') as HTMLButtonElement | null)?.click();
    await flushUi();
    expect(host.querySelector('.suggestion-toast')).toBeTruthy();

    const rejectAll = [...host.querySelectorAll('.reply-actions .secondary')] as HTMLButtonElement[];
    rejectAll.at(-1)?.click();
    await flushUi();

    expect(mocks.postJson).toHaveBeenCalledWith('/api/v1/customers/18800002222/suggestions/batch-resolve', {
      action: 'REJECT',
      suggestionIds: [11, 12],
      operator: 'desktop'
    });
    expect(host.querySelector('.suggestion-toast')).toBeFalsy();

    eventBus.emit('suggestion:show', {
      phone: '18800002222',
      suggestions: [suggestion(13, 'source')]
    });
    await flushUi();
    expect(host.querySelector('.suggestion-toast')).toBeTruthy();

    eventBus.emit('recognize:start', {});
    await flushUi();

    expect(host.querySelector('.suggestion-toast')).toBeFalsy();
    expect(host.querySelector('.suggestion-reopen')).toBeTruthy();
    app.unmount();
  });
});

function reply(patch: Partial<ReplySelectedPayload>): ReplySelectedPayload {
  return {
    text: 'hello',
    direction: 'NEXT_STEP',
    reason: 'reason',
    phone: '18800001111',
    isFallback: false,
    ...patch
  };
}

function suggestion(suggestionId: number, fieldName: string): ProfileSuggestion {
  return {
    suggestionId,
    fieldName,
    currentValue: 'Old',
    suggestedValue: `New ${suggestionId}`,
    reason: 'AI'
  };
}
