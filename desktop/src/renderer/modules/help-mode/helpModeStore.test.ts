import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { HelpRequestEvent, HelpRequestPayload, HelperReply } from './types';

const postJsonMock = vi.fn();

vi.mock('../../shared/apiClient', () => ({
  postJson: postJsonMock
}));

type HelpModeModule = typeof import('./helpModeStore');
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

async function freshStore(): Promise<{ help: HelpModeModule; eventBus: EventBusModule['eventBus'] }> {
  vi.resetModules();
  localStorage.clear();
  localStorage.setItem('desktop_config', JSON.stringify({
    helpMaxReplies: 2
  }));
  postJsonMock.mockReset();
  const help = await import('./helpModeStore');
  const { eventBus } = await import('../../shared/eventBus');
  return { help, eventBus };
}

describe('helpModeStore', () => {
  beforeEach(() => {
    installMemoryLocalStorage();
  });

  afterEach(() => {
    localStorage.clear();
    postJsonMock.mockReset();
  });

  it('opens and validates help request dialog state', async () => {
    const { help } = await freshStore();

    help.openHelpRequest(request({ phone: '' }));
    expect(help.helpModeState.requestDialogVisible).toBe(false);
    expect(help.helpModeState.toast).toBeTruthy();

    help.openHelpRequest(request({ phone: '18800001111' }));
    expect(help.helpModeState.requestDialogVisible).toBe(true);
    expect(help.helpModeState.activeRequest?.phone).toBe('18800001111');

    help.helpModeState.activeHelpId = 'help-a';
    help.openHelpRequest(request({ phone: '18800002222' }));
    expect(help.helpModeState.activeRequest?.phone).toBe('18800001111');
  });

  it('submits help requests, truncates keeper notes, and emits pending state', async () => {
    const { help, eventBus } = await freshStore();
    const pending: unknown[] = [];
    eventBus.on('help:pending', (payload) => pending.push(payload));
    help.openHelpRequest(request({ phone: '18800001111', clientMessage: '' }));
    help.helpModeState.keeperNote = 'x'.repeat(600);
    postJsonMock.mockResolvedValue({
      success: true,
      data: { helpId: 'help-a', leaderOnline: true, targetLeaderName: 'Leader A' }
    });

    await help.submitHelpRequest();

    expect(postJsonMock).toHaveBeenCalledWith('/api/v1/help/request', {
      phone: '18800001111',
      clientMessage: expect.any(String),
      aiSuggestions: [{ text: 'reply', direction: 'NEXT_STEP', reason: 'reason' }],
      keeperNote: 'x'.repeat(500)
    }, 5000);
    expect(help.helpModeState.requestDialogVisible).toBe(false);
    expect(help.helpModeState.activeHelpId).toBe('help-a');
    expect(pending).toEqual([{ helpId: 'help-a', phone: '18800001111' }]);
  });

  it('emits timeout when no leader fallback is available', async () => {
    const { help, eventBus } = await freshStore();
    const timeouts: unknown[] = [];
    eventBus.on('help:timeout', (payload) => timeouts.push(payload));
    help.openHelpRequest(request({ phone: '18800001111' }));
    postJsonMock.mockResolvedValue({ success: true, data: { helpId: 'help-a', leaderOnline: false, noFallbackAvailable: true } });

    await help.submitHelpRequest();

    expect(help.helpModeState.activeHelpId).toBe('');
    expect(timeouts).toEqual([{ phone: '18800001111', reason: 'NO_LEADER_ONLINE' }]);
  });

  it('maps help request failures and network errors to user-visible toasts', async () => {
    const { help } = await freshStore();
    help.openHelpRequest(request({ phone: '18800001111' }));
    postJsonMock.mockResolvedValueOnce({ success: false, errorCode: '80-10003' });

    await help.submitHelpRequest();
    expect(help.helpModeState.toast).toBeTruthy();
    expect(help.helpModeState.sendingRequest).toBe(false);

    postJsonMock.mockRejectedValueOnce(new Error('network down'));
    await help.submitHelpRequest();
    expect(help.helpModeState.toast).toBeTruthy();
    expect(help.helpModeState.sendingRequest).toBe(false);
  });

  it('upserts helper queue requests and tracks current helper request', async () => {
    const { help } = await freshStore();

    help.handleHelpRequest(helpPayload({ helpId: 'help-a', requesterName: 'A' }));
    help.handleHelpOfflineReplay(helpPayload({ helpId: 'help-b', requesterName: 'B' }));
    help.handleHelpRequest(helpPayload({ helpId: 'help-a', requesterName: 'A2' }));

    expect(help.helpModeState.helperQueue.map((item) => item.requesterName)).toEqual(['A2', 'B']);
    expect(help.currentHelperRequest()?.helpId).toBe('help-a');
  });

  it('manages helper draft replies with configured maximum', async () => {
    const { help } = await freshStore();

    help.addConfirmedReply({ text: 'confirmed', direction: 'NEXT_STEP' });
    help.addModifiedReply({ text: 'modified', direction: 'NEXT_STEP' });
    help.addOriginalReply();

    expect(help.helpModeState.draftReplies.map((reply) => reply.text)).toEqual(['confirmed', 'modified']);
    expect(help.helpModeState.toast).toBeTruthy();

    help.updateDraftReply(1, 'modified text');
    expect(help.helpModeState.draftReplies[1].text).toBe('modified text');
    help.removeDraftReply(0);
    expect(help.helpModeState.draftReplies.map((reply) => reply.text)).toEqual(['modified text']);
  });

  it('submits helper resolve replies and removes completed requests', async () => {
    const { help } = await freshStore();
    help.handleHelpRequest(helpPayload({ helpId: 'help-a', requesterName: 'A' }));
    help.handleHelpRequest(helpPayload({ helpId: 'help-b', requesterName: 'B' }));
    help.helpModeState.draftReplies = [
      helperReply({ text: 'first' }),
      helperReply({ text: '' }),
      helperReply({ text: 'third' })
    ];
    postJsonMock.mockResolvedValue({ success: true, data: {} });

    await help.submitHelpResolve();

    expect(postJsonMock).toHaveBeenCalledWith('/api/v1/help/resolve', {
      helpId: 'help-a',
      helperReplies: [helperReply({ text: 'first' }), helperReply({ text: 'third' })]
    }, 5000);
    expect(help.helpModeState.helperQueue.map((item) => item.helpId)).toEqual(['help-b']);
    expect(help.helpModeState.draftReplies).toEqual([]);
  });

  it('keeps helper request available on resolve failure', async () => {
    const { help } = await freshStore();
    help.handleHelpRequest(helpPayload({ helpId: 'help-a' }));
    help.helpModeState.draftReplies = [helperReply({ text: 'reply' })];
    postJsonMock.mockResolvedValue({ success: false });

    await help.submitHelpResolve();

    expect(help.helpModeState.helperQueue).toHaveLength(1);
    expect(help.helpModeState.resolving).toBe(false);
    expect(help.helpModeState.toast).toBeTruthy();
  });

  it('handles help responses and emits reply-selected for helper replies', async () => {
    const { help, eventBus } = await freshStore();
    const resolved: unknown[] = [];
    const selected: unknown[] = [];
    eventBus.on('help:resolved', (payload) => resolved.push(payload));
    eventBus.on('reply:selected', (payload) => selected.push(payload));

    help.handleHelpResponse({
      helpId: 'help-a',
      phone: '18800001111',
      helperReplies: [helperReply({ text: 'reply text', direction: '' })]
    });
    help.copyHelperReply(help.helpModeState.receivedResponse?.helperReplies[0] as HelperReply);

    expect(help.helpModeState.activeHelpId).toBe('');
    expect(resolved).toEqual([{ helpId: 'help-a', phone: '18800001111' }]);
    expect(selected).toEqual([{
      text: 'reply text',
      direction: expect.any(String),
      reason: 'HELP_REPLY',
      phone: '18800001111',
      isFallback: false
    }]);

    help.toggleHelpResponseExpanded();
    expect(help.helpModeState.responseExpanded).toBe(true);
    help.closeHelpResponse();
    expect(help.helpModeState.responseExpanded).toBe(false);
  });
});

function request(patch: Partial<HelpRequestEvent> = {}): HelpRequestEvent {
  return {
    phone: '18800001111',
    clientMessage: 'client message',
    aiSuggestions: [{ text: 'reply', direction: 'NEXT_STEP', reason: 'reason' }],
    ...patch
  };
}

function helpPayload(patch: Partial<HelpRequestPayload> = {}): HelpRequestPayload {
  return {
    helpId: 'help-a',
    requesterName: 'Keeper A',
    phone: '18800001111',
    clientMessage: 'need help',
    aiSuggestions: [{ text: 'reply', direction: 'NEXT_STEP', reason: 'reason' }],
    ...patch
  };
}

function helperReply(patch: Partial<HelperReply> = {}): HelperReply {
  return {
    text: 'reply',
    direction: 'NEXT_STEP',
    source: 'CONFIRMED',
    ...patch
  };
}
