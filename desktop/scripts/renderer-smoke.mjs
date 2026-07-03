import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { existsSync } from 'node:fs';
import { join } from 'node:path';

const root = process.cwd();
const mainFile = join(root, 'dist', 'main', 'main.js');
const rendererFile = join(root, 'dist', 'renderer', 'index.html');

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
    VITE_DEV_SERVER_URL: process.env.VITE_DEV_SERVER_URL ?? 'http://127.0.0.1:5173',
    PDA_ELECTRON_SMOKE: '1',
    PDA_ELECTRON_SMOKE_AUTO_QUIT: '0',
    PDA_RENDERER_SMOKE: '1'
  },
  stdio: ['ignore', 'pipe', 'pipe']
});

let output = '';
child.stdout.on('data', (chunk) => {
  output += chunk.toString();
});
child.stderr.on('data', (chunk) => {
  output += chunk.toString();
});

const timer = setTimeout(() => {
  child.kill();
}, 45000);

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
