import type { ReplySession } from './types';

export type ReplySessionSnapshot = {
  sessions: ReplySession[];
  activeSessionId: string;
};

type SessionStorage = Pick<Storage, 'setItem'>;

export function loadReplySessionSnapshot(raw: string | null): ReplySessionSnapshot | null {
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as Partial<ReplySessionSnapshot>;
    return {
      sessions: Array.isArray(parsed.sessions) ? parsed.sessions : [],
      activeSessionId: typeof parsed.activeSessionId === 'string' ? parsed.activeSessionId : ''
    };
  } catch {
    return null;
  }
}

export function persistReplySessionSnapshot(
  storage: SessionStorage,
  key: string,
  snapshot: ReplySessionSnapshot
): void {
  try {
    storage.setItem(key, JSON.stringify(snapshot));
  } catch {
  }
}
