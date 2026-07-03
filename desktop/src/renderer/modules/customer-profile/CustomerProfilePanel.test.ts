import { createApp, nextTick, type App } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { CustomerProfileView, CustomerSummary } from './types';

const mocks = vi.hoisted(() => ({
  getJson: vi.fn(),
  postJson: vi.fn(),
  cleanupExpiredPendingSaves: vi.fn(),
  cleanupSaveToTableService: vi.fn(),
  getPendingSave: vi.fn(),
  recoverPendingSave: vi.fn(),
  saveProfile: vi.fn(),
  syncProfileToTable: vi.fn(),
  getAlertsByPhone: vi.fn(),
  loadAlertsByPhone: vi.fn(),
  confirmStageSuggestion: vi.fn(),
  handleCustomerProfileLoaded: vi.fn(),
  ignoreStageSuggestion: vi.fn()
}));

vi.mock('../../shared/apiClient', () => ({
  getJson: mocks.getJson,
  postJson: mocks.postJson
}));

vi.mock('../save-to-table/saveToTableService', () => ({
  cleanupExpiredPendingSaves: mocks.cleanupExpiredPendingSaves,
  cleanupSaveToTableService: mocks.cleanupSaveToTableService,
  getPendingSave: mocks.getPendingSave,
  recoverPendingSave: mocks.recoverPendingSave,
  saveProfile: mocks.saveProfile,
  syncProfileToTable: mocks.syncProfileToTable
}));

vi.mock('../abnormal-alert/alertStore', () => ({
  getAlertsByPhone: mocks.getAlertsByPhone,
  loadAlertsByPhone: mocks.loadAlertsByPhone
}));

vi.mock('../stage-suggestion/stageSuggestionHandler', () => ({
  confirmStageSuggestion: mocks.confirmStageSuggestion,
  handleCustomerProfileLoaded: mocks.handleCustomerProfileLoaded,
  ignoreStageSuggestion: mocks.ignoreStageSuggestion
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
      get length() {
        return store.size;
      },
      key: vi.fn((index: number) => Array.from(store.keys())[index] ?? null),
      getItem: vi.fn((key: string) => store.get(key) ?? null),
      setItem: vi.fn((key: string, value: string) => store.set(key, String(value))),
      removeItem: vi.fn((key: string) => store.delete(key)),
      clear: vi.fn(() => store.clear())
    },
    configurable: true
  });
}

async function flushUi() {
  await Promise.resolve();
  await Promise.resolve();
  await nextTick();
}

async function mountPanel(): Promise<MountedPanel> {
  vi.resetModules();
  localStorage.clear();
  localStorage.setItem('desktop_config', JSON.stringify({
    searchDebounceMs: 20,
    searchResultLimit: 5,
    customerCacheLimit: 3,
    saveToTableTimeoutMs: 1000,
    saveRetryIntervalMs: 100,
    saveMaxRetries: 1
  }));
  const [{ default: CustomerProfilePanel }, { eventBus }] = await Promise.all([
    import('./CustomerProfilePanel.vue'),
    import('../../shared/eventBus')
  ]);
  const host = document.createElement('div');
  document.body.appendChild(host);
  const app = createApp(CustomerProfilePanel);
  app.mount(host);
  await flushUi();
  return { app, host, eventBus };
}

function setValue(element: HTMLInputElement | HTMLTextAreaElement, value: string) {
  element.value = value;
  element.dispatchEvent(new Event('input', { bubbles: true }));
  element.dispatchEvent(new Event('change', { bubbles: true }));
}

