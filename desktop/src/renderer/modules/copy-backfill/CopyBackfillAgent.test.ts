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

  it('copies selected replies, posts send-confirm, and shows success feedback from the event-bus listener', async () => {
    const { app, host, eventBus } = await mountAgent();
    const confirmed: unknown[] = [];
    eventBus.on('reply:send-confirmed', (payload) => confirmed.push(payload));

    eventBus.emit('reply:selected', reply({ text: 'Use this reply', direction: 'NEXT_STEP' }));
    await flushUi();
    await vi.runAllTimersAsync();
    await flushUi();

    expect(mocks.writeClipboardText).toHaveBeenCalledWith('Use this reply');
    expect(mocks.postJson).toHaveBeenCalledWith('/api/v1/chat/send-confirm', {
      phone: '18800001111',
      conversationSummary: '',
      isNewCustomer: false,
      sentText: 'Use this reply',
      selectedDirection: 'NEXT_STEP'
    }, undefined, expect.any(AbortSignal));
    expect(confirmed).toEqual([{ phone: '18800001111' }]);
    expect(host.textContent).toContain('已复制并记录发送');
    app.unmount();
  });

  it('keeps profile suggestions out of the global floating layer', async () => {
    const { app, host, eventBus } = await mountAgent();

    eventBus.emit('suggestion:show', {
      phone: '18800001111',
      suggestions: [suggestion(1, 'nickname'), suggestion(2, 'intentLevel')]
    });
    await flushUi();

    expect(host.querySelector('.suggestion-toast')).toBeFalsy();
    expect(host.querySelector('.suggestion-reopen')).toBeFalsy();
    expect(host.textContent).not.toContain('AI 更新建议');
    app.unmount();
  });

  it('collapses pending suggestions on recognize-start without rendering a fixed reopen button', async () => {
    const { app, host, eventBus } = await mountAgent();

    eventBus.emit('suggestion:show', {
      phone: '18800002222',
      suggestions: [suggestion(11, 'stage'), suggestion(12, 'store')]
    });
    await flushUi();

    expect(host.querySelector('.suggestion-toast')).toBeFalsy();

    eventBus.emit('recognize:start', {});
    await flushUi();

    expect(host.querySelector('.suggestion-toast')).toBeFalsy();
    expect(host.querySelector('.suggestion-reopen')).toBeFalsy();
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
