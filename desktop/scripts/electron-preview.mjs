import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { join } from 'node:path';

const root = process.cwd();
const port = Number(process.env.PDA_PREVIEW_PORT ?? '4173');
const previewUrl = `http://127.0.0.1:${port}`;
const viteBin = process.platform === 'win32'
  ? join(root, 'node_modules', '.bin', 'vite.cmd')
  : join(root, 'node_modules', '.bin', 'vite');
const electronBin = process.platform === 'win32'
  ? join(root, 'node_modules', '.bin', 'electron.cmd')
  : join(root, 'node_modules', '.bin', 'electron');

const preview = spawnCommand(viteBin, ['preview', '--host', '127.0.0.1', '--port', String(port), '--strictPort']);
let electron;

try {
  await waitForPreview(previewUrl, preview);
  electron = spawnCommand(electronBin, ['.'], {
    PDA_ADMIN_CONSOLE_URL: `${previewUrl}/#/admin`
  });
  const [code] = await once(electron, 'exit');
  process.exitCode = code ?? 1;
} finally {
  preview.kill();
}

function spawnCommand(command, args, extraEnv = {}) {
  if (process.platform === 'win32') {
    return spawn('cmd.exe', ['/c', command, ...args], {
      cwd: root,
      env: { ...process.env, ...extraEnv },
      stdio: 'inherit'
    });
  }
  return spawn(command, args, {
    cwd: root,
    env: { ...process.env, ...extraEnv },
    stdio: 'inherit'
  });
}

async function waitForPreview(url, processHandle) {
  const deadline = Date.now() + 15000;
  while (Date.now() < deadline) {
    if (processHandle.exitCode !== null) throw new Error('管理后台预览服务启动失败');
    try {
      const response = await fetch(url);
      if (response.ok) return;
    } catch {
      // The preview server is still starting.
    }
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  throw new Error('管理后台预览服务启动超时');
}
