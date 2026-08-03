import { createApp, nextTick } from 'vue';
import { afterEach, describe, expect, it, vi } from 'vitest';

const apiMocks = vi.hoisted(() => ({
  getJson: vi.fn(),
  putJson: vi.fn()
}));

vi.mock('../../shared/apiClient', () => ({
  getJson: apiMocks.getJson,
  putJson: apiMocks.putJson
}));

describe('CommunicationHistoryPanel', () => {
  afterEach(() => {
    document.body.innerHTML = '';
    vi.resetModules();
    apiMocks.getJson.mockReset();
    apiMocks.putJson.mockReset();
  });

  it('renders grouped messages with estimated and corrected markers and returns to the profile', async () => {
    const [{ default: Panel }, store, { eventBus }] = await Promise.all([
      import('./CommunicationHistoryPanel.vue'),
      import('./communicationHistoryStore'),
      import('../../shared/eventBus')
    ]);
    store.communicationHistoryState.phone = '18800001111';
    store.communicationHistoryState.activeView = 'messages';
    store.communicationHistoryState.messages = [
      message(10, 'CUSTOMER', '原识别腰疼', '一直腰痛', '2026-08-01T10:02:00', true),
      message(11, 'EMPLOYEE', '可以先安排评估', '可以先安排评估', '2026-08-01T10:03:00', false)
    ];
    const returns: string[] = [];
    eventBus.on<{ phone: string }>('communication:return-profile', ({ phone }) => returns.push(phone));
    const host = document.createElement('div');
    document.body.appendChild(host);
    const app = createApp(Panel);
    app.mount(host);
    await nextTick();

    expect(host.querySelector('.communication-date-group')?.textContent).toContain('2026-08-01');
    expect(host.textContent).toContain('客户');
    expect(host.textContent).toContain('员工');
    expect(host.textContent).toContain('抖音');
    expect(host.textContent).toContain('识别于 10:02');
    expect(host.textContent).toContain('已修正');
    expect(host.textContent).toContain('原识别文字：原识别腰疼');
    (host.querySelector('.communication-return-profile') as HTMLButtonElement).click();
    expect(returns).toEqual(['18800001111']);
    app.unmount();
  });

  it('renders historical summary versions in newest-first order', async () => {
    const [{ default: Panel }, store] = await Promise.all([
      import('./CommunicationHistoryPanel.vue'),
      import('./communicationHistoryStore')
    ]);
    store.communicationHistoryState.phone = '18800001111';
    store.communicationHistoryState.activeView = 'summaries';
    store.communicationHistoryState.summaries = [
      { id: 2, customerId: 7, versionNo: 2, summaryText: '最新汇总', lastMessageId: 11, generatedAt: '2026-08-01T10:20:00' },
      { id: 1, customerId: 7, versionNo: 1, summaryText: '上一版汇总', lastMessageId: 9, generatedAt: '2026-07-31T18:00:00' }
    ];
    const host = document.createElement('div');
    document.body.appendChild(host);
    const app = createApp(Panel);
    app.mount(host);
    await nextTick();

    expect([...host.querySelectorAll('.communication-summary-version')].map((item) => item.textContent))
      .toEqual([
        expect.stringContaining('最新汇总'),
        expect.stringContaining('上一版汇总')
      ]);
    app.unmount();
  });
});

function message(
  id: number,
  senderRole: string,
  originalText: string,
  currentText: string,
  messageTime: string,
  timeEstimated: boolean
) {
  return {
    id,
    batchId: 1,
    customerId: 7,
    username: 'keeper-1',
    platformCode: 'DOUYIN',
    senderRole,
    contentType: 'TEXT',
    originalText,
    currentText,
    messageTime,
    timeEstimated,
    sequenceNo: id,
    dedupeFingerprint: `fp-${id}`
  };
}
