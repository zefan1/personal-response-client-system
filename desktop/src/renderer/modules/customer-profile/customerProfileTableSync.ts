import type { SaveProfileInput } from '../save-to-table/types';

export type TableSyncStatusLevel = 'pending' | 'syncing' | 'success' | 'retrying' | 'skipped';

export type TableSyncStatus = {
  phone: string;
  level: TableSyncStatusLevel;
  message: string;
  detail?: string;
};

type TableSyncState = {
  tableSyncPrompt: SaveProfileInput | null;
  tableSyncStatus: TableSyncStatus | null;
};

export function createCustomerProfileTableSyncController(
  state: TableSyncState,
  skipPendingPrompt: () => void,
  timeoutMs = 15_000
) {
  let promptTimer: number | null = null;

  function clearPrompt(): void {
    if (promptTimer) {
      window.clearTimeout(promptTimer);
      promptTimer = null;
    }
    state.tableSyncPrompt = null;
  }

  function showPrompt(input: SaveProfileInput): void {
    clearPrompt();
    state.tableSyncPrompt = input;
    promptTimer = window.setTimeout(skipPendingPrompt, timeoutMs);
  }

  function setStatus(phone: string, level: TableSyncStatusLevel, message: string, detail?: string): void {
    state.tableSyncStatus = { phone, level, message, detail };
  }

  function clearStatus(): void {
    state.tableSyncStatus = null;
  }

  function cleanup(): void {
    clearPrompt();
  }

  return { showPrompt, clearPrompt, setStatus, clearStatus, cleanup };
}
