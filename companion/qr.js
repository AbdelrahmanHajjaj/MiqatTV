/* Compact, lossless pairing QR. Transport credentials and approval are unchanged. ES5 for old LG. */
(function (root) {
  'use strict';
  var alphabet = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:', base64 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_';
  function fail() { throw new Error('Invalid compact pairing QR'); }
  function token(bytes) {
    var text = '', i, n;
    for (i = 0; i < bytes.length; i += 3) {
      n = bytes[i] * 65536 + (bytes[i + 1] || 0) * 256 + (bytes[i + 2] || 0);
      text += base64.charAt(n >>> 18) + base64.charAt((n >>> 12) & 63);
      if (i + 1 < bytes.length) text += base64.charAt((n >>> 6) & 63);
      if (i + 2 < bytes.length) text += base64.charAt(n & 63);
    }
    return text;
  }
  function decode(text) {
    if (typeof text !== 'string' || text.length !== 112 || text.slice(0, 5) !== 'MIQ3:') fail();
    var bytes = [], i, a, b, c, n, pin = '';
    for (i = 5; i < text.length; i += 3) {
      a = alphabet.indexOf(text.charAt(i)); b = alphabet.indexOf(text.charAt(i + 1)); c = i + 2 < text.length ? alphabet.indexOf(text.charAt(i + 2)) : 0;
      if (a < 0 || b < 0 || c < 0) fail();
      n = a + b * 45 + c * 2025;
      if (n > (i + 2 < text.length ? 65535 : 255)) fail();
      if (i + 2 < text.length) bytes.push(n >>> 8, n & 255); else bytes.push(n);
    }
    if (bytes.length !== 71 || bytes[0] !== 3 || bytes[5] * 256 + bytes[6] === 0) fail();
    for (i = 7; i < 39; i++) pin += ('0' + bytes[i].toString(16)).slice(-2);
    return {type: 'miqat-tv-pair', version: 3, url: 'https://' + bytes.slice(1, 5).join('.') + ':' + (bytes[5] * 256 + bytes[6]), pin: pin, token: token(bytes.slice(39))};
  }
  function encode(value) {
    if (!value || value.type !== 'miqat-tv-pair' || value.version !== 3 || typeof value.url !== 'string' || !/^[0-9a-f]{64}$/.test(value.pin) || !/^[A-Za-z0-9_-]{43}$/.test(value.token)) fail();
    if (Object.keys(value).sort().join(',') !== 'pin,token,type,url,version') fail();
    var match = /^https:\/\/([0-9]+)\.([0-9]+)\.([0-9]+)\.([0-9]+):([0-9]+)$/.exec(value.url), bytes = [3], i, n, text = '', credential = [];
    if (!match) fail();
    for (i = 1; i <= 5; i++) if (String(Number(match[i])) !== match[i] || Number(match[i]) > (i === 5 ? 65535 : 255)) fail();
    if (Number(match[5]) < 1) fail();
    for (i = 1; i <= 4; i++) bytes.push(Number(match[i]));
    bytes.push(Number(match[5]) >>> 8, Number(match[5]) & 255);
    for (i = 0; i < 64; i += 2) bytes.push(parseInt(value.pin.slice(i, i + 2), 16));
    for (i = 0; i < 43; i += 4) {
      n = (base64.indexOf(value.token.charAt(i)) << 18) | (base64.indexOf(value.token.charAt(i + 1)) << 12) |
        ((i + 2 < 43 ? base64.indexOf(value.token.charAt(i + 2)) : 0) << 6) | (i + 3 < 43 ? base64.indexOf(value.token.charAt(i + 3)) : 0);
      credential.push((n >>> 16) & 255); if (i + 2 < 43) credential.push((n >>> 8) & 255); if (i + 3 < 43) credential.push(n & 255);
    }
    if (credential.length !== 32 || token(credential) !== value.token) fail();
    bytes = bytes.concat(credential);
    for (i = 0; i < bytes.length; i += 2) {
      n = bytes[i]; if (i + 1 < bytes.length) n = n * 256 + bytes[i + 1];
      text += alphabet.charAt(n % 45); n = Math.floor(n / 45); text += alphabet.charAt(n % 45);
      if (i + 1 < bytes.length) text += alphabet.charAt(Math.floor(n / 45));
    }
    return 'MIQ3:' + text;
  }
  var api = {encode: encode, decode: decode};
  if (typeof module === 'object' && module.exports) module.exports = api; else root.PairingQr = api;
}(this));
