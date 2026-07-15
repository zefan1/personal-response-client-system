import { createApp, nextTick, type App } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { CustomerProfileView, CustomerSummary } from './types';

const mocks = vi.hoisted(() => ({
  getJson: vi.fn(),
  postJson: vi.fn(),
  putJson: vi.fn(),
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
  postJson: mocks.postJson,
  putJson: mocks.putJson
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

  it('shows a helpful empty state when search has no result', async () => {
    const { app, host } = await mountPanel();
    mocks.getJson.mockResolvedValueOnce({
      success: true,
      data: { total: 0, customers: [] }
    });

    const input = host.querySelector('.search-row input') as HTMLInputElement | null;
    const searchButton = host.querySelector('.search-row button') as HTMLButtonElement | null;
    setValue(input as HTMLInputElement, 'not-found');
    searchButton?.click();
    await flushUi();

    expect(host.querySelector('.customer-search-state')?.textContent ?? '').toContain('未找到客户');
    expect(host.querySelector('.customer-search-state')?.textContent ?? '').toContain('手机号后四位');
    app.unmount();
  });

  it('opens a profile when a customer is selected from another panel', async () => {
    const { app, host, eventBus } = await mountPanel();
    mocks.getJson.mockResolvedValue({ success: true, data: view('18800001111', 'Workbench Lead') });

    eventBus.emit('customer:selected', {
      phone: '18800001111',
      scene: 'ACTIVE_REPLY',
      leadType: 'TUAN_GOU',
      sourceFrom: 'DASHBOARD'
    });
    await flushUi();

    expect(mocks.getJson).toHaveBeenCalledTimes(1);
    expect(mocks.getJson).toHaveBeenCalledWith('/api/v1/customers/18800001111', 5000, expect.any(AbortSignal));
    expect(host.querySelector('.profile-card')?.textContent ?? '').toContain('Workbench Lead');
    app.unmount();
  });

  it('uses phoneFull from masked search results when opening a profile', async () => {
    const { app, host } = await mountPanel();
    mocks.getJson
      .mockResolvedValueOnce({
        success: true,
        data: {
          total: 2,
          customers: [
            { ...summary('188****1111', 'Masked Lead'), phoneFull: '18800001111' },
            { ...summary('188****2222', 'Other Lead'), phoneFull: '18800002222' }
          ]
        }
      })
      .mockResolvedValueOnce({
        success: true,
        data: view('188****1111', 'Masked Lead', { phoneFull: '18800001111' })
      });

    const input = host.querySelector('.search-row input') as HTMLInputElement | null;
    const searchButton = host.querySelector('.search-row button') as HTMLButtonElement | null;
    setValue(input as HTMLInputElement, 'Lead');
    searchButton?.click();
    await flushUi();

    (host.querySelector('.search-results .result-row') as HTMLButtonElement | null)?.click();
    await flushUi();

    expect(mocks.getJson).toHaveBeenCalledWith('/api/v1/customers/18800001111', 5000, expect.any(AbortSignal));
    expect(host.querySelector('.profile-card')?.textContent ?? '').toContain('Masked Lead');
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
    expect(recognized.at(-1)).toMatchObject({
      source: 'PROFILE_CARD',
      response: { phone: '18800000005' }
    });

    actionButtons[1].click();
    await flushUi();
    expect(host.querySelectorAll('.field-grid input').length).toBeGreaterThan(0);
    expect(host.querySelector('.profile-edit-banner')?.textContent ?? '').toContain('正在编辑档案');
    app.unmount();
  });

  it('keeps the table sync prompt visually prominent after profile saves', async () => {
    const { app, host, eventBus } = await mountPanel();
    mocks.getJson
      .mockResolvedValueOnce({
        success: true,
        data: view('18800001111', 'Sync Lead', {
          customer: {
            phone: '18800001111',
            sourceTable: 'sheet-a',
            sourceRowId: 'row-a',
            nickname: 'Sync Lead',
            intendedStore: 'Store A'
          }
        })
      })
      .mockResolvedValueOnce({
        success: true,
        data: view('18800001111', 'Sync Lead Updated', {
          customer: {
            phone: '18800001111',
            sourceTable: 'sheet-a',
            sourceRowId: 'row-a',
            nickname: 'Sync Lead Updated',
            intendedStore: 'Store B'
          }
        })
      });
    mocks.saveProfile.mockResolvedValueOnce({
      status: 'OK',
      message: '档案已保存。是否同步到企微表格？',
      needRefresh: true,
      askTableSync: true
    });

    eventBus.emit('customer:selected', {
      phone: '18800001111',
      scene: 'ACTIVE_REPLY',
      leadType: 'TUAN_GOU',
      sourceFrom: 'DASHBOARD'
    });
    await flushUi();

    const editButton = [...host.querySelectorAll('.profile-actions button')]
      .find((button) => button.textContent?.includes('编辑档案')) as HTMLButtonElement | undefined;
    editButton?.click();
    await flushUi();
    const fieldInputs = [...host.querySelectorAll('.field-grid input')] as HTMLInputElement[];
    setValue(fieldInputs[1], 'Store B');
    const saveButton = [...host.querySelectorAll('.profile-actions button')]
      .find((button) => button.textContent?.includes('保存')) as HTMLButtonElement | undefined;
    saveButton?.click();
    await flushUi();
    await flushUi();

    const prompt = host.querySelector('.profile-sync-toast');
    expect(prompt?.textContent ?? '').toContain('是否同步到企微表格');
    expect(prompt?.textContent ?? '').toContain('同步');
    expect(prompt?.textContent ?? '').toContain('暂不');
    expect(host.querySelector('.profile-table-sync-status')?.textContent ?? '').toContain('等待同步企微表格');
    app.unmount();
  });

  it('shows table sync retry status in the profile body after confirm failures', async () => {
    const { app, host, eventBus } = await mountPanel();
    mocks.getJson
      .mockResolvedValueOnce({
        success: true,
        data: view('18800001111', 'Retry Lead', {
          customer: {
            phone: '18800001111',
            sourceTable: 'sheet-a',
            sourceRowId: 'row-a',
            nickname: 'Retry Lead',
            intendedStore: 'Store A'
          }
        })
      })
      .mockResolvedValueOnce({
        success: true,
        data: view('18800001111', 'Retry Lead Updated', {
          customer: {
            phone: '18800001111',
            sourceTable: 'sheet-a',
            sourceRowId: 'row-a',
            nickname: 'Retry Lead Updated',
            intendedStore: 'Store B'
          }
        })
      })
      .mockResolvedValueOnce({
        success: true,
        data: view('18800001111', 'Retry Lead Updated', {
          customer: {
            phone: '18800001111',
            sourceTable: 'sheet-a',
            sourceRowId: 'row-a',
            nickname: 'Retry Lead Updated',
            intendedStore: 'Store B'
          }
        })
      });
    mocks.saveProfile.mockResolvedValueOnce({
      status: 'OK',
      message: '档案已保存。是否同步到企微表格？',
      needRefresh: true,
      askTableSync: true
    });
    mocks.syncProfileToTable.mockResolvedValueOnce({
      status: 'FAILED_RETRYING',
      message: '表格同步失败，系统将在后台自动重试',
      needRefresh: true
    });

    eventBus.emit('customer:selected', {
      phone: '18800001111',
      scene: 'ACTIVE_REPLY',
      leadType: 'TUAN_GOU',
      sourceFrom: 'DASHBOARD'
    });
    await flushUi();

    const editButton = [...host.querySelectorAll('.profile-actions button')]
      .find((button) => button.textContent?.includes('编辑档案')) as HTMLButtonElement | undefined;
    editButton?.click();
    await flushUi();
    const fieldInputs = [...host.querySelectorAll('.field-grid input')] as HTMLInputElement[];
    setValue(fieldInputs[1], 'Store B');
    const saveButton = [...host.querySelectorAll('.profile-actions button')]
      .find((button) => button.textContent?.includes('保存')) as HTMLButtonElement | undefined;
    saveButton?.click();
    await flushUi();
    await flushUi();

    (host.querySelector('.profile-sync-toast .primary') as HTMLButtonElement | null)?.click();
    await flushUi();
    await flushUi();

    expect(host.querySelector('.profile-table-sync-status')?.textContent ?? '').toContain('表格同步失败');
    expect(host.querySelector('.profile-table-sync-status')?.textContent ?? '').toContain('无需重复保存');
    app.unmount();
  });

  it('uses a compact refresh icon in the header', async () => {
    const { app, host, eventBus } = await mountPanel();
    mocks.getJson.mockResolvedValue({ success: true, data: view('18800001111', 'Refresh Lead') });

    eventBus.emit('customer:selected', {
      phone: '18800001111',
      scene: 'ACTIVE_REPLY',
      leadType: 'TUAN_GOU',
      sourceFrom: 'DASHBOARD'
    });
    await flushUi();

    const refreshButton = host.querySelector('.panel-header .icon-refresh-button') as HTMLButtonElement | null;
    expect(refreshButton).toBeTruthy();
    expect(refreshButton?.textContent?.trim()).toBe('↻');
    expect(refreshButton?.getAttribute('aria-label')).toBe('刷新客户档案');
    expect(refreshButton?.getAttribute('title')).toBe('刷新');
    expect(refreshButton?.textContent).not.toContain('刷新');
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

function view(phone: string, nickname: string, patch: Partial<CustomerProfileView> & { customer?: Partial<CustomerProfileView['customer']> } = {}): CustomerProfileView {
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
      nextFollowupAt: '2026-07-04T10:00:00',
      ...patch.customer
    },
    phoneFull: patch.phoneFull,
    pendingSuggestions: patch.pendingSuggestions ?? []
  };
}
