const assert = require('node:assert/strict');

module.exports = function checkPreferences(core) {
  const retired = core.prefs({voice: 'alafasy', fajrVoice: 'alafasy', volume: 25});
  assert.equal(retired.voice, 'almalki');
  assert.equal(retired.fajrVoice, 'chebae_fajr');
  assert.equal(retired.volume, 25, 'retired voice migration preserves other settings');
  const baseline = core.prefs({clock:'24'}), current = core.prefs(baseline), edit = core.prefs(baseline);
  current.volume = 25; edit.clock = '12';
  const merged = core.mergePrefs(current, baseline, edit);
  assert.equal(merged.prefs.volume, 25, 'a full local draft preserves an unrelated later phone edit');
  assert.equal(merged.prefs.clock, '12');
  assert.equal(merged.conflicts.length, 0);
  assert.equal(current.clock, '24', 'merge does not mutate caller state');
  edit.volume = 30;
  assert.deepEqual(Array.from(core.mergePrefs(current, baseline, edit).conflicts), ['volume']);
  assert.equal(core.mergePrefs(current, baseline, edit).prefs.clock, '24', 'a conflict rejects the entire patch');
  const appearance = core.prefs(baseline), otherAppearance = core.prefs(baseline);
  appearance.theme = 'aurora'; otherAppearance.homeLayout = 'compact';
  assert.deepEqual(Array.from(core.mergePrefs(otherAppearance, baseline, appearance).conflicts), ['homeLayout']);
  otherAppearance.homeLayout = baseline.homeLayout; otherAppearance.mihrabOpacity = 70;
  assert.deepEqual(Array.from(core.mergePrefs(otherAppearance, baseline, appearance).conflicts), ['mihrabOpacity'], 'opacity shares the appearance conflict group');
  const offsets = core.prefs(baseline), otherOffsets = core.prefs(baseline);
  offsets.offsets[0] = 4; otherOffsets.offsets[2] = -3; offsets.bells[0] = false; otherOffsets.bells[2] = false;
  const perPrayer = core.mergePrefs(otherOffsets, baseline, offsets);
  assert.equal(perPrayer.conflicts.length, 0);
  assert.deepEqual(Array.from(perPrayer.prefs.offsets), [4, 0, -3, 0, 0, 0]);
  assert.deepEqual(Array.from(perPrayer.prefs.bells), [false, false, false, true, true, true]);
  const city = core.prefs(baseline), method = core.prefs(baseline);
  city.city = 'london'; method.method = 'KARACHI';
  assert.deepEqual(Array.from(core.mergePrefs(method, baseline, city).conflicts), ['method'], 'city dependencies share a conflict group');
  for (const bad of [null, [], {}, {...baseline, extra: true}]) {
    assert.throws(() => core.mergePrefs(current, bad, edit), /baseline/);
    assert.throws(() => core.mergePrefs(current, baseline, bad), /baseline/);
  }
  assert.throws(() => core.mergePrefs(current, baseline, {...baseline, volume: 101}), /Invalid/);
  assert.deepEqual(core.mergePrefs(current, baseline, baseline).prefs, core.prefs(current), 'a no-op cannot restore cached values');
};
