import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { listPackage } from '@electron/asar';
import { existsSync, mkdirSync, readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const root = process.cwd();
const releaseDir = join(root, 'release');
const reportDir = join(root, '..', '.tools', 'desktop');
const platformDirName = process.platform === 'win32'
  ? 'Private Domain Assistant-win32-x64'
  : process.platform === 'darwin'
    ? 'mac'
    : 'linux-unpacked';
const platformDir = join(releaseDir, platformDirName);

const executableName = process.platform === 'win32'
  ? 'Private Domain Assistant.exe'
  : process.platform === 'darwin'
    ? 'Private Domain Assistant.app'
    : 'private-domain-assistant';
const executablePath = join(platformDir, executableName);
const asarPath = process.platform === 'darwin'
  ? join(executablePath, 'Contents', 'Resources', 'app.asar')
  : join(platformDir, 'resources', 'app.asar');
const asarUnpackedPath = process.platform === 'darwin'
  ? join(executablePath, 'Contents', 'Resources', 'app.asar.unpacked')
  : join(platformDir, 'resources', 'app.asar.unpacked');

function sha256(path) {
  return createHash('sha256').update(readFileSync(path)).digest('hex');
}

function presentEnv(name) {
  return Boolean(process.env[name] && process.env[name]?.trim());
}

function signingConfiguration() {
  if (process.platform === 'win32') {
    const hasCertificateFile = presentEnv('WINDOWS_CERTIFICATE_FILE') && presentEnv('WINDOWS_CERTIFICATE_PASSWORD');
    const hasCustomParams = presentEnv('WINDOWS_SIGN_WITH_PARAMS');
    const hasHookModule = presentEnv('WINDOWS_SIGN_HOOK_MODULE_PATH');
    const configuredKeys = [
      'WINDOWS_CERTIFICATE_FILE',
      'WINDOWS_CERTIFICATE_PASSWORD',
      'WINDOWS_SIGNTOOL_PATH',
      'WINDOWS_SIGN_WITH_PARAMS',
      'WINDOWS_SIGN_HOOK_MODULE_PATH',
      'WINDOWS_TIMESTAMP_SERVER',
      'WINDOWS_SIGN_DESCRIPTION',
      'WINDOWS_SIGN_WEBSITE'
    ].filter(presentEnv);
    const certificateConfigured = hasCertificateFile || hasCustomParams || hasHookModule;
    return {
      certificateConfigured,
      configuredKeys,
      packagerSigningSupported: true,
      signingProvider: hasHookModule ? 'windows-hook' : hasCustomParams ? 'windows-sign-with-params' : hasCertificateFile ? 'windows-pfx' : null
    };
  }
  if (process.platform === 'darwin') {
    const certificateConfigured = presentEnv('PDA_MAC_CODESIGN_IDENTITY') || presentEnv('MAC_CODESIGN_IDENTITY');
    const passwordNotarizationConfigured = presentEnv('APPLE_ID') &&
      presentEnv('APPLE_APP_SPECIFIC_PASSWORD') &&
      (presentEnv('APPLE_TEAM_ID') || presentEnv('TEAM_ID'));
    const apiKeyNotarizationConfigured = presentEnv('APPLE_API_KEY') &&
      presentEnv('APPLE_API_KEY_ID') &&
      presentEnv('APPLE_API_ISSUER');
    const keychainNotarizationConfigured = presentEnv('APPLE_KEYCHAIN_PROFILE');
    const configuredKeys = [
      'PDA_MAC_CODESIGN_IDENTITY',
      'MAC_CODESIGN_IDENTITY',
      'PDA_MAC_ENTITLEMENTS',
      'PDA_MAC_ENTITLEMENTS_INHERIT',
      'APPLE_ID',
      'APPLE_APP_SPECIFIC_PASSWORD',
      'APPLE_TEAM_ID',
      'TEAM_ID',
      'APPLE_API_KEY',
      'APPLE_API_KEY_ID',
      'APPLE_API_ISSUER',
      'APPLE_KEYCHAIN_PROFILE'
    ].filter(presentEnv);
    return {
      certificateConfigured,
      notarizationConfigured: passwordNotarizationConfigured || apiKeyNotarizationConfigured || keychainNotarizationConfigured,
      configuredKeys,
      packagerSigningSupported: true,
      signingProvider: certificateConfigured ? 'macos-codesign-identity' : null
    };
  }
  return { certificateConfigured: false, configuredKeys: [], packagerSigningSupported: false, signingProvider: null };
}

function windowsAuthenticodeStatus(path) {
  if (process.platform !== 'win32' || !existsSync(path)) {
    return null;
  }
  const script = [
    '$sig = Get-AuthenticodeSignature -LiteralPath $env:PDA_EXE_PATH',
    '[Console]::OutputEncoding = [System.Text.Encoding]::UTF8',
    '$payload = [ordered]@{',
    '  status = [string]$sig.Status',
    '  statusMessage = [string]$sig.StatusMessage',
    '  signerCertificateSubject = if ($sig.SignerCertificate) { [string]$sig.SignerCertificate.Subject } else { $null }',
    '  signerCertificateThumbprint = if ($sig.SignerCertificate) { [string]$sig.SignerCertificate.Thumbprint } else { $null }',
    '  timeStamperCertificateSubject = if ($sig.TimeStamperCertificate) { [string]$sig.TimeStamperCertificate.Subject } else { $null }',
    '  signed = $sig.Status -eq "Valid"',
    '}',
    '$payload | ConvertTo-Json -Compress'
  ].join('\n');
  const result = spawnSync('powershell.exe', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', script], {
    env: { ...process.env, PDA_EXE_PATH: path },
    encoding: 'utf8'
  });
  if (result.status !== 0) {
    return {
      status: 'CHECK_FAILED',
      statusMessage: (result.stderr || result.stdout || '').trim(),
      signed: false
    };
  }
  try {
    return JSON.parse(result.stdout.trim());
  } catch {
    return {
      status: 'CHECK_PARSE_FAILED',
      statusMessage: result.stdout.trim(),
      signed: false
    };
  }
}

