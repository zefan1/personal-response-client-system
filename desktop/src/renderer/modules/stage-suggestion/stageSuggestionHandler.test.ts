import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { CustomerProfileView, ProfileSuggestion } from '../customer-profile/types';

const getJsonMock = vi.fn();
const postJsonMock = vi.fn();
const putJsonMock = vi.fn();

vi.mock('../../shared/apiClient', () => ({
  getJson: getJsonMock,
  postJson: postJsonMock,
  putJson: putJsonMock
}));

type StageModule = typeof import('./stageSuggestionHandler');
type CustomerModule = typeof import('../customer-profile/customerProfileStore');
type EventBusModule = typeof import('../../shared/eventBus');

function installMemoryLocalStorage(): void {
  const store = new Map<string, string>();
  const storage = {
    getItem: vi.fn((key: string) => store.get(key) ?? null),
    setItem: vi.fn((key: string, value: string) => {
      store.set(key, String(value));
    }),
    removeItem: vi.fn((key: string) => {
      store.delete(key);
    }),
    clear: vi.fn(() => {
      store.clear();
    })
  };
  Object.defineProperty(globalThis, 'localStorage', {
    value: storage,
    configurable: true
  });
}

async function freshModules(): Promise<{
  stage: StageModule;
  customer: CustomerModule;
  eventBus: EventBusModule['eventBus'];
}> {
  vi.resetModules();
  localStorage.clear();
  localStorage.setItem('desktop_config', JSON.stringify({
    saveMaxRetries: 1,
    saveRetryIntervalMs: 100,
    saveToTableTimeoutMs: 1000,
    stageSuggestPendingTtlS: 1
  }));
  getJsonMock.mockReset();
  postJsonMock.mockReset();
  putJsonMock.mockReset();
  const stage = await import('./stageSuggestionHandler');
  const customer = await import('../customer-profile/customerProfileStore');
  const { eventBus } = await import('../../shared/eventBus');
  return { stage, customer, eventBus };
}

