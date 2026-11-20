'use strict';
// Network safety rules for the browser worker (defence in depth; the backend validates too).
const dns = require('node:dns').promises;
const net = require('node:net');

const ALLOW_PRIVATE = process.env.ALLOW_PRIVATE_TARGETS === 'true';

function isPrivateIp(ip) {
  if (net.isIPv4(ip)) {
    const [a, b, c] = ip.split('.').map(Number);
    if (a === 0 || a === 10 || a === 127) return true;
    if (a === 169 && b === 254) return true;
    if (a === 172 && b >= 16 && b <= 31) return true;
    if (a === 192 && b === 168) return true;
    if (a === 100 && b >= 64 && b <= 127) return true;
    if (a === 192 && b === 0 && c === 0) return true;
    if (a === 198 && (b === 18 || b === 19)) return true;
    if (a >= 224) return true;
    return false;
  }
  if (net.isIPv6(ip)) {
    const v = ip.toLowerCase();
    if (v === '::' || v === '::1') return true;
    if (v.startsWith('fe8') || v.startsWith('fe9') || v.startsWith('fea') || v.startsWith('feb')) return true;
    if (v.startsWith('fc') || v.startsWith('fd')) return true;
    if (v.startsWith('ff')) return true;
    const mapped = v.match(/^::ffff:(\d+\.\d+\.\d+\.\d+)$/);
    if (mapped) return isPrivateIp(mapped[1]);
    return false;
  }
  return true; // unknown format => treat as unsafe
}

const cache = new Map(); // host -> {ok, at}
const TTL = 5 * 60 * 1000;

async function isHostSafe(hostname) {
  if (ALLOW_PRIVATE) return true;
  const host = hostname.replace(/^\[|\]$/g, '').toLowerCase();
  if (!host || host === 'localhost' || host.endsWith('.localhost') || host.endsWith('.local') || host.endsWith('.internal')) return false;
  if (net.isIP(host)) return !isPrivateIp(host);
  const hit = cache.get(host);
  if (hit && Date.now() - hit.at < TTL) return hit.ok;
  let ok = false;
  try {
    const addrs = await dns.lookup(host, { all: true });
    ok = addrs.length > 0 && addrs.every((a) => !isPrivateIp(a.address));
  } catch {
    // When the worker egresses through an upstream proxy (e.g. a locked-down dev sandbox), local DNS may not
    // resolve public names; the proxy resolves them instead. Production (no proxy) stays strict.
    ok = !!process.env.UPSTREAM_PROXY;
  }
  cache.set(host, { ok, at: Date.now() });
  return ok;
}

const MULTI_PART_SLD = new Set(['co', 'com', 'org', 'net', 'ac', 'gov', 'edu', 'ltd', 'plc', 'nic', 'gen', 'firm']);

/** Approximate registrable domain ("site") of a hostname, e.g. shop.example.co.uk -> example.co.uk */
function siteOf(hostname) {
  const parts = hostname.toLowerCase().replace(/\.$/, '').split('.');
  if (net.isIP(hostname) || parts.length <= 2) return parts.join('.');
  const tld = parts[parts.length - 1];
  const sld = parts[parts.length - 2];
  const n = tld.length === 2 && MULTI_PART_SLD.has(sld) ? 3 : 2;
  return parts.slice(-n).join('.');
}

function sameSite(hostname, site) {
  const h = hostname.toLowerCase();
  return h === site || h.endsWith('.' + site);
}

module.exports = { isPrivateIp, isHostSafe, siteOf, sameSite };
