import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { CustomerProfileView, CustomerSummary, ProfileSuggestion } from './types';
import type { SaveProfileInput, SaveResult } from '../save-to-table/types';

const getJsonMock = vi.fn();
const postJsonMock = vi.fn();
const saveProfileMock = vi.fn();
const syncProfileToTableMock = vi.fn();
const recoverPendingSaveMock = vi.fn();
const getPendingSaveMock = vi.fn();
const cleanupSaveToTableServiceMock = vi.fn();
const cleanupExpiredPendingSavesMock = vi.fn();
const getAlertsByPhoneMock = vi.fn();
const loadAlertsByPhoneMock = vi.fn();
const confirmStageSuggestionMock = vi.fn();
const ignoreStageSuggestionMock = vi.fn();
const handleCustomerProfileLoadedMock = vi.fn();

vi.mock('../../shared/apiClient', () => ({
  getJson: getJsonMock,
  postJson: postJsonMock
}));

vi.mock('../save-to-table/saveToTableService', () => ({
  cleanupExpiredPendingSaves: cleanupExpiredPendingSavesMock,
  cleanupSaveToTableService: cleanupSaveToTableServiceMock,
  getPendingSave: getPendingSaveMock,
  recoverPendingSave: recoverPendingSaveMock,
  saveProfile: saveProfileMock,
  syncProfileToTable: syncProfileToTableMock
}));

vi.mock('../abnormal-alert/alertStore', () => ({
  getAlertsByPhone: getAlertsByPhoneMock,
  loadAlertsByPhone: loadAlertsByPhoneMock
}));

vi.mock('../stage-suggestion/stageSuggestionHandler', () => ({
  confirmStageSuggestion: confirmStageSuggestionMock,
  handleCustomerProfileLoaded: handleCustomerProfileLoadedMock,
  ignoreStageSuggestion: ignoreStageSuggestionMock
}));

type CustomerProfileModule = typeof import('./customerProfileStore');
type EventBusModule = typeof import('../../shared/eventBus');

