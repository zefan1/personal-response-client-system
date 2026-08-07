import { describe, expect, it } from 'vitest';
import { resolveAdminConsoleUrl } from './adminConsoleUrl.js';

describe('resolveAdminConsoleUrl', () => {
  it('uses the configured HTTP address for packaged builds instead of a file URL from the desktop window', () => {
    expect(resolveAdminConsoleUrl({
      requestedUrl: 'file:///C:/Program%20Files/private-domain-assistant/index.html#/admin',
      configuredUrl: 'http://127.0.0.1:5173/#/admin',
      devServerUrl: undefined,
      isDev: false
    })).toBe('http://127.0.0.1:5173/#/admin');
  });
});
