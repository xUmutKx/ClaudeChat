#!/usr/bin/env node
// Claude Chat bridge: exposes the local `claude` CLI to the Android app over 127.0.0.1.
// Usage: CC_TOKEN=secret node bridge.js   (port: CC_PORT, default 8787)
const http = require('http');
const { spawn } = require('child_process');

const PORT = parseInt(process.env.CC_PORT || '8787', 10);
const TOKEN = process.env.CC_TOKEN || '';
const MODES = new Set(['default', 'acceptEdits', 'plan', 'bypassPermissions']);
let proc = null;
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

const server = http.createServer(async (req, res) => {
  if (TOKEN && req.headers['x-token'] !== TOKEN) { res.writeHead(401); return res.end('unauthorized'); }
  if (req.method === 'GET' && req.url === '/ping') { res.writeHead(200); return res.end('pong'); }
  if (req.method !== 'POST' || req.url !== '/chat') { res.writeHead(404); return res.end(); }

  let body;
  try { body = await readBody(req); } catch (e) { res.writeHead(400); return res.end('bad json'); }
  if (!body.prompt) { res.writeHead(400); return res.end('prompt required'); }

  // claude is kept alive between turns (stream-json input): no start-up cost and a warm prompt cache.
  let mode = MODES.has(body.mode) ? body.mode : 'acceptEdits';
  // claude refuses bypassPermissions when running as root, so fall back to acceptEdits there
  if (mode === 'bypassPermissions' && process.getuid && process.getuid() === 0) mode = 'acceptEdits';
  const model = typeof body.model === 'string' && /^[\w.\-\[\]]{1,80}$/.test(body.model) ? body.model : '';
  const effort = ['low', 'medium', 'high', 'xhigh', 'max'].includes(body.effort) ? body.effort : '';
  const addDir = typeof body.addDir === 'string' && body.addDir.startsWith('/') ? body.addDir : '';
  const cwd = body.cwd || '/root/projects';
  const key = JSON.stringify([cwd, mode, model, effort, addDir]);

  const reuse = proc && proc.child.exitCode === null && !proc.sink && proc.key === key && body.session && body.session === proc.session;
  if (!reuse) {
    if (proc) { try { proc.child.kill('SIGTERM'); } catch (e) {} proc = null; }
    const args = ['-p', '--input-format', 'stream-json', '--output-format', 'stream-json', '--verbose', '--include-partial-messages'];
    if (body.session) args.push('--resume', String(body.session));
    if (addDir) args.push('--add-dir', addDir);
    if (model) args.push('--model', model);
    if (effort) args.push('--effort', effort);
    args.push('--permission-mode', mode);
    const child = spawn('claude', args, { cwd, stdio: ['pipe', 'pipe', 'pipe'] });
    const p = { child, key, session: body.session ? String(body.session) : '', sink: null, buf: '', err: '' };
    proc = p;
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
          if (o && o.type === 'result') { const s = p.sink; p.sink = null; s.end(); }
        }
      }
    });
    child.on('close', (code) => {
      if (proc === p) proc = null;
      if (p.sink) { p.sink.write(JSON.stringify({ type: 'bridge_exit', code, stderr: p.err.trim() }) + '\n'); p.sink.end(); p.sink = null; }
    });
  }

  const p = proc;
  res.writeHead(200, { 'Content-Type': 'application/x-ndjson', 'Cache-Control': 'no-cache' });
  p.sink = res;
  p.err = '';
  res.on('close', () => { if (p.sink === res) { p.sink = null; try { p.child.kill('SIGTERM'); } catch (e) {} if (proc === p) proc = null; } });
  p.child.stdin.write(JSON.stringify({ type: 'user', message: { role: 'user', content: String(body.prompt) } }) + '\n');
});

server.on('error', (e) => { console.error('server error: ' + e.message); if (e.code === 'EADDRINUSE') process.exit(1); });
server.listen(PORT, '127.0.0.1', () => console.log('claude-chat bridge on 127.0.0.1:' + PORT));
