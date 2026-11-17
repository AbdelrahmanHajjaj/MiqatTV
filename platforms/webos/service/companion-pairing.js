'use strict';
var crypto = require('crypto'), wire = require('./companion-wire');
function tokenShape(value) { return typeof value === 'string' && /^[A-Za-z0-9_-]{43}$/.test(value); }
function token() { return crypto.randomBytes(32).toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, ''); }
function hash(value) { return crypto.createHash('sha256').update(value, 'utf8').digest('hex'); }
function equal(a, b) {
  if (typeof a !== 'string' || typeof b !== 'string') { return false; }
  var left = wire.buffer(a), right = wire.buffer(b);
  if (left.length !== right.length) { return false; }
  if (typeof crypto.timingSafeEqual === 'function') { return crypto.timingSafeEqual(left, right); }
  var difference = 0;
  for (var i = 0; i < left.length; i++) { difference |= left[i] ^ right[i]; }
  return difference === 0;
}
function name(value) {
  // Explicit Unicode control/format ranges also work on the ES5 TV runtime.
  return typeof value === 'string' && value.trim().length > 0 && value.length <= 64 &&
    !/[\x00-\x1f\x7f-\x9f\u00ad\u0600-\u0605\u061c\u06dd\u070f\u0890-\u0891\u08e2\u180e\u200b-\u200f\u202a-\u202e\u2060-\u206f\ufeff\ufff9-\ufffb]|\ud804[\udcbd\udccd]|\ud80d[\udc30-\udc3f]|\ud82f[\udca0-\udca3]|\ud834[\udd73-\udd7a]|\udb40[\udc01\udc20-\udc7f]/.test(value);
}
function code(window, nonce) {
  if (!tokenShape(window) || !tokenShape(nonce)) { throw new Error('Invalid pairing token'); }
  return ('000000' + parseInt(hash(window + ':' + nonce).slice(0, 8), 16) % 1000000).slice(-6);
}
function copy(value) { return value === null ? null : JSON.parse(JSON.stringify(value)); }
function Pairing(store, config) {
  // Names must reject invisible controls on the same runtime that handles TLS.
  name('runtime');
  var clients = store.read();
  if (!Array.isArray(clients) || clients.length > config.maxPhones) { throw new Error('Invalid paired phone store'); }
  var ids = Object.create(null);
  clients.forEach(function (client) {
    if (!client || Object.keys(client).sort().join(',') !== 'hash,id,name' || !tokenShape(client.id) || !name(client.name) ||
        typeof client.hash !== 'string' || !/^[0-9a-f]{64}$/.test(client.hash) || ids[client.id]) { throw new Error('Invalid paired phone'); }
    ids[client.id] = true;
  });
  clients = copy(clients);
  var window = '', deadline = 0, pending = null, grant = null;
  function close() { window = ''; deadline = 0; pending = null; grant = null; }
  function expire(now) { if (window && now >= deadline) { close(); } }
  function check(candidate, now) {
    expire(now);
    if (!window || !tokenShape(candidate) || !equal(window, candidate)) { throw new Error('Pairing expired'); }
  }
  function requestMatches(nonce, now) {
    expire(now);
    if (!pending || !tokenShape(nonce) || !equal(pending.nonce, nonce)) { throw new Error('No matching request'); }
  }
  this.open = function (now) {
    if (clients.length >= config.maxPhones) { throw new Error('Phone limit reached'); }
    close(); window = token(); deadline = now + config.pairWindowMillis; return window;
  };
  this.close = close;
  this.state = function (now) { expire(now); return {open: !!window, pending: copy(pending), approved: grant !== null}; };
  this.request = function (candidate, phoneName, nonce, now) {
    check(candidate, now);
    if (!name(phoneName) || !tokenShape(nonce)) { throw new Error('Invalid pairing request'); }
    if (pending) {
      if (!equal(pending.nonce, nonce) || pending.name !== phoneName) { throw new Error('Pairing busy'); }
    } else { pending = {name: phoneName, nonce: nonce, code: code(window, nonce)}; }
    return copy(pending);
  };
  // Only the sender-checked local TV service methods may call approve/reject/revoke.
  this.approve = function (nonce, now) { requestMatches(nonce, now); if (!grant) { grant = {id: token(), secret: token()}; } };
  this.reject = function (nonce, now) { requestMatches(nonce, now); close(); };
  this.poll = function (candidate, nonce, now) { check(candidate, now); requestMatches(nonce, now); return copy(grant); };
  this.finish = function (id, secret, now) {
    expire(now);
    if (!grant || !tokenShape(id) || !tokenShape(secret) || !equal(grant.id, id) || !equal(grant.secret, secret)) { throw new Error('Unknown grant'); }
    var next = copy(clients); next.push({id: id, name: pending.name, hash: hash(secret)});
    store.write(copy(next)); clients = next; close();
  };
  this.authenticate = function (id, secret) {
    if (!tokenShape(id) || !tokenShape(secret)) { return false; }
    var digest = hash(secret);
    return clients.some(function (client) { return equal(client.id, id) && equal(client.hash, digest); });
  };
  this.list = function () { return clients.map(function (client) { return {id: client.id, name: client.name}; }); };
  this.revoke = function (id) {
    if (!tokenShape(id)) { throw new Error('Unknown phone'); }
    var next = clients.filter(function (client) { return !equal(client.id, id); });
    if (next.length === clients.length) { throw new Error('Unknown phone'); }
    store.write(copy(next)); clients = next;
  };
}
module.exports = {Pairing: Pairing, tokenShape: tokenShape, token: token, hash: hash, code: code, equal: equal};
