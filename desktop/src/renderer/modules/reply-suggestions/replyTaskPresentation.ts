import type { ArchivedReplySession, ReplySession } from './types';

export type ReplyTaskItem = {
  sessionId: string;
  nickname?: string;
  status: string;
  jobId?: string;
  updatedAt: number;
  archived: boolean;
};

export function buildReplyTaskItems(
  sessions: readonly ReplySession[],
  archivedSessions: readonly ArchivedReplySession[]
): ReplyTaskItem[] {
  return [
    ...sessions.map((session) => toReplyTaskItem(session, false, session.updatedAt || session.createdAt)),
    ...archivedSessions.map((session) => toReplyTaskItem(session, true, session.archivedAt || session.updatedAt || session.createdAt))
  ].sort((left, right) => right.updatedAt - left.updatedAt).slice(0, 30);
}

function toReplyTaskItem(session: ReplySession, archived: boolean, updatedAt: number): ReplyTaskItem {
  return {
    sessionId: session.sessionId,
    nickname: session.currentNickname,
    status: replyTaskStatus(session),
    jobId: session.recognitionJobId,
    updatedAt,
    archived
  };
}

function replyTaskStatus(session: Pick<ReplySession, 'status' | 'recognitionJobStatus'>): string {
  if (session.recognitionJobStatus === 'QUEUED' || session.recognitionJobStatus === 'RECOGNIZING') {
    return session.recognitionJobStatus;
  }
  if (session.recognitionJobStatus === 'EXPIRED') return 'EXPIRED';
  return session.status;
}
