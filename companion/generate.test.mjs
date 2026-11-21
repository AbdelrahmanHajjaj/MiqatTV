import assert from 'node:assert/strict';
import {readFileSync,readdirSync} from 'node:fs';
import {test} from 'node:test';
import {generate,generatePhone,shortTone} from './generate.mjs';
import {vectorIcon} from './icons.mjs';
const data=generate();
test('phone controls retain independent defaults, real canonical locations/voices and bilingual metadata',()=>{
  const phone=generatePhone();
  assert.equal(phone.contract.controls.find(c=>c.id==='reminders.enabled').default,'false');
  assert.ok(phone.cities.some(c=>c.id===phone.contract.defaultCity&&Number.isFinite(c.lat)&&Number.isFinite(c.lng)));
  assert.deepEqual(phone.voices,JSON.parse(readFileSync(new URL('../shared/data/voices.json',import.meta.url))));
  assert.ok(phone.contract.perPrayer.some(c=>c.suffix==='before'));
  assert.ok(phone.contract.perPrayer.some(c=>c.suffix==='after'));
  assert.ok(phone.backgrounds.includes(phone.contract.controls.find(c=>c.id==='appearance.background').default));
});
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
  assert.deepEqual([...manifest.matchAll(/<uses-permission android:name="([^"]+)"/g)].map(x=>x[1]),['INTERNET','CAMERA','ACCESS_COARSE_LOCATION','ACCESS_FINE_LOCATION','POST_NOTIFICATIONS','SCHEDULE_EXACT_ALARM','RECEIVE_BOOT_COMPLETED','FOREGROUND_SERVICE','FOREGROUND_SERVICE_MEDIA_PLAYBACK','WAKE_LOCK','USE_FULL_SCREEN_INTENT','VIBRATE'].map(x=>'android.permission.'+x));
});

test('new branding settings reach both profiles with canonical scenes and opacity drafts',()=>{
  for(const profile of Object.values(data.profiles)) {
    const opacity=profile.find(c=>c.id==='appearance.mihrab-opacity');
    assert.ok(opacity.available);assert.equal(opacity.save,'appearance');assert.equal(opacity.default,'85');
    const choices=profile.find(c=>c.id==='appearance.background').options.map(c=>c.value);
    assert.ok(choices.includes('miqat_signature')&&choices.includes('miqat_quiet'));
  }
});

test('phone alert metadata keeps independent enable, weekday overrides and relative audio bounds',()=>{
 const {contract}=generatePhone();
 assert.equal(contract.version,5);
 assert.equal(contract.perPrayer.find(c=>c.suffix==='enabled').default,'true');
 for(let day=0;day<7;day++) {
  assert.deepEqual(contract.perPrayer.find(c=>c.suffix===`day-${day}-sound`).choices,['inherit','adhan','notification','silent']);
  assert.equal(contract.perPrayer.find(c=>c.suffix===`day-${day}-vibrate`).default,'false');
 }
 for(const c of [contract.controls.find(c=>c.id==='reminders.length-percent'),contract.perPrayer.find(c=>c.suffix==='length-percent')])
  assert.deepEqual([c.minimum,c.maximum,c.step,c.default],[5,100,5,'100']);
 const tone=shortTone();
 assert.equal(tone.toString('ascii',0,4),'RIFF');assert.equal(tone.toString('ascii',8,12),'WAVE');
 assert.equal(tone.readUInt32LE(4),tone.length-8);assert.equal(tone.readUInt32LE(40),tone.length-44);
 assert.ok(Math.abs((tone.length-44)/2/tone.readUInt32LE(24)-0.18)<0.0001);
 for(const prayer of JSON.parse(readFileSync(new URL('../shared/design/prayer-icons.json',import.meta.url))).prayers)
  assert.ok(vectorIcon(`<path d="${prayer.path}"/>`).includes(prayer.path));
});

test('native literal copy references resolve to bilingual canonical text',()=>{
  const directory=new URL('./android/src/main/java/tv/miqat/companion/',import.meta.url);
  for(const name of readdirSync(directory).filter(name=>name.endsWith('.java'))){
    const source=readFileSync(new URL(name,directory),'utf8');
    for(const [,key] of source.matchAll(/\bt\("([^"\n]+)"\)/g))
      assert.ok(data.text[key]?.en&&data.text[key]?.ar,`${name}: unknown text ${key}`);
  }
});
