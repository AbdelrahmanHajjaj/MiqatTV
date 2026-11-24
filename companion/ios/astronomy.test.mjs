import { readFileSync } from 'node:fs';
import { createContext, runInContext } from 'node:vm';
import assert from 'node:assert/strict';

const read = path => readFileSync(new URL(path, import.meta.url), 'utf8');
const ctx = createContext({});
runInContext(read('node_modules/adhan/lib/bundles/adhan.umd.js'), ctx);
runInContext(read('Astronomy.js'), ctx);
const methods = JSON.parse(read('../../shared/data/methods.json'));
const zero = Array(6).fill(0);
const offsets = [1, -1, 30, -30, 0, 2];
const cairo = (method, adjustment) =>
  Array.from(ctx.miqatPrayerTimes(2026, 11, 12, 30.0444, 31.2357, method, false, 'middle', adjustment));

// Same frozen Android PhoneScheduleCheck vector in Africa/Cairo (UTC+2).
const midnight = Date.parse('2026-11-11T22:00:00Z');
assert.deepEqual(cairo(methods[0], zero).map(ms => Math.floor((ms - midnight) / 60_000)),
  [288, 377, 700, 879, 1020, 1100]);
for (const method of methods) {
  const base = cairo(method, zero);
  const shifted = cairo(method, offsets);
  assert.deepEqual(shifted.map((ms, i) => Math.round((ms - base[i]) / 60_000)), offsets, method.id);
}
assert.equal(ctx.miqatPrayerTimes(2026, 6, 21, 89, 0, methods[0], false, 'middle', zero), null);
console.log('PASS: Android Cairo baseline, offsets applied once for every method, polar unavailability');
