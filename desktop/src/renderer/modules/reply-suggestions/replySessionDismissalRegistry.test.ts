import { describe, expect, it } from 'vitest';
import { createReplySessionDismissalRegistry } from './replySessionDismissalRegistry';

describe('replySessionDismissalRegistry', () => {
  it('blocks dismissed sessions until they are restored', () => {
    const registry = createReplySessionDismissalRegistry();

    registry.dismiss('reply-1');
    expect(registry.has('reply-1')).toBe(true);

    registry.restore('reply-1');
    expect(registry.has('reply-1')).toBe(false);
  });

  it('removes expired entries and keeps the newest configured entries', () => {
    let now = 0;
    const registry = createReplySessionDismissalRegistry({
      now: () => now,
      ttlMs: 100,
      maxEntries: 2
    });

    registry.dismiss('expired');
    now = 50;
    registry.dismiss('oldest');
    now = 80;
    registry.dismiss('newest');

    expect(registry.has('expired')).toBe(false);
    expect(registry.has('oldest')).toBe(true);
    expect(registry.has('newest')).toBe(true);

    now = 200;
    expect(registry.has('oldest')).toBe(false);
    expect(registry.has('newest')).toBe(false);
  });
});