function macCodeSignStatus(path) {
  if (process.platform !== 'darwin' || !existsSync(path)) {
    return null;
  }
  const verify = spawnSync('codesign', ['--verify', '--deep', '--strict', '--verbose=2', path], { encoding: 'utf8' });
  const display = spawnSync('codesign', ['--display', '--verbose=4', path], { encoding: 'utf8' });
  const output = `${display.stdout || ''}${display.stderr || ''}`;
  const authority = output
    .split(/\r?\n/)
    .filter((line) => line.trim().startsWith('Authority='))
    .map((line) => line.trim().replace(/^Authority=/, ''));
  return {
    status: verify.status === 0 ? 'Valid' : 'Invalid',
    statusMessage: (verify.stderr || verify.stdout || '').trim(),
    signed: verify.status === 0,
    authority
  };
}

const failures = [];
for (const [label, path] of [['release directory', platformDir], ['application executable', executablePath], ['application asar', asarPath]]) {
  if (!existsSync(path)) {
    failures.push(`${label} missing: ${path}`);
  }
}

const files = existsSync(platformDir) ? readdirSync(platformDir) : [];
const signConfig = signingConfiguration();
const signature = windowsAuthenticodeStatus(executablePath);
const macSignature = macCodeSignStatus(executablePath);
const signed = process.platform === 'win32'
  ? Boolean(signature?.signed)
  : process.platform === 'darwin'
    ? Boolean(macSignature?.signed)
    : false;
const requireSigned = process.env.PDA_REQUIRE_SIGNED_PACKAGE === '1';
if (requireSigned && !signed) {
  failures.push(`signed package required but executable is not signed: ${executablePath}`);
}
const requireNotarized = process.env.PDA_REQUIRE_NOTARIZED_PACKAGE === '1';
if (process.platform === 'darwin' && requireNotarized && !signConfig.notarizationConfigured) {
  failures.push('notarized package required but Apple notarization credentials are not configured');
}

let asarEntries = [];
let getWindowsEntries = [];
let getWindowsNativeEntries = [];
if (existsSync(asarPath)) {
  try {
    asarEntries = listPackage(asarPath).map((entry) => entry.replaceAll('\\', '/').replace(/^\/+/, ''));
    getWindowsEntries = asarEntries.filter((entry) => entry.startsWith('node_modules/get-windows/'));
    getWindowsNativeEntries = getWindowsEntries.filter((entry) => entry.endsWith('.node'));
    if (getWindowsEntries.length === 0) {
      failures.push('app.asar does not contain node_modules/get-windows');
    }
    if (getWindowsNativeEntries.length > 0) {
      const missingUnpackedNativeEntries = getWindowsNativeEntries.filter((entry) => !existsSync(join(asarUnpackedPath, entry)));
      if (missingUnpackedNativeEntries.length > 0) {
        failures.push(`get-windows native files missing from app.asar.unpacked: ${missingUnpackedNativeEntries.join(', ')}`);
      }
    }
  } catch (error) {
    failures.push(`unable to inspect app.asar: ${error instanceof Error ? error.message : String(error)}`);
  }
}

const report = {
  platform: process.platform,
  releaseDir: platformDir,
  executablePath,
  asarPath,
  topLevelFiles: files,
  asarSha256: existsSync(asarPath) ? sha256(asarPath) : null,
  asarBytes: existsSync(asarPath) ? statSync(asarPath).size : 0,
  asarEntryCount: asarEntries.length,
  getWindowsEntries: getWindowsEntries.length,
  getWindowsNativeEntries,
  getWindowsUnpackedPath: join(asarUnpackedPath, 'node_modules', 'get-windows'),
  getWindowsUnpackedPresent: existsSync(join(asarUnpackedPath, 'node_modules', 'get-windows')),
  signed,
  signature,
  macSignature,
  signingConfiguration: signConfig,
  requireSigned,
  requireNotarized,
  signingNote: signed
    ? 'Executable signature is valid according to local platform verification.'
    : 'No valid production signature was detected on this local package. Set PDA_REQUIRE_SIGNED_PACKAGE=1 in release CI to fail unsigned artifacts.'
};

mkdirSync(reportDir, { recursive: true });
const reportPath = join(reportDir, 'package_verify_report.json');
writeFileSync(reportPath, JSON.stringify(report, null, 2), 'utf8');

if (failures.length > 0) {
  console.error(failures.join('\n'));
  process.exit(1);
}

console.log(`package_verify_report=${reportPath}`);
console.log(`package_verify=passed platform=${report.platform} signed=${report.signed} signingConfigured=${Boolean(signConfig.certificateConfigured)} asarBytes=${report.asarBytes}`);
