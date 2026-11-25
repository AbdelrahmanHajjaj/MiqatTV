import {readFileSync, writeFileSync, mkdirSync} from 'node:fs';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {uiChoices, validateUiContract} from '../tools/ui-contract.mjs';
import {vectorIcon} from './icons.mjs';
import {validateStations} from '../tools/catalogs.mjs';

const root = new URL('../', import.meta.url);
const read = path => JSON.parse(readFileSync(new URL(path, root), 'utf8'));
// A short local alert tone; no recording, network or extra media dependency.
export function shortTone() {
  const rate=22050, samples=Math.round(rate*0.18), buffer=Buffer.alloc(44+samples*2);
  buffer.write('RIFF');buffer.writeUInt32LE(buffer.length-8,4);buffer.write('WAVEfmt ',8);
  buffer.writeUInt32LE(16,16);buffer.writeUInt16LE(1,20);buffer.writeUInt16LE(1,22);
  buffer.writeUInt32LE(rate,24);buffer.writeUInt32LE(rate*2,28);buffer.writeUInt16LE(2,32);buffer.writeUInt16LE(16,34);
  buffer.write('data',36);buffer.writeUInt32LE(samples*2,40);
  for(let i=0;i<samples;i++) {
    const envelope=Math.min(1,i/(rate*0.01),(samples-1-i)/(rate*0.03));
    buffer.writeInt16LE(Math.round(Math.sin(2*Math.PI*880*i/rate)*6500*envelope),44+i*2);
  }
  return buffer;
}
export function generatePhone() {
  const contract=read('shared/data/phone-ui-contract.json'),cities=read('shared/data/cities.json'),voices=read('shared/data/voices.json');
  const backgrounds=['plain',...read('shared/design/appearance.json').scenes],text=phoneText(read('shared/data/strings.json'));
  const choices={methods:read('shared/data/methods.json').map(x=>x.id),voices:[...voices.map(x=>x.id),'custom'],
    prayerVoices:['inherit',...voices.map(x=>x.id),'custom'],timezones:['system'],themes:read('shared/design/themes.json').filter(x=>x!=='custom'),backgrounds};
  if(contract.version!==10||!cities.some(c=>c.id===contract.defaultCity)||!voices.some(v=>v.id===contract.defaultFajrVoice))throw Error('Invalid phone catalog version/location');
  const ids=new Set();
  for(const control of [...contract.controls,...contract.perPrayer]) {
    const id=control.id??control.suffix;
    if(typeof id!=='string'||ids.has(id)||!text[control.label]?.en||!text[control.label]?.ar||typeof control.default!=='string')throw Error('Invalid phone control: '+id);
    ids.add(id);
    if(!Number.isInteger(control.since??1)||(control.since??1)<1||(control.since??1)>contract.version)throw Error('Invalid phone control version: '+id);
    if(control.kind==='number') {
      if(![control.minimum,control.maximum,control.step].every(Number.isInteger)||control.step<1||control.minimum>control.maximum||
         !/^-?\d+$/.test(control.default)||+control.default<control.minimum||+control.default>control.maximum||(+control.default-control.minimum)%control.step)throw Error('Invalid phone numeric control: '+id);
    } else if(control.kind==='choice') {
      const options=Array.isArray(control.choices)?control.choices:choices[control.choices];
      if(!options||!options.includes(control.default)||new Set(options).size!==options.length)throw Error('Invalid phone choices: '+id);
    } else if(control.kind!=='toggle'||!['true','false'].includes(control.default))throw Error('Invalid phone control kind: '+id);
  }
  for(const action of contract.actions??[]) {
    if(typeof action.id!=='string'||ids.has(action.id)||!['about','audio','radio'].includes(action.section)||!text[action.label]?.en||!text[action.label]?.ar)throw Error('Invalid phone action: '+action.id);
    ids.add(action.id);
  }
  const hijriEvents=read('shared/data/hijri-events.json'),eventIds=new Set();
  if(!Array.isArray(hijriEvents)||hijriEvents.length>64)throw Error('Invalid Hijri event catalog');
  for(const event of hijriEvents) {
    const end=event.lastDay??event.day;
    if(!/^[a-z][a-z0-9-]*$/.test(event.id)||eventIds.has(event.id)||
      !Number.isInteger(event.month)||event.month<1||event.month>12||!Number.isInteger(event.day)||event.day<1||event.day>30||
      !Number.isInteger(end)||end<event.day||end>30||
      typeof event.en!=='string'||!event.en.trim()||typeof event.ar!=='string'||!event.ar.trim()||
      ![event.en,event.ar].every(label=>/^[^%]*(?:%d[^%]*)?$/.test(label))||
      event.en.includes('%d')!==event.ar.includes('%d')||
      event.en.includes('%d')&&end===event.day)throw Error('Invalid Hijri event: '+event.id);
    eventIds.add(event.id);
  }
  const stations=read('shared/data/stations.json');
  validateStations(stations);
  return {contract,cities,voices,stations,backgrounds,hijriEvents,prayerVerse:read('shared/data/sacred-text.json').prayerVerse};
}
function phoneText(strings) {
  return {...read('companion/strings.json'), ...Object.fromEntries(Object.entries(strings).filter(([key]) => key.startsWith('about')))};
}

