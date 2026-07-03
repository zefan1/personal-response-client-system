import { createApp, nextTick, type App } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { CustomerProfileView } from '../customer-profile/types';
import type { QuickSearchItem } from '../quick-search/types';

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

type MountedOverlay = {
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

async function mountOverlay(): Promise<MountedOverlay> {
  vi.resetModules();
  localStorage.clear();
  localStorage.setItem('desktop_config', JSON.stringify({
    batchMaxCustomers: 5,
    batchCustomerBatchTimeoutMs: 1000
  }));
  const [{ default: BatchTemplateOverlay }, { eventBus }] = await Promise.all([
    import('./BatchTemplateOverlay.vue'),
    import('../../shared/eventBus')
  ]);
  const host = document.createElement('div');
  document.body.appendChild(host);
  const app = createApp(BatchTemplateOverlay);
  app.mount(host);
  await flushUi();
  return { app, host, eventBus };
}

async function startBatch(eventBus: MountedOverlay['eventBus'], phones = ['18800001111', '18800002222']): Promise<void> {
  eventBus.emit('batch:start', { phones, source: 'FOLLOWUP_LIST' });
  await flushUi();
  await flushUi();
}

describe('BatchTemplateOverlay', () => {
  beforeEach(() => {
    installMemoryLocalStorage();
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-07-03T12:00:00Z'));
    mocks.getJson.mockResolvedValue({ success: true, data: templates() });
    mocks.postJson.mockResolvedValue({ success: true, data: { customers: [profile('18800001111'), profile('18800002222')] } });
    mocks.writeClipboardText.mockResolvedValue({ success: true });
  });

  afterEach(() => {
    document.body.innerHTML = '';
    vi.useRealTimers();
    localStorage.clear();
    Object.values(mocks).forEach((mock) => mock.mockReset());
  });

  it('opens from batch:start, renders API templates and confirms the selected template into sending mode', async () => {
    const { app, host, eventBus } = await mountOverlay();

    await startBatch(eventBus);

    expect(mocks.getJson).toHaveBeenCalledWith('/api/v1/quick-search/items?contentType=TEMPLATE&enabled=true', 3000);
    expect(mocks.postJson).toHaveBeenCalledWith('/api/v1/customers/batch', { phones: ['18800001111', '18800002222'] }, 1000);
    expect(host.querySelector('.batch-overlay')).toBeTruthy();
    expect(host.querySelectorAll('.batch-template-row')).toHaveLength(2);
    expect(host.textContent).toContain('Morning template');
    expect(host.textContent).toContain('Reminder template');

    const templateRows = [...host.querySelectorAll('.batch-template-row')] as HTMLButtonElement[];
    templateRows[1].click();
    await flushUi();
    expect(templateRows[1].classList.contains('selected')).toBe(true);

    const confirm = host.querySelector('.batch-footer .primary') as HTMLButtonElement | null;
    confirm?.click();
    await flushUi();

    expect(host.querySelector('.batch-customer-card')?.textContent ?? '').toContain('Alice');
    expect(host.textContent).toContain('Reminder template');
    expect(host.querySelector('.batch-template-text')?.textContent ?? '').toContain('Reminder body');
    app.unmount();
  });

  it('copies the current batch text from the rendered button and posts send-confirm with the selected template', async () => {
    const { app, host, eventBus } = await mountOverlay();

    await startBatch(eventBus);
    (host.querySelector('.batch-footer .primary') as HTMLButtonElement | null)?.click();
    await flushUi();

    const copy = [...host.querySelectorAll('.batch-actions button')]
      .find((button) => button.classList.contains('primary')) as HTMLButtonElement | undefined;
    copy?.click();
    await flushUi();
    await flushUi();

    expect(mocks.writeClipboardText).toHaveBeenCalledWith('Morning body');
    expect(mocks.postJson).toHaveBeenLastCalledWith('/api/v1/chat/send-confirm', expect.objectContaining({
      phone: '18800001111',
      sentText: 'Morning body',
      selectedDirection: 'BATCH_TEMPLATE',
      source: 'BATCH_TEMPLATE',
      templateId: 1
    }), 2000);
    expect(copy?.textContent ?? '').toContain('已复制');
    app.unmount();
  });

  it('supports pause, resume, completion, and exit through actual batch controls', async () => {
    const { app, host, eventBus } = await mountOverlay();

    await startBatch(eventBus, ['18800001111']);
    (host.querySelector('.batch-footer .primary') as HTMLButtonElement | null)?.click();
    await flushUi();

    const footerButtons = [...host.querySelectorAll('.batch-status button')] as HTMLButtonElement[];
    footerButtons.find((button) => button.textContent?.includes('暂停') || button.textContent?.includes('鏆傚仠'))?.click();
    await flushUi();

    expect(host.querySelector('.batch-paused')).toBeTruthy();

    (host.querySelector('.batch-paused .primary') as HTMLButtonElement | null)?.click();
    await flushUi();
    expect(host.querySelector('.batch-customer-card')).toBeTruthy();

    const next = [...host.querySelectorAll('.batch-actions button')]
      .find((button) => button.textContent?.includes('下一个') || button.textContent?.includes('涓嬩竴')) as HTMLButtonElement | undefined;
    next?.click();
    await flushUi();

    expect(host.querySelector('.batch-complete')).toBeTruthy();

    (host.querySelector('.batch-complete .primary') as HTMLButtonElement | null)?.click();
    await flushUi();

    expect(host.querySelector('.batch-overlay')).toBeFalsy();
    app.unmount();
  });
});

function templates(): QuickSearchItem[] {
  return [
    template({ id: 1, title: 'Morning template', shortcutCode: 'morning', content: 'Morning body', sortOrder: 1 }),
    template({ id: 2, title: 'Reminder template', shortcutCode: 'reminder', content: 'Reminder body', scene: 'REMINDER', sortOrder: 2 })
  ];
}

function template(patch: Partial<QuickSearchItem>): QuickSearchItem {
  return {
    id: 1,
    contentType: 'TEMPLATE',
    scene: 'OPENING',
    leadType: 'GENERAL',
    title: 'Template',
    shortcutCode: 'tpl',
    content: 'Template body',
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
