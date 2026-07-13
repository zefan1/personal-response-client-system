import { createApp, nextTick, type App } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { FollowupItem } from './types';

const apiMocks = vi.hoisted(() => ({
  getJson: vi.fn()
}));

vi.mock('../../shared/apiClient', () => ({
  getJson: apiMocks.getJson
}));

type MountedPanel = {
  app: App<Element>;
  host: HTMLDivElement;
  eventBus: typeof import('../../shared/eventBus')['eventBus'];
};

async function flushUi() {
  await Promise.resolve();
  await Promise.resolve();
  await nextTick();
}

async function mountPanel(): Promise<MountedPanel> {
  vi.resetModules();
  const [{ default: FollowupListPanel }, { eventBus }] = await Promise.all([
    import('./FollowupListPanel.vue'),
    import('../../shared/eventBus')
  ]);
  const host = document.createElement('div');
  document.body.appendChild(host);
  const app = createApp(FollowupListPanel);
  app.mount(host);
  await flushUi();
  return { app, host, eventBus };
}

function activeTabText(host: Element): string {
  return host.querySelector('.tab-button.active')?.textContent ?? '';
}

function loadedItems(): FollowupItem[] {
  return [
    item({ phone: '18800000001', nickname: 'Overdue', reminderType: 'OVERDUE', overdueHours: 3 }),
    item({ phone: '18800000002', nickname: 'Today', reminderType: 'DUE_TODAY' }),
    item({ phone: '18800000003', nickname: 'Appointment', reminderType: 'APPOINTMENT', appointmentTime: '14:30' }),
    item({ phone: '18800000004', nickname: 'New Lead', reminderType: 'NEW_LEAD', sourceTable: 'sheet-a' })
  ];
}

describe('FollowupListPanel', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-07-03T12:08:00Z'));
    apiMocks.getJson.mockResolvedValue({
      success: true,
      data: {
        keeperId: 'keeper-a',
        totalCount: 4,
        items: loadedItems()
      },
      errorCode: null,
      message: null
    });
  });

  afterEach(() => {
    document.body.innerHTML = '';
    vi.useRealTimers();
    apiMocks.getJson.mockReset();
  });

  it('renders loaded followups and switches tabs from user clicks', async () => {
    const { app, host } = await mountPanel();

    expect(apiMocks.getJson).toHaveBeenCalledWith('/api/v1/followups/today', 10000);
    expect(host.querySelectorAll('.tab-button')).toHaveLength(4);
    expect(activeTabText(host)).toContain('1');
    expect(host.textContent).toContain('Overdue');
    expect(host.textContent).toContain('20:08');
    const refreshButton = host.querySelector('.panel-header .icon-refresh-button') as HTMLButtonElement | null;
    expect(refreshButton?.textContent?.trim()).toBe('↻');
    expect(refreshButton?.getAttribute('aria-label')).toBe('刷新今日跟进');
    expect(refreshButton?.textContent).not.toContain('刷新');

    const tabs = [...host.querySelectorAll('.tab-button')] as HTMLButtonElement[];
    tabs[2].click();
    await flushUi();

    expect(activeTabText(host)).toContain('1');
    expect(host.textContent).toContain('Appointment');
    expect(host.textContent).not.toContain('Overdue');

    app.unmount();
  });

  it('honors all valid workbench tab-switch events and ignores invalid payloads', async () => {
    const { app, host, eventBus } = await mountPanel();

    eventBus.emit('followup:switch-tab', { tab: 'DUE_TODAY' });
    await flushUi();
    expect(host.textContent).toContain('Today');

    eventBus.emit('followup:switch-tab', { tab: 'APPOINTMENT' });
    await flushUi();
    expect(host.textContent).toContain('Appointment');

    eventBus.emit('followup:switch-tab', { tab: 'NEW_LEAD' });
    await flushUi();
    expect(host.textContent).toContain('New Lead');

    eventBus.emit('followup:switch-tab', { tab: 'NOT_A_TAB' });
    await flushUi();
    expect(host.textContent).toContain('New Lead');

    app.unmount();
  });

  it('emits batch template events from the rendered batch bar', async () => {
    const { app, host, eventBus } = await mountPanel();
    const batchEvents: unknown[] = [];
    eventBus.on('batch:start', (payload) => batchEvents.push(payload));

    const checkbox = host.querySelector('.followup-row input[type="checkbox"]') as HTMLInputElement | null;
    expect(checkbox).toBeTruthy();
    if (checkbox) {
      checkbox.checked = true;
    }
    checkbox?.dispatchEvent(new Event('change', { bubbles: true }));
    await flushUi();

    const batchButtons = [...host.querySelectorAll('.batch-bar button')] as HTMLButtonElement[];
    expect(batchButtons.length).toBeGreaterThanOrEqual(3);
    batchButtons[2].click();
    await flushUi();

    expect(batchEvents).toEqual([{ phones: ['18800000001'], source: 'FOLLOWUP_LIST' }]);
    app.unmount();
  });
});

function item(patch: Partial<FollowupItem>): FollowupItem {
  return {
    phone: '18800000000',
    phoneFull: undefined,
    nickname: 'Customer',
    leadType: 'PENDING',
    reminderType: 'DUE_TODAY',
    alertLevel: 'NORMAL',
    overdueHours: null,
    ...patch
  };
}
