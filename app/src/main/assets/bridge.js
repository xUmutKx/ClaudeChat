#!/usr/bin/env node
// Claude Chat bridge: exposes the local `claude` CLI to the Android app over 127.0.0.1.
// Usage: CC_TOKEN=secret node bridge.js   (port: CC_PORT, default 8787)
const http = require('http');
const os = require('os');
const { spawn } = require('child_process');

const PORT = parseInt(process.env.CC_PORT || '8787', 10);
const TOKEN = process.env.CC_TOKEN || '';
const MODES = new Set(['default', 'acceptEdits', 'plan', 'bypassPermissions']);
const MAX_IDLE = 3; // finished chats keep a warm claude process; only the newest few are kept
const procs = new Map(); // chat id -> { child, key, session, sink, buf, err, last }
process.on('uncaughtException', (e) => { console.error('uncaught: ' + (e && e.stack || e)); });
process.on('unhandledRejection', (e) => { console.error('unhandled: ' + e); });

function readBody(req) {
  return new Promise((resolve, reject) => {
    let data = '';
    req.on('data', (c) => { data += c; if (data.length > 2e6) req.destroy(); });
    req.on('end', () => { try { resolve(JSON.parse(data || '{}')); } catch (e) { reject(e); } });
    req.on('error', reject);
  });
}

function drop(id) {
  const p = procs.get(id);
  if (!p) return;
  procs.delete(id);
  try { p.child.kill('SIGTERM'); } catch (e) {}
}

function trimIdle() {
  const idle = [...procs.entries()].filter(([, p]) => !p.sink).sort((a, b) => a[1].last - b[1].last);
  while (idle.length > MAX_IDLE) drop(idle.shift()[0]);
}

// one-off topic title for a conversation (separate short claude call; does not touch the chat processes)
function makeTitle(user, reply) {
  return new Promise((resolve) => {
    const prompt = 'Write a short title (2 to 6 words) for the topic of this conversation. Use the same language as the user. ' +
      "Describe the subject, do not copy the user's words. No quotes, no trailing punctuation. Reply with only the title.\n\n" +
      'User: ' + user + '\n\nAssistant: ' + reply;
    let out = '';
    let done = false;
    const finish = (t) => { if (!done) { done = true; resolve(t); } };
    let child;
    try { child = spawn('claude', ['-p', '--model', 'haiku'], { cwd: os.tmpdir(), stdio: ['pipe', 'pipe', 'ignore'] }); }
    catch (e) { return finish(''); }
    const timer = setTimeout(() => { try { child.kill('SIGTERM'); } catch (e) {} finish(''); }, 50000);
    child.on('error', () => { clearTimeout(timer); finish(''); });
    child.stdin.on('error', () => {});
    child.stdout.on('data', (d) => { out += d; });
    child.on('close', () => { clearTimeout(timer); finish(out.trim().split('\n')[0].trim()); });
    child.stdin.end(prompt);
  });
}

