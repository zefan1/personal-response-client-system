import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { SaveProfileInput } from '../save-to-table/types';
import { createCustomerProfileTableSyncController, type TableSyncStatus } from './customerProfileTableSync';

type TableSyncState = {
  tableSyncPrompt: SaveProfileInput | null;
  tableSyncStatus: TableSyncStatus | null;
};

function input(phone = '18800001111'): SaveProfileInput {
  return {
    phone,
    editedFields: {},
    version: 1,
    hasTableRow: true
  };
}

describe('customerProfileTableSync', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  it('replaces a pending sync prompt and only skips the latest one after its timeout', () => {
    const state: TableSyncState = { tableSyncPrompt: null, tableSyncStatus: null };
    const skip = vi.fn();
    const controller = createCustomerProfileTableSyncController(state, skip);

    controller.showPrompt(input('18800001111'));
    controller.showPrompt(input('18800002222'));
    vi.advanceTimersByTime(15_000);

    expect(skip).toHaveBeenCalledTimes(1);
    expect(state.tableSyncPrompt).toMatchObject({ phone: '18800002222' });
  });

  it('clears the prompt timer and records the current table sync status', () => {
    const state: TableSyncState = { tableSyncPrompt: null, tableSyncStatus: null };
    const skip = vi.fn();
    const controller = createCustomerProfileTableSyncController(state, skip);

    controller.showPrompt(input());
    controller.clearPrompt();
    controller.setStatus('18800001111', 'success', 'synced', 'detail');
    vi.advanceTimersByTime(15_000);

    expect(skip).not.toHaveBeenCalled();
    expect(state.tableSyncPrompt).toBeNull();
    expect(state.tableSyncStatus).toEqual({ phone: '18800001111', level: 'success', message: 'synced', detail: 'detail' });
    controller.clearStatus();
    expect(state.tableSyncStatus).toBeNull();
  });
});
