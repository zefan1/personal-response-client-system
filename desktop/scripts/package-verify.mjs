import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';
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

function sha256(path) {
  return createHash('sha256').update(readFileSync(path)).digest('hex');
}

function presentEnv(name) {
  return Boolean(process.env[name] && process.env[name]?.trim());
}

function signingConfiguration() {
  if (process.platform === 'win32') {
    const configuredKeys = [
      'CSC_LINK',
      'CSC_KEY_PASSWORD',
      'WIN_CSC_LINK',
      'WIN_CSC_KEY_PASSWORD',
      'WINDOWS_CERTIFICATE_FILE',
      'WINDOWS_CERTIFICATE_PASSWORD',
      'AZURE_TENANT_ID',
      'AZURE_CLIENT_ID',
      'AZURE_CLIENT_SECRET',
      'AZURE_KEY_VAULT_URI',
      'AZURE_KEY_VAULT_CERTIFICATE_NAME'
    ].filter(presentEnv);
    const certificateConfigured = (
      (presentEnv('CSC_LINK') && presentEnv('CSC_KEY_PASSWORD')) ||
      (presentEnv('WIN_CSC_LINK') && presentEnv('WIN_CSC_KEY_PASSWORD')) ||
      (presentEnv('WINDOWS_CERTIFICATE_FILE') && presentEnv('WINDOWS_CERTIFICATE_PASSWORD')) ||
      (presentEnv('AZURE_TENANT_ID') && presentEnv('AZURE_CLIENT_ID') && presentEnv('AZURE_CLIENT_SECRET') &&
        presentEnv('AZURE_KEY_VAULT_URI') && presentEnv('AZURE_KEY_VAULT_CERTIFICATE_NAME'))
    );
    return { certificateConfigured, configuredKeys };
  }
  if (process.platform === 'darwin') {
    const configuredKeys = [
      'CSC_LINK',
      'CSC_KEY_PASSWORD',
      'APPLE_ID',
      'APPLE_APP_SPECIFIC_PASSWORD',
      'APPLE_TEAM_ID'
    ].filter(presentEnv);
    return {
      certificateConfigured: presentEnv('CSC_LINK') && presentEnv('CSC_KEY_PASSWORD'),
      notarizationConfigured: presentEnv('APPLE_ID') && presentEnv('APPLE_APP_SPECIFIC_PASSWORD') && presentEnv('APPLE_TEAM_ID'),
      configuredKeys
    };
  }
  return { certificateConfigured: false, configuredKeys: [] };
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

const report = {
  platform: process.platform,
  releaseDir: platformDir,
  executablePath,
  asarPath,
  topLevelFiles: files,
  asarSha256: existsSync(asarPath) ? sha256(asarPath) : null,
  asarBytes: existsSync(asarPath) ? statSync(asarPath).size : 0,
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