function installMemoryLocalStorage(): void {
  const store = new Map<string, string>();
  const storage = {
    get length() {
      return store.size;
    },
    key: vi.fn((index: number) => Array.from(store.keys())[index] ?? null),
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

async function freshStore(): Promise<{ profile: CustomerProfileModule; eventBus: EventBusModule['eventBus'] }> {
  vi.resetModules();
  localStorage.clear();
  localStorage.setItem('desktop_config', JSON.stringify({
    searchDebounceMs: 100,
    searchResultLimit: 2,
    customerCacheLimit: 2,
    saveToTableTimeoutMs: 1000,
    saveRetryIntervalMs: 100,
    saveMaxRetries: 1
  }));
  resetMocks();
  const profile = await import('./customerProfileStore');
  const { eventBus } = await import('../../shared/eventBus');
  return { profile, eventBus };
}

describe('customerProfileStore', () => {
  beforeEach(() => {
    installMemoryLocalStorage();
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-07-03T12:00:00Z'));
    Object.defineProperty(navigator, 'onLine', {
      value: true,
      configurable: true
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    localStorage.clear();
    resetMocks();
  });

  it('searches customers, truncates by configured limit, and opens the only exact result', async () => {
    const { profile } = await freshStore();
    getJsonMock.mockResolvedValueOnce({
      success: true,
      data: { total: 3, customers: [summary('18800000001'), summary('18800000002'), summary('18800000003')] }
    });

    profile.searchImmediately(' Alice ');
    await vi.runAllTimersAsync();

    expect(getJsonMock).toHaveBeenCalledWith('/api/v1/customers/search?q=Alice&limit=2', 3000, expect.any(AbortSignal));
    expect(profile.customerProfileState.searchResults.map((entry) => entry.phone)).toEqual(['18800000001', '18800000002']);
    expect(profile.customerProfileState.searchTruncated).toBe(true);

    getJsonMock.mockReset();
    getJsonMock
      .mockResolvedValueOnce({ success: true, data: { total: 1, customers: [summary('18800009999')] } })
      .mockResolvedValueOnce({ success: true, data: view('18800009999') });
    loadAlertsByPhoneMock.mockResolvedValue([]);

    await profile.searchCustomers('single');

    expect(profile.customerProfileState.profile?.customer.phone).toBe('18800009999');
    expect(profile.customerProfileState.searchResults).toEqual([]);
  });

  it('opens profiles, renders cached data first, refreshes alerts, caches online data, and emits customer selection', async () => {
    const { profile, eventBus } = await freshStore();
    const selected: unknown[] = [];
    eventBus.on('customer:selected', (payload) => selected.push(payload));
    localStorage.setItem('customer_cache:18800001111', JSON.stringify({
      phone: '18800001111',
      fullProfile: view('18800001111', { nickname: 'Cached', version: 1 }),
      cachedAt: '2026-07-02T12:00:00Z',
      lastViewedAt: '2026-07-02T12:00:00Z'
    }));
    getAlertsByPhoneMock.mockReturnValue([]);
    loadAlertsByPhoneMock.mockResolvedValue([alert('alert-a')]);
    getJsonMock.mockResolvedValue({ success: true, data: view('18800001111', { nickname: 'Online', version: 2 }) });

    await profile.openProfile('18800001111', 'FOLLOWUP_LIST');

    expect(profile.customerProfileState.profile?.customer.nickname).toBe('Online');
    expect(profile.customerProfileState.fromCache).toBe(false);
    expect(profile.customerProfileState.profileAlert).toMatchObject({ alertId: 'alert-a' });
    expect(handleCustomerProfileLoadedMock).toHaveBeenCalledWith(expect.objectContaining({
      customer: expect.objectContaining({ phone: '18800001111' })
    }));
    expect(selected).toEqual([{
      phone: '18800001111',
      scene: 'CHAT_RECOGNIZE',
      leadType: 'TUAN_GOU',
      sourceFrom: 'FOLLOWUP_LIST'
    }]);
    expect(JSON.parse(localStorage.getItem('customer_cache:18800001111') ?? '{}').fullProfile.customer.nickname).toBe('Online');
  });

  it('falls back to cached profile data when online profile loading fails', async () => {
    const { profile } = await freshStore();
    localStorage.setItem('customer_cache:18800001111', JSON.stringify({
      phone: '18800001111',
      fullProfile: view('18800001111', { nickname: 'Cached' }),
      cachedAt: '2026-07-02T12:00:00Z',
      lastViewedAt: '2026-07-02T12:00:00Z'
    }));
    getJsonMock.mockRejectedValue(new Error('timeout'));

    await profile.openProfile('18800001111', 'PROFILE_CARD');

    expect(profile.customerProfileState.profile?.customer.nickname).toBe('Cached');
    expect(profile.customerProfileState.fromCache).toBe(true);
    expect(profile.customerProfileState.offline).toBe(true);
  });

  it('handles candidate selection and candidate dismissal events', async () => {
    const { profile, eventBus } = await freshStore();
    const selected: unknown[] = [];
    eventBus.on('customer:selected', (payload) => selected.push(payload));
    getJsonMock.mockResolvedValue({ success: true, data: view('18800002222') });
    loadAlertsByPhoneMock.mockResolvedValue([]);

    profile.showCandidates({ matchInfo: { customers: [summary('18800002222'), summary('18800003333')] } });
    expect(profile.customerProfileState.candidateVisible).toBe(true);

    profile.chooseCandidate(summary('18800002222'));
    await vi.runAllTimersAsync();
    expect(profile.customerProfileState.candidateVisible).toBe(false);
    expect(profile.customerProfileState.profile?.customer.phone).toBe('18800002222');

    profile.showCandidates({ candidates: [summary('18800004444')] });
    profile.dismissCandidates();
    expect(selected.at(-1)).toEqual({ phone: '', scene: 'CHAT_RECOGNIZE', sourceFrom: 'CANDIDATE_DISMISSED' });
  });

  it('generates replies from the profile and emits recognize results on success', async () => {
    const { profile, eventBus } = await freshStore();
    const selected: unknown[] = [];
    const recognized: unknown[] = [];
    eventBus.on('customer:selected', (payload) => selected.push(payload));
    eventBus.on('recognize:result', (payload) => recognized.push(payload));
    profile.customerProfileState.profile = view('18800001111');
    postJsonMock.mockResolvedValue({ success: true, data: { phone: '18800001111', skill: { suggestions: [] } } });

    await profile.generateReplyFromProfile();

    expect(postJsonMock).toHaveBeenCalledWith('/api/v1/chat/generate', {
      phone: '18800001111',
      scene: 'ACTIVE_REPLY',
      clientMessage: ''
    });
    expect(selected[0]).toMatchObject({ phone: '18800001111', scene: 'ACTIVE_REPLY', sourceFrom: 'PROFILE_CARD' });
    expect(recognized[0]).toMatchObject({ phone: '18800001111' });
    expect(profile.customerProfileState.generating).toBe(false);
  });

  it('saves changed profile fields, handles table sync prompts, and refreshes after confirm or skip', async () => {
    const { profile } = await freshStore();
    profile.customerProfileState.profile = view('18800001111', {
      nickname: 'Before',
      sourceTable: 'sheet-a',
      sourceRowId: 'row-1',
      version: 7
    });
    profile.enterEditMode();
    profile.customerProfileState.editFields.nickname = 'After';
    const saveResult: SaveResult = { status: 'OK', message: 'saved', needRefresh: true, askTableSync: true };
    saveProfileMock.mockResolvedValue(saveResult);

    await profile.saveProfileEdits();

    expect(saveProfileMock).toHaveBeenCalledWith({
      phone: '18800001111',
      editedFields: { nickname: 'After' },
      version: 7,
      hasTableRow: true,
      sourceTable: 'sheet-a',
      sourceRowId: 'row-1'
    });
    expect(profile.customerProfileState.editMode).toBe(false);
    expect(profile.customerProfileState.tableSyncPrompt).toMatchObject({ phone: '18800001111' });

    getJsonMock.mockResolvedValue({ success: true, data: view('18800001111', { nickname: 'Synced' }) });
    syncProfileToTableMock.mockResolvedValue({ status: 'OK', message: 'synced', needRefresh: true });
    loadAlertsByPhoneMock.mockResolvedValue([]);
    await profile.confirmTableSync();

    expect(syncProfileToTableMock).toHaveBeenCalledWith(expect.objectContaining({ phone: '18800001111' }));
    expect(profile.customerProfileState.profile?.customer.nickname).toBe('Synced');

    profile.customerProfileState.tableSyncPrompt = {
      phone: '18800001111',
      editedFields: { nickname: 'Skip' },
      version: 8,
      hasTableRow: true
    };
    getJsonMock.mockResolvedValueOnce({ success: true, data: view('18800001111', { nickname: 'Skipped' }) });
    await profile.skipTableSync();
    expect(profile.customerProfileState.profile?.customer.nickname).toBe('Skipped');
  });

  it('keeps edit mode on conflicts and shows pending banner for give-up saves', async () => {
    const { profile } = await freshStore();
    profile.customerProfileState.profile = view('18800001111', { nickname: 'Before', version: 7 });
    profile.enterEditMode();
    profile.customerProfileState.editFields.nickname = 'Conflict Edit';
    saveProfileMock.mockResolvedValueOnce({ status: 'CONFLICT', message: 'conflict', needRefresh: true });
    getJsonMock.mockResolvedValueOnce({ success: true, data: view('18800001111', { nickname: 'Server' }) });
    loadAlertsByPhoneMock.mockResolvedValue([]);

    await profile.saveProfileEdits();

    expect(profile.customerProfileState.editMode).toBe(true);
    expect(profile.customerProfileState.editFields.nickname).toBe('Conflict Edit');

    saveProfileMock.mockResolvedValueOnce({ status: 'GIVE_UP', message: 'pending', needRefresh: false });
    await profile.saveProfileEdits();

    expect(profile.customerProfileState.pendingSaveBanner).toBe('pending');
  });

  it('resolves field and stage suggestions through the correct downstream paths', async () => {
    const { profile } = await freshStore();
    const fieldSuggestion = suggestion({ id: 1, fieldName: 'nickname', suggestedValue: 'Alice' });
    const stageSuggestion = suggestion({
      id: undefined,
      suggestionId: 2,
      fieldName: 'customerStage',
      suggestionType: 'STAGE_CHANGE',
      suggestedValue: 'VISITED',
      toStage: 'VISITED'
    });
    profile.customerProfileState.profile = view('18800001111', {}, [fieldSuggestion, stageSuggestion]);
    profile.customerProfileState.suggestions = [
      { ...fieldSuggestion, resolved: false, resolving: false },
      { ...stageSuggestion, resolved: false, resolving: false }
    ];
    confirmStageSuggestionMock.mockResolvedValue(true);
    postJsonMock.mockResolvedValue({ success: true, data: {} });

    await profile.resolveProfileSuggestion('CONFIRM', profile.customerProfileState.suggestions[0]);
    expect(postJsonMock).toHaveBeenCalledWith('/api/v1/customers/18800001111/suggestions/batch-resolve', {
      action: 'CONFIRM',
      suggestionIds: [1],
      operator: 'desktop'
    }, 5000);
    expect(profile.customerProfileState.suggestions.map((item) => item.fieldName)).toEqual(['customerStage']);

    await profile.resolveProfileSuggestion('CONFIRM', profile.customerProfileState.suggestions[0]);
    expect(confirmStageSuggestionMock).toHaveBeenCalledWith(expect.objectContaining({ fieldName: 'customerStage' }));
    expect(profile.customerProfileState.suggestions).toEqual([]);
  });

  it('merges websocket suggestions, stage updates, abnormal alerts, and send confirmation refreshes', async () => {
    const { profile } = await freshStore();
    profile.customerProfileState.profile = view('18800001111', { customerStage: 'NEW' }, [
      suggestion({ fieldName: 'nickname', suggestedValue: 'Alice' })
    ]);
    profile.customerProfileState.suggestions = [suggestion({ fieldName: 'nickname', suggestedValue: 'Alice' })];

    profile.appendProfileSuggestions({
      phone: '18800001111',
      suggestions: [
        suggestion({ fieldName: 'nickname', suggestedValue: 'Alice' }),
        suggestion({ fieldName: 'intentLevel', suggestedValue: 'HIGH' })
      ]
    });
    expect(profile.customerProfileState.suggestions.map((item) => item.fieldName)).toEqual(['nickname', 'intentLevel']);

    profile.appendStageSuggestion({
      phone: '18800001111',
      suggestionId: 3,
      fromStage: 'NEW',
      toStage: 'VISITED',
      reason: 'stage',
      stageOptionMatch: true
    });
    expect(profile.customerProfileState.suggestions.some((item) => item.fieldName === 'customerStage')).toBe(true);

    profile.handleProfileAbnormalAlert(alert('alert-live'));
    expect(profile.customerProfileState.profileAlert?.alertId).toBe('alert-live');
    profile.handleProfileAbnormalAlert({ ...alert('alert-live'), acknowledged: true });
    expect(profile.customerProfileState.profileAlert).toBeNull();

    profile.handleStageUpdated({ phone: '18800001111', newStage: 'VISITED' });
    expect(profile.customerProfileState.profile?.customer.customerStage).toBe('VISITED');
    expect(profile.customerProfileState.suggestions.some((item) => item.fieldName === 'customerStage')).toBe(false);

    getJsonMock.mockResolvedValueOnce({ success: true, data: view('18800001111', { nickname: 'Refreshed' }) });
    loadAlertsByPhoneMock.mockResolvedValue([]);
    profile.handleSendConfirmed({ phone: '****1111' });
    await vi.runAllTimersAsync();
    expect(profile.customerProfileState.profile?.customer.nickname).toBe('Refreshed');
  });
});

function resetMocks(): void {
  getJsonMock.mockReset();
  postJsonMock.mockReset();
  saveProfileMock.mockReset();
  syncProfileToTableMock.mockReset();
  recoverPendingSaveMock.mockReset();
  getPendingSaveMock.mockReset();
  cleanupSaveToTableServiceMock.mockReset();
  cleanupExpiredPendingSavesMock.mockReset();
  getAlertsByPhoneMock.mockReset();
  loadAlertsByPhoneMock.mockReset();
  confirmStageSuggestionMock.mockReset();
  ignoreStageSuggestionMock.mockReset();
  handleCustomerProfileLoadedMock.mockReset();
  getAlertsByPhoneMock.mockReturnValue([]);
  loadAlertsByPhoneMock.mockResolvedValue([]);
  getPendingSaveMock.mockReturnValue(null);
  recoverPendingSaveMock.mockResolvedValue(null);
  saveProfileMock.mockResolvedValue({ status: 'OK', message: 'saved', needRefresh: true });
  syncProfileToTableMock.mockResolvedValue({ status: 'OK', message: 'synced', needRefresh: true });
  confirmStageSuggestionMock.mockResolvedValue(true);
  ignoreStageSuggestionMock.mockResolvedValue(true);
}

function summary(phone: string): CustomerSummary {
  return {
    phone,
    nickname: `Customer ${phone.slice(-4)}`,
    leadType: 'TUAN_GOU',
    assignedKeeper: 'keeper-a'
  };
}

function view(phone: string, patch: Partial<CustomerProfileView['customer']> = {}, pendingSuggestions: ProfileSuggestion[] = []): CustomerProfileView {
  return {
    customer: {
      phone,
      nickname: 'Customer',
      leadType: 'TUAN_GOU',
      assignedKeeper: 'keeper-a',
      customerStage: 'NEW',
      sourceTable: null,
      sourceRowId: null,
      version: 7,
      ...patch
    },
    pendingSuggestions
  };
}

function suggestion(patch: Partial<ProfileSuggestion>): ProfileSuggestion {
  return {
    id: 1,
    fieldName: 'nickname',
    currentValue: 'Old',
    suggestedValue: 'New',
    suggestionType: 'FIELD_UPDATE',
    reason: 'AI suggestion',
    ...patch
  };
}

function alert(alertId: string) {
  return {
    alertId,
    phone: '18800001111',
    alertType: 'CHURN_RISK' as const,
    message: 'risk',
    level: 'WARN' as const,
    occurredAt: '2026-07-03T12:00:00Z',
    acknowledged: false
  };
}
