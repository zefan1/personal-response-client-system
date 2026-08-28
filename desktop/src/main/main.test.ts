import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

describe('Electron main process startup', () => {
  const source = readFileSync(resolve(process.cwd(), 'src/main/main.ts'), 'utf8');

  it('uses the executable path before packaging and a stable product id after packaging', () => {
    const identityConstant = "const WINDOWS_APP_USER_MODEL_ID = app.isPackaged ? 'com.privateflow.private-domain-assistant' : process.execPath;";

    expect(source).toContain(identityConstant);
  });

  it('sets the app user model id only on Windows and before the app becomes ready', () => {
    const identityCall = 'app.setAppUserModelId(WINDOWS_APP_USER_MODEL_ID);';
    const windowsGuard = /if \(process\.platform === 'win32'\) \{\s+app\.setAppUserModelId\(WINDOWS_APP_USER_MODEL_ID\);\s+\}/;

    expect(source).toMatch(windowsGuard);
    expect(source.indexOf(identityCall)).toBeGreaterThanOrEqual(0);
    expect(source.indexOf(identityCall)).toBeLessThan(source.indexOf('app.whenReady()'));
  });

  it('smoke checks the unified speech-library sidebar action', () => {
    expect(source).toContain("actionLabels.join('|') !== '识别|话术库|批量|预约'");
  });
});
