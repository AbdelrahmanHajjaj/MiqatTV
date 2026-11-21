const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, 'mockups.js'), 'utf8');
const controls = new Map();
const context = vm.createContext({
  document: {addEventListener() {}, getElementById(id) {
    if (!controls.has(id)) controls.set(id, {addEventListener() {}});
    return controls.get(id);
  }},
  fetch: () => new Promise(() => {}),
});
vm.runInContext(fs.readFileSync(path.join(__dirname,'review-features.js'),'utf8'),context);
vm.runInContext(source, context);
const pages = vm.runInContext('pages', context);
const ids = new Set(pages.map(p => p.id));
assert.equal(ids.size, pages.length, 'Duplicate artboard IDs');
for (const p of pages) {
  assert.ok(ids.has(p.back), `Missing Back destination from ${p.id}`);
  for (const [, target] of (p.content + p.foot).matchAll(/data-go="([^"]+)"/g))
    assert.ok(ids.has(target), `Missing destination ${target} from ${p.id}`);
}
const coverage = {
  prayers:'home', 'phone-settings':'settings', tvs:'tvs', pair:'pair',
  overview:'tv-remote', settings:'tv-settings', editor:'tv-choice',
  appearance:'tv-appearance', updates:'updates', preferences:'language',
  'lg-setup':'lg-guide', 'tv-setup':'tv-platform', onboarding:'welcome',
  qibla:'qibla', widgets:'widget-editor',
};
const contract = fs.readFileSync(path.join(__dirname, '../../../companion/core/src/commonMain/kotlin/tv/miqat/companion/core/Settings.kt'),'utf8');
const routes = [...contract.match(/val screens = listOf\(([^\n]+)\)/)[1].matchAll(/"([^"]+)"/g)].map(m=>m[1]);
assert.deepEqual(Object.keys(coverage).sort(), routes.sort(), 'Current route coverage changed');
for (const id of Object.values(coverage)) assert.ok(ids.has(id));
const setup = pages.find(p=>p.id==='setup-location').content;
assert.ok(!setup.includes('role="switch"'), 'Setup must not reintroduce an alert switch');
const tv = pages.find(p=>p.id==='tv-platform').content;
assert.ok(!/Samsung|Roku|Other TV|Coming soon/i.test(tv), 'Unsupported TV setup entries');
assert.ok(!/Remote (up|down|left|right)|Remote OK/i.test(pages.find(p=>p.id==='tv-remote').content), 'Protocol does not offer D-pad commands');
for (const name of ['fajr','dhuhr','asr','maghrib','isha']) {
  const content = pages.find(p=>p.id==='prayer-'+name).content;
  assert.equal((content.match(/aria-label="(?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)"/g)||[]).length,7);
}
console.log(`PASS: ${pages.length} unique artboards; complete ${routes.length}-route coverage; all mock links, setup and weekday invariants.`);

assert.equal(pages.find(p=>p.id==='notification-request').content.includes('data-go="exact-access"'),false);
assert.ok(!ids.has('exact-access')&&!ids.has('alarm-return'));
assert.ok(!/After you confirm|Confirming opens/.test(pages.find(p=>p.id==='setup-location').content));
for(const name of ['fajr','dhuhr','asr','maghrib','isha']) {
 const content=pages.find(p=>p.id==='prayer-'+name).content;
 assert.ok(content.includes('data-prayer-enabled'));
 assert.ok(content.includes('Short tone')&&!content.includes('Stop after'));
}
assert.ok(pages.find(p=>p.id==='prayer-disabled').content.includes('fieldset class="prayer-config" disabled'));
assert.ok(!pages.find(p=>p.id==='adhan').content.includes('ADHAN · PLAYING'));
assert.ok(!pages.find(p=>p.id==='home').content.includes('Tomorrow’s Fajr'));
assert.ok(Math.abs(vm.runInContext('qiblaBearing(30.0444,31.2357)',context)-136.1)<1);
assert.equal(vm.runInContext('durationAtPercent(200,75)',context),150);
assert.equal(vm.runInContext('durationAtPercent(200,0)',context),10);
const month=vm.runInContext('monthDays(reviewToday)',context);
assert.ok([29,30].includes(month.length));
assert.equal(vm.runInContext('hijriParts(reviewToday).day',context),'11');
assert.ok(!pages.find(p=>p.id==='qibla-map').content.includes('<img src='),'Map must not preload external tiles');
console.log('PASS: revised permission flow, prayer enable/sound/length, calendar month and Qibla geometry.');

// Calendar month traversal includes Hijri year transitions.
for(const date of ['2026-06-10T12:00:00Z','2026-06-20T12:00:00Z','2026-12-31T12:00:00Z']) {
 const days=vm.runInContext(`monthDays(new Date('${date}'))`,context);
 assert.ok([29,30].includes(days.length));
 assert.equal(vm.runInContext(`hijriParts(new Date('${days[0].toISOString()}')).day`,context),'1');
 for(let i=1;i<days.length;i++)assert.equal(+days[i]-+days[i-1],86400000);
 const next=new Date(+days[days.length-1]+86400000);
 assert.equal(vm.runInContext(`hijriParts(new Date('${next.toISOString()}')).day`,context),'1');
}
assert.equal(vm.runInContext('durationAtPercent(200,110)',context),200);
const research=fs.readFileSync(path.join(__dirname,'research.html'),'utf8');
for(const [,id] of research.matchAll(/href="index.html#([^" ]+)"/g))assert.ok(ids.has(id),`Stale research link ${id}`);
console.log('PASS: calendar boundaries, length clamping and research anchors.');
