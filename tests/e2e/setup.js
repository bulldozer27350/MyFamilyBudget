'use strict';

const { spawn } = require('child_process');
const path      = require('path');
const http      = require('http');

const ROOT      = path.resolve(__dirname, '..', '..');
const BACK_DIR  = path.join(ROOT, 'back', 'server');
const VIEW_DIR  = path.join(ROOT, 'view');

function log(tag, msg) {
  process.stdout.write('[' + tag + '] ' + msg + '\n');
}

function isUrlReady(urlStr) {
  return new Promise(resolve => {
    try {
      const req = http.get(urlStr, res => {
        resolve(res.statusCode >= 200 && res.statusCode < 500);
      });
      req.on('error', () => resolve(false));
      req.setTimeout(1500, () => {
        req.destroy();
        resolve(false);
      });
    } catch (_) {
      resolve(false);
    }
  });
}

function spawnProc(cmd, args, cwd, tag, extraEnv) {
  const isWin = process.platform === 'win32';
  const proc = spawn(cmd, args, {
    cwd,
    stdio: ['ignore', 'pipe', 'pipe'],
    shell: isWin,
    env: Object.assign({}, process.env, extraEnv || {}),
  });
  proc.stdout.on('data', d => log(tag, d.toString().trimEnd()));
  proc.stderr.on('data', d => log(tag + ':err', d.toString().trimEnd()));
  return proc;
}

function waitOn(resources, timeout) {
  timeout = timeout || 120000;
  return new Promise((resolve, reject) => {
    const isWin = process.platform === 'win32';
    const cmd  = isWin ? 'npx.cmd' : 'npx';
    const args = ['wait-on'].concat(resources, ['--timeout', String(timeout)]);
    const proc = spawn(cmd, args, { stdio: 'inherit', shell: isWin });
    proc.on('close', code => {
      if (code === 0) resolve();
      else reject(new Error('wait-on failed with code ' + code));
    });
  });
}

function runPlaywright() {
  return new Promise(resolve => {
    const cfg   = path.join(ROOT, 'playwright.config.js');
    const isWin = process.platform === 'win32';
    const cmd   = isWin ? 'npx.cmd' : 'npx';
    const args  = ['playwright', 'test', '--config', cfg];
    const proc  = spawn(cmd, args, { stdio: 'inherit', shell: isWin, cwd: ROOT });
    proc.on('close', code => resolve(code));
  });
}

function kill(proc) {
  if (!proc) return;
  try {
    if (process.platform === 'win32') {
      spawn('taskkill', ['/PID', String(proc.pid), '/F', '/T'], { shell: false });
    } else {
      process.kill(-proc.pid, 'SIGTERM');
    }
  } catch (_) {}
}

(async () => {
  log('setup', 'ROOT = ' + ROOT);

  const backUrl  = 'http://localhost:8080/api/v1/overview';
  const frontUrl = 'http://localhost:3000';

  let backendProc  = null;
  let frontendProc = null;

  // 1. Backend Spring Boot
  const backendAlreadyUp = await isUrlReady(backUrl);
  if (backendAlreadyUp) {
    log('setup', 'Spring Boot backend is already running.');
  } else {
    log('setup', 'Starting Spring Boot backend...');
    const mvnCmd = process.platform === 'win32' ? 'mvn.cmd' : 'mvn';
    backendProc = spawnProc(mvnCmd, ['spring-boot:run', '-q'], BACK_DIR, 'backend');
  }

  // 2. Frontend Express server
  const frontendAlreadyUp = await isUrlReady(frontUrl);
  if (frontendAlreadyUp) {
    log('setup', 'Frontend server is already running.');
  } else {
    log('setup', 'Starting frontend server...');
    frontendProc = spawnProc('node', ['server.js'], VIEW_DIR, 'frontend', {
      TARGET_SPRINGBOOT: 'http://localhost:8080',
      BACKEND_URL: 'http://localhost:8080/api/v1'
    });
  }

  // 3. Wait for both servers to be ready
  log('setup', 'Waiting for servers to be reachable...');
  try {
    await waitOn([frontUrl, backUrl]);
  } catch (err) {
    log('setup', 'ERROR: ' + err.message);
    kill(backendProc);
    kill(frontendProc);
    process.exit(1);
  }
  log('setup', 'Both servers are ready.');

  // 4. Launch Playwright tests
  log('setup', 'Launching Playwright tests...');
  const exitCode = await runPlaywright();
  log('setup', 'Playwright exited with code ' + exitCode);

  // 5. Clean shutdown of processes started by setup
  log('setup', 'Cleaning up processes...');
  if (frontendProc) kill(frontendProc);
  if (backendProc)  kill(backendProc);

  process.exit(exitCode);
})();