export type SaveStatus = 'OK' | 'CONFLICT' | 'FAILED_RETRYING' | 'GIVE_UP' | 'BUSY';

export type SaveProfileInput = {
  phone: string;
  editedFields: Record<string, unknown>;
  version: number;
  hasTableRow: boolean;
  sourceTable?: string | null;
  sourceRowId?: string | null;
};

export type SaveResult = {
  status: SaveStatus;
  message: string;
  needRefresh: boolean;
  askTableSync?: boolean;
};

export type PendingSaveRecord = {
  phone: string;
  editedFields: Record<string, unknown>;
  version: number;
  createdAt: number;
  retryCount: number;
};
