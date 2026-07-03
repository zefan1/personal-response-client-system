import { createHash } from 'node:crypto';
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

const failures = [];
for (const [label, path] of [['release directory', platformDir], ['application executable', executablePath], ['application asar', asarPath]]) {
  if (!existsSync(path)) {
    failures.push(`${label} missing: ${path}`);
  }
}

const files = existsSync(platformDir) ? readdirSync(platformDir) : [];
const report = {
  platform: process.platform,
  releaseDir: platformDir,
  executablePath,
  asarPath,
  topLevelFiles: files,
  asarSha256: existsSync(asarPath) ? sha256(asarPath) : null,
  asarBytes: existsSync(asarPath) ? statSync(asarPath).size : 0,
  signed: false,
  signingNote: 'No production code-signing certificate is configured in this local build; this verifies packaged artifact structure only.'
};

mkdirSync(reportDir, { recursive: true });
const reportPath = join(reportDir, 'package_verify_report.json');
writeFileSync(reportPath, JSON.stringify(report, null, 2), 'utf8');

if (failures.length > 0) {
  console.error(failures.join('\n'));
  process.exit(1);
}

console.log(`package_verify_report=${reportPath}`);
console.log(`package_verify=passed platform=${report.platform} signed=${report.signed} asarBytes=${report.asarBytes}`);