// ---- sign-in helpers: Claude opens in the Android browser, GitHub shows a device code --------------------------------
// `claude auth login` and `gh auth login` open the browser through $BROWSER. Pointing it at a tiny script hands the link to the app,
// which opens it on the phone; the answer comes back to the CLI over localhost, so nothing has to be copied by hand.
const fs2 = require('fs'), path2 = require('path'), { execFile } = require('child_process');
const LOGIN_DIR = path2.join(process.env.HOME || '/root', 'claudechat');
const stripAnsi = (t) => t.replace(/\x1b\][^\x07\x1b]*(?:\x07|\x1b\\)/g, '').replace(/\x1b\[[0-9;?]*[ -\/]*[@-~]/g, '').replace(/\r/g, '');
const LOGIN_CMD = { claude: 'claude auth login', gh: 'gh auth login -h github.com -p https -w' };
const logins = {}; // 'claude' | 'gh' -> { child, url, code, out, state, started, pressed }

function ensureOpener() {
  try {
    fs2.mkdirSync(LOGIN_DIR, { recursive: true });
    fs2.writeFileSync(path2.join(LOGIN_DIR, 'openurl.sh'), '#!/bin/sh\nprintf "%s" "$1" > "' + LOGIN_DIR + '/login-${CC_LOGIN:-x}.url"\n', { mode: 0o755 });
  } catch (e) {}
}

function startLogin(what) {
  if (!LOGIN_CMD[what]) return null;
  const old = logins[what];
  if (old && old.child) { old.state = 'cancelled'; try { old.child.kill('SIGTERM'); } catch (e) {} }
  ensureOpener();
  try { fs2.unlinkSync(path2.join(LOGIN_DIR, 'login-' + what + '.url')); } catch (e) {}
  const st = { child: null, url: '', code: '', out: '', state: 'starting', started: Date.now(), pressed: false };
  logins[what] = st;
  let child;
  try {
    // `script` gives the CLI a terminal (it prints its prompts only to one); -e passes the exit status through
    child = spawn('script', ['-qefc', LOGIN_CMD[what], '/dev/null'], {
      env: Object.assign({}, process.env, { BROWSER: path2.join(LOGIN_DIR, 'openurl.sh'), CC_LOGIN: what, TERM: 'dumb' }),
      stdio: ['pipe', 'pipe', 'pipe'],
    });
  } catch (e) { st.state = 'error'; st.out = String(e); return st; }
  st.child = child;
  const feed = (d) => {
    st.out = (st.out + stripAnsi(d.toString())).slice(-4000);
    if (what === 'gh') {
      const m = /one-time code:\s*([A-Z0-9]{4}-[A-Z0-9]{4})/i.exec(st.out);
      if (m) st.code = m[1];
      if (!st.pressed && /Press Enter/i.test(st.out)) { st.pressed = true; try { child.stdin.write('\n'); } catch (e) {} }
    }
  };
  child.stdout.on('data', feed);
  child.stderr.on('data', feed);
  child.stdin.on('error', () => {});
  child.on('error', (e) => { st.state = 'error'; st.out += '\n' + e; });
  child.on('close', (code) => {
    st.child = null;
    if (st.state === 'cancelled') return;
    st.state = code === 0 ? 'done' : 'error';
    if (what === 'gh' && code === 0) { try { spawn('gh', ['auth', 'setup-git'], { stdio: 'ignore' }); } catch (e) {} }
  });
  return st;
}

function loginView(what) {
  const st = logins[what];
  if (!st) return { state: 'idle', url: '', code: '', out: '', age: 0 };
  if (!st.url) { try { st.url = fs2.readFileSync(path2.join(LOGIN_DIR, 'login-' + what + '.url'), 'utf8').trim(); } catch (e) {} }
  const state = st.state === 'starting' && (st.url || st.code) ? 'waiting' : st.state;
  return { state, url: st.url, code: st.code, out: st.out.slice(-300), age: Math.round((Date.now() - st.started) / 1000) };
}

function run(cmd, args, ms) {
  return new Promise((resolve) => {
    try { execFile(cmd, args, { timeout: ms || 8000, maxBuffer: 1 << 20 }, (err, stdout, stderr) => resolve({ err, out: String(stdout || ''), errOut: String(stderr || '') })); }
    catch (e) { resolve({ err: e, out: '', errOut: '' }); }
  });
}

async function authStatus() {
  const [c, g] = await Promise.all([run('claude', ['auth', 'status']), run('gh', ['auth', 'status'])]);
  let claude = { loggedIn: false, email: '' };
  try { const j = JSON.parse(c.out); claude = { loggedIn: !!j.loggedIn, email: j.email || '' }; } catch (e) {}
  const missing = g.err && g.err.code === 'ENOENT';
  const gtxt = stripAnsi(g.out + g.errOut);
  const um = /account\s+(\S+)/i.exec(gtxt);
  const gh = { installed: !missing, loggedIn: !missing && !g.err, user: um ? um[1] : '' };
  return { claude, gh };
}

const server = http.createServer(async (req, res) => {
  if (TOKEN && req.headers['x-token'] !== TOKEN) { res.writeHead(401); return res.end('unauthorized'); }
  if (req.method === 'GET' && req.url === '/ping') { res.writeHead(200); return res.end('pong'); }
  if (req.method === 'GET' && req.url === '/auth') {
    const a = await authStatus();
    res.writeHead(200, { 'Content-Type': 'application/json' });
    return res.end(JSON.stringify(a));
  }
  if (req.method === 'GET' && req.url === '/gh/token') {
    // the GitHub login (gh auth login) doubles as the key for GitHub Models: nothing to copy by hand
    const r = await run('gh', ['auth', 'token']);
    res.writeHead(r.err ? 404 : 200, { 'Content-Type': 'text/plain' });
    return res.end(r.err ? '' : r.out.trim());
  }
  if (req.method === 'GET' && req.url.startsWith('/login/state')) {
    const what = (req.url.split('what=')[1] || '').split('&')[0];
    res.writeHead(200, { 'Content-Type': 'application/json' });
    return res.end(JSON.stringify(loginView(what)));
  }
  if (req.method === 'POST' && req.url.startsWith('/login/')) {
    let b;
    try { b = await readBody(req); } catch (e) { res.writeHead(400); return res.end('bad json'); }
    const what = String(b.what || '');
    if (!LOGIN_CMD[what]) { res.writeHead(400); return res.end('unknown'); }
    if (req.url === '/login/start') startLogin(what);
    else if (req.url === '/login/cancel') { const st = logins[what]; if (st && st.child) { st.state = 'cancelled'; try { st.child.kill('SIGTERM'); } catch (e) {} } }
    else if (req.url === '/login/code') { const st = logins[what]; if (st && st.child) { try { st.child.stdin.write(String(b.code || '').trim() + '\n'); } catch (e) {} } }
    else { res.writeHead(404); return res.end(); }
    res.writeHead(200, { 'Content-Type': 'application/json' });
    return res.end(JSON.stringify(loginView(what)));
  }
  if (req.method === 'GET' && req.url.startsWith('/ls')) {
    const fs = require('fs'), path = require('path');
    let p = '/root';
    try { p = path.resolve(decodeURIComponent((req.url.split('?p=')[1] || '/root').split('&')[0]) || '/'); } catch (e) {}
    let dirs = [];
    try { dirs = fs.readdirSync(p, { withFileTypes: true }).filter((d) => { if (d.name.startsWith('.')) return false; try { return d.isDirectory() || fs.statSync(path.join(p, d.name)).isDirectory(); } catch (e) { return false; } }).map((d) => d.name).sort(); } catch (e) {}
    res.writeHead(200, { 'Content-Type': 'application/json' });
    return res.end(JSON.stringify({ path: p, parent: path.dirname(p), dirs }));
  }
  if (req.method === 'GET' && req.url === '/build') {
    // the app asks every few seconds and the folder walk below is synchronous: answer from a cache so the event loop is never held up
    if (global.__bc && Date.now() - global.__bc.t < 8000) { res.writeHead(200, { 'Content-Type': 'application/json' }); return res.end(global.__bc.s); }
    const __end = res.end.bind(res); res.end = (x) => { global.__bc = { t: Date.now(), s: x }; return __end(x); };
    // newest recent log that looks like a Gradle build (Claude Code background-task output, or a *build*.log in the project folders)
    const fs = require('fs'), path = require('path');
    const found = [];
    const walk = (dir, depth) => {
      if (depth > 6) return;
      let ents = [];
      try { ents = fs.readdirSync(dir, { withFileTypes: true }); } catch (e) { return; }
      for (const e of ents) {
        const f = path.join(dir, e.name);
        if (e.isDirectory()) { if (e.name !== 'node_modules' && e.name !== '.git' && e.name !== 'build' && e.name !== '.gradle') walk(f, depth + 1); }
        else if (/\.(output|log)$/.test(e.name)) { try { const st = fs.statSync(f); if (Date.now() - st.mtimeMs < 30 * 60 * 1000 && st.size > 200) found.push({ f, m: st.mtimeMs, size: st.size }); } catch (x) {} }
      }
    };
    walk('/tmp', 2); walk('/root/projects', 5);
    found.sort((a, b) => b.m - a.m);
    let out = { file: '', age: -1, text: '' };
    for (const c of found.slice(0, 12)) {
      try {
        const fd = fs.openSync(c.f, 'r'); const len = Math.min(c.size, 200000); const buf = Buffer.alloc(len);
        fs.readSync(fd, buf, 0, len, c.size - len); fs.closeSync(fd);
        const t = buf.toString('utf8');
        if (/> Task :|Configure project|BUILD (SUCCESSFUL|FAILED)/.test(t)) { out = { file: c.f, age: Math.round((Date.now() - c.m) / 1000), text: t }; break; }
      } catch (e) {}
    }
    // a Gradle JVM is running right now (the dex / R8 steps print nothing for minutes, so the log alone cannot tell)
    let alive = false;
    const clients = []; // seconds each Gradle client process has been running (the build started when it did)
    try {
      // process start times are in clock ticks since boot; boot time itself is unreliable (proot fakes /proc/uptime), so measure against this process
      const ticksOf = (pid) => { const st = fs.readFileSync('/proc/' + pid + '/stat', 'utf8'); return Number(st.slice(st.lastIndexOf(')') + 2).split(' ')[19]); };
      let selfTicks = NaN; try { selfTicks = ticksOf(process.pid); } catch (x) {}
      for (const d of fs.readdirSync('/proc')) {
        if (!/^\d+$/.test(d) || Number(d) === process.pid) continue;
        try {
          const cmd = fs.readFileSync('/proc/' + d + '/cmdline', 'utf8');
          if (/org\.gradle|GradleDaemon|GradleWrapperMain/.test(cmd)) alive = true;
          if (isFinite(selfTicks) && /GradleWrapperMain|org\.gradle\.launcher\.GradleMain|gradle-launcher/.test(cmd) && !/GradleDaemon/.test(cmd)) {
            const secs = process.uptime() + (selfTicks - ticksOf(d)) / 100;
            if (secs >= 0 && secs < 6 * 3600) clients.push({ secs: Math.round(secs), cmd: cmd.replace(/\0/g, ' ') });
          }
        } catch (x) {}
      }
    } catch (e) {}
    out.alive = alive;
    // which build is this log of? the log is named <project>_build.log or sits in the project folder: prefer the client that mentions that project
    out.elapsed = -1;
    if (clients.length) {
      const stem = path.basename(out.file || '').replace(/(_|-)?build.*$/i, '').replace(/\.(log|output)$/i, '');
      const hit = stem.length > 2 ? clients.filter((c) => c.cmd.toLowerCase().includes(stem.toLowerCase())) : [];
      const pick = (hit.length ? hit : clients).sort((a, b) => a.secs - b.secs)[0];
      out.elapsed = pick.secs;
    }
    res.writeHead(200, { 'Content-Type': 'application/json' });
    return res.end(JSON.stringify(out));
  }
  if (req.method === 'GET' && req.url === '/ps') {
    const list = [...procs.entries()].map(([chat, p]) => ({ chat, pid: p.child.pid, busy: !!p.sink, idle: Math.round((Date.now() - p.last) / 1000) }));
    res.writeHead(200, { 'Content-Type': 'application/json' });
    return res.end(JSON.stringify({ procs: list }));
  }
  if (req.method === 'POST' && req.url === '/kill') {
    let b;
    try { b = await readBody(req); } catch (e) { res.writeHead(400); return res.end('bad json'); }
    drop(String(b.chat || ''));
    res.writeHead(200); return res.end('ok');
  }
  if (req.method === 'POST' && req.url === '/title') {
    let b;
    try { b = await readBody(req); } catch (e) { res.writeHead(400); return res.end('bad json'); }
    const title = await makeTitle(String(b.user || '').slice(0, 600), String(b.reply || '').slice(0, 600));
    res.writeHead(200, { 'Content-Type': 'application/json' });
    return res.end(JSON.stringify({ title }));
  }
  if (req.method !== 'POST' || req.url !== '/chat') { res.writeHead(404); return res.end(); }

  let body;
  try { body = await readBody(req); } catch (e) { res.writeHead(400); return res.end('bad json'); }
  if (!body.prompt) { res.writeHead(400); return res.end('prompt required'); }

  // every chat has its own claude process, so a running chat keeps going while another one is opened.
  // The process is kept alive between turns (stream-json input): no start-up cost and a warm prompt cache.
  const chat = typeof body.chat === 'string' && body.chat ? body.chat.slice(0, 40) : 'default';
  let mode = MODES.has(body.mode) ? body.mode : 'acceptEdits';
  // claude refuses bypassPermissions when running as root, so fall back to acceptEdits there
  let wantBypass = mode === 'bypassPermissions';
  if (wantBypass && process.getuid && process.getuid() === 0) mode = 'acceptEdits';
  // non-interactive (-p) turns cannot answer permission prompts, so anything not pre-allowed is silently denied.
  // Root cannot use bypassPermissions: when the user asked for it, allow every tool instead; otherwise allow the usual build/ship commands.
  const allow = wantBypass ? ['Bash', 'WebFetch', 'WebSearch'] : ['Bash(su:*)', 'Bash(gh:*)', 'Bash(gradle:*)', 'Bash(git:*)', 'Bash(node:*)', 'Bash(cd:*)', 'Bash(export:*)'];
  const model = typeof body.model === 'string' && /^[\w.\-\[\]]{1,80}$/.test(body.model) ? body.model : '';
  const effort = ['low', 'medium', 'high', 'xhigh', 'max'].includes(body.effort) ? body.effort : '';
  const addDir = typeof body.addDir === 'string' && body.addDir.startsWith('/') ? body.addDir : '';
  const cwd = body.cwd || '/root/projects';
  const key = JSON.stringify([cwd, mode, model, effort, addDir, wantBypass]);

  const cur = procs.get(chat);
  const reuse = cur && cur.child.exitCode === null && !cur.sink && cur.key === key && body.session && body.session === cur.session;
  if (!reuse) {
    drop(chat);
    const args = ['-p', '--input-format', 'stream-json', '--output-format', 'stream-json', '--verbose', '--include-partial-messages'];
    if (body.session) args.push('--resume', String(body.session));
    if (addDir) args.push('--add-dir', addDir);
    if (model) args.push('--model', model);
    if (effort) args.push('--effort', effort);
    args.push('--permission-mode', mode);
    args.push('--allowedTools', allow.join(','));
    const child = spawn('claude', args, { cwd, stdio: ['pipe', 'pipe', 'pipe'] });
    const p = { child, key, session: body.session ? String(body.session) : '', sink: null, buf: '', err: '', last: Date.now() };
    procs.set(chat, p);
    child.stderr.on('data', (d) => { p.err += d; if (p.err.length > 8000) p.err = p.err.slice(-8000); });
    child.on('error', (e) => { p.err += String(e); });
    child.stdin.on('error', () => {});
    child.stdout.on('data', (d) => {
      p.buf += d;
      let i;
      while ((i = p.buf.indexOf('\n')) >= 0) {
        const line = p.buf.slice(0, i); p.buf = p.buf.slice(i + 1);
        if (!line.trim()) continue;
        let o = null;
        try { o = JSON.parse(line); } catch (e) {}
        if (o && o.session_id && (o.type === 'result' || (o.type === 'system' && o.subtype === 'init'))) p.session = o.session_id;
        if (p.sink) {
          p.sink.write(line + '\n');
          if (o && o.type === 'result') { const s = p.sink; p.sink = null; p.last = Date.now(); s.end(); trimIdle(); }
        }
      }
    });
    child.on('close', (code) => {
      if (procs.get(chat) === p) procs.delete(chat);
      if (p.sink) { p.sink.write(JSON.stringify({ type: 'bridge_exit', code, stderr: p.err.trim() }) + '\n'); p.sink.end(); p.sink = null; }
    });
  }

  const p = procs.get(chat);
  res.writeHead(200, { 'Content-Type': 'application/x-ndjson', 'Cache-Control': 'no-cache' });
  p.sink = res;
  p.err = '';
  res.on('close', () => { if (p.sink === res) { p.sink = null; drop(chat); } });
  p.child.stdin.write(JSON.stringify({ type: 'user', message: { role: 'user', content: String(body.prompt) } }) + '\n');
});

server.on('error', (e) => { console.error('server error: ' + e.message); if (e.code === 'EADDRINUSE') process.exit(1); });
server.listen(PORT, '127.0.0.1', () => console.log('claude-chat bridge on 127.0.0.1:' + PORT));
