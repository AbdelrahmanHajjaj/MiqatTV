'use strict';
var crypto = require('crypto'), wire = require('./companion-wire');
var forge, pki;
function library() {
  if (pki) { return pki; }
  pki = require('./vendor/forge/pki'); forge = require('./vendor/forge/forge'); require('./vendor/forge/sha256');
  // Certificate signing/blinding must use OS randomness without a JS entropy fallback.
  forge.random.getBytesSync = function (count) { return crypto.randomBytes(count).toString('binary'); };
  forge.random.getBytes = function (count, callback) {
    if (!callback) { return forge.random.getBytesSync(count); }
    crypto.randomBytes(count, function (error, bytes) { callback(error, bytes && bytes.toString('binary')); });
  };
  return pki;
}
function checked(saved) {
  if (!saved || Object.keys(saved).sort().join(',') !== 'cert,key' || typeof saved.key !== 'string' || saved.key.length > 8192 ||
      typeof saved.cert !== 'string' || saved.cert.length > 4096) { throw new Error('Invalid TV identity'); }
  var pki = library(), cert = pki.certificateFromPem(saved.cert);
  if (cert.signatureOid !== '1.2.840.113549.1.1.11' || cert.publicKey.n.bitLength() !== 2048 || cert.publicKey.e.intValue() !== 65537 ||
      !cert.verify(cert) || +cert.validity.notBefore > Date.now() || +cert.validity.notAfter <= Date.now()) { throw new Error('Invalid TV certificate'); }
  var challenge = crypto.randomBytes(32), signature = crypto.createSign('RSA-SHA256').update(challenge).sign(saved.key);
  if (!crypto.createVerify('RSA-SHA256').update(challenge).verify(pki.publicKeyToPem(cert.publicKey), signature)) { throw new Error('TV identity key mismatch'); }
  var der = wire.buffer(forge.asn1.toDer(pki.certificateToAsn1(cert)).getBytes(), 'binary');
  return {key: saved.key, cert: saved.cert, certificate: der.toString('base64'), pin: crypto.createHash('sha256').update(der).digest('hex')};
}
function load(store, callback) {
  var saved, existing;
  try { saved = store.read(); if (saved !== null) { existing = checked(saved); } }
  catch (error) { callback(error); return; }
  if (saved !== null) { callback(null, existing); return; }
  function generated(error, publicKey, privateKey) {
    if (error) { callback(error); return; }
    var identity;
    try {
      var pki = library(), cert = pki.createCertificate(), attrs = [{name: 'commonName', value: 'Miqat'}];
      cert.publicKey = pki.publicKeyFromPem(publicKey); cert.serialNumber = '00' + crypto.randomBytes(16).toString('hex');
      cert.validity.notBefore = new Date('2020-01-01T00:00:00Z'); cert.validity.notAfter = new Date('2050-01-01T00:00:00Z');
      cert.setSubject(attrs); cert.setIssuer(attrs);
      cert.setExtensions([{name: 'basicConstraints', cA: false}, {name: 'keyUsage', digitalSignature: true, keyEncipherment: true}, {name: 'extKeyUsage', serverAuth: true}]);
      cert.sign(pki.privateKeyFromPem(privateKey), forge.md.sha256.create());
      saved = {key: privateKey, cert: pki.certificateToPem(cert)}; identity = checked(saved); store.write(saved);
    } catch (failure) { callback(failure); return; }
    callback(null, identity);
  }
  if (typeof crypto.generateKeyPair === 'function') {
    crypto.generateKeyPair('rsa', {modulusLength: 2048, publicExponent: 65537,
      publicKeyEncoding: {type: 'spki', format: 'pem'}, privateKeyEncoding: {type: 'pkcs8', format: 'pem'}}, generated);
  } else {
    try {
      var pki = library();
      // Forge yields between key-generation steps; entropy still comes only from the OS.
      pki.rsa.generateKeyPair({bits: 2048, e: 65537}, function (error, pair) {
        if (error) { generated(error); return; }
        generated(null, pki.publicKeyToPem(pair.publicKey), pki.privateKeyToPem(pair.privateKey));
      });
    } catch (error) { callback(error); }
  }
}
module.exports = {load: load, checked: checked};
