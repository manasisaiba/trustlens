'use strict';
/**
 * TrustLens browser worker.
 * A small internal HTTP service that drives real Chromium via Playwright.
 * It exposes ONLY a fixed set of browser tools; it never executes arbitrary code from callers.
 * Safety: SSRF/private-IP blocking on every request, navigation locked to the target site,
 * all non-GET requests aborted (so forms, bookings, payments and messages can never be sent),
 * downloads/popups/dialogs suppressed, per-session action caps and idle timeouts.
 */
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { chromium } = require('playwright');
const { isHostSafe, siteOf, sameSite } = require('./netguard');

const PORT = Number(process.env.PORT || 4000);
const SCREENSHOT_DIR = path.resolve(process.env.SCREENSHOT_DIR || './data/screenshots');
const MAX_SESSIONS = Number(process.env.MAX_SESSIONS || 8);
const MAX_ACTIONS = Number(process.env.MAX_ACTIONS_PER_SESSION || 40);
const SESSION_IDLE_MS = Number(process.env.SESSION_IDLE_MS || 5 * 60 * 1000);
const NAV_TIMEOUT = Number(process.env.NAV_TIMEOUT_MS || 25000);
const TEXT_LIMIT = 8000;

fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });

let browser = null;
async function getBrowser() {
  if (browser && browser.isConnected()) return browser;
  const opts = { args: ['--disable-dev-shm-usage', '--no-sandbox'] };
  if (process.env.UPSTREAM_PROXY) opts.proxy = { server: process.env.UPSTREAM_PROXY, bypass: process.env.UPSTREAM_PROXY_BYPASS || '' };
  browser = await chromium.launch(opts);
  browser.on('disconnected', () => { browser = null; });
  return browser;
}

/** @type {Map<string, any>} */
const sessions = new Map();

function log(...a) { console.log(new Date().toISOString(), ...a); }

// ---------------------------------------------------------------- page helpers

const LABEL_SCRIPT = () => {
  const out = [];
  document.querySelectorAll('[data-tl-id]').forEach((el) => el.removeAttribute('data-tl-id'));
  const sel = 'a[href], button, input:not([type=hidden]), select, textarea, [role=button], [role=link], [role=tab], [role=menuitem], summary, [onclick]';
  const els = Array.from(document.querySelectorAll(sel));
  let n = 0;
  for (const el of els) {
    if (out.length >= 120) break;
    const r = el.getBoundingClientRect();
    const st = window.getComputedStyle(el);
    if (r.width < 2 || r.height < 2 || st.visibility === 'hidden' || st.display === 'none' || Number(st.opacity) === 0) continue;
    const id = 'e' + (++n);
    el.setAttribute('data-tl-id', id);
    if (el.tagName === 'A') el.removeAttribute('target');
    const text = (el.innerText || el.getAttribute('aria-label') || el.getAttribute('title') || el.value || el.getAttribute('placeholder') || el.getAttribute('alt') || '')
      .replace(/\s+/g, ' ').trim().slice(0, 80);
    const item = { id, tag: el.tagName.toLowerCase(), text };
    const role = el.getAttribute('role'); if (role) item.role = role;
    if (el.tagName === 'A') item.href = el.href;
    if (el.type && el.tagName !== 'A' && el.tagName !== 'BUTTON') item.type = el.type;
    if (el.name) item.name = el.name;
    if (r.top > window.innerHeight) item.belowFold = true;
    out.push(item);
  }
  const text = (document.body ? document.body.innerText : '').replace(/[ \t]+/g, ' ').replace(/\n\s*\n+/g, '\n').trim();
  return { elements: out, text, title: document.title };
};

async function snapshot(s, { includeImage = false, fullPage = false, label = 'step' } = {}) {
  const page = s.page;
  let data = { elements: [], text: '', title: '' };
  try {
    data = await page.evaluate(LABEL_SCRIPT);
  } catch (e) {
    data.text = '(page could not be inspected: ' + e.message + ')';
  }
  s.elements = new Map(data.elements.map((e) => [e.id, e]));
  s.shotCount += 1;
  const dir = path.join(SCREENSHOT_DIR, s.folder);
  fs.mkdirSync(dir, { recursive: true });
  const file = `${s.label}-${String(s.shotCount).padStart(2, '0')}-${label}.jpg`;
  let imageBase64;
  try {
    const buf = await page.screenshot({ type: 'jpeg', quality: 60, fullPage, timeout: 15000 });
    fs.writeFileSync(path.join(dir, file), buf);
    if (includeImage && !fullPage) imageBase64 = buf.toString('base64');
  } catch (e) {
    log('screenshot failed', e.message);
  }
  return {
    url: page.url(),
    title: data.title,
    text: data.text.slice(0, TEXT_LIMIT),
    textTruncated: data.text.length > TEXT_LIMIT,
    elements: data.elements,
    screenshot: `${s.folder}/${file}`,
    imageBase64,
    blockedRequests: s.blocked.slice(-5),
  };
}

