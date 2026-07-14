import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  getJson: vi.fn(),
  saveDesktopConfig: vi.fn()
}));

vi.mock('./apiClient', () => ({
  getJson: mocks.getJson
}));

vi.mock('./config', () => ({
  saveDesktopConfig: mocks.saveDesktopConfig
}));

import {
  applyDesktopStatus,
  desktopStatusState,
  loadDesktopStatus,
  resetDesktopStatus
} from './desktopStatusStore';

describe('desktop status permissions', () => {
  beforeEach(() => {
    resetDesktopStatus();
    mocks.getJson.mockResolvedValue({
      success: true,
      data: {
        accountName: '组长',
        role: 'LEADER',
        permissions: ['TAG_MANAGEMENT', 'TAG_MANAGEMENT', ''],
        skillStatus: { status: 'OK', label: '正常' },
        llmStatus: { status: 'OK', label: '正常', replyGenerationEnabled: false },
        runtimeConfig: { clipboardScreenshotConfirmPromptS: 15 }
      },
      errorCode: null,
      message: null
    });
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('loads and persists normalized permissions from the desktop status endpoint', async () => {
    await loadDesktopStatus();

    expect(mocks.getJson).toHaveBeenCalledWith('/api/v1/desktop/status');
    expect(desktopStatusState.loaded).toBe(true);
    expect(desktopStatusState.role).toBe('LEADER');
    expect(desktopStatusState.permissions).toEqual(['TAG_MANAGEMENT']);
    expect(mocks.saveDesktopConfig).toHaveBeenCalledWith({
      accountPermissions: ['TAG_MANAGEMENT'],
      clipboardScreenshotConfirmPromptS: 15
    });
  });

  it('clears delegated permissions on reset', () => {
    applyDesktopStatus({ role: 'KEEPER', permissions: ['TAG_MANAGEMENT'] });
    expect(desktopStatusState.permissions).toEqual(['TAG_MANAGEMENT']);

    resetDesktopStatus();

    expect(desktopStatusState.loaded).toBe(false);
    expect(desktopStatusState.permissions).toEqual([]);
  });
});
