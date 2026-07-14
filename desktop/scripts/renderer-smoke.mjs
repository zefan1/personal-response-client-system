import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { existsSync, mkdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const root = process.cwd();
const mainFile = join(root, 'dist', 'main', 'main.js');
const rendererFile = join(root, 'dist', 'renderer', 'index.html');
const smokeUserDataDir = join(tmpdir(), `pda-renderer-smoke-${process.pid}-${Date.now()}`);
const screenshotDir = process.env.PDA_RENDERER_SMOKE_SCREENSHOT_DIR ?? join(root, '..', '.tools', 'screenshots');
const apiBaseUrl = process.env.PDA_SMOKE_API_BASE_URL ?? 'http://localhost:8080';
const accessToken = await loginForSmoke(apiBaseUrl);

if (!existsSync(mainFile) || !existsSync(rendererFile)) {
  console.error('renderer smoke requires built dist/main/main.js and dist/renderer/index.html');
  process.exit(1);
}

const electronBin = process.platform === 'win32'
  ? join(root, 'node_modules', '.bin', 'electron.cmd')
  : join(root, 'node_modules', '.bin', 'electron');
const command = process.platform === 'win32' ? 'cmd.exe' : electronBin;
const args = process.platform === 'win32' ? ['/c', electronBin, '.'] : ['.'];

const child = spawn(command, args, {
  cwd: root,
  env: {
    ...process.env,
    PDA_ELECTRON_SMOKE: '1',
    PDA_ELECTRON_SMOKE_AUTO_QUIT: '0',
    PDA_RENDERER_SMOKE: '1',
    PDA_RENDERER_SMOKE_ACCESS_TOKEN: accessToken,
    PDA_RENDERER_SMOKE_SCREENSHOT_DIR: screenshotDir,
    PDA_SMOKE_API_BASE_URL: apiBaseUrl,
    PDA_ELECTRON_SMOKE_USER_DATA_DIR: smokeUserDataDir
  },
  stdio: ['ignore', 'pipe', 'pipe']
});

mkdirSync(smokeUserDataDir, { recursive: true });
mkdirSync(screenshotDir, { recursive: true });

let output = '';
child.stdout.on('data', (chunk) => {
  output += chunk.toString();
});
child.stderr.on('data', (chunk) => {
  output += chunk.toString();
});

const timer = setTimeout(() => {
  child.kill();
}, 75000);

const [code] = await once(child, 'exit');
clearTimeout(timer);

if (code !== 0) {
  console.error(output);
  process.exit(code ?? 1);
}

if (!output.includes('renderer_smoke=passed')) {
  console.error(output);
  console.error('renderer smoke did not report success');
  process.exit(1);
}

console.log('renderer_smoke=passed');

async function loginForSmoke(baseUrl) {
  const response = await fetch(`${baseUrl}/admin/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      username: process.env.PDA_SMOKE_ADMIN_USERNAME ?? 'admin',
      password: process.env.PDA_SMOKE_ADMIN_PASSWORD ?? 'admin123'
    })
  });
  const payload = await response.json();
  const token = payload?.data?.accessToken;
  if (!response.ok || !payload?.success || !token) {
    throw new Error(`renderer smoke login failed: status=${response.status} message=${payload?.message ?? 'unknown'}`);
  }
  return token;
}
