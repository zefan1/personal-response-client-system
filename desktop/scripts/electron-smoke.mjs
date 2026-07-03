import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { existsSync } from 'node:fs';
import { join } from 'node:path';

const root = process.cwd();
const mainFile = join(root, 'dist', 'main', 'main.js');
const rendererFile = join(root, 'dist', 'renderer', 'index.html');

if (!existsSync(mainFile) || !existsSync(rendererFile)) {
  console.error('electron smoke requires built dist/main/main.js and dist/renderer/index.html');
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
    ELECTRON_ENABLE_LOGGING: '1',
    PDA_ELECTRON_SMOKE: '1'
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
}, 15000);

const [code] = await once(child, 'exit');
clearTimeout(timer);

if (code !== 0) {
  console.error(output);
  process.exit(code ?? 1);
}

console.log('electron_smoke=passed');
