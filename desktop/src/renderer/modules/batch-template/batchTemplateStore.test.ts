import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { CustomerProfileView } from '../customer-profile/types';
import type { QuickSearchItem } from '../quick-search/types';

const getJsonMock = vi.fn();
const postJsonMock = vi.fn();
const writeClipboardTextMock = vi.fn();

vi.mock('../../shared/apiClient', () => ({
  getJson: getJsonMock,
  postJson: postJsonMock
}));

vi.mock('../../shared/desktopBridge', () => ({
  writeClipboardText: writeClipboardTextMock
}));

type BatchModule = typeof import('./batchTemplateStore');

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

async function freshStore(): Promise<BatchModule> {
  vi.resetModules();
  localStorage.clear();
  localStorage.setItem('desktop_config', JSON.stringify({
    batchMaxCustomers: 3,
    batchCustomerBatchTimeoutMs: 1000
  }));
  getJsonMock.mockReset();
  postJsonMock.mockReset();
  writeClipboardTextMock.mockReset();
  return await import('./batchTemplateStore');
}

describe('batchTemplateStore', () => {
  beforeEach(() => {
    installMemoryLocalStorage();
    vi.setSystemTime(new Date('2026-07-03T12:00:00Z'));
  });

  afterEach(() => {
    vi.useRealTimers();
    localStorage.clear();
    getJsonMock.mockReset();
    postJsonMock.mockReset();
    writeClipboardTextMock.mockReset();
  });

  it('starts a batch flow with unique phones, loads templates and customers, then auto-selects a template', async () => {
    const store = await freshStore();
    getJsonMock.mockResolvedValueOnce({ success: true, data: [template({ id: 1, title: 'Template A' })] });
    postJsonMock.mockResolvedValueOnce({ success: true, data: { customers: [profile('18800001111'), profile('18800002222')] } });

    await store.startBatchTemplateFlow({ phones: ['18800001111', '18800001111', '18800002222'], source: 'FOLLOWUP_LIST' });

    expect(store.batchTemplateState.phase).toBe('SELECT_TEMPLATE');
    expect(store.batchTemplateState.phones).toEqual(['18800001111', '18800002222']);
    expect(store.batchTemplateState.customers.map((entry) => entry.profile?.customer.phone)).toEqual(['18800001111', '18800002222']);
    expect(store.selectedTemplate.value?.id).toBe(1);
    expect(JSON.parse(localStorage.getItem('batch_template_cache') ?? '[]')).toHaveLength(1);
  });

  it('rejects batches over the configured customer limit', async () => {
    const store = await freshStore();

    await store.startBatchTemplateFlow({ phones: ['1', '2', '3', '4'], source: 'FOLLOWUP_LIST' });

    expect(store.batchTemplateState.phase).toBe('IDLE');
    expect(store.batchTemplateState.toast).toBeTruthy();
    expect(getJsonMock).not.toHaveBeenCalled();
  });

  it('falls back to cached templates and one-by-one customer loading when bulk APIs fail', async () => {
    const store = await freshStore();
    localStorage.setItem('batch_template_cache', JSON.stringify([template({ id: 9, title: 'Cached' })]));
    getJsonMock
      .mockRejectedValueOnce(new Error('template list down'))
      .mockResolvedValueOnce({ success: true, data: profile('18800001111') })
      .mockResolvedValueOnce({ success: false, data: null });
    postJsonMock.mockRejectedValueOnce(new Error('batch down'));

    await store.startBatchTemplateFlow({ phones: ['18800001111', '18800002222'], source: 'FOLLOWUP_LIST' });

    expect(store.batchTemplateState.templates.map((entry) => entry.id)).toEqual([9]);
    expect(store.batchTemplateState.customers).toEqual([
      expect.objectContaining({ phone: '18800001111', profile: expect.objectContaining({ customer: expect.objectContaining({ phone: '18800001111' }) }), skipped: false }),
      expect.objectContaining({ phone: '18800002222', profile: null, skipped: true })
    ]);
  });

  it('filters visible templates by scene and enabled template content', async () => {
    const store = await freshStore();
    store.batchTemplateState.templates = [
      template({ id: 1, scene: 'OPENING', isEnabled: true, contentType: 'TEMPLATE' }),
      template({ id: 2, scene: 'REVISIT', isEnabled: true, contentType: 'TEMPLATE' }),
      template({ id: 3, scene: 'OPENING', isEnabled: false, contentType: 'TEMPLATE' }),
      template({ id: 4, scene: 'OPENING', isEnabled: true, contentType: 'IMAGE' })
    ];

    expect(store.visibleBatchTemplates.value.map((entry) => entry.id)).toEqual([1, 2]);
    store.setBatchSceneFilter('OPENING');
    expect(store.visibleBatchTemplates.value.map((entry) => entry.id)).toEqual([1]);
  });

  it('confirms template selection, skips unavailable customers, and completes when all are skipped', async () => {
    const store = await freshStore();
    store.batchTemplateState.templates = [template({ id: 1 })];
    store.batchTemplateState.selectedTemplateId = 1;
    store.batchTemplateState.customers = [
      { phone: 'missing-1', profile: null, copied: false, skipped: false },
      { phone: 'missing-2', profile: null, copied: false, skipped: false }
    ];

    store.confirmBatchTemplate();

    expect(store.batchTemplateState.phase).toBe('COMPLETED');
    expect(store.batchTemplateState.customers.every((entry) => entry.skipped)).toBe(true);
  });

  it('fills selected template variables from the current customer profile', async () => {
    const store = await freshStore();
    store.batchTemplateState.templates = [template({
      id: 1,
      content: '您好 {客户昵称}，预约{预约时间} 到 {预约门店}，管家{管家名}，尾号{手机后4位}，未知{不存在}'
    })];
    store.batchTemplateState.selectedTemplateId = 1;
    store.batchTemplateState.customers = [
      { phone: '18800001111', profile: profile('18800001111'), copied: false, skipped: false }
    ];

    expect(store.filledTemplateText.value).toContain('Alice');
    expect(store.filledTemplateText.value).toContain('7月5日');
    expect(store.filledTemplateText.value).toContain('Store A');
    expect(store.filledTemplateText.value).toContain('Keeper A');
    expect(store.filledTemplateText.value).toContain('1111');
    expect(store.filledTemplateText.value).toContain('{不存在}');
  });

  it('copies current batch text, records local log, and sends confirmation asynchronously', async () => {
    const store = await freshStore();
    writeClipboardTextMock.mockResolvedValue({ success: true });
    postJsonMock.mockResolvedValue({ success: true, data: {} });
    store.batchTemplateState.phase = 'SENDING';
    store.batchTemplateState.templates = [template({ id: 1, content: 'Hello {客户昵称}' })];
    store.batchTemplateState.selectedTemplateId = 1;
    store.batchTemplateState.customers = [
      { phone: '18800001111', profile: profile('18800001111'), copied: false, skipped: false }
    ];

    await store.copyCurrentBatchText();
    await Promise.resolve();

    expect(writeClipboardTextMock).toHaveBeenCalledWith('Hello Alice');
    expect(store.batchTemplateState.customers[0].copied).toBe(true);
    expect(store.batchTemplateState.localLogs[0]).toMatchObject({ phoneTail: '1111', templateId: 1, result: 'COPIED' });
    expect(postJsonMock).toHaveBeenCalledWith('/api/v1/chat/send-confirm', expect.objectContaining({
      phone: '18800001111',
      sentText: 'Hello Alice',
      selectedDirection: 'BATCH_TEMPLATE',
      templateId: 1
    }), 2000);
  });

  it('records copy failures without marking customer copied', async () => {
    const store = await freshStore();
    writeClipboardTextMock.mockResolvedValue({ success: false, error: 'denied' });
    store.batchTemplateState.templates = [template({ id: 1, content: 'Hello' })];
    store.batchTemplateState.selectedTemplateId = 1;
    store.batchTemplateState.customers = [
      { phone: '18800001111', profile: profile('18800001111'), copied: false, skipped: false }
    ];

    await store.copyCurrentBatchText();

    expect(store.batchTemplateState.customers[0].copied).toBe(false);
    expect(store.batchTemplateState.localLogs[0]).toMatchObject({ result: 'COPY_FAILED', errorMessage: 'denied' });
    expect(store.batchTemplateState.toast).toBeTruthy();
  });

  it('navigates batch customers and supports pause, resume, and exit', async () => {
    const store = await freshStore();
    store.batchTemplateState.phase = 'SENDING';
    store.batchTemplateState.templates = [template({ id: 1 })];
    store.batchTemplateState.selectedTemplateId = 1;
    store.batchTemplateState.customers = [
      { phone: '18800001111', profile: profile('18800001111'), copied: false, skipped: false },
      { phone: '18800002222', profile: profile('18800002222'), copied: false, skipped: false }
    ];

    store.nextBatchCustomer();
    expect(store.batchTemplateState.currentIndex).toBe(1);
    store.nextBatchCustomer();
    expect(store.batchTemplateState.phase).toBe('COMPLETED');

    store.previousBatchCustomer();
    expect(store.batchTemplateState.currentIndex).toBe(1);
    store.pauseBatchTemplate();
    expect(store.batchTemplateState.phase).toBe('PAUSED');
    store.resumeBatchTemplate();
    expect(store.batchTemplateState.phase).toBe('SENDING');
    store.exitBatchTemplate();
    expect(store.batchTemplateState.phase).toBe('IDLE');
    expect(store.batchTemplateState.customers).toEqual([]);
  });
});

function template(patch: Partial<QuickSearchItem>): QuickSearchItem {
  return {
    id: 1,
    contentType: 'TEMPLATE',
    scene: 'OPENING',
    leadType: 'GENERAL',
    title: 'Template',
    shortcutCode: 'tpl',
    content: 'Hello {客户昵称}',
    imageUrl: null,
    sortOrder: 1,
    isEnabled: true,
    updatedAt: '2026-07-03T12:00:00',
    ...patch
  };
}

function profile(phone: string): CustomerProfileView {
  return {
    customer: {
      phone,
      nickname: phone.endsWith('1111') ? 'Alice' : 'Bob',
      leadType: 'TUAN_GOU',
      assignedKeeper: 'Keeper A',
      intendedStore: 'Store A',
      appointmentDate: '2026-07-05',
      appointmentStore: 'Store A',
      appointmentItem: 'Project A',
      sourceTable: 'sheet',
      version: 1
    },
    pendingSuggestions: []
  };
}