async function settle(page) {
  try { await page.waitForLoadState('domcontentloaded', { timeout: NAV_TIMEOUT }); } catch {}
  try { await page.waitForLoadState('networkidle', { timeout: 4000 }); } catch {}
}

async function createSession({ startUrl, folder, label }) {
  if (sessions.size >= MAX_SESSIONS) throw httpError(429, 'Too many concurrent browser sessions');
  let u;
  try { u = new URL(startUrl); } catch { throw httpError(400, 'Invalid startUrl'); }
  if (!['http:', 'https:'].includes(u.protocol)) throw httpError(400, 'Only http(s) URLs are allowed');
  if (!(await isHostSafe(u.hostname))) throw httpError(400, 'Target host is private, internal or unresolvable');

  const b = await getBrowser();
  const context = await b.newContext({
    viewport: { width: 1280, height: 800 },
    acceptDownloads: false,
    javaScriptEnabled: true,
    serviceWorkers: 'block',
    permissions: [],
    userAgent: 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36 TrustLensAudit/0.1',
  });
  const id = crypto.randomUUID();
  const s = {
    id, context, page: null, site: siteOf(u.hostname), startUrl: u.toString(),
    folder: (folder || 'misc').replace(/[^a-zA-Z0-9_-]/g, ''), label: (label || 'p').replace(/[^a-zA-Z0-9_-]/g, ''),
    shotCount: 0, actions: 0, elements: new Map(), blocked: [], lastUsed: Date.now(),
  };

  await context.route('**/*', async (route) => {
    const req = route.request();
    let ru;
    try { ru = new URL(req.url()); } catch { return route.abort(); }
    if (ru.protocol === 'data:' || ru.protocol === 'blob:') return route.continue();
    if (!['http:', 'https:'].includes(ru.protocol)) { s.blocked.push(`scheme ${ru.protocol}`); return route.abort(); }
    const method = req.method();
    if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
      s.blocked.push(`${method} ${ru.hostname}${ru.pathname} (non-GET requests are blocked: nothing is submitted)`);
      return route.abort('blockedbyclient');
    }
    if (!(await isHostSafe(ru.hostname))) { s.blocked.push(`private host ${ru.hostname}`); return route.abort('blockedbyclient'); }
    if (req.isNavigationRequest() && req.frame() === s.page?.mainFrame() && !sameSite(ru.hostname, s.site)) {
      s.blocked.push(`navigation to external site ${ru.hostname}`);
      return route.abort('blockedbyclient');
    }
    return route.continue();
  });
  context.on('page', async (p) => { if (s.page && p !== s.page) { try { await p.close(); } catch {} } });

  const page = await context.newPage();
  s.page = page;
  page.on('dialog', (d) => d.dismiss().catch(() => {}));
  page.setDefaultTimeout(10000);
  sessions.set(id, s);
  try {
    const resp = await page.goto(s.startUrl, { timeout: NAV_TIMEOUT, waitUntil: 'domcontentloaded' });
    await settle(page);
    // If the site redirected to a different registrable domain (e.g. example.com -> example.net), lock to the final site.
    try { s.site = siteOf(new URL(page.url()).hostname); } catch {}
    const state = await snapshot(s, { includeImage: true, label: 'start' });
    return { sessionId: id, ok: true, httpStatus: resp ? resp.status() : null, state };
  } catch (e) {
    await destroy(id);
    throw httpError(502, 'Could not load the website: ' + shortErr(e));
  }
}

function shortErr(e) { return String(e && e.message ? e.message : e).split('\n')[0].slice(0, 300); }
function httpError(status, message) { const e = new Error(message); e.status = status; return e; }

async function destroy(id) {
  const s = sessions.get(id);
  if (!s) return;
  sessions.delete(id);
  try { await s.context.close(); } catch {}
}

