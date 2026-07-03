import { createApp, nextTick, type App } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { AbnormalAlertPayload, ChatResponse, ProfileSuggestion, ReplySuggestion } from './types';

const mocks = vi.hoisted(() => ({
  postJson: vi.fn(),
  getAlertsByPhone: vi.fn()
}));

vi.mock('../../shared/apiClient', () => ({
  postJson: mocks.postJson
}));

vi.mock('../abnormal-alert/alertStore', () => ({
  getAlertsByPhone: mocks.getAlertsByPhone
}));

type MountedPanel = {
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

async function mountPanel(): Promise<MountedPanel> {
  vi.resetModules();
  localStorage.clear();
  localStorage.setItem('desktop_config', JSON.stringify({
    requestTotalTimeoutMs: 1000,
    fallbackRetryIntervalMs: 100,
    fallbackMaxRetries: 2
  }));
  const [{ default: ReplySuggestionPanel }, { eventBus }] = await Promise.all([
    import('./ReplySuggestionPanel.vue'),
    import('../../shared/eventBus')
  ]);
  const host = document.createElement('div');
  document.body.appendChild(host);
  const app = createApp(ReplySuggestionPanel);
  app.mount(host);
  await flushUi();
  return { app, host, eventBus };
}

describe('ReplySuggestionPanel', () => {
  beforeEach(() => {
    installMemoryLocalStorage();
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-07-03T12:00:00Z'));
    mocks.getAlertsByPhone.mockReturnValue([]);
    mocks.postJson.mockResolvedValue({ success: true, data: {} });
  });

  afterEach(() => {
    document.body.innerHTML = '';
    vi.useRealTimers();
    localStorage.clear();
    mocks.postJson.mockReset();
    mocks.getAlertsByPhone.mockReset();
  });

  it('renders recognized reply cards and emits DOM-driven reply and help events', async () => {
    const { app, host, eventBus } = await mountPanel();
    const selected: unknown[] = [];
    const helpRequests: unknown[] = [];
    eventBus.on('reply:selected', (payload) => selected.push(payload));
    eventBus.on('help:request', (payload) => helpRequests.push(payload));

    eventBus.emit('recognize:result', { response: response('18800001111', [suggestion('Ask for budget'), suggestion('Offer appointment')]) });
    await flushUi();

    expect(host.querySelectorAll('.reply-card')).toHaveLength(2);
    expect(host.textContent).toContain('Ask for budget');
    expect(host.textContent).toContain('Alice');
    expect(host.textContent).toContain('****1111');

    const copyButton = host.querySelector('.reply-card .primary') as HTMLButtonElement | null;
    copyButton?.click();
    await flushUi();

    expect(selected).toEqual([{
      text: 'Ask for budget',
      direction: 'NEXT_STEP',
      reason: 'reason',
      phone: '****1111',
      isFallback: false
    }]);

    const actionButtons = [...host.querySelectorAll('.reply-actions button')] as HTMLButtonElement[];
    actionButtons.at(-1)?.click();
    await flushUi();

    expect(helpRequests).toEqual([{
      phone: '18800001111',
      clientMessage: '',
      aiSuggestions: [
        { text: 'Ask for budget', direction: 'NEXT_STEP' },
        { text: 'Offer appointment', direction: 'NEXT_STEP' }
      ]
    }]);
    expect(host.textContent).toContain('Ask for budget');
    app.unmount();
  });

  it('renders profile suggestions and resolves them through the batch API from actual buttons', async () => {
    const { app, host, eventBus } = await mountPanel();
    eventBus.emit('recognize:result', { response: response('18800002222', [suggestion('Reply')]) });
    await flushUi();

    eventBus.emit('PROFILE_SUGGESTIONS', {
      phone: '18800002222',
      suggestions: [profileSuggestion(11, 'intentLevel', 'LOW', 'HIGH'), profileSuggestion(12, 'intendedStore', '', 'Store A')]
    });
    await flushUi();

    expect(host.querySelectorAll('.suggestion-item')).toHaveLength(2);
    expect(host.textContent).toContain('intentLevel');
    expect(host.textContent).toContain('intendedStore');

    const confirmAll = host.querySelector('.suggestion-head .secondary') as HTMLButtonElement | null;
    confirmAll?.click();
    await flushUi();

    expect(mocks.postJson).toHaveBeenCalledWith('/api/v1/customers/18800002222/suggestions/batch-resolve', {
      action: 'CONFIRM',
      suggestionIds: [11, 12],
      operator: 'desktop'
    });
    expect(host.textContent).toContain('AI 更新建议 (0)');
    expect(host.querySelector('.suggestion-list')).toBeFalsy();
    app.unmount();
  });

  it('shows current abnormal alerts and clears the banner after an acknowledged alert event', async () => {
    const { app, host, eventBus } = await mountPanel();
    mocks.getAlertsByPhone.mockReturnValue([alert('alert-a', '18800003333', false)]);

    eventBus.emit('recognize:result', { response: response('18800003333', [suggestion('Reply')]) });
    await flushUi();

    expect(host.querySelector('.alert-banner')?.textContent ?? '').toContain('High churn risk');

    eventBus.emit('abnormal:alert', alert('alert-a', '18800003333', true));
    await flushUi();

    expect(host.querySelector('.alert-banner')).toBeFalsy();
    app.unmount();
  });
});

function response(phone: string, suggestions: ReplySuggestion[]): ChatResponse {
  return {
    phone,
    nickname: 'Alice',
    match: { matchType: 'EXACT' },
    skill: { suggestions }
  };
}

function suggestion(text: string): ReplySuggestion {
  return {
    text,
    direction: 'NEXT_STEP',
    reason: 'reason'
  };
}

function profileSuggestion(suggestionId: number, fieldName: string, currentValue: unknown, suggestedValue: unknown): ProfileSuggestion {
  return {
    suggestionId,
    fieldName,
    currentValue,
    suggestedValue,
    reason: 'AI'
  };
}

function alert(alertId: string, phone: string, acknowledged: boolean): AbnormalAlertPayload {
  return {
    alertId,
    phone,
    alertType: 'CHURN_RISK',
    message: 'High churn risk',
    level: 'WARN',
    occurredAt: '2026-07-03T12:00:00Z',
    acknowledged
  };
}
