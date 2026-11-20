'use strict';
const test = require('node:test');
const assert = require('node:assert');
const { isPrivateIp, isHostSafe, siteOf, sameSite } = require('../src/netguard');

test('private ranges are blocked', () => {
  for (const ip of ['127.0.0.1', '10.1.2.3', '172.16.0.1', '192.168.1.1', '169.254.169.254', '100.64.0.1', '0.0.0.0', '::1', 'fd00::1', '::ffff:127.0.0.1']) {
    assert.strictEqual(isPrivateIp(ip), true, ip);
  }
  for (const ip of ['8.8.8.8', '93.184.216.34', '2606:4700::1111']) assert.strictEqual(isPrivateIp(ip), false, ip);
});

test('localhost hostnames are blocked', async () => {
  assert.strictEqual(await isHostSafe('localhost'), false);
  assert.strictEqual(await isHostSafe('127.0.0.1'), false);
  assert.strictEqual(await isHostSafe('metadata.internal'), false);
});

test('site matching', () => {
  assert.strictEqual(siteOf('www.example.com'), 'example.com');
  assert.strictEqual(siteOf('shop.example.co.uk'), 'example.co.uk');
  assert.ok(sameSite('blog.example.com', 'example.com'));
  assert.ok(!sameSite('evil-example.com', 'example.com'));
});
