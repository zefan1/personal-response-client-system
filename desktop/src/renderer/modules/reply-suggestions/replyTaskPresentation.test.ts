import { describe, expect, it } from 'vitest';
import { buildReplyTaskItems } from './replyTaskPresentation';

describe('buildReplyTaskItems', () => {
  it('keeps current and archived sessions ordered by their latest timestamp', () => {
    const tasks = buildReplyTaskItems([
      session('current', { updatedAt: 20, recognitionJobStatus: 'RECOGNIZING' })
    ], [
      { ...session('archived', { updatedAt: 10 }), archivedAt: 30 }
    ]);

    expect(tasks).toEqual([
      expect.objectContaining({ sessionId: 'archived', archived: true, updatedAt: 30, status: 'READY' }),
      expect.objectContaining({ sessionId: 'current', archived: false, updatedAt: 20, status: 'RECOGNIZING' })
    ]);
  });
});

function session(sessionId: string, overrides: Record<string, unknown> = {}) {
  return {
    sessionId,
    status: 'READY',
    currentNickname: sessionId,
    recognitionJobId: '',
    recognitionJobStatus: null,
    createdAt: 1,
    updatedAt: 1,
    ...overrides
  } as Parameters<typeof buildReplyTaskItems>[0][number];
}