export function generate(platforms = ['android','lg']) {
  const controls = read('shared/data/ui-contract.json'), strings = read('shared/data/strings.json');
  const contract = read('companion/contract.json');
  validateUiContract(controls, strings);
  const pair = (ar, en) => ({ar, en});
  const label = key => {
    const [name, index] = key.split(':');
    const source = strings[name];
    if (!source) throw new Error('Unknown label: ' + key);
    return pair(index === undefined ? source.ar : source.ar[Number(index)], index === undefined ? source.en : source.en[Number(index)]);
  };
  const indexed = (key, index) => pair(strings[key].ar[index], strings[key].en[index]);
  const cities = read('shared/data/cities.json'), methods = read('shared/data/methods.json');
  const voices = read('shared/data/voices.json'), stations = read('shared/data/stations.json');
  const themes = read('shared/design/themes.json');
  const choiceArrays = {'prayer.asr':'asrSchools','reminders.before':'remindChoices','reminders.close':'callCloseChoices',
    'radio.screen-off':'radioDarkChoices','display.clock':'clocks','display.bar-mode':'dockModes','display.bar-position':'dockPositions',
    'appearance.countdown':'homeCountdowns','appearance.prayers':'homePrayerModes','appearance.favourites':'homeLayouts','appearance.cycle':'appearanceCycleChoices'};
  function option(c, value, index) {
    let item;
    if (c.choices === 'cities') { item = cities.find(x => x.id === value); return pair(item.ar + ' · ' + item.countryAr, item.en + ' · ' + item.countryEn); }
    if (['methods','voices','stations'].includes(c.choices)) {
      if (value === 'city-default') return pair('حسب المدينة','City default');
      item = ({methods,voices,stations})[c.choices].find(x => x.id === value);
      return pair(item.ar + (item.whereAr ? ' · ' + item.whereAr : ''), item.en + (item.whereEn ? ' · ' + item.whereEn : ''));
    }
    if (c.choices === 'themes') return indexed('themeNames', index);
    if (c.choices === 'backgrounds') {
      const a=read('shared/design/appearance.json');
      return indexed('backgroundNames', ['theme',...a.proceduralBackgrounds,...a.patterns,...a.scenes].indexOf(value));
    }
    if (choiceArrays[c.id]) return indexed(choiceArrays[c.id], index);
    if (c.id === 'appearance.position') return index < 3 ? indexed('homeSides', index) : indexed('homeBars', index - 3);
    if (c.id === 'display.language') return [pair('لغة التلفاز','TV language'),pair('العربية','Arabic'),pair('الإنجليزية','English')][index];
    if (c.id === 'display.digits') return [pair('تلقائي','Automatic'),pair('0123456789','0123456789'),pair('٠١٢٣٤٥٦٧٨٩','٠١٢٣٤٥٦٧٨٩')][index];
    if (c.id === 'display.bar-timeout') return value === '0' ? pair('دائماً','Always') : pair(value + ' ثانية', value + ' seconds');
    if (c.id === 'tests.prayer') return indexed('prayerNames', Number(value));
    if (c.id === 'device.startup-input') {
      if (value === 'DTV' || value === 'ATV') return pair('البث التلفزيوني', 'Live TV');
      return pair(value.replace('_', ' '), value.replace('_', ' '));
    }
    if (c.choices === 'zones') {
      if (value === 'system') return pair('توقيت التلفاز','TV timezone');
      const n = Number(value), text = 'UTC' + (n < 0 ? '−' : '+') + String(Math.floor(Math.abs(n)/60)).padStart(2,'0') + ':' + String(Math.abs(n)%60).padStart(2,'0');
      return pair(text,text);
    }
    throw new Error('Unlabelled option: ' + c.id);
  }
  const all = controls.map(c => ({...c, title:label(c.label), options:uiChoices(c).map((value,i) => ({value,...option(c,value,i)})), route:contract.actionRoutes[c.id] || ''}));
  const defaults = {'prayer.location':'cairo','prayer.method':'EGYPTIAN','prayer.asr':'shafi','prayer.zone':'system',
    'reminders.before':'15','reminders.close':'0','reminders.voice':'almalki','reminders.fajr-voice':'chebae_fajr','reminders.volume':'60',
    'radio.station':'cairo','radio.screen-off':'0','display.language':'system','display.digits':'western','display.clock':'12',
    'display.size':'100','display.bar-mode':'hidden','display.bar-position':'top','display.bar-timeout':'20',
    'appearance.countdown':'mihrab','appearance.position':'auto','appearance.prayers':'all','appearance.favourites':'two_rows',
    'appearance.mihrab-opacity':'85','appearance.theme':'mihrab','appearance.background':'miqat_signature','appearance.lighting':'35','appearance.cycle':'0','tests.prayer':'0'};
  const trueIds = ['reminders.adhan','reminders.announce','radio.resume',...controls.filter(c=>c.section==='prayer-day').map(c=>c.id)];
  const appOptions = [pair('القرآن','Quran'),pair('الفيديو','Video'),pair('الموسيقى','Music'),pair('المتصفح','Browser')].map((x,i)=>({value:['quran','video','music','browser'][i],...x}));
  const extras = contract.extraSettings.map(c=>({...c, title:pair(c.ar,c.en), save:'immediate',
    options:c.choiceSource ? all.find(x=>x.id===c.choiceSource).options.filter(x=>c.values.includes(x.value)) : c.kind === 'list' ? appOptions:[], route:''}));
  const buttons = contract.remoteButtons.map(c=>({id:'remote.'+c.id,section:'remote',kind:'choice',save:'immediate',title:pair(c.ar,c.en),default:'@default',route:'',
    options:['@default','@home','@apps','@inputs','@settings','@dock'].map((value,i)=>({value,...indexed('buttonActions',i)})).concat(appOptions)}));
  const profiles = {};
  for (const platform of platforms) {
    const gapPath = platform === 'android' ? 'platforms/android-tv/src/androidTest/assets/' : 'platforms/webos/';
    const gaps = read(gapPath + 'ui-gaps.json'), choiceGaps = read(gapPath + 'ui-choice-gaps.json');
    const unsupported = new Set(gaps.map(x=>x.id));
    // These Android features exist behind Home or a combined control, despite missing catalog shortcuts.
    if (platform === 'android') ['prayer.today','reminders.per-prayer','device.apps','tests.prayer','radio.play','radio.stop'].forEach(id=>unsupported.delete(id));
    const entries = all.map(c=>{
      const missing = choiceGaps.find(x=>x.id===c.id)?.values || [];
      let options = c.options.filter(x=>!missing.includes(x.value));
      // The phone exposes the two independently persisted positions as separate editors.
      if(c.id==='appearance.position')options=options.filter(x=>!['top','bottom'].includes(x.value));
      const def = platform==='lg' && c.id==='appearance.background' ? read('shared/design/appearance.json').defaultBackground : platform==='lg' && c.id==='display.language' ? 'ar' : defaults[c.id];
      return {...c, options, default:def ?? (c.kind==='toggle' ? String(trueIds.includes(c.id)) : c.kind==='number' ? '0' : options[0]?.value || ''),
        available:!unsupported.has(c.id), reason:gaps.find(x=>x.id===c.id)?.reason || ''};
    });
    entries.push(...extras.filter(x=>x.profiles?.includes(platform)||!x.profiles&&platform==='android').map(x=>({...x,available:true})));
    if (platform === 'android') entries.push(...buttons.map(x=>({...x,available:true})));
    profiles[platform] = entries;
  }
  const tokens=read('shared/design/mihrab/tokens.json');
  const colors=Object.fromEntries(Object.entries(tokens.color).filter(([,v])=>v.$type==='color' && /^#[0-9a-f]{6}$/i.test(v.$value)).map(([k,v])=>[k,v.$value]));
  const themeData={mihrab:{version:1,colors}};
  for(const id of themes.filter(x=>!['mihrab','custom'].includes(x))) themeData[id]=read('shared/design/assets/themes/'+id+'.json');
  const sections={prayer:label('sectionPrayer'),reminders:label('sectionReminders'),adhan:label('adhanRow'),radio:label('sectionRadio'),display:label('sectionDisplay'),appearance:label('sectionAppearance'),
    network:pair('الشبكة','Network'),device:label('sectionDevice'),tests:pair('اختبارات التلفاز','TV tests'),offsets:label('prayerOffsets'),'prayer-day':pair('الأذان لكل صلاة','Adhan per prayer'),
    favourites:pair('التطبيقات المفضلة','Favourite apps'),apps:pair('التطبيقات','Apps'),remote:pair('أزرار الريموت','Remote buttons'),pairing:label('companionPhones')};
  const commands=read('companion/protocol/lan.json').commands;
  for(const id of commands)if(!all.some(c=>c.id===id&&c.kind==='action'))throw Error('Unknown canonical command: '+id);
  return {version:1,setupPlatforms:read('shared/data/tv-platforms.json'),commands,profiles,demoSystemLanguage:'ar',sections,colors,themeKeys:read('shared/design/appearance.json').colors,themeSurfaces:read('shared/design/appearance.json').surfaces,themes:themeData,appOptions,prayerNames:strings.prayerNames,text:phoneText(strings), cities:cities.map(c=>({id:c.id,method:c.method,asr:c.hanafi?'hanafi':'shafi'}))};
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  if(process.argv[2]==='--android-tv') {
    const output=new URL('platforms/android-tv/build/generated/companion/',root);mkdirSync(output,{recursive:true});
    writeFileSync(new URL('catalog.json',output),JSON.stringify(generate(['android'])));process.exit(0);
  }
  if(process.argv[2]==='--lg-tv') {
    const output=new URL('platforms/webos/build/service/',root);mkdirSync(output,{recursive:true});
    writeFileSync(new URL('companion-catalog.json',output),JSON.stringify(generate(['lg']).profiles.lg));process.exit(0);
  }
  const output = new URL('android/build/generated/companion/',import.meta.url);
  mkdirSync(output,{recursive:true});
  writeFileSync(new URL('catalog.json',output),JSON.stringify(generate()));
  const phone=generatePhone();
  writeFileSync(new URL('phone.json',output),JSON.stringify(phone));
  const res = new URL('android/build/generated/companion-res/',import.meta.url);
  mkdirSync(new URL('drawable/',res),{recursive:true});
  mkdirSync(new URL('values/',res),{recursive:true});
  mkdirSync(new URL('drawable-nodpi/',res),{recursive:true});
  writeFileSync(new URL('drawable-nodpi/miqat_icon.png',res),readFileSync(new URL('shared/design/artwork/icon.png',root)));
  writeFileSync(new URL('drawable-nodpi/miqat_mark.png',res),readFileSync(new URL('shared/design/assets/qr-mark.png',root)));
  mkdirSync(new URL('raw/',res),{recursive:true});
  writeFileSync(new URL('raw/miqat_short_tone.wav',res),shortTone());
  for(const [id,svg] of Object.entries({...read('shared/design/icons.json'),...Object.fromEntries(read('shared/design/prayer-icons.json').prayers.map(p=>[p.id,`<path d="${p.path}"/>`]))}))writeFileSync(new URL('drawable/companion_icon_'+id+'.xml',res),vectorIcon(svg,id==='back'));
  const catalog=generate(), colors=catalog.colors;
  writeFileSync(new URL('values/companion_colors.xml',res),'<resources>'+['lapis-900','lapis-800','gold-400','plaster-100','mist-400'].map(id=>'<color name="companion_'+id.replaceAll('-','_')+'">'+colors[id]+'</color>').join('')+'</resources>');
  const sampleTimes=['05:10','06:30','12:00','15:15','17:30','19:00'];
  for(const language of ['en','ar']) {
    const directory=new URL(language==='ar'?'values-ar/':'values/',res);
    mkdirSync(directory,{recursive:true});
    const entries=catalog.prayerNames[language].map((name,i)=>['widget_preview_prayer_'+i,name]);
    const sampleDate=new Intl.DateTimeFormat(language+'-u-ca-islamic-umalqura',{day:'numeric',month:'long',year:'numeric',timeZone:'UTC'}).format(new Date('2026-11-21T12:00:00Z'));
    const circleDate=language==='ar'?sampleDate.replace(/[0-9]/g,digit=>'٠١٢٣٤٥٦٧٨٩'[Number(digit)]):sampleDate;
    const cairo=read('shared/data/cities.json').find(city=>city.id==='cairo');
    const countdown=language==='ar'?'٢٤:٣٠':'24:30';
    entries.push(['widget_preview_metadata',sampleDate+' · '+cairo[language]],['widget_preview_hijri',circleDate],['widget_preview_countdown',countdown],['widget_preview_remaining',catalog.text.phone_remaining[language].replace('%s',countdown)]);
    const clock=new Intl.DateTimeFormat(language,{hour:'numeric',minute:'2-digit',hour12:phone.contract.controls.find(c=>c.id==='display.clock').default==='12',numberingSystem:language==='ar'?'arab':'latn',timeZone:'UTC'});
    entries.push(...sampleTimes.map((time,i)=>['widget_preview_time_'+i,clock.format(new Date('2026-11-21T'+time+':00Z'))]));
    const xml=entries.map(([name,value])=>'<string name="'+name+'">'+value.replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll("'","\\'")+'</string>').join('');
    writeFileSync(new URL('companion_widget_preview.xml',directory),'<resources>'+xml+'</resources>');
  }
  console.log('Generated companion catalog from canonical product sources');
}
