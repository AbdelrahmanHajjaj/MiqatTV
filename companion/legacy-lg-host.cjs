// Bounded local fixture for the actual shipped modules on Node 0.10.25. No TV operations.
'use strict';
var path = require('path'), root = path.resolve(__dirname, '../platforms/webos/build/service');
var server = require(root + '/companion-server'), Pairing = require(root + '/companion-pairing').Pairing;
var identities = require(root + '/companion-identity'), adapter = require(root + '/companion-settings'), core = require(root + '/core');
var config = require('./protocol/lan.json'), saved = [], listener = null, writes = 0, reads = 0, closing = false;
var state = {prefs: core.prefs({lang: 'en', volume: 25}), input: 'HDMI_3', startup: {enabled: false, launches: 7}, audioVolume: {fixture: 'preserve'}};
var pairing = new Pairing({read: function () { return saved; }, write: function (value) { saved = value; }}, config);
function now() { var t = process.hrtime(); return t[0] * 1000 + t[1] / 1000000; }
function send(value) { process.stdout.write(JSON.stringify(value) + '\n'); }
function stats() { return {state: state, writes: writes, reads: reads, phones: pairing.list()}; }
function fail(error) { send({event: 'failure', message: String(error.message || error)}); process.exit(1); }
var timeout = setTimeout(function () { if (listener) { listener.close(); } process.exit(2); }, 240000);
var settings = {
  info: function (done) { done(null, {protocol: 1, profile: 'lg', name: 'LG legacy integration fixture', version: 'test', systemLanguage: ''}); },
  read: function (done) { reads++; done(null, adapter.snapshot(state)); },
  apply: function (expected, changes, done) {
    var edit = adapter.patch(state, expected, changes);
    if (!edit.conflicts.length) {
      if (edit.startup !== null) { throw new Error('No OS operations in fixture'); }
      state.prefs = edit.prefs; writes++;
    }
    var result = adapter.snapshot(state); result.status = edit.conflicts.length ? 'conflict' : 'saved'; result.conflicts = edit.conflicts; done(null, result);
  }
};
var input = require('readline').createInterface({input: process.stdin, output: process.stdout, terminal: false});
input.on('line', function (text) {
  try {
    var command = JSON.parse(text);
    if (command.action === 'approve') { pairing.approve(command.nonce, now()); send({event: 'approved'}); }
    else if (command.action === 'revoke') { pairing.revoke(command.id); send({event: 'revoked'}); }
    else if (command.action === 'cut') {
      listener.close(function () { send({event: 'cut', stats: stats()}); });
    } else if (command.action === 'stop') {
      if (closing) { return; } closing = true;
      var finish = function () { clearTimeout(timeout); send({event: 'stopped', stats: stats()}); process.exit(0); };
      if (listener) { listener.close(finish); } else { finish(); }
    } else { throw new Error('Unknown fixture command'); }
  } catch (error) { fail(error); }
});
input.on('close', function () { if (!closing) { if (listener) { listener.close(); } process.exit(0); } });
if (!server.supported()) { fail(new Error('Historical runtime cannot offer required legacy TLS')); }
identities.load({read: function () { return null; }, write: function (value) {
  // Exercise loading the generated private key using this same historical runtime.
  if (identities.checked(value).pin.length !== 64) { throw new Error('Invalid persisted identity'); }
}}, function (error, identity) {
  if (error) { fail(error); return; }
  try {
    var selected = {};
    Object.keys(config).forEach(function (key) { selected[key] = config[key]; });
    if (process.argv[2] !== '--phone') { selected.port = 0; }
    listener = server.listen(identity, pairing, settings, function () {
      var pending = pairing.state(now()).pending;
      if (process.argv[2] === '--phone' && pending && pending.name === 'Companion integration fixture' && !pairing.state(now()).approved) { pairing.approve(pending.nonce, now()); }
    }, '127.0.0.1', selected, function (error) {
      if (error) { fail(error); return; }
      send({event: 'ready', qr: {type: 'miqat-tv-pair', version: config.qrVersion, url: 'https://127.0.0.1:' + listener.port(),
        pin: identity.pin, token: pairing.open(now())}, certificate: identity.certificate, stats: stats()});
    }, true);
  } catch (failure) { fail(failure); }
});
