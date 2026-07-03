import { createApp, nextTick, type App } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { NewLeadAlertPayload } from './types';

const mocks = vi.hoisted(() => ({
  writeClipboardText: vi.fn()
}));

vi.mock('../../shared/desktopBridge', () => ({
  writeClipboardText: mocks.writeClipboardText
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
    toastMaxCount: 2,
    toastNewLeadDismissS: 30
  }));
  const [{ default: NewLeadToastAgent }, { eventBus }] = await Promise.all([
    import('./NewLeadToastAgent.vue'),
    import('../../shared/eventBus')
  ]);
  const host = document.createElement('div');
  document.body.appendChild(host);
  const app = createApp(NewLeadToastAgent);
  app.mount(host);
  await flushUi();
  return { app, host, eventBus };
}

describe('NewLeadToastAgent', () => {
  beforeEach(() => {
    installMemoryLocalStorage();
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-07-03T12:00:00Z'));
    vi.spyOn(Math, 'random').mockReturnValue(0.5);
    mocks.writeClipboardText.mockResolvedValue({ success: true });
  });

  afterEach(() => {
    document.body.innerHTML = '';
    vi.useRealTimers();
    vi.restoreAllMocks();
    localStorage.clear();
    mocks.writeClipboardText.mockReset();
  });

  it('renders incoming new-lead alerts, copies the full phone, and shows clipboard feedback', async () => {
    const { app, host, eventBus } = await mountAgent();

    eventBus.emit('NEW_LEAD_ALERT', payload({ nickname: 'Lead A', phoneFull: '188 0000-1111' }));
    await flushUi();

    expect(host.querySelectorAll('.new-lead-toast')).toHaveLength(1);
    expect(host.textContent).toContain('Lead A');
    expect(host.textContent).toContain('188****1111');
    expect(host.textContent).toContain('sheet');

    const copy = host.querySelector('.reply-actions .secondary') as HTMLButtonElement | null;
    copy?.click();
    await flushUi();

    expect(mocks.writeClipboardText).toHaveBeenCalledWith('18800001111');
    expect(host.querySelector('.new-lead-light-toast')?.textContent ?? '').not.toEqual('');
    app.unmount();
  });

  it('generates opening replies from the rendered button and removes the toast', async () => {
    const { app, host, eventBus } = await mountAgent();
    const selected: unknown[] = [];
    eventBus.on('customer:selected', (event) => selected.push(event));

    eventBus.emit('NEW_LEAD_ALERT', payload({ phone: '188****2222', phoneFull: '18800002222', leadType: 'XIAN_SUO' }));
    await flushUi();

    const generate = host.querySelector('.reply-actions .primary') as HTMLButtonElement | null;
    generate?.click();
    await flushUi();

    expect(selected).toHaveLength(1);
    expect(selected[0]).toMatchObject({
      phone: '18800002222',
      scene: 'OPENING',
      leadType: 'XIAN_SUO',
      sourceFrom: 'NEW_LEAD'
    });
    expect(host.querySelector('.new-lead-toast')).toBeFalsy();
    app.unmount();
  });

  it('queues overflow alerts and switches to the new-lead tab from the collapsed button', async () => {
    const { app, host, eventBus } = await mountAgent();
    const tabs: unknown[] = [];
    eventBus.on('followup:switch-tab', (event) => tabs.push(event));

    eventBus.emit('NEW_LEAD_ALERT', payload({ phone: 'lead-1', phoneFull: '18800000001', nickname: 'Lead 1' }));
    eventBus.emit('NEW_LEAD_ALERT', payload({ phone: 'lead-2', phoneFull: '18800000002', nickname: 'Lead 2' }));
    eventBus.emit('NEW_LEAD_ALERT', payload({ phone: 'lead-3', phoneFull: '18800000003', nickname: 'Lead 3' }));
    await flushUi();

    expect(host.querySelectorAll('.new-lead-toast')).toHaveLength(2);
    expect(host.querySelector('.new-lead-collapsed')?.textContent ?? '').not.toEqual('');

    (host.querySelector('.new-lead-collapsed') as HTMLButtonElement | null)?.click();
    await flushUi();

    expect(tabs).toEqual([{ tab: 'NEW_LEAD' }]);
    expect(host.querySelector('.new-lead-collapsed')).toBeFalsy();
    app.unmount();
  });
});

function payload(patch: Partial<NewLeadAlertPayload> = {}): NewLeadAlertPayload {
  return {
    phone: '188****1111',
    phoneFull: '18800001111',
    nickname: 'New Lead',
    leadType: 'TUAN_GOU',
    priority: 'HIGH',
    sourceTable: 'sheet',
    assignedKeeper: 'keeper',
    arrivedAt: '2026-07-03T12:00:00Z',
    ...patch
  };
}
