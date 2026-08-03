import { describe, expect, it, vi } from 'vitest';
import type { ReplySession } from './types';
import { loadReplySessionSnapshot, persistReplySessionSnapshot } from './replySessionPersistence';

const session = { sessionId: 'reply-1', status: 'READY' } as ReplySession;

describe('replySessionPersistence', () => {
  it('loads only a valid session snapshot and falls back to an empty active session id', () => {
    const snapshot = loadReplySessionSnapshot(JSON.stringify({
      sessions: [session],
      activeSessionId: 42
    }));

    expect(snapshot).toEqual({
      sessions: [session],
      activeSessionId: ''
    });
    expect(loadReplySessionSnapshot('{invalid json')).toBeNull();
    expect(loadReplySessionSnapshot(JSON.stringify({ activeSessionId: 'reply-1' }))).toEqual({
      sessions: [],
      activeSessionId: 'reply-1'
    });
  });

  it('serializes a snapshot and ignores unavailable browser storage', () => {
    const storage = { setItem: vi.fn() };
    const snapshot = { sessions: [session], activeSessionId: 'reply-1' };

    persistReplySessionSnapshot(storage, 'reply-sessions:alice', snapshot);
    persistReplySessionSnapshot({
      setItem: () => { throw new Error('storage unavailable'); }
    }, 'reply-sessions:alice', snapshot);

    expect(storage.setItem).toHaveBeenCalledWith('reply-sessions:alice', JSON.stringify(snapshot));
  });
});
