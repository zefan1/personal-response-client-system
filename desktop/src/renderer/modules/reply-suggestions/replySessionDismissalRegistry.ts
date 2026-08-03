type Options = {
  now?: () => number;
  ttlMs?: number;
  maxEntries?: number;
};

export type ReplySessionDismissalRegistry = {
  dismiss: (sessionId: string) => void;
  has: (sessionId: string) => boolean;
  restore: (sessionId: string) => void;
  clear: () => void;
};

export function createReplySessionDismissalRegistry({
  now = Date.now,
  ttlMs = 30 * 60 * 1000,
  maxEntries = 200
}: Options = {}): ReplySessionDismissalRegistry {
  const dismissedSessionIds = new Map<string, number>();

  function prune(): void {
    const currentTime = now();
    for (const [sessionId, dismissedAt] of dismissedSessionIds) {
      if (currentTime - dismissedAt > ttlMs) {
        dismissedSessionIds.delete(sessionId);
      }
    }
    while (dismissedSessionIds.size > maxEntries) {
      const oldest = dismissedSessionIds.keys().next().value;
      if (!oldest) return;
      dismissedSessionIds.delete(oldest);
    }
  }

  return {
    dismiss(sessionId: string): void {
      dismissedSessionIds.set(sessionId, now());
      prune();
    },
    has(sessionId: string): boolean {
      prune();
      return dismissedSessionIds.has(sessionId);
    },
    restore(sessionId: string): void {
      dismissedSessionIds.delete(sessionId);
    },
    clear(): void {
      dismissedSessionIds.clear();
    }
  };
}
