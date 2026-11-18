const assert = require('node:assert/strict');

module.exports = function checkCompanionSettings(adapter, core) {
  const state = {prefs:core.prefs({volume:25}), input:'HDMI_3', startup:{enabled:true, launches:3}, audioVolume:{id:'fixture'}};
  const snapshot = adapter.snapshot(state), baseline = snapshot.values;
  const editable = snapshot.controls.filter(c => c.available && c.kind !== 'action' && c.section !== 'tests');
  assert.equal(Object.keys(baseline).length, editable.length, 'every offered setting reads actual state');
  assert.equal(baseline['reminders.volume'], '25');
  assert.equal(baseline['device.startup'], 'true');
  assert.equal(baseline['appearance.background'], 'miqat_signature');
  assert.equal(baseline['prayer.zone'], 'system');
  assert.equal(baseline['appearance.mihrab-opacity'], '85');
  assert.equal(adapter.patch(state, baseline, {'appearance.mihrab-opacity':'70'}).prefs.mihrabOpacity,70);
  const unchanged = JSON.stringify(state);
  for (const spec of editable) {
    const result = adapter.patch(state, baseline, {[spec.id]:baseline[spec.id]});
    assert.equal(result.conflicts.length,0,'every read value is valid: '+spec.id);
  }
  assert.equal(JSON.stringify(state), unchanged, 'read/patch planning never persists or mutates state');
  const offset = adapter.patch(state, baseline, {'offsets.fajr':'4','prayer.adhan-isha':'false'});
  assert.deepEqual(Array.from(offset.prefs.offsets),[4,0,0,0,0,0]); assert.equal(offset.prefs.bells[5],false);
  assert.equal(offset.prefs.volume,25);
  const newState={...state,prefs:core.prefs(state.prefs)}; newState.prefs.clock='12';
  const merged=adapter.patch(newState,baseline,{'reminders.volume':'30'});
  assert.equal(merged.prefs.clock,'12'); assert.equal(merged.prefs.volume,30);
  assert.deepEqual(Array.from(adapter.patch(newState,baseline,{'display.clock':'12'}).conflicts),['display.clock']);
  newState.prefs.homeLayout='compact';
  assert.deepEqual(Array.from(adapter.patch(newState,baseline,{'appearance.theme':'aurora'}).conflicts),['appearance.countdown']);
  const city=adapter.patch(state,baseline,{'prayer.location':'london'}).prefs;
  assert.equal(city.city,'london'); assert.equal(city.method,core.city('london').method); assert.equal(city.hanafi,core.city('london').hanafi);
  const startup=adapter.patch(state,baseline,{'device.startup':'false'});
  assert.equal(startup.startup,false); assert.deepEqual(startup.prefs,core.prefs(state.prefs),'startup is a separately orchestrated OS operation');
  for (const bad of [{'appearance.mihrab-opacity':'49'},{'appearance.mihrab-opacity':'86'},{},{'display.size':'85'},{'reminders.volume':'101'},{'offsets.fajr':'1.5'},{'prayer.zone':'15.0'},
    {'prayer.location':'unknown'},{'reminders.voice':'../outside'}, {'device.startup':'false','display.clock':'12'},
    {'prayer.facing':'10'},{'radio.playback':'true'},{'device.startup':false},JSON.parse('{"__proto__":"value"}')]) {
    assert.throws(()=>adapter.patch(state,baseline,bad));
  }
  assert.throws(()=>adapter.patch(state,{}, {'display.size':'110'}), /baseline/i);
  assert.throws(()=>adapter.patch(state,{}, {'appearance.theme':'aurora'}), /baseline/i);
  assert.equal(JSON.stringify(state),unchanged,'invalid patches preserve all settings and outer state');
};
