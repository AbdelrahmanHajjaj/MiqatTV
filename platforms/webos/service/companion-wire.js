'use strict';
function buffer(text, encoding) {
  if (typeof text !== 'string') { throw new Error('Text buffer required'); }
  return typeof Buffer.from === 'function' ? Buffer.from(text, encoding || 'utf8') : new Buffer(text, encoding || 'utf8');
}
function same(left, right) {
  if (left.length !== right.length) { return false; }
  for (var i = 0; i < left.length; i++) { if (left[i] !== right[i]) { return false; } }
  return true;
}
function json(bytes, maximum) {
  if (!Buffer.isBuffer(bytes) || bytes.length > maximum) { throw new Error('JSON too large'); }
  var text = bytes.toString('utf8'), at = 0;
  if (!same(buffer(text), bytes)) { throw new Error('Invalid UTF-8'); }
  function space() { while (at < text.length && /[ \t\r\n]/.test(text.charAt(at))) { at++; } }
  function string() {
    var start = at++;
    while (at < text.length) {
      var ch = text.charAt(at++);
      if (ch === '"') { return JSON.parse(text.slice(start, at)); }
      if (ch === '\\') { at++; }
    }
    throw new Error('Truncated JSON string');
  }
  function read(depth) {
    if (depth > 16) { throw new Error('JSON too deep'); }
    space(); var ch = text.charAt(at), result, count = 0;
    if (ch === '{' || ch === '[') {
      var object = ch === '{', end = object ? '}' : ']'; result = object ? Object.create(null) : []; at++; space();
      if (text.charAt(at) === end) { at++; return result; }
      while (true) {
        if (++count > 4096) { throw new Error('Excessive JSON items'); }
        if (object) {
          if (text.charAt(at) !== '"') { throw new Error('Expected JSON key'); }
          var key = string(); space();
          if (Object.prototype.hasOwnProperty.call(result, key)) { throw new Error('Duplicate JSON key'); }
          if (text.charAt(at++) !== ':') { throw new Error('Expected JSON colon'); }
          result[key] = read(depth + 1);
        } else { result.push(read(depth + 1)); }
        space(); ch = text.charAt(at++);
        if (ch === end) { return result; }
        if (ch !== ',') { throw new Error('Invalid JSON separator'); }
        space();
      }
    }
    if (ch === '"') { return string(); }
    var start = at;
    while (at < text.length && !/[ \t\r\n,}\]]/.test(text.charAt(at))) { at++; }
    var token = text.slice(start, at);
    if (token === 'true') { return true; } if (token === 'false') { return false; } if (token === 'null') { return null; }
    if (!/^-?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][+-]?[0-9]+)?$/.test(token) || !isFinite(Number(token))) { throw new Error('Invalid JSON value'); }
    return Number(token);
  }
  var result = read(0); space();
  if (at !== text.length || !result || typeof result !== 'object' || Array.isArray(result)) { throw new Error('JSON object required'); }
  return result;
}
function keys(value, allowed) {
  if (!value || typeof value !== 'object' || Array.isArray(value) || Object.keys(value).some(function (key) { return allowed.indexOf(key) < 0; })) { throw new Error('Unknown JSON field'); }
}
function string(value, key) { if (typeof value[key] !== 'string') { throw new Error('String required'); } return value[key]; }
// One bounded request per connection; no browser, proxy, streaming or pipelining routes.
function request(bytes, maximum) {
  var end = -1;
  for (var offset = 0; offset + 3 < Math.min(bytes.length, 8192); offset++) {
    if (bytes[offset] === 13 && bytes[offset + 1] === 10 && bytes[offset + 2] === 13 && bytes[offset + 3] === 10) { end = offset; break; }
  }
  if (end < 0) { if (bytes.length >= 8192) { throw new Error('Headers too large'); } return null; }
  if (end + 4 > 8192) { throw new Error('Headers too large'); }
  for (var i = 0; i < end + 4; i++) {
    if ((bytes[i] < 32 || bytes[i] > 126) && bytes[i] !== 13 && bytes[i] !== 10) { throw new Error('Invalid header character'); }
  }
  var lines = bytes.slice(0, end).toString('ascii').split('\r\n');
  if (!/^(GET|POST) \/v1\/[a-z-]+ HTTP\/1\.[01]$/.test(lines[0])) { throw new Error('Invalid request line'); }
  var parts = lines.shift().split(' '), headers = Object.create(null);
  lines.forEach(function (line) {
    var split = line.indexOf(':'), key = line.slice(0, split);
    if (split <= 0 || !/^[A-Za-z0-9-]+$/.test(key)) { throw new Error('Invalid header'); }
    key = key.toLowerCase();
    if (Object.prototype.hasOwnProperty.call(headers, key)) { throw new Error('Duplicate header'); }
    var value = line.slice(split + 1).trim();
    if (/[\r\n]/.test(value)) { throw new Error('Invalid header'); }
    headers[key] = value;
  });
  if (!headers.host || 'origin' in headers || 'transfer-encoding' in headers || 'expect' in headers) { throw new Error('Unsupported request'); }
  var length = headers['content-length'], count = 0;
  if (length !== undefined) { if (!/^(0|[1-9][0-9]{0,8})$/.test(length)) { throw new Error('Invalid length'); } count = Number(length); }
  if (count > maximum || parts[0] === 'GET' && count !== 0) { throw new Error('Invalid body length'); }
  if (parts[0] === 'POST' && (length === undefined || (headers['content-type'] || '').toLowerCase() !== 'application/json')) { throw new Error('JSON required'); }
  if (bytes.length < end + 4 + count) { return null; }
  if (bytes.length !== end + 4 + count) { throw new Error('Extra request bytes'); }
  return {method: parts[0], path: parts[1], headers: headers, body: bytes.slice(end + 4)};
}
module.exports = {json: json, keys: keys, string: string, request: request, buffer: buffer};
