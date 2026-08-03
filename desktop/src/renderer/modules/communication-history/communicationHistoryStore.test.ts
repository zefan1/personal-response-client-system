import { beforeEach, describe, expect, it, vi } from 'vitest';

const apiMocks = vi.hoisted(() => ({
  getJson: vi.fn(),
  putJson: vi.fn()
}));

vi.mock('../../shared/apiClient', () => ({
  getJson: apiMocks.getJson,
  putJson: apiMocks.putJson
}));

describe('communicationHistoryStore', () => {
  beforeEach(() => {
    vi.resetModules();
    apiMocks.getJson.mockReset();
    apiMocks.putJson.mockReset();
  });

  it('loads filtered messages and prepends older pages in chronological order', async () => {
    const store = await import('./communicationHistoryStore');
    apiMocks.getJson
      .mockResolvedValueOnce({
        success: true,
        data: {
          messages: [message(12, '2026-08-01T10:05:00'), message(11, '2026-08-01T10:02:00')],
          nextBeforeId: 11
        }
      })
      .mockResolvedValueOnce({
        success: true,
        data: { messages: [message(10, '2026-07-31T18:00:00')], nextBeforeId: null }
      });
    store.communicationHistoryState.platform = 'DOUYIN';
    store.communicationHistoryState.fromDate = '2026-07-31';
    store.communicationHistoryState.toDate = '2026-08-01';
    store.communicationHistoryState.keyword = '腰痛';

    await store.openCommunicationHistory('18800001111', 'messages');

    expect(apiMocks.getJson).toHaveBeenNthCalledWith(
      1,
      '/api/v1/communications/customers/18800001111/messages?platform=DOUYIN&from=2026-07-31&to=2026-08-01&keyword=%E8%85%B0%E7%97%9B&limit=50'
    );
    expect(store.communicationHistoryState.messages.map((item) => item.id)).toEqual([11, 12]);

    await store.loadOlderMessages();

    expect(apiMocks.getJson).toHaveBeenNthCalledWith(
      2,
      expect.stringContaining('beforeId=11')
    );
    expect(store.communicationHistoryState.messages.map((item) => item.id)).toEqual([10, 11, 12]);
    expect(store.communicationHistoryState.nextBeforeId).toBeNull();
  });

  it('loads a phone-less customer history by customer id', async () => {
    const store = await import('./communicationHistoryStore');
    apiMocks.getJson.mockResolvedValueOnce({
      success: true,
      data: { messages: [message(44, '2026-08-01T10:00:00')], nextBeforeId: null }
    });

    await store.openCommunicationHistoryByCustomerId(44, '', 'messages');

    expect(apiMocks.getJson).toHaveBeenCalledWith(
      '/api/v1/communications/customers/by-id/44/messages?limit=50'
    );
    expect(store.communicationHistoryState.messages.map((item) => item.id)).toEqual([44]);
  });
});

function message(id: number, messageTime: string) {
  return {
    id,
    batchId: 1,
    customerId: 7,
    username: 'keeper-1',
    platformCode: 'DOUYIN',
    senderRole: id % 2 === 0 ? 'CUSTOMER' : 'EMPLOYEE',
    contentType: 'TEXT',
    originalText: `message-${id}`,
    currentText: `message-${id}`,
    messageTime,
    timeEstimated: false,
    sequenceNo: id,
    dedupeFingerprint: `fp-${id}`
  };
}