describe('CustomerProfilePanel', () => {
  beforeEach(() => {
    installMemoryLocalStorage();
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-07-03T12:00:00Z'));
    Object.defineProperty(navigator, 'onLine', {
      value: true,
      configurable: true
    });
    resetMocks();
  });

  afterEach(() => {
    document.body.innerHTML = '';
    vi.useRealTimers();
    localStorage.clear();
    resetMocks();
  });

  it('renders search results and opens a selected profile through the result row', async () => {
    const { app, host } = await mountPanel();
    mocks.getJson
      .mockResolvedValueOnce({
        success: true,
        data: { total: 2, customers: [summary('18800000001', 'Alice'), summary('18800000002', 'Bob')] }
      })
      .mockResolvedValueOnce({ success: true, data: view('18800000001', 'Alice') });

    const input = host.querySelector('.search-row input') as HTMLInputElement | null;
    const searchButton = host.querySelector('.search-row button') as HTMLButtonElement | null;
    expect(input).toBeTruthy();
    expect(searchButton).toBeTruthy();
    setValue(input as HTMLInputElement, 'Alice');
    searchButton?.click();
    await flushUi();

    expect(host.querySelectorAll('.search-results .result-row')).toHaveLength(2);
    expect(host.textContent).toContain('Alice');

    const firstResult = host.querySelector('.search-results .result-row') as HTMLButtonElement | null;
    firstResult?.click();
    await flushUi();

    expect(mocks.getJson).toHaveBeenCalledWith('/api/v1/customers/18800000001', 5000, expect.any(AbortSignal));
    expect(host.querySelector('.profile-card')?.textContent ?? '').toContain('Alice');
    expect(mocks.handleCustomerProfileLoaded).toHaveBeenCalledWith(expect.objectContaining({
      customer: expect.objectContaining({ phone: '18800000001' })
    }));
    app.unmount();
  });

  it('shows candidate modal from recognition events and opens the chosen candidate', async () => {
    const { app, host, eventBus } = await mountPanel();
    mocks.getJson.mockResolvedValue({ success: true, data: view('18800000003', 'Candidate C') });

    eventBus.emit('recognize:multiple', {
      matchInfo: {
        customers: [summary('18800000003', 'Candidate C'), summary('18800000004', 'Candidate D')]
      }
    });
    await flushUi();

    expect(host.querySelector('.candidate-modal')).toBeTruthy();
    expect(host.querySelectorAll('.candidate-modal .result-row')).toHaveLength(2);

    const candidate = host.querySelector('.candidate-modal .result-row') as HTMLButtonElement | null;
    candidate?.click();
    await flushUi();

    expect(host.querySelector('.candidate-modal')).toBeFalsy();
    expect(host.querySelector('.profile-card')?.textContent ?? '').toContain('Candidate C');
    app.unmount();
  });

  it('emits reply generation events and enters edit mode from profile actions', async () => {
    const { app, host, eventBus } = await mountPanel();
    const selected: unknown[] = [];
    const recognized: unknown[] = [];
    eventBus.on('customer:selected', (payload) => selected.push(payload));
    eventBus.on('recognize:result', (payload) => recognized.push(payload));
    mocks.getJson.mockResolvedValue({ success: true, data: view('18800000005', 'Profile E') });
    mocks.postJson.mockResolvedValue({ success: true, data: { phone: '18800000005', skill: { suggestions: [] } } });

    eventBus.emit('recognize:multiple', { candidates: [summary('18800000005', 'Profile E')] });
    await flushUi();
    (host.querySelector('.candidate-modal .result-row') as HTMLButtonElement | null)?.click();
    await flushUi();

    const actionButtons = [...host.querySelectorAll('.profile-actions button')] as HTMLButtonElement[];
    expect(actionButtons.length).toBeGreaterThanOrEqual(2);
    actionButtons[0].click();
    await flushUi();

    expect(mocks.postJson).toHaveBeenCalledWith('/api/v1/chat/generate', {
      phone: '18800000005',
      scene: 'ACTIVE_REPLY',
      clientMessage: ''
    });
    expect(selected.at(-1)).toMatchObject({ phone: '18800000005', scene: 'ACTIVE_REPLY', sourceFrom: 'PROFILE_CARD' });
    expect(recognized.at(-1)).toMatchObject({ phone: '18800000005' });

    actionButtons[1].click();
    await flushUi();
    expect(host.querySelectorAll('.field-grid input').length).toBeGreaterThan(0);
    app.unmount();
  });
});

function resetMocks(): void {
  Object.values(mocks).forEach((mock) => mock.mockReset());
  mocks.getAlertsByPhone.mockReturnValue([]);
  mocks.loadAlertsByPhone.mockResolvedValue([]);
  mocks.getPendingSave.mockReturnValue(null);
  mocks.recoverPendingSave.mockResolvedValue(null);
  mocks.saveProfile.mockResolvedValue({ status: 'OK', message: 'saved', needRefresh: true });
  mocks.syncProfileToTable.mockResolvedValue({ status: 'OK', message: 'synced', needRefresh: true });
  mocks.confirmStageSuggestion.mockResolvedValue(true);
  mocks.ignoreStageSuggestion.mockResolvedValue(true);
}

function summary(phone: string, nickname: string): CustomerSummary {
  return {
    phone,
    nickname,
    leadType: 'TUAN_GOU',
    assignedKeeper: 'keeper-a',
    lastFollowupAt: '2026-07-03T10:00:00',
    intendedStore: 'Store A'
  };
}

function view(phone: string, nickname: string): CustomerProfileView {
  return {
    customer: {
      phone,
      nickname,
      leadType: 'TUAN_GOU',
      assignedKeeper: 'keeper-a',
      customerStage: 'NEW',
      sourceTable: null,
      sourceRowId: null,
      version: 7,
      intendedStore: 'Store A',
      intendedProject: 'Repair',
      intentLevel: 'HIGH',
      followupNotes: 'Call tomorrow',
      nextFollowupAt: '2026-07-04T10:00:00'
    },
    pendingSuggestions: []
  };
}
