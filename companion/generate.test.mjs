import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {test} from 'node:test';
import {generate} from './generate.mjs';
import {vectorIcon} from './icons.mjs';
const data=generate();
test('native icons reuse canonical geometry, preserve filled dots and reject active SVG',()=>{
  const icons=JSON.parse(readFileSync(new URL('../shared/design/icons.json',import.meta.url)));
  for(const svg of Object.values(icons)){const vector=vectorIcon(svg);assert.match(vector,/viewportWidth="24"/);assert.match(vector,/android:pathData=/);}
  assert.match(vectorIcon(icons.back,true),/autoMirrored="true"/);
  assert.match(vectorIcon(icons.more),/fillColor="#FFFFFFFF"/);
  assert.throws(()=>vectorIcon('<script/>'));
});
test('remote commands reference canonical action controls only',()=>{
  const canonical=JSON.parse(readFileSync(new URL('../shared/data/ui-contract.json',import.meta.url)));
  assert.equal(data.commands.length,7);assert.equal(new Set(data.commands).size,data.commands.length);
  for(const id of data.commands)assert.ok(canonical.some(c=>c.id===id&&c.kind==='action'),id);
});
test('every canonical control has bilingual phone metadata and an explicit action route',()=>{
  const source=JSON.parse(readFileSync(new URL('../shared/data/ui-contract.json',import.meta.url)));
  for(const profile of Object.values(data.profiles)) {
    assert.equal(new Set(profile.map(x=>x.id)).size,profile.length);
    for(const c of source) {
      const bound=profile.find(x=>x.id===c.id);
      assert.ok(bound?.title.ar && bound.title.en,c.id);
      if(c.kind==='action') assert.ok(bound.route,c.id);
      if(!bound.available) assert.ok(bound.reason,c.id);
      for(const o of bound.options) assert.ok(o.ar && o.en,c.id+'='+o.value);
      if(bound.available && c.kind==='choice') assert.ok(bound.options.some(x=>x.value===bound.default),c.id);
    }
  }
});
test('profiles preserve real differences without treating missing shortcuts as missing functionality',()=>{
  assert.deepEqual(Object.keys(generate(['lg']).profiles),['lg']);
  assert.deepEqual(generate(['lg']).profiles.lg,data.profiles.lg);
  const item=(profile,id)=>data.profiles[profile].find(x=>x.id===id);
  assert.equal(item('android','reminders.volume').available,false);
  assert.equal(item('lg','reminders.volume').available,true);
  assert.equal(item('android','device.remote').available,true);
  assert.equal(item('lg','device.remote').available,false);
  assert.equal(item('android','reminders.per-prayer').available,true);
  assert.equal(item('lg','appearance.theme').options.some(x=>x.value==='custom'),false);
});
test('generation is deterministic and never includes network addresses or audio bytes',()=>{
  assert.deepEqual(data,generate());
  assert.equal(/https?:\/\//.test(JSON.stringify(data)),false);
  assert.equal(data.profiles.android.find(x=>x.id==='favourites.order').maximum,9);
});
test('companion copy is bilingual and compact/mihrab positions remain independently editable',()=>{
  for(const [key,value] of Object.entries(data.text))assert.ok(value.ar?.trim()&&value.en?.trim(),key);
  for(const controls of Object.values(data.profiles)) {
    assert.deepEqual(controls.find(x=>x.id==='appearance.position').options.map(x=>x.value),['auto','left','right']);
    assert.deepEqual(controls.find(x=>x.id==='appearance.compact-position').options.map(x=>x.value),['top','bottom']);
  }
  const manifest=readFileSync(new URL('android/src/main/AndroidManifest.xml',import.meta.url),'utf8');
  assert.deepEqual([...manifest.matchAll(/<uses-permission android:name="([^"]+)"/g)].map(x=>x[1]),['android.permission.INTERNET','android.permission.CAMERA']);
});
