'use strict';
var tls = require('tls'), crypto = require('crypto');
var wire = require('./companion-wire');
function options() {
  if (typeof tls.createSecureContext === 'function') {
    if (!tls.TLSSocket || typeof tls.TLSSocket.prototype.disableRenegotiation !== 'function') { throw new Error('Unsupported TLS'); }
    return {minVersion: 'TLSv1.2', ciphers: 'ECDHE-RSA-AES128-GCM-SHA256:ECDHE-RSA-AES256-GCM-SHA384',
      secureOptions: crypto.constants.SSL_OP_NO_RENEGOTIATION || 0};
  }
  var constants = require('constants');
  if (!constants.SSL_OP_NO_SSLv2 || !constants.SSL_OP_NO_SSLv3 || !constants.SSL_OP_NO_TLSv1 || !constants.SSL_OP_NO_TLSv1_1 ||
      tls.getCiphers().indexOf('aes128-gcm-sha256') < 0) { throw new Error('Unsupported legacy TLS'); }
  return {secureProtocol: 'SSLv23_method', ciphers: 'AES128-GCM-SHA256',
    secureOptions: constants.SSL_OP_NO_SSLv2 | constants.SSL_OP_NO_SSLv3 | constants.SSL_OP_NO_TLSv1 | constants.SSL_OP_NO_TLSv1_1};
}
function supported() {
  try {
    var selected = options();
    if (typeof tls.createSecureContext === 'function') { tls.createSecureContext(selected); }
    else { crypto.createCredentials(selected); }
    if (crypto.randomBytes(32).length !== 32) { return false; }
    return true;
  } catch (error) { return false; }
}
function privateAddress(address, loopback) {
  if (typeof address !== 'string' || !/^(0|[1-9][0-9]{0,2})(\.(0|[1-9][0-9]{0,2})){3}$/.test(address)) { return false; }
  var parts = address.split('.').map(Number);
  return !parts.some(function (n) { return n > 255; }) && (parts[0] === 10 || parts[0] === 172 && parts[1] >= 16 && parts[1] <= 31 ||
    parts[0] === 192 && parts[1] === 168 || loopback === true && parts[0] === 127);
}
function listen(identity, pairing, settings, changed, host, config, ready, loopback) {
  if (!supported() || !privateAddress(host, loopback)) { throw new Error('Unsupported secure LAN'); }
  var sockets = [], closed = false, reported = false;
  function report(error) { if (!reported) { reported = true; ready(error, api); } }
  var selected = options(); selected.key = identity.key; selected.cert = identity.cert;
  selected.honorCipherOrder = true; selected.handshakeTimeout = config.timeoutMillis;
  if (typeof tls.createSecureContext !== 'function') { tls.CLIENT_RENEG_LIMIT = 0; }
  var server = tls.createServer(selected, function (socket) {
    if (closed || !privateAddress(socket.remoteAddress, loopback)) { socket.destroy(); return; }
    if (typeof socket.disableRenegotiation === 'function') { socket.disableRenegotiation(); }
    var input = wire.buffer(''), done = false;
    function respond(status, body) {
      if (done || socket.destroyed) { return; } done = true;
      var text;
      try { text = JSON.stringify(body); } catch (error) { status = 500; text = '{"error":"unavailable"}'; }
      if (typeof text !== 'string' || Buffer.byteLength(text) > config.maxResponseBytes) { status = 500; text = '{"error":"unavailable"}'; }
      socket.end('HTTP/1.1 ' + status + ' Response\r\nContent-Type: application/json\r\nContent-Length: ' + Buffer.byteLength(text) +
        '\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n' + text);
    }
    function result(error, value) { respond(error ? 500 : 200, error ? {error: 'unavailable'} : value); }
    function route(request) {
      var body = request.method === 'POST' ? wire.json(request.body, config.maxBodyBytes) : Object.create(null);
      var time = process.hrtime(), now = time[0] * 1000 + time[1] / 1000000;
      if (request.method === 'POST' && (request.path === '/v1/pair' || request.path === '/v1/pair-status')) {
        wire.keys(body, request.path === '/v1/pair' ? ['token', 'name', 'nonce'] : ['token', 'nonce']);
        var token = wire.string(body, 'token'), nonce = wire.string(body, 'nonce'), response;
        try {
          if (request.path === '/v1/pair') {
            var pending = pairing.request(token, wire.string(body, 'name'), nonce, now); changed(); response = {state: 'pending', code: pending.code};
          } else {
            var grant = pairing.poll(token, nonce, now); response = grant ? {state: 'approved', id: grant.id, secret: grant.secret} : {state: 'pending'};
          }
        } catch (denied) { respond(403, {error: 'denied'}); return; }
        respond(200, response); return;
      }
      var authorization = request.headers.authorization || '';
      if (!/^Bearer [A-Za-z0-9_-]{43}\.[A-Za-z0-9_-]{43}$/.test(authorization)) { respond(403, {error: 'denied'}); return; }
      var id = authorization.slice(7, 50), secret = authorization.slice(51);
      if (request.path === '/v1/pair-complete' && request.method === 'POST') {
        wire.keys(body, []); pairing.finish(id, secret, now); changed(); respond(200, {state: 'paired'}); return;
      }
      if (!pairing.authenticate(id, secret)) { respond(403, {error: 'denied'}); return; }
      if (request.path === '/v1/info' && request.method === 'GET') { settings.info(result); }
      else if (request.path === '/v1/remote' && request.method === 'GET' && typeof settings.remote === 'function') { settings.remote(result); }
      else if (request.path === '/v1/command' && request.method === 'POST' && typeof settings.command === 'function') {
        wire.keys(body, ['id', 'value']);
        var command = wire.string(body, 'id'), value = wire.string(body, 'value');
        if (config.commands.indexOf(command) < 0 || value.length > 2048) { throw new Error('Unsupported command'); }
        settings.command(command, value, result);
      }
      else if (request.path === '/v1/settings' && request.method === 'GET') { settings.read(result); }
      else if (request.path === '/v1/settings' && request.method === 'POST') {
        wire.keys(body, ['expected', 'changes']); settings.apply(body.expected, body.changes, result);
      } else { respond(404, {error: 'unknown'}); }
    }
    socket.on('data', function (chunk) {
      if (done) { return; }
      if (input.length + chunk.length > 8192 + config.maxBodyBytes) { respond(400, {error: 'invalid'}); return; }
      input = Buffer.concat([input, chunk]);
      try { var request = wire.request(input, config.maxBodyBytes); if (request) { socket.pause(); route(request); } }
      catch (error) { respond(400, {error: 'invalid'}); }
    });
    socket.on('error', function () {});
  });
  server.maxConnections = 8;
  server.on('connection', function (socket) {
    if (closed || sockets.length >= 8 || !privateAddress(socket.remoteAddress, loopback)) { socket.destroy(); return; }
    sockets.push(socket);
    // Absolute connection deadline covers handshake, slow readers/writers and asynchronous handlers.
    var timer = setTimeout(function () { socket.destroy(); }, config.timeoutMillis);
    socket.on('close', function () { clearTimeout(timer); var index = sockets.indexOf(socket); if (index >= 0) { sockets.splice(index, 1); } });
    socket.on('error', function () {});
  });
  server.on('tlsClientError', function () {});
  server.on('clientError', function () {});
  server.on('error', function (error) { report(error); });
  var api = {port: function () { return server.address() && server.address().port; }, close: function (callback) {
    if (closed) { if (callback) { callback(); } return; } closed = true; pairing.close();
    sockets.slice().forEach(function (socket) { socket.destroy(); });
    server.close(callback);
  }};
  server.listen(config.port, host, function () { report(null); });
  return api;
}
module.exports = {supported: supported, privateAddress: privateAddress, listen: listen};