async function runAction(id, body) {
  const s = sessions.get(id);
  if (!s) throw httpError(404, 'Session not found or expired');
  s.lastUsed = Date.now();
  if (++s.actions > MAX_ACTIONS) throw httpError(429, 'Action limit reached for this session');
  const page = s.page;
  const type = body.type;
  const includeImage = !!body.includeImage;
  const before = page.url();
  let note = '';
  let ok = true;

  try {
    switch (type) {
      case 'inspect_page':
      case 'record_current_url':
      case 'take_screenshot':
      case 'extract_visible_text':
        note = type === 'record_current_url' ? `Current URL: ${page.url()}` : '';
        break;
      case 'navigate_to_page': {
        let target;
        try { target = new URL(body.url, page.url()); } catch { throw httpError(400, 'Invalid url'); }
        if (!sameSite(target.hostname, s.site)) { ok = false; note = `Blocked: ${target.hostname} is outside the audited website.`; break; }
        await page.goto(target.toString(), { timeout: NAV_TIMEOUT, waitUntil: 'domcontentloaded' });
        await settle(page);
        break;
      }
      case 'click_element': {
        const el = s.elements.get(body.elementId);
        if (!el) { ok = false; note = `Element ${body.elementId} does not exist on the current page.`; break; }
        if (el.href && /^(tel|mailto|sms|whatsapp):/i.test(el.href)) {
          note = `Element is a contact link (${el.href}). Not activated to avoid contacting the business; contact method is visible.`;
          break;
        }
        if (el.href) {
          try {
            const hu = new URL(el.href);
            if (/^https?:$/.test(hu.protocol) && !sameSite(hu.hostname, s.site)) {
              ok = false; note = `Link points to external site ${hu.hostname}; navigation blocked by safety policy.`; break;
            }
          } catch {}
        }
        const loc = page.locator(`[data-tl-id="${body.elementId}"]`).first();
        await loc.scrollIntoViewIfNeeded({ timeout: 5000 }).catch(() => {});
        await loc.click({ timeout: 8000 });
        await settle(page);
        break;
      }
      case 'fill_form': {
        const fields = Array.isArray(body.fields) ? body.fields.slice(0, 10) : [];
        const filled = [];
        for (const f of fields) {
          const el = s.elements.get(f.elementId);
          if (!el || !['input', 'textarea', 'select'].includes(el.tag)) continue;
          if (['password', 'file', 'hidden', 'submit', 'image'].includes(el.type)) continue;
          const value = String(f.value ?? '').slice(0, 100);
          const loc = page.locator(`[data-tl-id="${f.elementId}"]`).first();
          if (el.tag === 'select') await loc.selectOption({ label: value }).catch(() => loc.selectOption({ index: 1 }).catch(() => {}));
          else if (el.type === 'checkbox' || el.type === 'radio') await loc.check({ timeout: 3000 }).catch(() => {});
          else await loc.fill(value, { timeout: 5000 });
          filled.push(`${el.name || el.text || f.elementId}="${value}"`);
        }
        note = filled.length ? `Filled with synthetic data (not submitted): ${filled.join(', ')}` : 'No fillable fields matched.';
        ok = filled.length > 0;
        break;
      }
      case 'scroll':
        await page.mouse.wheel(0, 700);
        await page.waitForTimeout(600);
        break;
      default:
        throw httpError(400, `Unsupported action type: ${type}`);
    }
  } catch (e) {
    if (e.status) throw e;
    ok = false;
    note = 'Action failed: ' + shortErr(e);
  }
  if (page.url().startsWith('chrome-error://')) {
    ok = false;
    note = (note ? note + ' ' : '') + 'The page failed to load (network error or blocked).';
    try {
      await page.goto(before.startsWith('chrome-error://') ? s.startUrl : before, { timeout: NAV_TIMEOUT, waitUntil: 'domcontentloaded' });
      await settle(page);
      note += ' Returned to the previous page.';
    } catch {}
  }
  const state = await snapshot(s, { includeImage, fullPage: !!body.fullPage, label: type.replace(/_.*/, '') });
  if (type === 'extract_visible_text') state.text = state.text; // already full (limited) text
  return { ok, note, navigated: before !== page.url(), state };
}

// ---------------------------------------------------------------- HTTP server

function send(res, status, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(status, { 'content-type': 'application/json', 'content-length': Buffer.byteLength(body) });
  res.end(body);
}

function readJson(req) {
  return new Promise((resolve, reject) => {
    let size = 0; const chunks = [];
    req.on('data', (c) => { size += c.length; if (size > 1e6) { reject(httpError(413, 'Body too large')); req.destroy(); } else chunks.push(c); });
    req.on('end', () => { try { resolve(chunks.length ? JSON.parse(Buffer.concat(chunks).toString()) : {}); } catch { reject(httpError(400, 'Invalid JSON')); } });
    req.on('error', reject);
  });
}

const server = http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url, 'http://x');
    const parts = url.pathname.split('/').filter(Boolean);
    if (req.method === 'GET' && url.pathname === '/health') {
      return send(res, 200, { status: 'ok', sessions: sessions.size, browserConnected: !!(browser && browser.isConnected()) });
    }
    if (req.method === 'POST' && url.pathname === '/sessions') {
      const body = await readJson(req);
      return send(res, 201, await createSession(body));
    }
    if (parts[0] === 'sessions' && parts[1]) {
      if (req.method === 'POST' && parts[2] === 'actions') return send(res, 200, await runAction(parts[1], await readJson(req)));
      if (req.method === 'DELETE' && parts.length === 2) { await destroy(parts[1]); return send(res, 200, { ok: true }); }
    }
    send(res, 404, { error: 'Not found' });
  } catch (e) {
    const status = e.status || 500;
    if (status >= 500) log('error', e);
    send(res, status, { error: e.message || 'Internal error' });
  }
});

setInterval(() => {
  const now = Date.now();
  for (const [id, s] of sessions) if (now - s.lastUsed > SESSION_IDLE_MS) { log('expiring idle session', id); destroy(id); }
}, 30000).unref();

process.on('SIGTERM', async () => { for (const id of sessions.keys()) await destroy(id); if (browser) await browser.close(); process.exit(0); });

getBrowser().then(() => log('chromium ready')).catch((e) => log('chromium failed to launch', e.message));
server.listen(PORT, () => log(`browser worker listening on :${PORT}, screenshots -> ${SCREENSHOT_DIR}`));
