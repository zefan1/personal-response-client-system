import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { existsSync, readdirSync } from 'node:fs';
import { join } from 'node:path';

const root = process.cwd();
const electronVersion = '40.10.2';
const cacheRoot = join(process.env.LOCALAPPDATA ?? '', 'electron', 'Cache');

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
  '--platform=win32',
  '--arch=x64',
  '--out=release',
  '--overwrite',
  '--asar',
  `--electron-version=${electronVersion}`,
  '--ignore=(^/src|^/node_modules/\\.cache|^/release|^/scripts|^/electron-builder\\.yml|^/tsconfig.*|^/vite\\.config\\.ts|^/index\\.html)'
];

if (electronZipDir) {
  args.push(`--electron-zip-dir=${electronZipDir}`);
}

const child = spawn(process.execPath, [packagerBin, ...args], {
  cwd: root,
  stdio: 'inherit'
});

const [code] = await once(child, 'exit');
process.exit(code ?? 1);
