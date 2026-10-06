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

const server = http.createServer(async (req, res) => {
  if (TOKEN && req.headers['x-token'] !== TOKEN) { res.writeHead(401); return res.end('unauthorized'); }
  if (req.method === 'GET' && req.url === '/ping') { res.writeHead(200); return res.end('pong'); }
  if (req.method === 'GET' && req.url.startsWith('/ls')) {
    const fs = require('fs'), path = require('path');
    let p = '/root';
    try { p = path.resolve(decodeURIComponent((req.url.split('?p=')[1] || '/root').split('&')[0]) || '/'); } catch (e) {}
    let dirs = [];
    try { dirs = fs.readdirSync(p, { withFileTypes: true }).filter((d) => { if (d.name.startsWith('.')) return false; try { return d.isDirectory() || fs.statSync(path.join(p, d.name)).isDirectory(); } catch (e) { return false; } }).map((d) => d.name).sort(); } catch (e) {}
    res.writeHead(200, { 'Content-Type': 'application/json' });
    return res.end(JSON.stringify({ path: p, parent: path.dirname(p), dirs }));
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
