'use strict';
var core = require('./core');
var portable = require('./Miqat-core').tv.miqat.core;
var controls = require('./companion-catalog.json');
var fields = {
  'prayer.location': 'city', 'prayer.hijri': 'hijriOffset', 'reminders.enabled': 'automatic',
  'reminders.before': 'remind', 'reminders.spoken': 'remindVoice', 'reminders.adhan': 'adhan',
  'reminders.announce': 'announce', 'reminders.voice': 'voice', 'reminders.fajr-voice': 'fajrVoice',
  'reminders.volume': 'volume', 'reminders.close': 'callClose', 'radio.station': 'station', 'radio.resume': 'radioResume',
  'display.language': 'lang', 'display.digits': 'digits', 'display.clock': 'clock', 'display.size': 'uiScale',
  'display.quick-labels': 'quickLabels', 'display.bar-position': 'dockPosition', 'display.bar-timeout': 'dockAutoHide',
  'appearance.countdown': 'homeLayout', 'appearance.position': 'archSide', 'appearance.prayers': 'prayerMode',
  'appearance.theme': 'theme', 'appearance.compact-position': 'homeBar'
};
var prayers = ['fajr', 'sunrise', 'dhuhr', 'asr', 'maghrib', 'isha'];
var specs = Object.create(null);
controls.forEach(function (control) {
  if (control.available && control.kind !== 'action' && control.section !== 'tests') { specs[control.id] = control; }
});
function value(p, state, id) {
  if (fields[id]) { return String(p[fields[id]]); }
  if (id === 'prayer.method') { return p.method || core.city(p.city).method; }
  if (id === 'prayer.asr') { return p.hanafi ? 'hanafi' : 'shafi'; }
  if (id === 'prayer.zone') { return p.utcOffset === null ? 'system' : String(p.utcOffset); }
  if (id === 'appearance.background') { return p.background === 'plain' ? 'solid' : p.background; }
  if (id === 'device.startup') { return String(!!(state.startup && state.startup.enabled)); }
  if (id.indexOf('offsets.') === 0) { return String(p.offsets[prayers.indexOf(id.slice(8))]); }
  if (id.indexOf('prayer.adhan-') === 0) { return String(p.bells[prayers.indexOf(id.slice(13))]); }
  throw new Error('Unbound LG setting: ' + id);
}
function snapshot(state) {
  var p = core.prefs(state.prefs), values = {};
  Object.keys(specs).forEach(function (id) { values[id] = value(p, state, id); });
  return {profile: 'lg', systemLanguage: '', controls: controls, values: values};
}
function record(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) { throw new Error('Invalid settings patch'); }
  Object.keys(value).forEach(function (id) {
    if (!Object.prototype.hasOwnProperty.call(specs, id) || typeof value[id] !== 'string') { throw new Error('Unknown setting: ' + id); }
  });
}
function validate(id, text) {
  var spec = specs[id];
  if (spec.kind === 'toggle') { if (text !== 'true' && text !== 'false') { throw new Error('Invalid toggle'); } }
  else if (spec.kind === 'number') {
    var number = Number(text), low = portable.UiContract.minimum(id);
    if (!/^-?(0|[1-9][0-9]*)$/.test(text) || number < low || number > portable.UiContract.maximum(id) ||
        id === 'display.size' && (number - low) % portable.UiContract.numberStep(id) !== 0) { throw new Error('Invalid number'); }
  } else if (spec.kind === 'choice') {
    if (!spec.options.some(function (option) { return option.value === text; })) { throw new Error('Invalid choice'); }
  } else { throw new Error('Unknown editor'); }
}
function patch(state, expected, changes) {
  record(expected); record(changes);
  var edits = Object.keys(changes), next = core.prefs(state.prefs), request = Object.create(null);
  if (!edits.length) { throw new Error('Empty settings patch'); }
  edits.forEach(function (id) { validate(id, changes[id]); request[id] = changes[id]; });
  if (request['prayer.location'] !== undefined) {
    var city = core.city(request['prayer.location']);
    if (request['prayer.method'] === undefined) { request['prayer.method'] = city.method; }
    if (request['prayer.asr'] === undefined) { request['prayer.asr'] = city.hanafi ? 'hanafi' : 'shafi'; }
  }
  edits = Object.keys(request);
  if (edits.indexOf('device.startup') >= 0 && edits.length !== 1) { throw new Error('Save startup separately'); }
  var current = snapshot(state), ids = Object.keys(specs);
  var conflicts = portable.SettingChanges.conflicts(ids, ids.map(function (id) { return specs[id].save === 'appearance' ? 'appearance' : ''; }),
    ids.map(function (id) { return current.values[id]; }), ids.map(function (id) { return expected[id] === undefined ? null : expected[id]; }), edits);
  if (conflicts.length) { return {prefs: next, conflicts: conflicts}; }
  edits.forEach(function (id) {
    var text = request[id], field = fields[id];
    if (field) { next[field] = typeof core.defaults[field] === 'boolean' ? text === 'true' : typeof core.defaults[field] === 'number' ? Number(text) : text; }
    else if (id === 'prayer.method') { next.method = text; }
    else if (id === 'prayer.asr') { next.hanafi = text === 'hanafi'; }
    else if (id === 'prayer.zone') { next.utcOffset = text === 'system' ? null : Number(text); }
    else if (id === 'appearance.background') { next.background = text === 'solid' ? 'plain' : text; }
    else if (id.indexOf('offsets.') === 0) { next.offsets[prayers.indexOf(id.slice(8))] = Number(text); }
    else if (id.indexOf('prayer.adhan-') === 0) { next.bells[prayers.indexOf(id.slice(13))] = text === 'true'; }
  });
  return {prefs: core.prefs(next), conflicts: [], startup: request['device.startup'] === undefined ? null : request['device.startup'] === 'true'};
}
module.exports = {snapshot: snapshot, patch: patch};
