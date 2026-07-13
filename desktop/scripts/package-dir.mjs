import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { existsSync, readdirSync } from 'node:fs';
import { join } from 'node:path';

const root = process.cwd();
const electronVersion = '40.10.2';
const cacheRoot = join(process.env.LOCALAPPDATA ?? '', 'electron', 'Cache');
const targetPlatform = process.env.PDA_PACKAGE_PLATFORM ?? process.platform;
const targetArch = process.env.PDA_PACKAGE_ARCH ?? 'x64';
const requireSigned = process.env.PDA_REQUIRE_SIGNED_PACKAGE === '1';

function presentEnv(name) {
  return Boolean(process.env[name] && process.env[name]?.trim());
}

function envValue(...names) {
  for (const name of names) {
    const value = process.env[name]?.trim();
    if (value) {
      return value;
    }
  }
  return '';
}

function findElectronZipDir() {
  if (!existsSync(cacheRoot)) {
    return null;
  }
  const wanted = `electron-v${electronVersion}-win32-x64.zip`;
  for (const dir of readdirSync(cacheRoot, { withFileTypes: true })) {
    if (!dir.isDirectory()) {
      continue;
    }
    const candidateDir = join(cacheRoot, dir.name);
    if (existsSync(join(candidateDir, wanted))) {
      return candidateDir;
    }
  }
  return null;
}

const electronZipDir = findElectronZipDir();
const packagerBin = join(root, 'node_modules', '@electron', 'packager', 'bin', 'electron-packager.mjs');
const args = [
  '.',
  'Private Domain Assistant',
  `--platform=${targetPlatform}`,
  `--arch=${targetArch}`,
  '--out=release',
  '--overwrite',
  '--asar',
  `--electron-version=${electronVersion}`,
  '--ignore=(^/src|^/node_modules/\\.cache|^/release|^/scripts|^/electron-builder\\.yml|^/tsconfig.*|^/vite\\.config\\.ts|^/index\\.html)'
];

if (electronZipDir) {
  args.push(`--electron-zip-dir=${electronZipDir}`);
}

if (targetPlatform === 'win32') {
  const hasCertificateFile = presentEnv('WINDOWS_CERTIFICATE_FILE') && presentEnv('WINDOWS_CERTIFICATE_PASSWORD');
  const hasCustomParams = presentEnv('WINDOWS_SIGN_WITH_PARAMS');
  const hasHookModule = presentEnv('WINDOWS_SIGN_HOOK_MODULE_PATH');
  const canAttemptWindowsSigning = hasCertificateFile || hasCustomParams || hasHookModule;
  if (requireSigned && process.platform !== 'win32') {
    console.error('PDA_REQUIRE_SIGNED_PACKAGE=1 requires Windows host signing for win32 packages.');
    process.exit(1);
  }
  if (requireSigned && !canAttemptWindowsSigning) {
    console.error('PDA_REQUIRE_SIGNED_PACKAGE=1 requires WINDOWS_CERTIFICATE_FILE/WINDOWS_CERTIFICATE_PASSWORD, WINDOWS_SIGN_WITH_PARAMS, or WINDOWS_SIGN_HOOK_MODULE_PATH.');
    process.exit(1);
  }
  if (canAttemptWindowsSigning) {
    args.push('--windows-sign.description=Private Domain Assistant');
  }
}

if (targetPlatform === 'darwin') {
  const identity = envValue('PDA_MAC_CODESIGN_IDENTITY', 'MAC_CODESIGN_IDENTITY');
  if (requireSigned && process.platform !== 'darwin') {
    console.error('PDA_REQUIRE_SIGNED_PACKAGE=1 requires a macOS host to sign darwin packages.');
    process.exit(1);
  }
  if (requireSigned && !identity) {
    console.error('PDA_REQUIRE_SIGNED_PACKAGE=1 requires PDA_MAC_CODESIGN_IDENTITY or MAC_CODESIGN_IDENTITY for darwin packages.');
    process.exit(1);
  }
  if (identity) {
    args.push(`--osx-sign.identity=${identity}`, '--osx-sign.continueOnError=false');
    const entitlements = envValue('PDA_MAC_ENTITLEMENTS');
    const entitlementsInherit = envValue('PDA_MAC_ENTITLEMENTS_INHERIT');
    if (entitlements) {
      args.push(`--osx-sign.entitlements=${entitlements}`);
    }
    if (entitlementsInherit) {
      args.push(`--osx-sign.entitlements-inherit=${entitlementsInherit}`);
    }
  }
}

const child = spawn(process.execPath, [packagerBin, ...args], {
  cwd: root,
  stdio: 'inherit'
});

const [code] = await once(child, 'exit');
process.exit(code ?? 1);