describe('stageSuggestionHandler', () => {
  beforeEach(() => {
    installMemoryLocalStorage();
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-07-03T12:00:00Z'));
  });

  afterEach(async () => {
    const stage = await import('./stageSuggestionHandler');
    stage.cleanupStageSuggestionHandler();
    vi.useRealTimers();
    localStorage.clear();
    getJsonMock.mockReset();
    postJsonMock.mockReset();
    putJsonMock.mockReset();
  });

  it('emits stage suggestions from loaded current profile and deduplicates repeated suggestions', async () => {
    const { stage, eventBus } = await freshModules();
    const suggestions: unknown[] = [];
    eventBus.on('stage:suggest', (payload) => suggestions.push(payload));

    stage.handleCustomerProfileLoaded(profile('18800001111', 'NEW', [
      suggestion({ suggestionId: 1, currentValue: 'NEW', suggestedValue: 'VISITED' }),
      suggestion({ suggestionId: 1, currentValue: 'NEW', suggestedValue: 'VISITED' }),
      suggestion({ suggestionId: 2, fieldName: 'nickname', suggestedValue: 'Alice' })
    ]));

    expect(suggestions).toEqual([{
      phone: '18800001111',
      suggestionId: 1,
      fromStage: 'NEW',
      toStage: 'VISITED',
      reason: 'stage reason',
      stageOptionMatch: true,
      validOptions: ['NEW', 'VISITED'],
      createdAt: '2026-07-03T12:00:00Z',
      suggestionType: 'STAGE_CHANGE'
    }]);
  });

  it('queues suggestions for non-current customers and flushes them when customer is selected', async () => {
    const { stage, eventBus } = await freshModules();
    const suggestions: unknown[] = [];
    eventBus.on('stage:suggest', (payload) => suggestions.push(payload));
    stage.initializeStageSuggestionHandler();
    stage.handleCustomerProfileLoaded(profile('18800001111', 'NEW'));

    eventBus.emit('PROFILE_SUGGESTIONS', {
      phone: '18800002222',
      suggestions: [suggestion({ suggestionId: 2, suggestedValue: 'VISITED' })]
    });
    expect(suggestions).toHaveLength(0);

    eventBus.emit('customer:selected', { phone: '18800002222' });
    expect(suggestions).toHaveLength(1);
    expect(suggestions[0]).toMatchObject({ phone: '18800002222', suggestionId: 2 });
  });

  it('drops expired pending suggestions instead of emitting stale stage prompts', async () => {
    const { stage, eventBus } = await freshModules();
    const suggestions: unknown[] = [];
    eventBus.on('stage:suggest', (payload) => suggestions.push(payload));
    stage.initializeStageSuggestionHandler();
    stage.handleCustomerProfileLoaded(profile('18800001111', 'NEW'));

    eventBus.emit('PROFILE_SUGGESTIONS', {
      phone: '18800003333',
      suggestions: [suggestion({ suggestionId: 3, suggestedValue: 'VISITED' })]
    });
    vi.setSystemTime(new Date('2026-07-03T12:00:02Z'));
    eventBus.emit('customer:selected', { phone: '18800003333' });

    expect(suggestions).toEqual([]);
  });

  it('confirms a stage suggestion and emits stage updated on success', async () => {
    const { stage, customer, eventBus } = await freshModules();
    const updated: unknown[] = [];
    eventBus.on('stage:updated', (payload) => updated.push(payload));
    customer.customerProfileState.profile = profile('18800001111', 'NEW');
    putJsonMock.mockResolvedValue({ success: true, data: {} });

    await expect(stage.confirmStageSuggestion(suggestion({ suggestedValue: 'VISITED' }))).resolves.toBe(true);

    expect(putJsonMock).toHaveBeenCalledWith('/api/v1/customers/18800001111', {
      version: 7,
      fields: { customerStage: 'VISITED' },
      operator: 'desktop'
    }, 1000);
    expect(updated).toEqual([{ phone: '18800001111', newStage: 'VISITED' }]);
  });

  it('treats conflict refresh as success when refreshed profile already has the target stage', async () => {
    const { stage, customer } = await freshModules();
    customer.customerProfileState.profile = profile('18800001111', 'NEW');
    putJsonMock.mockResolvedValue({ success: false, errorCode: '50-10002' });
    getJsonMock.mockResolvedValue({ success: true, data: profile('18800001111', 'VISITED') });

    await expect(stage.confirmStageSuggestion(suggestion({ suggestedValue: 'VISITED' }))).resolves.toBe(true);

    expect(getJsonMock).toHaveBeenCalledWith('/api/v1/customers/18800001111');
    expect(customer.customerProfileState.profile?.customer.customerStage).toBe('VISITED');
  });

  it('retries transient confirm failures and returns false after configured attempts', async () => {
    const { stage, customer } = await freshModules();
    customer.customerProfileState.profile = profile('18800001111', 'NEW');
    putJsonMock.mockRejectedValue(new Error('network down'));

    const confirmed = stage.confirmStageSuggestion(suggestion({ suggestedValue: 'VISITED' }));
    await vi.advanceTimersByTimeAsync(100);

    await expect(confirmed).resolves.toBe(false);
    expect(putJsonMock).toHaveBeenCalledTimes(2);
  });

  it('ignores stage suggestions through batch resolve and treats backend failure as non-blocking', async () => {
    const { stage, customer } = await freshModules();
    customer.customerProfileState.profile = profile('18800001111', 'NEW');
    postJsonMock.mockResolvedValueOnce({ success: true, data: {} });

    await expect(stage.ignoreStageSuggestion(suggestion({ suggestionId: 8 }))).resolves.toBe(true);

    expect(postJsonMock).toHaveBeenCalledWith('/api/v1/customers/18800001111/suggestions/batch-resolve', {
      action: 'REJECT',
      suggestionIds: [8],
      operator: 'desktop'
    }, 5000);

    postJsonMock.mockRejectedValueOnce(new Error('network down'));
    await expect(stage.ignoreStageSuggestion(suggestion({ suggestionId: 9 }))).resolves.toBe(true);
  });
});

function profile(phone: string, customerStage: string, pendingSuggestions: ProfileSuggestion[] = []): CustomerProfileView {
  return {
    customer: {
      phone,
      nickname: 'Alice',
      leadType: 'TUAN_GOU',
      customerStage,
      version: 7
    },
    pendingSuggestions
  };
}

function suggestion(patch: Partial<ProfileSuggestion>): ProfileSuggestion {
  return {
    suggestionId: 1,
    phone: '18800001111',
    fieldName: 'customerStage',
    currentValue: 'NEW',
    suggestedValue: 'VISITED',
    reason: 'stage reason',
    suggestionType: 'STAGE_CHANGE',
    fromStage: 'NEW',
    toStage: 'VISITED',
    stageOptionMatch: true,
    validOptions: ['NEW', 'VISITED'],
    createdAt: '2026-07-03T12:00:00Z',
    ...patch
  };
}
