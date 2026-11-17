'use strict';
module.exports = function (service, io) {
  var appId = 'tv.miqat.webos', sessions = Object.create(null), epoch = 0, hold = null;
  var server = null, pairing = null, identity = null, listener = null, config = null;
  var initializing = false, starting = false, writing = false, supported = null, failure = '', owner = '', address = '', qr = null, expiry = null, waiters = [];
  var remoteCommand = null;
  function active() { return Object.keys(sessions).length > 0; }
  function now() { var t = process.hrtime(); return t[0] * 1000 + t[1] / 1000000; }
  function release(activity) { if (activity) { service.activityManager.complete(activity, {restart: false}, function () {}); } }
  function cancelPairing() { clearTimeout(expiry); expiry = null; owner = ''; qr = null; if (pairing) { pairing.close(); } }
  function state(session) {
    var current = pairing ? pairing.state(now()) : {open: false, pending: null, approved: false};
    if (!current.open && owner) { cancelPairing(); }
    return {supported: supported, starting: initializing || starting, failure: failure, listening: !!listener,
      phones: pairing ? pairing.list() : [], open: current.open, pending: current.pending, approved: current.approved,
      ownsPairing: session.id === owner, qr: session.id === owner && current.open ? qr : null};
  }
  function publish(settings) {
    var current = null, unavailable = false;
    if (settings) { try { current = io.current(); } catch (error) { unavailable = true; } }
    Object.keys(sessions).forEach(function (token) {
      var session = sessions[token], reply = {returnValue: true, subscribed: true, session: session.id, companion: state(session)};
      if (settings) { if (unavailable) { reply.currentUnavailable = true; } else { reply.current = current; } }
      try { session.message.respond(reply); } catch (error) { remove(token); }
    });
  }
  function stop() {
    epoch++; initializing = starting = false; cancelPairing();
    finishCommand('uncertain');
    var old = listener, activity = hold; listener = null; hold = null; pairing = null; identity = null; address = '';
    var pending = waiters; waiters = []; pending.forEach(function (callback) { callback(new Error('Launcher inactive')); });
    if (old) { old.close(function () { release(activity); }); } else { release(activity); }
  }
  function remove(token) {
    var session = sessions[token]; if (!session) { return; }
    if (remoteCommand && remoteCommand.session === session.id) { finishCommand('uncertain'); }
    var owning = session.id === owner; if (owning) { cancelPairing(); }
    delete sessions[token]; if (!active()) { stop(); }
    else if (owning) { publish(false); }
  }
  function settingsReply(status, conflicts) { var result = io.snapshot(); result.status = status; result.conflicts = conflicts || []; return result; }
  function finishCommand(status) {
    var pending = remoteCommand; if (!pending) { return; } remoteCommand = null; clearTimeout(pending.timer);
    pending.done(null, {status: status});
  }
  var settings = {
    info: function (done) { if (!active()) { throw new Error('Launcher inactive'); } done(null, io.info()); },
    read: function (done) { if (!active()) { throw new Error('Launcher inactive'); } done(null, io.snapshot()); },
    remote: function (done) {
      if (!active()) { throw new Error('Launcher inactive'); }
      if (typeof io.remote !== 'function') { done(null, {commands: [], inputs: []}); return; }
      io.remote(done);
    },
    command: function (id, value, done) {
      if (!active() || !config || config.commands.indexOf(id) < 0 || typeof value !== 'string' || value.length > 2048 || id !== 'device.inputs' && value !== '') { throw new Error('Unsupported command'); }
      var ids = Object.keys(sessions), stopping = id === 'radio.stop' || id === 'tests.cancel';
      if (remoteCommand || writing || !stopping && ids.some(function (token) { return sessions[token].editing; })) { done(null, {status: 'busy'}); return; }
      var target = sessions[ids[ids.length - 1]], request = require('./companion-pairing').token();
      remoteCommand = {session: target.id, request: request, done: done, timer: setTimeout(function () { finishCommand('uncertain'); }, 4000)};
      try { target.message.respond({returnValue: true, subscribed: true, session: target.id, companion: state(target), command: {request: request, id: id, value: value}}); }
      catch (error) { finishCommand('uncertain'); }
    },
    apply: function (expected, changes, done) {
      if (!active()) { throw new Error('Launcher inactive'); }
      if (writing || Object.keys(sessions).some(function (token) { return sessions[token].editing; })) { done(null, settingsReply('busy')); return; }
      writing = true;
      try { io.apply(expected, changes, function (error, result) {
        writing = false;
        if (!error && result.status === 'saved') { publish(true); }
        done(error, result);
      }); } catch (error) { writing = false; throw error; }
    }
  };
  function host() {
    var interfaces = require('os').networkInterfaces(), result = '';
    Object.keys(interfaces).sort().some(function (key) {
      return interfaces[key].some(function (address) {
        if (!address.internal && (address.family === 'IPv4' || address.family === 4) && server.privateAddress(address.address)) { result = address.address; return true; }
        return false;
      });
    });
    if (!result) { throw new Error('No private network'); } return result;
  }
  function ensureListener(callback) {
    if (listener) { callback(null); return; }
    waiters.push(callback); if (starting) { return; } starting = true;
    var generation = epoch;
    publish(false); if (!active()) { return; }
    function finish(error) {
      if (generation !== epoch) { return; }
      starting = false; failure = error ? 'connection' : '';
      var pending = waiters; waiters = []; pending.forEach(function (done) { done(error); }); publish(false);
    }
    var store;
    try { store = require('./companion-files')(io.directory, 'companion-identity.json'); }
    catch (error) { finish(error); return; }
    require('./companion-identity').load({read: store.read, write: function (value) {
      if (generation !== epoch || !active()) { throw new Error('Launcher inactive'); } store.write(value);
    }}, function (error, value) {
      if (generation !== epoch || !active()) { return; }
      if (error) { finish(error); return; } identity = value;
      try {
        var selected = host(), pending = server.listen(identity, pairing, settings, function () { publish(false); }, selected, config, function (error) {
          if (generation !== epoch || !active()) { pending.close(); return; }
          if (error) { pending.close(); finish(error); return; }
          address = selected; listener = pending; finish(null);
        });
      } catch (failure) { finish(failure); }
    });
  }
  function initialize() {
    if (initializing || pairing || !active()) { return; }
    server = require('./companion-server'); supported = server.supported();
    if (!supported) { publish(false); return; }
    initializing = true; failure = ''; var generation = epoch;
    try { service.activityManager.create(appId + '.companion-foreground', function (activity) {
      if (generation !== epoch || !active()) { release(activity); return; }
      hold = activity;
      try {
        config = require('./companion-lan.json'); var store = require('./companion-files')(io.directory, 'companion-phones.json');
        var Pairing = require('./companion-pairing').Pairing;
        pairing = new Pairing({read: function () {
          var value = store.read(); if (value === null) { return []; }
          if (Object.keys(value).join(',') !== 'phones') { throw new Error('Invalid phone store'); } return value.phones;
        }, write: function (phones) { store.write({phones: phones}); }}, config);
        initializing = false;
        if (pairing.list().length) { ensureListener(function () {}); }
        publish(false);
      } catch (error) { initializing = false; failure = 'storage'; hold = null; release(activity); publish(false); }
    }); } catch (error) { initializing = false; failure = 'service'; publish(false); }
  }
  function checked(message) {
    if (message.sender !== appId) { throw new Error('TV launcher required'); }
    var session;
    Object.keys(sessions).some(function (token) { if (sessions[token].id === message.payload.session) { session = sessions[token]; return true; } return false; });
    if (!session || !pairing) { throw new Error('No active TV session'); } return session;
  }
  function register(name, action) { service.register(name, function (message) {
    try { action(message); } catch (error) { message.respond({returnValue: false, errorText: 'Companion operation unavailable'}); }
  }); }
  service.register('companionForeground', function (message) {
    if (message.sender !== appId || !message.isSubscription || typeof message.uniqueToken !== 'string') { message.respond({returnValue: false}); return; }
    var token = message.uniqueToken;
    if (sessions[token]) { message.respond({returnValue: false}); return; }
    sessions[token] = {id: token, message: message, editing: true, page: false};
    initialize(); if (sessions[token]) { message.respond({returnValue: true, subscribed: true, session: token, companion: state(sessions[token])}); }
  }, function (message) { remove(message.uniqueToken); });
  register('companionUi', function (message) {
    var session = checked(message), p = message.payload;
    if (typeof p.editing !== 'boolean' || typeof p.pairingPage !== 'boolean') { throw new Error('Invalid UI state'); }
    session.editing = p.editing; session.page = p.pairingPage;
    if (!session.page && owner === session.id) { cancelPairing(); publish(false); }
    message.respond({returnValue: true});
  });
  register('companionLeave', function (message) {
    if (message.sender !== appId) { throw new Error('TV launcher required'); }
    Object.keys(sessions).forEach(function (token) { if (sessions[token].id === message.payload.session) { remove(token); } });
    message.respond({returnValue: true});
  });
  register('companionResult', function (message) {
    var session = checked(message), p = message.payload;
    if (!remoteCommand || remoteCommand.session !== session.id || remoteCommand.request !== p.request || ['accepted', 'busy', 'unavailable'].indexOf(p.status) < 0) { throw new Error('Unknown command result'); }
    finishCommand(p.status); message.respond({returnValue: true});
  });
  register('companionPairing', function (message) {
    var session = checked(message), p = message.payload;
    if (!session.page) { throw new Error('Pairing page required'); }
    function finish(error) {
      if (error || !active() || !Object.keys(sessions).some(function (key) { return sessions[key] === session; }) || !session.page) {
        if (p.action !== 'open') { message.respond({returnValue: false}); } return;
      }
      try {
        if (p.action === 'open') {
          if (owner && owner !== session.id) { throw new Error('Another pairing page is active'); }
          var window = pairing.open(now()); owner = session.id;
          qr = {type: 'miqat-tv-pair', version: config.qrVersion, url: 'https://' + address + ':' + config.port, pin: identity.pin, token: window};
          if (Buffer.byteLength(JSON.stringify(qr)) > config.maxQrBytes) { cancelPairing(); throw new Error('QR too large'); }
          clearTimeout(expiry); expiry = setTimeout(function () { cancelPairing(); publish(false); }, config.pairWindowMillis);
        } else if (p.action === 'revoke') { pairing.revoke(p.id); }
        else {
          if (owner !== session.id) { throw new Error('Pairing owner required'); }
          if (p.action === 'approve') { pairing.approve(p.nonce, now()); }
          else if (p.action === 'reject') { pairing.reject(p.nonce, now()); cancelPairing(); }
          else if (p.action === 'cancel') { cancelPairing(); }
          else { throw new Error('Unknown pairing action'); }
        }
        if (p.action !== 'open') { message.respond({returnValue: true}); } publish(false);
      } catch (operationError) {
        if (p.action === 'open') { failure = 'connection'; publish(false); }
        else { message.respond({returnValue: false}); }
      }
    }
    if (p.action === 'open') {
      if (owner && owner !== session.id) { throw new Error('Another pairing page is active'); }
      message.respond({returnValue: true}); ensureListener(finish);
    } else { finish(null); }
  });
  return {changed: function () { if (active() && pairing) { publish(true); } }};
};
