const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const { chromium } = require('playwright');
const base = path.join(__dirname, 'build/app');
const voices = JSON.parse(fs.readFileSync(path.join(__dirname, '../../shared/data/voices.json')));
const sacred = JSON.parse(fs.readFileSync(path.join(__dirname, '../../shared/data/sacred-text.json')));
const uiGaps = JSON.parse(fs.readFileSync(path.join(__dirname, 'ui-gaps.json')));
const uiChoiceGaps = JSON.parse(fs.readFileSync(path.join(__dirname, 'ui-choice-gaps.json')));
const appVersion = require('./app/appinfo.json').version;
const shots = path.resolve(__dirname, '../../state/webos-port');
const capture=process.argv.includes('--capture');
if(capture)fs.mkdirSync(shots, { recursive: true });
const server = http.createServer((req, res) => {
  const file = path.resolve(base, '.' + (req.url === '/' ? '/index.html' : req.url));
  if (!file.startsWith(base + path.sep) || !fs.existsSync(file)) { res.writeHead(404); res.end(); return; }
  const types = {'.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.ttf': 'font/ttf', '.svg': 'image/svg+xml'};
  res.setHeader('Content-Type', types[path.extname(file)] || 'application/octet-stream');
  res.end(fs.readFileSync(file));
});
(async () => {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  let browser, page;
  try {
    browser = await chromium.launch({channel: 'msedge', headless: true});
    page = await browser.newPage({viewport: {width: 1920, height: 1080}});
    const originalPress = page.keyboard.press.bind(page.keyboard);
    page.keyboard.press = async (...args) => { await originalPress(...args); await page.waitForFunction(() => document.querySelector('#screen')?.getAttribute('aria-busy') !== 'true'); };
    const locatorPrototype=Object.getPrototypeOf(page.locator('body')), originalClick=locatorPrototype.click;
    locatorPrototype.click=async function(...args) { await originalClick.apply(this,args); await page.waitForFunction(() => document.querySelector('#screen')?.getAttribute('aria-busy') !== 'true'); };
    const identity=await new Promise((resolve,reject)=>require('./build/service/companion-identity').load({read:()=>null,write(){}},(error,value)=>error?reject(error):resolve(value)));
    const qrFixture={type:'miqat-tv-pair',version:3,url:'https://192.168.1.5:45761',pin:identity.pin,token:require('./service/companion-pairing').token()};
    await page.addInitScript(qrFixture => {
      // Mock the TV boundary and decoder events; the physical LG validates actual picture/sound.
      window.mediaCalls = [];
      window.volumeCalls = [];
      window.onlineCalls = [];
      window.closeCount = 0; window.close = () => { window.closeCount++; };
      window.companionCalls=[]; window.companionState={supported:true,starting:false,failure:'',phones:[],open:false,pending:null,approved:false,ownsPairing:false,qr:null};
      window.publishCompanion=(extra={})=>window.companionBridge?.onservicecallback(JSON.stringify({returnValue:true,subscribed:true,session:'owned-frontend',companion:window.companionState,...extra}));
      window.companionRequest=(name='Home phone')=>{
        window.companionState.pending={name,nonce:'N'.repeat(43),code:'123456'};window.publishCompanion();
      };
      window.companionComplete=()=>{
        if(!window.companionState.approved)throw Error('Fixture approval required');
        window.companionState.phones.push({id:'P'.repeat(43),name:window.companionState.pending.name});
        Object.assign(window.companionState,{open:false,pending:null,approved:false,ownsPairing:false,qr:null});window.publishCompanion();
      };
      HTMLMediaElement.prototype.load = function () { window.mediaCalls.push({op: 'load', tag: this.tagName, src: this.getAttribute('src'), children: this.children.length}); };
      HTMLMediaElement.prototype.pause = function () { window.mediaCalls.push({op: 'pause', tag: this.tagName}); };
      HTMLMediaElement.prototype.play = function () {
        const inputVideo = document.getElementById('input-video');
        window.mediaCalls.push({op: 'play', tag: this.tagName, src: this.getAttribute('src'), masterVolume: window.mockMasterVolume === undefined ? 50 : window.mockMasterVolume, elementVolume: this.volume,
          inputSource: inputVideo.querySelector('source')?.getAttribute('src')});
        setTimeout(() => this.dispatchEvent(new Event('playing')), 0); return Promise.resolve();
      };
      window.PalmServiceBridge = function () {
        this.cancel=()=>{window.companionCalls.push({action:'bridge-cancel'});if(window.companionBridge===this)window.companionBridge=null;};
        this.call = (uri, raw) => {
          const p = JSON.parse(raw), method = uri.split('/').pop();
          if(method.startsWith('companion')) {
            window.companionCalls.push({method,...p});const fixture=window.companionState;
            const respond=(ok=true)=>setTimeout(()=>this.onservicecallback(JSON.stringify({returnValue:ok})),5);
            if(method==='companionForeground'){window.companionBridge=this;setTimeout(()=>window.publishCompanion(),5);return;}
            if(method==='companionLeave'){Object.assign(fixture,{open:false,pending:null,approved:false,ownsPairing:false,qr:null});respond();return;}
            if(method==='companionUi') {
              window.companionEditing=p.editing;window.companionPage=p.pairingPage;
              if(!p.pairingPage&&fixture.open){Object.assign(fixture,{open:false,pending:null,approved:false,ownsPairing:false,qr:null});setTimeout(()=>window.publishCompanion(),5);}
              respond();return;
            }
            if(method==='companionPairing') {
              if(window.failCompanion||!window.companionPage||!fixture.supported){respond(false);return;}
              if(p.action==='open')Object.assign(fixture,{open:true,pending:null,approved:false,ownsPairing:true,qr:qrFixture});
              else if(p.action==='approve'){if(p.nonce!==fixture.pending?.nonce){respond(false);return;}fixture.approved=true;}
              else if(p.action==='reject'||p.action==='cancel')Object.assign(fixture,{open:false,pending:null,approved:false,ownsPairing:false,qr:null});
              else if(p.action==='revoke')fixture.phones=fixture.phones.filter(phone=>phone.id!==p.id);
              else {respond(false);return;}
              setTimeout(()=>{this.onservicecallback(JSON.stringify({returnValue:true}));window.publishCompanion();},5);return;
            }
          }
          const state = JSON.parse(localStorage.getItem('fixture') || '{}');
          if (method === 'savePrefs') {
            const merged = window.Miqat.mergePrefs(state.prefs, p.expected, p.prefs);
            if (merged.conflicts.length) {
              setTimeout(() => this.onservicecallback(JSON.stringify({returnValue: false, conflicts: merged.conflicts, prefs: merged.prefs})), 5); return;
            }
            state.prefs = merged.prefs;
          }
          if (method === 'setStartup') state.startup = {enabled: p.enabled};
          if (method === 'selectInput') state.input = p.id;
          if (method === 'media') state.media = p;
          if (method === 'launchApp') state.launchedApp = p.id;
          localStorage.setItem('fixture', JSON.stringify(state));
          const reply = {returnValue: true, state, prefs: state.prefs, enabled: state.startup && state.startup.enabled,
            current: window.Miqat.input(state.input || 'HDMI_1'), inputs: ['HDMI_1', 'HDMI_2', 'HDMI_3', 'DTV'].map(window.Miqat.input)};
          if (method === 'onlinePrayer') {
            window.onlineCalls.push(p);
            if (p.action === 'check') {
              const finish = () => {
                reply.returnValue = !window.failOnline;
                const prefs = window.Miqat.prefs(state.prefs), times = window.Miqat.day(new Date(), prefs).times;
                const differences = [0, 1, -1, 1, 0, 0];
                reply.comparison = {date: '12-11-2026', timezone: 'Africa/Cairo', calculated: times,
                  reference: times.map((t, i) => t + differences[i] * 60000), differences};
                this.onservicecallback(JSON.stringify(reply));
              };
              if (window.deferOnline) (window.onlineReplies ||= []).push(finish);
              else setTimeout(finish, 5);
              return;
            }
          }
          if (method === 'audioVolume') {
            window.volumeCalls.push(p);
            const finish = () => {
              reply.mode = window.legacyVolume ? 'tv' : 'element';
              if (window.failVolume && p.action === 'prepare') {
                reply.returnValue = false; reply.errorText = 'Fixture volume failure';
              } else if (window.legacyVolume) {
                const owner = window.mockVolumeOwner;
                if (p.action === 'prepare') {
                  const original = owner && window.mockMasterVolume === owner.applied ? owner.original : window.mockMasterVolume;
                  const applied = state.prefs.volume;
                  window.mockMasterVolume = applied; window.mockVolumeOwner = {id: p.id, original, applied};
                } else if (p.action === 'release' && owner && owner.id === p.id) {
                  if (window.mockMasterVolume === owner.applied) window.mockMasterVolume = owner.original;
                  window.mockVolumeOwner = null;
                }
              }
              this.onservicecallback(JSON.stringify(reply));
            };
            if (window.deferVolume && p.action === 'prepare') { (window.volumeReplies ||= []).push(finish); }
            else setTimeout(finish, 5);
            return;
          }
          if (method === 'takeAlert') { reply.alert = state.pendingAlert; if (state.expiredAlert) { reply.returnValue = false; } }
          if (method === 'launchApp' && window.failAppLaunch) { reply.returnValue = false; reply.errorText = 'App unavailable'; }
          setTimeout(() => this.onservicecallback(JSON.stringify(reply)), 5);
        };
      };
    },qrFixture);
    async function inputRestored() {
      await page.waitForFunction(() => {
        const video = document.getElementById('input-video'), source = video.querySelector('source');
        return source && video.volume === 1 && !video.muted && !source.type.includes('mediaOption=') && !document.querySelector('audio');
      });
    }
    const errors = []; page.on('pageerror', e => errors.push(e.message));
    // Observe startup before app initialization, independent of the runner's speed.
    let resumeStartup;
    const startupGate=new Promise(resolve=>{resumeStartup=resolve;});
    const holdStartup=async route=>{await startupGate;await route.continue();};
    await page.route('**/app.js',holdStartup);
    await page.goto('http://127.0.0.1:' + server.address().port, {waitUntil: 'commit'});
    await page.locator('#splash').waitFor({state:'visible'});
    assert.equal(await page.locator('#splash').isVisible(), true, 'startup artwork is visible before the first screen');
    assert.equal(await page.locator('#screen').evaluate(screen=>screen.childElementCount),0,'startup precedes the first screen');
    assert.equal(await page.locator('#splash').evaluate(async element=>{
      const image=new Image();image.src=getComputedStyle(element).backgroundImage.match(/url\(["']?(.*?)["']?\)/)[1];
      await image.decode();return image.naturalWidth>0&&image.naturalHeight>0;
    }),true,'startup artwork decodes');
    resumeStartup();await page.waitForLoadState('domcontentloaded');
    await page.unroute('**/app.js',holdStartup);
    await page.locator('#splash').waitFor({state:'hidden'});
    assert.notEqual(await page.locator('#stage').getAttribute('data-layout'),null,'startup hands off to the initialized layout');
    assert.match(await page.title(), /^Miqat/);
    await page.evaluate(() => document.fonts.ready);
    await inputRestored();
    const beforeSpacing = await page.evaluate(() => localStorage.getItem('fixture'));
    for (const lang of ['ar', 'en']) for (const uiScale of [110,80,90,100,120,130,140,150]) {
      await page.evaluate(({lang,uiScale}) => localStorage.setItem('fixture', JSON.stringify({prefs:window.Miqat.prefs({lang,uiScale}),input:'HDMI_1'})), {lang,uiScale});
      await page.reload(); await inputRestored(); await page.keyboard.press('Enter');
      await page.locator('[data-action=home]').click(); await page.locator('[data-action=settings]').click();
      await page.locator('[data-action=section][data-value=prayer]').focus();
      await page.keyboard.press('ArrowDown');
      assert.equal(await page.locator('.settings-nav .selected').getAttribute('data-value'),'reminders','Down previews the focused sidebar section without OK');
      assert.equal(await page.locator(':focus').getAttribute('data-value'),'reminders','preview retains sidebar focus');
      assert.ok(await page.locator('.settings-rows [data-ui^="reminders."]').count()>0,'previewed section renders real reminder controls');
      await page.keyboard.press('Enter');
      assert.equal(await page.locator(':focus').getAttribute('data-ui'), 'reminders.enabled', 'OK enters the first Reminders control');
      await page.keyboard.press('Escape');
      await page.keyboard.press(lang==='ar'?'ArrowLeft':'ArrowRight');
      assert.equal(await page.locator(':focus').evaluate(el=>!!el.closest('.settings-rows')),true,'content-direction arrow enters the previewed section');
      await page.keyboard.press('Escape');
      await page.keyboard.press('ArrowUp');
      assert.equal(await page.locator('.settings-nav .selected').getAttribute('data-value'),'prayer','Up previews the previous section without OK');
      const spacing = await page.locator('.settings-page').evaluate(el => {
        const box=el.getBoundingClientRect(), scale=box.width/el.offsetWidth, rtl=document.documentElement.dir==='rtl';
        const width=el.querySelector('.settings-sidebar').offsetWidth*scale;
        const edge=rtl ? box.right-width : box.left+width;
        const row=el.querySelector('.settings-rows button').getBoundingClientRect();
        const nav=el.querySelector('.settings-nav').getBoundingClientRect();
        return {gap:rtl ? edge-row.right : row.left-edge, navGap:rtl ? nav.left-edge : edge-nav.right, width, navLeft:nav.left, navRight:nav.right};
      });
      assert.ok(spacing.gap>=48, `${lang} ${uiScale}% Settings keeps space beyond the sidebar: ${JSON.stringify(spacing)}`);
      assert.ok(spacing.navGap>=12, 'sidebar contains its navigation and focus ring');
      assert.ok(spacing.width<=561, 'sidebar stays slim when interface size increases');
      assert.ok(spacing.navLeft>=48 && spacing.navRight<=1872, 'navigation keeps padding from the TV edges');
      if(uiScale===110) if(capture)await page.screenshot({path:path.join(shots,'settings-spacing-'+lang+'-110.png')});
      await page.locator('[data-action=section][data-value=device]').click();
      const about = page.locator('[data-ui="device.about"]');
      await about.focus();
      const beforeAbout = await page.locator('.settings-rows').evaluate(el => el.scrollTop);
      await page.keyboard.press('Enter');
      assert.equal(await page.locator('.about-version').textContent(), appVersion, 'About shows the installed package version');
      assert.match(await page.locator('.about-details').innerText(), lang === 'ar' ? /عبد الرحمن حجاج/ : /Abdelrahman Hajjaj/);
      assert.equal(await page.locator('.about-details button').isDisabled(), true, 'Email is unavailable');
      assert.ok(await page.locator('.about-qr > svg').evaluate(el => {
        const qr = el.getBoundingClientRect(), page = el.closest('.about-page').getBoundingClientRect();
        return qr.width > 240 && qr.bottom <= page.bottom && qr.left >= page.left && qr.right <= page.right;
      }), 'the full feedback QR is visible');
      if (capture && uiScale === 150) await page.screenshot({path:path.join(shots,'about-'+lang+'.png')});
      await page.keyboard.press('Escape');
      assert.equal(await page.locator(':focus').getAttribute('data-ui'), 'device.about');
      assert.equal(await page.locator('.settings-rows').evaluate(el => el.scrollTop), beforeAbout);
      if (uiScale === 110) {
        await page.locator('.settings-nav [data-value=reminders]').click();
        assert.equal(await page.locator(':focus').getAttribute('data-ui'), 'reminders.enabled', 'activating Reminders moves focus into content');
        await page.locator('.settings-rows [data-action=tests]').click();
        assert.equal(await page.locator(':focus').getAttribute('data-ui'), 'tests.prayer', 'opening Adhan Tests focuses its first control');
        await page.keyboard.press('Escape');
        await page.keyboard.press('Enter');
        assert.equal(await page.locator(':focus').getAttribute('data-ui'), 'tests.prayer', 'OK on the Tests sidebar enters its first control');
      }
      await page.locator('[data-action=section][data-value=appearance]').click();
      assert.ok(await page.locator('.appearance-preview').evaluate(el => {
        const preview=el.getBoundingClientRect(), rows=document.querySelector('.settings-rows').getBoundingClientRect();
        return preview.left>=rows.left-1 && preview.right<=rows.right+1;
      }), 'appearance preview stays within the content column');
    }
    await page.evaluate(original => original===null ? localStorage.removeItem('fixture') : localStorage.setItem('fixture', original), beforeSpacing);
    await page.reload(); await inputRestored();
    console.log('PASS: Settings spacing, About version/QR/return focus and appearance bounds in both languages at every supported size.');
    if(process.argv.includes('--settings-layout')) return;
    async function preservingSettings() {
      const original = await page.evaluate(() => localStorage.getItem('fixture'));
      for (const lang of ['ar', 'en']) {
        const next = lang === 'ar' ? 'ArrowLeft' : 'ArrowRight';
        await page.evaluate(lang => localStorage.setItem('fixture', JSON.stringify({prefs: window.Miqat.prefs({lang,clock:'24',volume:25}), input:'HDMI_1', startup:{enabled:false, launches:7}, audioVolume:{fixture:true}})), lang);
        await page.reload(); await inputRestored(); await page.keyboard.press('Enter');
        await page.locator('[data-action=home]').click(); await page.locator('[data-action=settings]').click();
        await page.locator('[data-action=section][data-value=display]').click();
        await page.evaluate(() => { const state=JSON.parse(localStorage.getItem('fixture')); state.prefs.volume=30; localStorage.setItem('fixture',JSON.stringify(state)); });
        await page.locator('[data-action=uiScale]').focus(); await page.keyboard.press(next);
        assert.deepEqual(await page.evaluate(() => { const state=JSON.parse(localStorage.getItem('fixture')); return [state.prefs.uiScale,state.prefs.volume,state.startup.launches,state.input,state.audioVolume]; }), [110,30,7,'HDMI_1',{fixture:true}], 'immediate save preserves newer unrelated preferences and outer state');
        await page.locator('[data-action=section][data-value=reminders]').click();
        await page.evaluate(() => { const state=JSON.parse(localStorage.getItem('fixture')); state.prefs.volume=40; localStorage.setItem('fixture',JSON.stringify(state)); });
        await page.locator('[data-action=volume]').focus(); await page.keyboard.press(next);
        await page.locator('[data-action=reload-settings]').waitFor();
        assert.equal(await page.evaluate(() => JSON.parse(localStorage.getItem('fixture')).prefs.volume),40,'a conflicting immediate edit cannot overwrite the newer value');
        await page.locator('[data-action=reload-settings]').click();
        await page.waitForFunction(() => !document.querySelector('[data-action=reload-settings]'));
        assert.equal(await page.locator('[data-action=volume] strong').innerText(),lang === 'ar' ? '٤٠%' : '40%', 'reload reads the actual value');
        await page.locator('[data-action=section][data-value=appearance]').click();
        await page.evaluate(() => { const state=JSON.parse(localStorage.getItem('fixture')); state.prefs.homeLayout='compact'; localStorage.setItem('fixture',JSON.stringify(state)); });
        await page.locator('[data-action=carousel-step][data-value="theme:1"]').click();
        await page.locator('[data-action=reload-settings]').waitFor();
        assert.equal(await page.evaluate(() => JSON.parse(localStorage.getItem('fixture')).prefs.theme),'mihrab','a conflicting theme edit preserves the current appearance group');
        await page.locator('[data-action=reload-settings]').click(); await page.locator('[data-action=reload-settings]').waitFor({state:'detached'});
        assert.equal(await page.evaluate(() => JSON.parse(localStorage.getItem('fixture')).prefs.homeLayout),'compact');
        await page.locator('[data-action=section][data-value=display]').click();await page.locator('[data-action=uiScale]').focus();
        await page.evaluate(()=>{const state=JSON.parse(localStorage.getItem('fixture'));state.prefs.uiScale=130;localStorage.setItem('fixture',JSON.stringify(state));window.publishCompanion({current:{prefs:state.prefs,startup:false}});});
        await page.waitForFunction(()=>document.querySelector('[data-action=uiScale] strong').textContent.match(/130|١٣٠/));
        assert.equal(await page.locator(':focus').getAttribute('data-action'),'uiScale','a phone save preserves TV browsing focus without an abandoned draft');
        await page.evaluate(()=>window.publishCompanion({currentUnavailable:true}));await page.locator('[data-action=reload-settings]').waitFor();
        assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('fixture')).prefs.uiScale),130,'notification failure cannot alter the saved value');
        await page.locator('[data-action=reload-settings]').click();await page.locator('[data-action=reload-settings]').waitFor({state:'detached'});
      }
      await page.evaluate(original => { if(original===null)localStorage.removeItem('fixture'); else localStorage.setItem('fixture',original); }, original);
      await page.reload(); await inputRestored();
    }
    await preservingSettings();
    if (process.argv.includes('--alignment-only')) {
      for (const lang of ['en','ar']) {
        await page.evaluate(lang=>localStorage.setItem('fixture',JSON.stringify({prefs:window.Miqat.prefs({lang}),input:'HDMI_1'})),lang);
        await page.reload();await inputRestored();await page.keyboard.press('Enter');await page.locator('[data-action=home]').click();
        assert.equal(await page.locator('.arch [data-hijri]:visible').count(),1,'Hijri date belongs to the full prayer card');
        assert.ok((await page.locator('.arch [data-hijri]').innerText()).length>5,'Hijri date is populated');
        assert.equal(await page.locator('.verse-ref').count(),0,'Home verse has no extra caption');
        assert.equal(await page.locator('.home-prayers .prayer.next').count(),1,'one next prayer is outlined');
        assert.equal(await page.locator('.card-metadata .city-name').count(),1,'city and date share card metadata');
        if(capture)await page.screenshot({path:path.join(shots,'aligned-home-'+lang+'.png')});
        await page.locator('[data-action=settings]').click();await page.locator('[data-action=section][data-value=appearance]').click();
        assert.equal(JSON.parse(await page.locator('[data-ui="appearance.theme"]').getAttribute('data-ui-values'))[0],'mihrab');
        assert.equal(JSON.parse(await page.locator('[data-ui="appearance.background"]').getAttribute('data-ui-values'))[0],'miqat_signature');
        assert.equal(await page.locator('[data-action=apply-appearance],[data-action=cancel-appearance]').count(),0);
        assert.equal(await page.locator('[data-action=homeLayout] .segment').count(),3,'short choices are segmented');
        assert.equal(await page.locator('[data-action=prayerMode] .segment').count(),5);
        await page.locator('[data-action=prayerMode]').focus();await page.keyboard.press(lang==='ar'?'ArrowLeft':'ArrowRight');
        assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('fixture')).prefs.prayerMode),'current','segment saves immediately');
        await page.keyboard.press('Escape');
        assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('fixture')).prefs.prayerMode),'current','Back retains the value');
        if(capture)await page.screenshot({path:path.join(shots,'aligned-appearance-'+lang+'.png')});
      }
      assert.deepEqual(errors,[]);console.log('PASS: bilingual phone-aligned Home metadata, next-prayer border, Miqat-first filled carousels and immediate segmented settings. Browser/Luna mock.');return;
    }

    if (process.argv.includes('--preferences-only')) {
      assert.deepEqual(errors, []);
      console.log('PASS: bilingual LG settings merge, unrelated/outer-state preservation, inline and appearance conflicts, retained drafts and explicit reload. Luna and media are mocked.'); return;
    }
    async function pairingControls(collect=async()=>{}) {
      await page.locator('[data-action=section][data-value=device]').click();
      await page.locator('[data-action=companion]').click(); await page.waitForSelector('[data-action=pairing-open]:enabled');
      assert.equal(await page.locator('[data-action=pairing-back]').evaluate(el=>el===document.activeElement),true,'pairing opens at Back');
      await page.keyboard.press('ArrowDown');
      assert.equal(await page.locator('[data-action=pairing-open]').evaluate(el=>el===document.activeElement),true,'Down from pairing Back enters the controls');
      await page.keyboard.press('ArrowUp');
      assert.equal(await page.locator('[data-action=pairing-back]').evaluate(el=>el===document.activeElement),true,'Up from the first pairing control returns to Back');
      await collect();
      await page.locator('[data-action=pairing-open]').click(); await page.waitForSelector('.pairing-symbol > svg');
      assert.deepEqual(await page.locator('.pairing-symbol > svg').evaluate(el=>[el.getAttribute('width'),el.getAttribute('height'),!!el.getAttribute('viewBox')]),['400','400',true],'old WebKit gets explicit compact QR dimensions');
      assert.equal(await page.locator('.pairing-symbol > svg > g[transform]').count(),3,'three original styled finder eyes');
      assert.equal(await page.locator('.pairing-symbol .qr-mark').getAttribute('data-clearance'),'0.35','approved half-gap clearance');
      assert.match(await page.locator('.pairing-symbol .qr-mark image').getAttribute('xlink:href'),/^data:image\/png;base64,/,'local alpha mark ships in the real pairing DOM');
      assert.ok(await page.locator('.pairing-symbol > svg path').count()>300,'actual data modules use canonical mihrab paths');
      const assertQrVisible=async()=>assert.ok(await page.locator('.pairing-symbol').evaluate(el=>{const qr=el.getBoundingClientRect(),body=el.closest('.pairing-body').getBoundingClientRect();return qr.left>=body.left&&qr.right<=body.right&&qr.top>=body.top&&qr.bottom<=body.bottom;}),'complete QR and quiet zone stay visible at the chosen UI size');
      await assertQrVisible();
      assert.equal(await page.locator('.pairing-qr').getAttribute('role'),'img');await collect();
      await page.locator('[data-action=pairing-cancel]').click();await page.locator('.pairing-qr').waitFor({state:'detached'});
      await page.evaluate(()=>{window.qrMakes=0;window.originalQrFactory=window.originalQrFactory||window.qrcode;window.qrcode=(...args)=>{window.qrMakes++;return window.originalQrFactory(...args);};});
      await page.locator('[data-action=pairing-open]').click();await page.locator('.pairing-qr').waitFor();
      assert.equal(await page.evaluate(()=>window.qrMakes),1,'new window generates its QR once');
      await page.evaluate(()=>{window.pairingQrNode=document.querySelector('.pairing-qr');});
      await page.evaluate(()=>window.companionRequest('هاتف <b>المنزل</b> & "Phone"'));
      await page.waitForSelector('[data-action=pairing-reject]');assert.equal(await page.locator(':focus').getAttribute('data-action'),'pairing-reject','new requests default to Reject');
      assert.ok(await page.locator('[data-action=pairing-reject]').evaluate(el=>{const box=el.getBoundingClientRect(),frame=el.closest('.pairing-body').getBoundingClientRect();const scale=window.Miqat.prefs(JSON.parse(localStorage.getItem('fixture')).prefs).uiScale/100;return box.left>=frame.left+10*scale&&box.right<=frame.right-10*scale;}),'focused pairing controls reserve the complete shared focus ring');
      assert.equal(await page.evaluate(()=>window.qrMakes),1,'phone request does not regenerate an unchanged QR while approval waits');
      assert.equal(await page.evaluate(()=>document.querySelector('.pairing-qr')===window.pairingQrNode),true,'pending phone preserves the actual QR DOM and decoded artwork');
      await page.keyboard.press('ArrowDown');
      assert.equal(await page.locator('[data-action=pairing-approve]').evaluate(el=>el===document.activeElement),true,'Down advances from Reject to Approve');
      await page.keyboard.press('ArrowUp');
      assert.equal(await page.locator('[data-action=pairing-reject]').evaluate(el=>el===document.activeElement),true,'Up returns to Reject without approving');
      assert.equal(await page.locator('.pairing-code').innerText(),'123456');assert.equal(await page.locator('.pairing-controls h2 b').count(),0,'phone names are escaped');await collect();
      await assertQrVisible();
      if(process.argv.includes('--pairing-only')) {
        const label=await page.evaluate(()=>document.documentElement.lang+'-'+window.Miqat.prefs(JSON.parse(localStorage.getItem('fixture')).prefs).uiScale);
        if(capture)await page.screenshot({path:path.join(shots,'companion-browser-'+label+'.png')});
        if(label==='en-100') {
          if(capture)await page.locator('.pairing-qr').screenshot({path:path.join(shots,'companion-browser-qr.png')});
          if(capture)fs.writeFileSync(path.join(shots,'companion-browser-qr.json'),require('../../companion/qr').encode(qrFixture));
        }
      }
      await page.locator('[data-action=pairing-reject]').click();await page.locator('.pairing-code').waitFor({state:'detached'});
      await page.locator('[data-action=pairing-open]').click();await page.locator('.pairing-qr').waitFor();await page.evaluate(()=>window.companionRequest());
      await page.evaluate(()=>{window.pairingQrNode=document.querySelector('.pairing-qr');});
      await page.locator('[data-action=pairing-approve]').click();await page.locator('[data-action=pairing-approve]').waitFor({state:'detached'});
      assert.equal(await page.evaluate(()=>document.querySelector('.pairing-qr')===window.pairingQrNode),true,'approval preserves the actual QR DOM');
      assert.equal(await page.locator(':focus').getAttribute('data-action'),'pairing-cancel');assert.equal(await page.locator('[data-action=pairing-revoke]').count(),0,'approval waits for completion');
      await page.evaluate(()=>window.companionComplete());await page.locator('[data-action=pairing-revoke]').waitFor();await collect();
      await page.evaluate(()=>window.failCompanion=true);await page.locator('[data-action=pairing-revoke]').click();await page.waitForFunction(()=>document.querySelector('.pairing-controls [role=status]'));
      assert.equal(await page.locator('[data-action=pairing-revoke]').count(),1,'failed revocation keeps the phone');
      await page.evaluate(()=>window.failCompanion=false);await page.locator('[data-action=pairing-revoke]').click();await page.locator('[data-action=pairing-revoke]').waitFor({state:'detached'});
      await page.locator('[data-action=pairing-open]').click();await page.locator('.pairing-qr').waitFor();await page.keyboard.press('Escape');
      await page.locator('.settings-page').waitFor();assert.equal(await page.locator(':focus').getAttribute('data-action'),'companion');
      await page.waitForFunction(()=>!window.companionState.open,'leaving the pairing page cancels its window');
      await page.locator('[data-action=companion]').click();await page.locator('[data-action=pairing-open]').click();await page.locator('.pairing-qr').waitFor();
      await page.evaluate(()=>{Object.defineProperty(document,'hidden',{configurable:true,value:true});document.dispatchEvent(new Event('visibilitychange'));});
      await page.waitForFunction(()=>!window.companionBridge);assert.equal(await page.locator('.pairing-qr').count(),0,'backgrounding clears the QR immediately');
      assert.ok(await page.evaluate(()=>window.companionCalls.some(c=>c.method==='companionLeave')&&window.companionCalls.some(c=>c.action==='bridge-cancel')));
      await page.evaluate(()=>{Object.defineProperty(document,'hidden',{configurable:true,value:false});document.dispatchEvent(new Event('visibilitychange'));});
      await page.locator('[data-action=pairing-open]:enabled').waitFor();
      await page.evaluate(()=>{window.companionState.supported=false;window.publishCompanion();});await page.locator('[data-action=pairing-open]:disabled').waitFor();
      assert.equal(await page.locator('.pairing-qr').count(),0);assert.ok(await page.locator('.pairing-controls').innerText());
      await page.evaluate(()=>{window.companionState.supported=true;window.publishCompanion();});await page.locator('[data-action=pairing-open]:enabled').waitFor();
      await page.locator('[data-action=pairing-back]').click();
    }
    if(process.argv.includes('--remote-only')) {
      for(const lang of ['ar','en']) {
        await page.evaluate(lang=>localStorage.setItem('fixture',JSON.stringify({prefs:window.Miqat.prefs({lang}),input:'HDMI_1',startup:{enabled:false}})),lang);
        await page.reload();await inputRestored();
        let serial=0;
        const command=async(id,value='')=>{
          const request='R'.repeat(42)+(++serial).toString(36);
          await page.evaluate(command=>window.publishCompanion({command}),{request,id,value});
          await page.waitForFunction(request=>window.companionCalls.some(call=>call.method==='companionResult'&&call.request===request),request);
          return page.evaluate(request=>window.companionCalls.find(call=>call.method==='companionResult'&&call.request===request).status,request);
        };
        assert.equal(await command('device.home'),'accepted');await page.locator('.home').waitFor();
        assert.equal(await command('device.inputs'),'accepted');await page.locator('[data-action=select-input]').first().waitFor();
        assert.equal(await command('device.inputs','HDMI_2'),'accepted');await inputRestored();
        assert.equal(await command('device.inputs','arbitrary-input'),'unavailable');assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('fixture')).input),'HDMI_2');
        assert.equal(await command('radio.play'),'accepted');await page.waitForFunction(()=>JSON.parse(localStorage.getItem('fixture')).media?.event==='playing');
        assert.equal(await command('device.home'),'busy','navigation does not discard current media/editor ownership');
        assert.equal(await command('radio.stop'),'accepted');await page.waitForFunction(()=>JSON.parse(localStorage.getItem('fixture')).media?.event==='stopped');
        assert.equal(await command('tests.adhan-short'),'accepted');await page.locator('.adhan-overlay').waitFor();
        assert.equal(await command('tests.cancel'),'accepted');await page.waitForFunction(()=>!document.querySelector('.adhan-overlay'));
        assert.equal(await command('device.home'),'accepted');await page.locator('.home').waitFor();
        const oldPrefs=await page.evaluate(()=>JSON.parse(localStorage.getItem('fixture')).prefs);
        await page.locator('[data-action=settings]').click();await page.locator('[data-action=section][data-value=appearance]').click();
        assert.equal(await command('device.home'),'accepted','browsing immediate settings holds no unsaved draft');assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('fixture')).prefs),oldPrefs);
        console.log('PASS: authenticated-command frontend dispatch '+lang+' Home/input validation/radio/adhan preview/Stop and local draft/media guards; native boundaries mocked');
      }
      assert.deepEqual(errors,[]);return;
    }
    if(process.argv.includes('--pairing-only')) {
      for(const lang of ['ar','en'])for(const uiScale of [80,100,150]) {
        await page.evaluate(({lang,uiScale})=>localStorage.setItem('fixture',JSON.stringify({prefs:window.Miqat.prefs({lang,uiScale}),input:'HDMI_1'})),{lang,uiScale});
        await page.reload();await inputRestored();await page.keyboard.press('Enter');await page.locator('[data-action=home]').click();await page.locator('[data-action=settings]').click();
        await pairingControls();
      }
      for(const theme of ['mihrab','mosaic','brass','rose']) {
        await page.evaluate(theme=>localStorage.setItem('fixture',JSON.stringify({prefs:window.Miqat.prefs({lang:'en',uiScale:100,theme}),input:'HDMI_1'})),theme);
        await page.reload();await inputRestored();await page.keyboard.press('Enter');
        await page.locator('[data-action=home]').click();await page.locator('[data-action=settings]').click();
        await page.locator('[data-action=section][data-value=device]').click();await page.locator('[data-action=companion]').click();
        await page.waitForSelector('[data-action=pairing-open]:enabled');await page.locator('[data-action=pairing-open]').click();await page.waitForSelector('.pairing-symbol > svg');
        const binding=await page.locator('.pairing-symbol > svg').evaluate((el,theme)=>{
          const d=window.MiqatData,expected=window.mihrabQr.palette(d.palettes[theme],d.qrStyle,d.palettes.mihrab['plaster-100']);
          return {paper:el.querySelector(':scope > rect').getAttribute('fill'),expected:expected.paper,rim:el.querySelector('#miqat-qr-rim stop').getAttribute('stop-color'),expectedRim:expected.rim[0],logo:el.querySelector('#miqat-qr-logo-tint feFlood').getAttribute('flood-color'),expectedLogo:d.palettes[theme]['gold-400']};
        },theme);
        assert.equal(binding.logo,binding.expectedLogo,'centre mark follows '+theme);assert.equal(binding.paper,binding.expected,'pairing paper follows '+theme);assert.equal(binding.rim,binding.expectedRim,'finder colour follows '+theme);
        if(capture)await page.screenshot({path:path.join(shots,'companion-approved-'+theme+'-en.png')});
      }
      assert.deepEqual(errors,[]);console.log('PASS: LG bilingual pairing QR, default Reject, approve/complete/revoke/failure, Back/background cleanup and unsupported state at 80/100/150%; actual theme binding in Mihrab/Mosaic/Brass/Rose. Luna/media are mocked.');return;
    }
    await page.evaluate(() => { window.originalVideo = document.getElementById('input-video'); });
    // Magic Remote OK becomes a click while its pointer is visible.
    await page.mouse.click(960, 540);
    await page.waitForSelector('.bar', {timeout: 1500});
    await page.keyboard.press('Escape');
    assert.equal(await page.locator('button').count(), 0);
    await page.keyboard.press('Enter');
    await page.waitForSelector('.bar');
    const barBounds = await page.locator('.bar').boundingBox();
    assert.equal(barBounds.y, 0, 'prayer bar stays at top like the Android dock');
    assert.ok(barBounds.height < 140, 'dock stays compact over HDMI');
    assert.equal(await page.locator(':focus').getAttribute('data-action'), 'home');
    await page.keyboard.press('ArrowLeft');
    assert.equal(await page.locator(':focus').getAttribute('data-action'), 'inputs');
    await page.keyboard.press('ArrowRight');
    assert.equal(await page.locator(':focus').getAttribute('data-action'), 'home');
    await page.keyboard.press('Enter');
    await page.waitForSelector('.arch');
    assert.equal(await page.locator('html').getAttribute('dir'), 'rtl');
    assert.match(await page.locator('[data-countdown]').innerText(), /[٠-٩]/);
    assert.equal(await page.locator('.prayer-name').getAttribute('dir'), 'rtl');
    assert.match(await page.locator('.prayer-name').evaluate(el => getComputedStyle(el).fontFamily), /Alexandria/);
    assert.doesNotMatch(await page.locator('body').innerText(), /Miqat|ميقات/);
    async function homeControls() {
      assert.equal(await page.locator('.verse').innerHTML(), sacred.prayerVerse.join('<br>'), 'verse DOM keeps exact Arabic text and line breaks even when the layout hides it');
      assert.deepEqual(await page.locator('.home-quick button').evaluateAll(items => items.map(el => el.dataset.action)), ['prayer-day', 'inputs', 'radio', 'tv-settings', 'settings']);
      assert.equal(await page.locator('.home-apps [data-action=native-app]').count(), 3, 'large Home tiles belong to TV apps');
      const clock = await page.locator('.home-clock .clock').boundingBox();
      for (const control of await page.locator('.home-quick button').all()) {
        const b = await control.boundingBox();
        assert.ok(b.y < 150 && b.width <= 72 && b.height <= 72, 'quick controls are compact top icons');
        assert.ok(b.x >= clock.x + clock.width || b.x + b.width <= clock.x, 'top icons do not cover the clock');
        assert.ok(await control.getAttribute('aria-label'), 'icon has an accessible name');
      }
      await homeNavigation();
    }
    async function homeNavigation() {
      const apps = page.locator('.home-apps button');
      await page.evaluate(() => {
        const api = window['tv.miqat:core'].tv.miqat.core.HomeNavigation;
        const hook = window.homeContractHook = {api, original: api.move, calls: 0};
        api.move = function (...args) { hook.calls++; return hook.original.apply(api, args); };
      });
      for (let i = 0; i < await apps.count(); i++) {
        await apps.nth(i).focus();
        await page.keyboard.press('ArrowDown');
        assert.equal(await page.locator(':focus').getAttribute('data-action'), 'apps', 'Down from the last app row selects All apps');
        await page.keyboard.press('ArrowUp');
        assert.equal(await page.locator(':focus').getAttribute('data-value'), await apps.nth(i).getAttribute('data-value'), 'Up restores the last Home app');
        await page.keyboard.press('ArrowUp');
        assert.equal(await page.locator(':focus').getAttribute('data-action'), ['radio', 'tv-settings', 'settings'][i], 'Up enters the Android top-controls map');
      }
      const calls = await page.evaluate(() => {
        const hook = window.homeContractHook;
        hook.api.move = hook.original; delete window.homeContractHook;
        return hook.calls;
      });
      assert.equal(calls, await apps.count() * 3, 'every Home arrow uses the shared contract exactly once');
      await apps.first().focus();
      await page.keyboard.press('ArrowDown');
      await page.keyboard.press('Enter');
      await page.waitForSelector('.app-grid');
      await appsNavigation();
      await page.keyboard.press('Escape');
      await page.waitForSelector('.home');
      assert.equal(await page.locator(':focus').getAttribute('data-action'), 'apps', 'Back from All apps restores its Home focus');
      await apps.first().focus();
    }
    async function appsNavigation() {
      const tiles = page.locator('.app-grid .app-tile');
      const rtl = await page.locator('html').getAttribute('dir') === 'rtl';
      const end = rtl ? 'ArrowLeft' : 'ArrowRight', start = rtl ? 'ArrowRight' : 'ArrowLeft';
      await page.evaluate(() => {
        const api = window['tv.miqat:core'].tv.miqat.core.AllAppsNavigation;
        const hook = window.appsContractHook = {api, original: api.move, calls: 0};
        api.move = function (...args) { hook.calls++; return hook.original.apply(api, args); };
      });
      for (let i = 0; i < Math.min(5, await tiles.count()); i++) {
        await tiles.nth(i).focus();
        await page.keyboard.press('ArrowUp');
        assert.equal(await page.locator(':focus').getAttribute('data-action'), 'apps-back', 'Up from every first-row column selects Back');
      }
      for (const [key, actions] of [[end, ['prayer-day', 'settings', 'apps-back']], [start, ['settings', 'prayer-day', 'apps-back']]]) {
        for (const action of actions) {
          await page.keyboard.press(key);
          assert.equal(await page.locator(':focus').getAttribute('data-action'), action, 'horizontal movement stays in the circular Apps header');
        }
      }
      for (const [action, index] of [['apps-back', 0], ['prayer-day', 4], ['settings', 4]]) {
        await page.locator(`[data-action=${action}]`).focus();
        await page.keyboard.press('ArrowDown');
        assert.ok(await tiles.nth(index).evaluate(el => el === document.activeElement), 'header Down returns to the grid');
      }
      await tiles.nth(5).focus();
      await page.keyboard.press('ArrowUp');
      assert.ok(await tiles.first().evaluate(el => el === document.activeElement), 'Up from a lower row stays in app content');
      await tiles.nth(14).focus();
      await page.keyboard.press('ArrowDown');
      assert.ok(await tiles.nth(15).evaluate(el => el === document.activeElement), 'Down clamps to the partial final row');
      await page.keyboard.press('ArrowDown');
      assert.ok(await tiles.nth(15).evaluate(el => el === document.activeElement), 'Down stays at the final row');
      await page.keyboard.press(start);
      assert.ok(await tiles.nth(15).evaluate(el => el === document.activeElement), 'horizontal grid movement stays within its row');
      const calls = await page.evaluate(() => {
        const hook = window.appsContractHook;
        hook.api.move = hook.original; delete window.appsContractHook;
        return hook.calls;
      });
      assert.equal(calls, Math.min(5, await tiles.count()) + 13, 'every All apps arrow uses the shared contract exactly once');
    }
    assert.equal(await page.locator(':focus').getAttribute('data-action'), 'native-app', 'Home starts in app content');
    await homeControls();
    await page.locator('[data-action=prayer-day]').focus();
    await page.keyboard.press('ArrowLeft');
    assert.equal(await page.locator(':focus').getAttribute('data-action'), 'inputs', 'RTL remote moves along the small top row');
    await page.keyboard.press('ArrowDown');
    assert.equal(await page.locator(':focus').getAttribute('data-action'), 'native-app', 'Down reaches app tiles');
    if(capture)await page.screenshot({path: path.join(shots, 'home-ar.png')});
    assert.equal(await page.locator('.arch-shape path').first().getAttribute('d'), JSON.parse(fs.readFileSync(path.join(__dirname, '../../shared/design/mihrab.json'))).outer, 'Home uses the canonical Miqat crown and shoulders');
    const arCount = await page.locator('.prayer').count(); assert.equal(arCount, 6);
    await page.locator('[data-action=settings]').click();
    async function settingsCoverage() {
      const seen = new Set();
      const offered = {};
      async function collect(section) {
        const bound = await page.locator('.settings-rows button:not([data-action=carousel-step])').evaluateAll(items => items.map(el => ({
          id: el.getAttribute('data-ui'), kind: el.getAttribute('data-ui-kind'), label: el.firstChild.textContent,
          segments: !!el.querySelector('.segments'), role: el.getAttribute('role'), checked: el.getAttribute('aria-checked'),
          action: el.getAttribute('data-action'), values: JSON.parse(el.getAttribute('data-ui-values'))
        })));
        assert.ok(bound.length);
        assert.equal(new Set(bound.map(item => item.id)).size, bound.length, 'no duplicate control in an actual section');
        for (const item of bound) {
          assert.ok(item.id, 'every actual settings row has a contract ID: ' + item.action);
          const expected = await page.evaluate(({id, kind, section}) => {
            const control = window['tv.miqat:core'].tv.miqat.core.UiContract.bind(id, kind);
            if (control.section !== section) throw new Error('Wrong control section: ' + id);
            const [key, index] = control.labelKey.split(':');
            const label = window.MiqatData.strings[key][document.documentElement.lang];
            return index === undefined ? label : label[Number(index)];
          }, {...item, section});
          assert.equal(item.label, expected, 'actual row uses its canonical bilingual label');
          seen.add(item.id);
          if (item.kind === 'choice') {
            const editor = await page.evaluate(id => window['tv.miqat:core'].tv.miqat.core.UiContract.choiceEditor(id), item.id);
            assert.equal(item.segments, editor === 'segments', 'actual choice widget follows the shared editor: ' + item.id);
            if (item.id === 'display.digits') {
              const labels = await page.locator('[data-ui="display.digits"] .segment').allTextContents();
              assert.ok(labels.includes('0123') && labels.includes('٠١٢٣'), 'digit samples remain distinct in both languages');
            }
            assert.ok(Array.isArray(item.values), 'actual choice binding exposes values');
            offered[item.id] ||= new Set();
            item.values.forEach(value => offered[item.id].add(value));
          }
          if (item.kind === 'toggle') {
            assert.equal(item.role, 'switch', 'actual toggle exposes a switch, not a text status');
            assert.ok(['true', 'false'].includes(item.checked), 'switch exposes its saved state');
          }
        }
      }
      for (const section of ['prayer', 'reminders', 'radio', 'display', 'appearance', 'device', 'tests']) {
        await page.locator('[data-action=section][data-value=' + section + ']').click();
        await collect(section);
      }
      await pairingControls(async()=>{
        const controls=await page.locator('[data-ui^="pairing."]').evaluateAll(items=>items.map(el=>({id:el.dataset.ui,kind:el.dataset.uiKind,text:el.textContent})));
        for(const item of controls){
          const expected=await page.evaluate(({id,kind})=>{const c=window['tv.miqat:core'].tv.miqat.core.UiContract.bind(id,kind);return window.MiqatData.strings[c.labelKey][document.documentElement.lang];},item);
          assert.ok(item.text.includes(expected),'actual pairing control uses canonical bilingual label');seen.add(item.id);
        }
      });
      await page.locator('[data-action=section][data-value=appearance]').click();
      const countdown = page.locator('[data-action=homeLayout]');
      const rtl = await page.locator('html').getAttribute('dir') === 'rtl';
      await countdown.focus();
      await page.keyboard.press(rtl ? 'ArrowRight' : 'ArrowLeft');
      await page.keyboard.press(rtl ? 'ArrowRight' : 'ArrowLeft');
      for (let mode = 0; mode < 3; mode++) {
        await collect('appearance');
        if (mode < 2) await page.keyboard.press(rtl ? 'ArrowLeft' : 'ArrowRight');
      }
      await page.locator('[data-action=reset-layout]').click();
      await page.locator('[data-action=section][data-value=prayer]').click();
      await page.locator('[data-action=offsets]').click();
      await collect('offsets');
      await page.locator('[data-action=section][data-value=prayer]').click();
      await page.locator('[data-action=prayer-day]').click();
      const prayerBindings = await page.locator('[data-action=bell]').evaluateAll(items => items.map(el => ({id: el.getAttribute('data-ui'), kind: el.getAttribute('data-ui-kind')})));
      assert.equal(prayerBindings.length, 5);
      assert.equal(new Set(prayerBindings.map(c => c.id)).size, 5);
      for (const control of prayerBindings) { assert.equal(control.kind, 'toggle'); seen.add(control.id); }
      const bellsBefore = await page.evaluate(() => window.Miqat.prefs(JSON.parse(localStorage.getItem('fixture')).prefs).bells);
      for (const index of [0, 2, 3, 4, 5]) {
        const bell = page.locator('[data-action=bell][data-value="' + index + '"]');
        for (const value of [!bellsBefore[index], bellsBefore[index]]) {
          const previous = await bell.elementHandle();
          await bell.click();
          // The mock persists before replying; wait for the real save callback to redraw.
          await page.waitForFunction(el => !el.isConnected, previous);
          await previous.dispose();
          assert.equal(await page.evaluate(index => JSON.parse(localStorage.getItem('fixture')).prefs.bells[index], index), value);
        }
      }
      assert.deepEqual(await page.evaluate(() => JSON.parse(localStorage.getItem('fixture')).prefs.bells), bellsBefore);
      await page.keyboard.press('Escape');
      await page.waitForSelector('.settings-page');
      assert.equal(await page.locator(':focus').getAttribute('data-ui'), 'prayer.today', 'Back restores the originating setting');
      await page.locator('[data-action=section][data-value=tests]').click();
      await page.locator('[data-action=test-adhan]').click();
      const quietInput = page.locator('[data-action=quiet-input]');
      await quietInput.waitFor();
      assert.equal(await quietInput.getAttribute('data-ui-kind'), 'action');
      seen.add(await quietInput.getAttribute('data-ui'));
      await page.keyboard.press('Escape');
      await inputRestored();
      await page.evaluate(({bound, gaps}) => window['tv.miqat:core'].tv.miqat.core.UiContract.validateCoverage(bound, gaps),
        {bound: [...seen], gaps: uiGaps.map(gap => gap.id)});
      await page.evaluate(({offered, gaps}) => {
        const core = window['tv.miqat:core'].tv.miqat.core.UiContract;
        for (const [id, values] of Object.entries(offered)) core.validateChoiceCoverage(id, values, gaps.find(g => g.id === id)?.values || []);
      }, {offered: Object.fromEntries(Object.entries(offered).map(([id, values]) => [id, [...values]])), gaps: uiChoiceGaps});
      const rejected = await page.evaluate(({bound, gaps}) => {
        try { window['tv.miqat:core'].tv.miqat.core.UiContract.validateCoverage(bound.slice(1), gaps); return false; }
        catch (error) { return /Missing UI control/.test(error.message); }
      }, {bound: [...seen], gaps: uiGaps.map(gap => gap.id)});
      assert.ok(rejected, 'losing a real binding fails coverage');
      await page.locator('[data-action=section][data-value=prayer]').click();
    }
    async function settingsValuesFit() {
      for (const [section, actions] of [['reminders', ['remind', 'voice']], ['tests', ['testPrayer', 'test-banner', 'test-card']]]) {
        await page.locator('[data-action=section][data-value=' + section + ']').click();
        for (const action of actions) {
          const bounds = await page.locator('[data-action=' + action + '] .value').evaluate(el => {
            const range = document.createRange();
            range.selectNodeContents(el);
            const text = range.getBoundingClientRect();
            let fits = true;
            // Old WebKit paints Arabic outside normal font metrics; check every clipping ancestor.
            for (let parent = el; parent; parent = parent.parentElement) {
              const style = getComputedStyle(parent), box = parent.getBoundingClientRect();
              if (style.overflowX !== 'visible') fits &&= text.left >= box.left + 1 && text.right <= box.right - 1;
              if (style.overflowY !== 'visible') fits &&= text.top >= box.top + 1 && text.bottom <= box.bottom - 1;
            }
            return {label: el.textContent, fits};
          });
          assert.ok(bounds.fits, 'settings value glyphs have paint clearance inside their clipping box: ' + bounds.label);
        }
        const wraps = await page.locator('[data-action=' + actions[0] + '] .value').evaluate(el => {
          const original = el.innerHTML;
          el.textContent = (el.textContent + ' ').repeat(24);
          const value = el.getBoundingClientRect(), button = el.parentElement.getBoundingClientRect();
          const result = {wrapped: value.height > parseFloat(getComputedStyle(el).fontSize) * 2,
            contained: value.left >= button.left && value.right <= button.right && value.top >= button.top && value.bottom <= button.bottom};
          el.innerHTML = original;
          return result;
        });
        assert.ok(wraps.wrapped && wraps.contained, 'long settings values wrap inside their row');
      }
    }
    async function onlineChecks() {
      await page.locator('[data-action=section][data-value=prayer]').click();
      const before = await page.evaluate(() => ({prefs: JSON.parse(localStorage.getItem('fixture')).prefs,
        source: document.getElementById('input-video').innerHTML, media: window.mediaCalls.length}));
      await page.locator('[data-action=online-prayer]').click();
      await page.waitForSelector('.online-check tbody tr');
      assert.equal(await page.locator('.online-check tbody tr').count(), 6);
      assert.equal(await page.locator(':focus').getAttribute('data-action'), 'online-back');
      const back = await page.locator('[data-action=online-back]').boundingBox(), brand = await page.locator('.online-check .brand').boundingBox();
      assert.ok(back.x + back.width < brand.x || brand.x + brand.width < back.x, 'Back and the brand have separate header space');
      for (const cell of await page.locator('.online-check th, .online-check td, .online-note, [data-action=online-retry]').all()) {
        const box = await cell.boundingBox();
        assert.ok(box.x >= 0 && box.y >= 0 && box.x + box.width <= 1920 && box.y + box.height <= 1080, 'online comparison fits screen');
      }
      const lang = await page.locator('html').getAttribute('lang');
      if(capture)await page.screenshot({path: path.join(shots, 'online-' + lang + '.png')});
      assert.deepEqual(await page.evaluate(() => ({prefs: JSON.parse(localStorage.getItem('fixture')).prefs,
        source: document.getElementById('input-video').innerHTML, media: window.mediaCalls.length})), before, 'manual comparison preserves settings and HDMI');
      await page.keyboard.press('Escape');
      assert.equal(await page.locator(':focus').getAttribute('data-ui'), 'prayer.online');
      await page.evaluate(() => { window.failOnline = true; });
      await page.locator('[data-action=online-prayer]').click();
      await page.waitForSelector('[data-action=online-retry]');
      assert.equal(await page.locator('.online-check [role=status]').innerText(), await page.evaluate(() => window.MiqatData.strings.onlineUnavailable[document.documentElement.lang]));
      await page.evaluate(() => { window.failOnline = false; });
      await page.locator('[data-action=online-retry]').click(); await page.waitForSelector('.online-check tbody tr');
      await page.keyboard.press('Escape');
      await page.evaluate(() => { window.deferOnline = true; });
      await page.locator('[data-action=online-prayer]').click();
      await page.waitForFunction(() => window.onlineReplies?.length === 1);
      await page.keyboard.press('Escape');
      await page.evaluate(() => window.onlineReplies.shift()());
      assert.equal(await page.locator('.online-check').count(), 0, 'late result cannot reopen the comparison');
      assert.equal(await page.locator(':focus').getAttribute('data-ui'), 'prayer.online');
      assert.equal(await page.evaluate(() => window.onlineCalls.at(-1).action), 'cancel');
      await page.locator('[data-action=online-prayer]').click();
      await page.waitForFunction(() => window.onlineReplies?.length === 1);
      await page.evaluate(() => {
        const state = JSON.parse(localStorage.getItem('fixture'));
        state.pendingAlert = {id: 'online:banner:' + Date.now(), mode: 'banner', prayer: 0, test: true};
        localStorage.setItem('fixture', JSON.stringify(state));
        document.dispatchEvent(new CustomEvent('webOSRelaunch', {detail: {alertId: state.pendingAlert.id}}));
      });
      await page.waitForSelector('#overlay [data-action=dismiss-alert]');
      await page.evaluate(() => window.onlineReplies.shift()());
      assert.ok(await page.evaluate(() => document.getElementById('overlay').contains(document.activeElement)), 'late response preserves alert focus');
      await page.locator('#overlay [data-action=dismiss-alert]').click();
      await page.waitForSelector('.online-check [data-action=online-retry]');
      await page.keyboard.press('Escape');
      await page.evaluate(() => {
        const state = JSON.parse(localStorage.getItem('fixture')); delete state.pendingAlert;
        localStorage.setItem('fixture', JSON.stringify(state));
      });
      await page.locator('[data-action=online-prayer]').click();
      await page.waitForFunction(() => window.onlineReplies?.length === 1);
      await page.evaluate(() => {
        Object.defineProperty(document, 'hidden', {configurable: true, value: true}); document.dispatchEvent(new Event('visibilitychange'));
        Object.defineProperty(document, 'hidden', {configurable: true, value: false}); document.dispatchEvent(new Event('visibilitychange'));
        window.onlineReplies.shift()();
      });
      await page.waitForSelector('[data-action=online-retry]');
      assert.equal(await page.locator('.online-check tbody tr').count(), 0, 'suspend cancels and keeps a retryable view');
      await page.keyboard.press('Escape');
      await page.evaluate(() => { window.deferOnline = false; });
      assert.deepEqual(await page.evaluate(() => JSON.parse(localStorage.getItem('fixture')).prefs), before.prefs);
    }
    await settingsValuesFit();
    await settingsCoverage();
    await onlineChecks();
    await page.locator('[data-action=section][data-value=prayer]').click();
    if(capture)await page.screenshot({path: path.join(shots, 'settings-ar.png')});
    await page.keyboard.press('ArrowLeft');
    assert.equal(await page.locator(':focus').getAttribute('data-action'), 'city', 'RTL direction enters settings rows');
    await page.keyboard.press('Escape');
    assert.equal(await page.locator(':focus').getAttribute('data-action'), 'section', 'Back returns to sidebar');
    await page.locator('[data-action=section][data-value=reminders]').click();
    await page.locator('[data-action=automatic]').focus();
    for (let i = 0; i < 10; i++) await page.keyboard.press('ArrowDown');
    assert.equal(await page.locator(':focus').getAttribute('data-action'), 'tests', 'remote reaches the last scrolling reminder setting');
    const bottomSetting = await page.locator(':focus').boundingBox();
    assert.ok(bottomSetting.y >= 172 && bottomSetting.y + bottomSetting.height <= 972, 'focused row scrolls fully into view');
    if(capture)await page.screenshot({path: path.join(shots, 'reminders-ar.png')});
    await page.locator('[data-action=section][data-value=display]').click();
    async function selectInlineChoice(action, index) {
      const row = page.locator('[data-action=' + action + ']');
      await row.focus(); await page.keyboard.press('Enter');
      await row.locator('[data-choice-index="' + index + '"]').click();
      await page.keyboard.press('Enter');
      await page.locator('[data-action=' + action + ']:not(.editing)').waitFor();
    }
    await selectInlineChoice('language', 1);
    await page.waitForFunction(() => document.documentElement.dir === 'ltr');
    assert.equal(await page.locator('html').getAttribute('dir'), 'ltr');
    await settingsValuesFit();
    await settingsCoverage();
    await onlineChecks();
    await page.locator('[data-action=section][data-value=prayer]').click();
    await page.locator('[data-action=city]').click();
    await page.locator('[data-action=choose][data-value=Egypt]').click();
    await page.locator('[data-action=choose][data-value=giza]').click();
    await page.waitForSelector('[data-action=city]');
    assert.match(await page.locator('[data-action=city]').innerText(), /Giza/);
    await selectInlineChoice('hanafi', 1);
    assert.match(await page.locator('[data-action=hanafi] .segment.selected').innerText(), /Hanafi/);
    await page.locator('[data-action=section][data-value=device]').click();
    await page.locator('[data-action=startup]').click();
    await page.waitForFunction(() => document.querySelector('[data-action=startup]').getAttribute('aria-checked') === 'true');
    assert.equal(await page.locator('[data-action=startup]').getAttribute('role'), 'switch');
    if(capture)await page.screenshot({path: path.join(shots, 'settings-en.png')});
    await page.keyboard.press('Escape');
    await page.keyboard.press('Escape');
    await page.waitForSelector('.arch');
    await homeControls();
    await page.locator('[data-action=prayer-day]').focus();
    await page.keyboard.press('ArrowRight');
    assert.equal(await page.locator(':focus').getAttribute('data-action'), 'inputs', 'LTR remote moves along the small top row');
    if(capture)await page.screenshot({path: path.join(shots, 'home-en.png')});
    await page.locator('[data-action=apps]').click();
    assert.equal(await page.locator('.app-grid button').count(), 16);
    if(capture)await page.screenshot({path: path.join(shots, 'apps-en.png')});
    assert.equal(await page.locator('[data-action=apps-back]').getAttribute('aria-label'), 'Back');
    await page.locator('[data-action=apps-back]').click();
    assert.equal(await page.locator(':focus').getAttribute('data-action'), 'apps', 'visible Back restores Home focus');
    await page.keyboard.press('Enter');
    await page.locator('.apps-tools [data-action=settings]').click();
    assert.equal(await page.locator('[data-action=settings-back]').getAttribute('aria-label'), 'Back');
    await page.locator('[data-action=settings-back]').click();
    assert.equal(await page.locator('.apps-page').count(), 1, 'Settings visible Back returns to All apps');
    await page.locator('.apps-tools [data-action=settings]').click();
    await page.keyboard.press('Escape');
    await page.waitForSelector('.apps-page');
    assert.equal(await page.locator(':focus').getAttribute('data-action'), 'settings', 'Settings opened from apps returns to apps');
    await page.locator('.apps-tools [data-action=prayer-day]').click();
    await page.keyboard.press('Escape');
    assert.equal(await page.locator(':focus').getAttribute('data-action'), 'prayer-day', 'prayer chip returns to apps');
    assert.equal(await page.locator('[data-value="com.webos.app.adapp"]').count(), 0);
    await page.locator('[data-value="netflix"]').focus();
    for (let i = 0; i < 3; i++) await page.keyboard.press('ArrowDown');
    assert.equal(await page.locator(':focus').getAttribute('data-value'), 'com.palmdts.devmode', 'remote reaches the last app row in the five-column grid');
    await page.locator('[data-value="com.webos.app.browser"]').click();
    assert.equal(await page.evaluate(() => JSON.parse(localStorage.getItem('fixture')).launchedApp), 'com.webos.app.browser');
    await page.evaluate(() => { window.failAppLaunch = true; });
    await page.locator('[data-value="com.webos.app.smartshare"]').click();
    await page.waitForSelector('#notice', {state: 'visible'});
    assert.match(await page.locator('#notice').innerText(), /Could not open/);
    assert.equal(await page.locator('.app-grid').count(), 1, 'failed launch leaves app list usable');
    await page.evaluate(() => { window.failAppLaunch = false; });
    if(capture)await page.screenshot({path: path.join(shots, 'apps-error-en.png')});
    await page.keyboard.press('Escape');
    await page.waitForSelector('.home');
    await page.locator('[data-action=inputs]').click();
    await page.locator('[data-value=HDMI_2]').click();
    await page.waitForSelector('#screen button', {state: 'detached'});
    assert.equal(await page.locator('button').count(), 0);
    assert.equal(await page.evaluate(() => window.originalVideo === document.getElementById('input-video')), true, 'internal navigation retains video element');
    await page.keyboard.press('Enter');
    assert.match(await page.locator('.bar').getAttribute('aria-label'), /HDMI 2/);
    if(capture)await page.screenshot({path: path.join(shots, 'bar-en.png')});
    const buttons = await page.locator('button').all();
    for (const button of buttons) {
      const bounds = await button.boundingBox();
      assert.ok(bounds.x >= 0 && bounds.y >= 0 && bounds.x + bounds.width <= 1920 && bounds.y + bounds.height <= 1080, 'button fits screen');
    }
    await page.keyboard.press('Escape');
    assert.equal(await page.locator('button').count(), 0, 'Back dismisses bar without closing app');
    await page.reload(); await inputRestored();
    await page.keyboard.press('Enter');
    assert.match(await page.locator('.bar').getAttribute('aria-label'), /HDMI 2.*Giza/);
    await page.locator('[data-action=input]').click();
    assert.equal(await page.locator('button').count(), 0, 'clicking Hide does not reopen the dock');
    await page.locator('#screen').click({position: {x: 960, y: 540}});
    await page.waitForSelector('.bar');
    await page.locator('[data-action=settings]').click();
    await page.locator('[data-action=section][data-value=prayer]').click();
    await page.locator('[data-action=offsets]').click();
    await page.locator('[data-action=offset-0]').click();
    await page.keyboard.press('ArrowRight');
    await page.keyboard.press('ArrowRight');
    await page.keyboard.press('Enter');
    await page.waitForSelector('[data-action=offset-0]');
    assert.match(await page.locator('[data-action=offset-0]').innerText(), /2 min/);
    await page.locator('[data-action=offset-0]').click();
    await page.keyboard.press('ArrowRight');
    await page.keyboard.press('Escape');
    assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('fixture')).prefs.offsets[0]),3,'Back preserves the direct stepper adjustment');
    const numericBefore = await page.evaluate(() => JSON.parse(localStorage.getItem('fixture')).prefs);
    const numberCases = [
      ['prayer', 'hijriOffset', -2, 2, 1, numericBefore.hijriOffset],
      ['reminders', 'volume', 0, 100, 5, numericBefore.volume],
      ...numericBefore.offsets.map((value, i) => ['offsets', 'offset-' + i, -30, 30, 1, value])
    ];
    async function numberArrows(code, times) {
      for (let i=0;i<times;i++) await page.keyboard.press(code===37?'ArrowLeft':'ArrowRight');
    }
    for (const [section, action, low, high, step, original] of numberCases) {
      await page.locator('[data-action=section][data-value=' + (section === 'offsets' ? 'prayer' : section) + ']').click();
      if (section === 'offsets') await page.locator('[data-action=offsets]').click();
      const row = page.locator('[data-action=' + action + ']');
      for (const [code, expected] of [[37, low], [39, high]]) {
        await row.click();
        await numberArrows(code, 100);
        assert.equal(await row.locator('.inline-step strong').innerText(), expected + (action === 'volume' ? '%' : action.startsWith('offset-') ? ' min' : ''), 'real editor clamps ' + action);
        await page.keyboard.press('Enter');
        await page.locator('[data-action=' + action + ']:not(.editing)').waitFor();
      }
      await row.click(); await numberArrows(37, 100); await page.keyboard.press('Escape');
      if(section==='offsets') await page.locator('[data-action=offsets]').click();
      await row.click();
      assert.equal(await row.locator('.inline-step strong').innerText(), low + (action === 'volume' ? '%' : action.startsWith('offset-') ? ' min' : ''), 'Back preserves the last immediate adjustment');
      assert.ok(Number.isInteger((high - original) / step));
      await numberArrows(39, (original - low) / step);
      await page.keyboard.press('Enter'); await page.locator('[data-action=' + action + ']:not(.editing)').waitFor();
    }
    assert.deepEqual(await page.evaluate(() => JSON.parse(localStorage.getItem('fixture')).prefs), numericBefore, 'all numeric save/cancel round trips restore original preferences');
    await page.locator('[data-action=section][data-value=prayer]').click();
    await page.locator('[data-action=offsets]').click();
    await page.keyboard.press('Escape');
    await page.locator('[data-action=prayer-day]').click();
    assert.equal(await page.locator('[data-action=bell]').count(), 5, 'no sunrise bell');
    await page.locator('[data-action=bell][data-value="0"]').click();
    await page.waitForFunction(() => document.querySelector('[data-action=bell][data-value="0"]').getAttribute('aria-checked') === 'false');
    if(capture)await page.screenshot({path: path.join(shots, 'prayer-day-en.png')});
    await page.locator('[data-action=tests]').click();
    await page.locator('[data-action=section][data-value=reminders]').click();
    await page.locator('[data-action=voice]').click();
    assert.equal(await page.locator('.voice-name').count(), voices.length, 'every available adhan voice is in one scrollable list');
    assert.equal(await page.locator('.voice-preview').count(), voices.length, 'each voice has a preview control');
    assert.equal(await page.locator('[data-value=alafasy]').count(), 0, 'retired recording is not selectable');
    await page.locator('[data-action=preview-voice][data-value=makkah]').click();
    await page.waitForFunction(() => document.querySelector('audio') && document.querySelector('audio').getAttribute('src'));
    assert.match(await page.locator('audio').getAttribute('src'), /makkah\.mp3$/);
    assert.equal(await page.locator('.voice-list').count(), 1, 'preview stays in the voice list');
    const voiceScroll = await page.locator('.voice-list').evaluate(el => el.scrollTop);
    assert.ok(voiceScroll > 0, 'last voice is reachable by scrolling');
    await page.evaluate(() => document.querySelector('audio').dispatchEvent(new Event('ended')));
    await inputRestored();
    assert.equal(await page.locator('.voice-list').evaluate(el => el.scrollTop), voiceScroll, 'preview completion preserves list position');
    if(capture)await page.screenshot({path: path.join(shots, 'voices-en.png')});
    await page.keyboard.press('Escape');
    await page.locator('[data-action=section][data-value=tests]').click();
    const loadsBefore = await page.evaluate(() => window.mediaCalls.filter(c => c.tag === 'VIDEO').length);
    await page.locator('[data-action=test-banner]').click();
    await page.waitForSelector('.banner-overlay .alert-box');
    assert.equal(await page.evaluate(() => window.mediaCalls.filter(c => c.tag === 'VIDEO').length), loadsBefore, 'silent banner never pauses, unloads or reloads HDMI');
    assert.equal(await page.locator('#input-video source').getAttribute('src'), 'ext://hdmi:2');
    if(capture)await page.screenshot({path: path.join(shots, 'banner-en.png')});
    await page.locator('[data-action=dismiss-alert]').click();
    assert.equal(await page.locator('.bar').count(), 0, 'pointer dismiss must not open the bar underneath');
    assert.equal(await page.locator(':focus').getAttribute('data-action'), 'test-banner', 'banner returns to the test that opened it');
    async function tests() {
      if (!(await page.locator('.settings-page').count())) {
        if (!(await page.locator('[data-action=settings]').count())) { await page.keyboard.press('Enter'); }
        await page.locator('[data-action=settings]').click();
      }
      await page.locator('[data-action=section][data-value=tests]').click();
    }
    await tests();
    await page.locator('[data-action=test-card]').click();
    assert.equal(await page.evaluate(() => window.mediaCalls.filter(c => c.tag === 'VIDEO').length), loadsBefore, 'silent card preserves HDMI');
    await page.keyboard.press('Escape');
    await tests();
    await page.locator('[data-action=test-adhan]').click();
    await page.waitForFunction(() => document.querySelector('audio') && document.querySelector('audio').getAttribute('src'));
    assert.match(await page.locator('audio').getAttribute('src'), /chebae_fajr\.mp3$/);
    assert.equal(await page.locator('#input-video source').count(), 1, 'adhan retains HDMI while audio plays');
    assert.equal(await page.locator('#input-video source').getAttribute('src'), 'ext://hdmi:2', 'adhan retains the selected HDMI picture');
    assert.deepEqual(await page.locator('#input-video').evaluate(el => [el.volume, el.muted]), [0.15, false], 'adhan lowers HDMI before starting');
    assert.equal(await page.locator('[data-action=quiet-input]').innerText(), 'Mute HDMI');
    if(capture)await page.screenshot({path: path.join(shots, 'adhan-test-en.png')});
    assert.equal(await page.locator('.call-frame').count(), 1, 'adhan uses the Android mihrab layout');
    assert.equal(await page.locator('#overlay').evaluate(el => getComputedStyle(el).backgroundColor), 'rgba(0, 0, 0, 0)', 'only the adhan mihrab has a background');
    assert.equal(await page.locator('#screen').evaluate(el => getComputedStyle(el).visibility), 'hidden', 'adhan hides the originating page outside its mihrab');
    assert.equal(await page.locator('.call-verse').innerText(), 'حَيَّ عَلَى الصَّلَاةِ');
    assert.deepEqual(await page.locator('.call-column button').allTextContents(), ['Close'], 'Close is the only adhan control');
    await page.locator('[data-action=dismiss-alert]').click();
    await inputRestored();
    assert.equal(await page.locator(':focus').getAttribute('data-action'), 'test-adhan', 'adhan returns to the original test row');
    assert.equal(await page.locator('#screen').evaluate(el => getComputedStyle(el).visibility), 'visible', 'dismissing adhan restores the originating page');
    assert.equal(await page.locator('audio').count(), 0, 'audio source removed before input returns');
    assert.equal(await page.locator('#input-video source').getAttribute('src'), 'ext://hdmi:2');
    await tests();
    await page.locator('[data-action=test-spoken]').click();
    await page.waitForFunction(() => document.querySelector('audio') && document.querySelector('audio').getAttribute('src'));
    assert.equal(await page.locator('#input-video source').count(), 0, 'spoken playback retains the existing decoder handoff');
    assert.match(await page.locator('audio').getAttribute('src'), /before_fajr\.mp3$/);
    await page.evaluate(() => document.querySelector('audio').dispatchEvent(new Event('ended')));
    await inputRestored();
    await tests();
    await page.locator('[data-action=test-adhan]').click();
    await page.locator('[data-action=quiet-input]').waitFor();
    assert.equal(await page.locator(':focus').getAttribute('data-action'), 'dismiss-alert');
    await page.keyboard.press('ArrowDown');
    assert.equal(await page.locator(':focus').getAttribute('data-action'), 'quiet-input');
    await page.keyboard.press('Enter');
    assert.equal(await page.locator('#input-video').evaluate(el => el.muted), true);
    assert.equal(await page.locator('[data-action=quiet-input]').innerText(), 'Hide HDMI');
    assert.equal(await page.locator('#input-video source').count(), 1, 'mute retains HDMI video');
    await page.keyboard.press('Enter');
    assert.equal(await page.locator('#input-video source').count(), 0, 'Hide HDMI releases the decoder');
    assert.match(await page.locator('#overlay').evaluate(el => getComputedStyle(el).backgroundColor), /^rgb\(/, 'the full call page is opaque');
    assert.equal(await page.locator(':focus').getAttribute('data-action'), 'dismiss-alert');
    await page.waitForFunction(() => document.querySelector('audio')?.getAttribute('src'), null);
    await page.keyboard.press('Escape');
    await inputRestored();
    await page.locator('#input-video').evaluate(video => Object.defineProperty(video, 'volume', {configurable: true, get: () => 1, set() {}}));
    await tests();
    await page.locator('[data-action=test-adhan]').click();
    await page.locator('[data-action=quiet-input]').waitFor();
    assert.equal(await page.locator('#input-video').evaluate(video => video.muted), true, 'rejected attenuation falls back to mute');
    assert.equal(await page.locator('[data-action=quiet-input]').innerText(), 'Hide HDMI');
    await page.locator('#input-video').evaluate(video => delete video.volume);
    await page.keyboard.press('Escape');
    await inputRestored();
    await page.locator('#input-video').evaluate(video => {
      Object.defineProperty(video, 'volume', {configurable: true, get: () => 1, set() {}});
      Object.defineProperty(video, 'muted', {configurable: true, get: () => false, set() {}});
    });
    await tests();
    await page.locator('[data-action=test-adhan]').click();
    await page.waitForFunction(() => document.querySelector('audio')?.getAttribute('src'));
    assert.equal(await page.locator('#input-video source').count(), 0, 'rejected volume and mute release HDMI before adhan');
    assert.match(await page.locator('#overlay').evaluate(el => getComputedStyle(el).backgroundColor), /^rgb\(/);
    await page.locator('#input-video').evaluate(video => { delete video.volume; delete video.muted; });
    await page.keyboard.press('Escape');
    await inputRestored();
    await tests();
    await page.locator('[data-action=test-adhan]').click();
    await page.waitForFunction(() => document.querySelector('audio') && document.querySelector('audio').getAttribute('src'));
    await page.evaluate(() => {
      const audio = document.querySelector('audio');
      Object.defineProperty(audio, 'error', {value: {code: 4}}); audio.dispatchEvent(new Event('error'));
    });
    await inputRestored();
    assert.equal(await page.locator('audio').count(), 0, 'media failure releases decoder and restores input');
    const hdmiFixture = await page.evaluate(() => JSON.parse(localStorage.getItem('fixture')));
    for (const input of ['DTV', 'AV_1']) {
      await page.evaluate(input => {
        const state = JSON.parse(localStorage.getItem('fixture')); state.input = input;
        localStorage.setItem('fixture', JSON.stringify(state));
      }, input);
      await page.reload(); await inputRestored(); await tests();
      await page.locator('[data-action=test-adhan]').click();
      await page.waitForFunction(() => document.querySelector('audio')?.getAttribute('src'));
      assert.equal(await page.locator('#input-video source').count(), 0, input + ' keeps the existing audio decoder handoff');
      await page.evaluate(() => document.querySelector('audio').dispatchEvent(new Event('ended')));
      await inputRestored();
      assert.equal(await page.locator('#input-video source').getAttribute('type'), await page.evaluate(input => window.Miqat.input(input).type, input));
    }
    await page.evaluate(state => localStorage.setItem('fixture', JSON.stringify(state)), hdmiFixture);
    await page.reload(); await inputRestored(); await tests();
    await tests();
    await page.locator('[data-action=section][data-value=appearance]').click();
    await page.waitForSelector('#notice', {state: 'hidden'});
    assert.equal(await page.locator('.appearance-preview svg').count(), 1);
    assert.equal((await page.locator('.appearance-preview svg').boundingBox()).height, 396, 'larger preview stays within the reserved panel');
    const carousel = key => page.locator(['theme','background'].includes(key) ? '[data-action=carousel][data-value=' + key + ']' : '[data-action=' + key + ']');
    await carousel('theme').focus();
    for (let i = 0; i < 19; i++) await page.keyboard.press('ArrowRight');
    assert.equal(await carousel('theme').locator('.carousel-choice').innerText(), 'Mosaic', 'remote reaches all 20 themes inline');
    assert.match(await page.locator('.appearance-preview').innerText(), /Mosaic/);
    await page.keyboard.press('ArrowRight');
    assert.equal(await carousel('theme').locator('.carousel-choice').innerText(), 'Mosaic', 'carousel clamps at the end');
    assert.equal(await page.locator('#theme-style').getAttribute('href'), 'theme-mosaic.css', 'theme saves immediately and styles the whole page');
    if(capture)await page.screenshot({path: path.join(shots, 'themes-en.png')});
    await page.keyboard.press('ArrowDown');
    assert.equal(await page.locator(':focus').getAttribute('data-value'), 'background', 'Down moves between carousel rows');
    await page.keyboard.press('Escape');
    assert.equal(await carousel('theme').locator('.carousel-choice').innerText(), 'Mosaic', 'Back retains the selected theme');
    await carousel('theme').focus(); for(let i=0;i<19;i++) await page.keyboard.press('ArrowLeft');
    await page.locator('[data-action=carousel-step][data-value="theme:1"]').click();
    assert.notEqual(await carousel('theme').locator('.carousel-choice').innerText(), 'Miqat', 'pointer arrow updates the preview inline');
    await page.locator('[data-action=reset-layout]').click();
    assert.equal(await carousel('theme').locator('.carousel-choice').innerText(), await page.evaluate(()=>window.MiqatData.strings.themeNames.en[1]));
    await carousel('theme').focus(); await page.keyboard.press('ArrowLeft');
    const backgroundBeforePreset = await page.evaluate(() => JSON.parse(localStorage.getItem('fixture')).prefs.background);
    const forestIndex = await page.evaluate(() => window.MiqatData.themes.indexOf('forest'));
    await carousel('theme').focus();
    for (let i = 0; i < forestIndex; i++) await page.keyboard.press('ArrowRight');
    assert.match(await page.locator('.appearance-model svg').evaluate(el => getComputedStyle(el).backgroundImage), /backgrounds\/tea_hills.jpg/, 'scenic theme chooses its paired landscape in the draft');
    assert.equal(await page.evaluate(() => JSON.parse(localStorage.getItem('fixture')).prefs.background), 'tea_hills', 'theme selection saves its paired background');
    await carousel('background').focus();
    for (let i = 0; i < 30; i++) await page.keyboard.press('ArrowLeft');
    await page.keyboard.press('ArrowRight'); await page.keyboard.press('ArrowRight');
    for (const layout of ['mihrab', 'compact', 'hidden']) {
      const svg = page.locator('.appearance-model svg');
      assert.equal(await svg.locator('pattern, image').count(), 0, 'old WebKit preview must not clip controls through an SVG image paint server');
      assert.match(await svg.evaluate(el => getComputedStyle(el).backgroundImage), /eight_point_stars/, 'preview retains the selected CSS pattern');
      assert.match(await svg.evaluate(el => getComputedStyle(el).backgroundImage), /forest-eight_point_stars/, 'draft theme chooses the matching tinted mask');
      assert.equal(await svg.evaluate(el => getComputedStyle(el).backgroundSize), '100% 100%', 'preview shows the complete composition');
      assert.deepEqual(await svg.locator('rect').evaluateAll(nodes => nodes.flatMap(el => {
        const box = el.getBBox();
        return box.x < 0 || box.y < 0 || box.x + box.width > 1920 || box.y + box.height > 1080 ? [el.outerHTML] : [];
      })), [], 'patterned ' + layout + ' preview keeps all controls in its viewport');
      if (layout !== 'hidden') { await carousel('homeLayout').focus(); await page.keyboard.press('ArrowRight'); }
    }
    await carousel('homeLayout').focus(); await page.keyboard.press('ArrowLeft'); await page.keyboard.press('ArrowLeft');
    assert.ok(await page.evaluate(() => new Promise(resolve => {
      const pattern = new Image(); pattern.onload = () => resolve(pattern.naturalWidth > 0); pattern.onerror = () => resolve(false);
      pattern.src = /url\(["']?([^"')]+)["']?\)/.exec(getComputedStyle(document.querySelector('.appearance-model svg')).backgroundImage)[1];
    })), 'preview pattern asset decodes');
    await carousel('homeLayout').focus(); await page.keyboard.press('ArrowRight');
    await carousel('homeBar').focus(); await page.keyboard.press('ArrowRight');
    await carousel('prayerMode').focus();
    for (let i = 0; i < 3; i++) await page.keyboard.press('ArrowRight');
    await carousel('theme').focus();
    if(capture)await page.screenshot({path: path.join(shots, 'appearance-en.png')});
    await page.waitForFunction(() => document.getElementById('theme-style').getAttribute('href') === 'theme-forest.css');
    assert.match(await page.locator('.settings-page').evaluate(el => getComputedStyle(el).backgroundImage), /eight_point_stars/, 'the whole appearance page previews its saved background');
    assert.equal(await page.evaluate(() => JSON.parse(localStorage.getItem('fixture')).prefs.prayerMode), 'both');
    await page.waitForSelector('[data-action=home]');
    await page.locator('[data-action=home]').click();
    assert.match(await page.locator('.home').evaluate(el => getComputedStyle(el).backgroundImage), /eight_point_stars/, 'saved pattern still appears on Home');
    await homeControls();
    if(capture)await page.screenshot({path: path.join(shots, 'home-compact-en.png')});
    const dateBox = await page.locator('[data-gregorian]').boundingBox(), compactArch = await page.locator('.arch').boundingBox();
    assert.ok(dateBox.y + dateBox.height < compactArch.y, 'both dates fit above the compact countdown');
    assert.equal(compactArch.y, 916, 'compact countdown can be placed at the bottom');
    assert.ok(await page.locator('.home-prayers .prayer:visible').count() <= 2, 'current/next mode filters the prayer strip');
    if (await page.locator('.home-prayers .prayer.current').count()) {
      assert.match(await page.locator('.home-prayers .prayer').first().getAttribute('class'), /current/, 'current prayer precedes next, including after Isha');
    }
    for (const button of await page.locator('#screen button').all()) {
      const b = await button.boundingBox(); assert.ok(b.x >= 0 && b.y >= 0 && b.x + b.width <= 1920 && b.y + b.height <= 1080, 'compact Home controls fit');
    }
    await page.locator('[data-action=radio]').click(); await page.locator('[data-action=radio-play]').click();
    assert.equal(await page.locator('#input-video source').count(), 0, 'radio retains the existing decoder handoff');
    await page.waitForFunction(() => document.querySelector('audio') && document.querySelector('audio').getAttribute('src'));
    assert.match(await page.locator('audio').getAttribute('src'), /^https:\/\//, 'radio uses the shared station URL');
    await page.locator('[data-action=input]').click(); await inputRestored();
    assert.equal(await page.locator('audio').count(), 0);
    await page.reload(); await inputRestored();
    const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('fixture')).prefs);
    assert.equal(saved.offsets[0], 3); assert.equal(saved.bells[0], false); assert.equal(saved.theme, 'forest');
    assert.equal(saved.automatic, false, 'test buttons do not enable real alarms');
    await tests();
    await page.locator('[data-action=section][data-value=display]').click();
    await selectInlineChoice('language', 0);
    await page.waitForFunction(() => document.documentElement.dir === 'rtl');
    await page.locator('[data-action=section][data-value=tests]').click();
    await page.locator('[data-action=test-adhan]').click();
    await page.waitForFunction(() => document.querySelector('audio') && document.querySelector('audio').getAttribute('src'));
    assert.equal(await page.locator('#overlay').evaluate(el => getComputedStyle(el).backgroundColor), 'rgba(0, 0, 0, 0)', 'Arabic adhan keeps the same transparent outer area');
    assert.equal(await page.locator('#screen').evaluate(el => getComputedStyle(el).visibility), 'hidden', 'Arabic adhan hides the originating page');
    assert.deepEqual(await page.locator('.call-column button').allTextContents(), ['إغلاق']);
    if(capture)await page.screenshot({path: path.join(shots, 'adhan-test-ar.png')});
    await page.keyboard.press('Escape');
    await inputRestored();
    assert.equal(await page.locator(':focus').getAttribute('data-action'), 'test-adhan');
    await page.locator('[data-action=section][data-value=appearance]').click();
    await carousel('theme').focus();
    await page.keyboard.press('ArrowLeft');
    const rtlTheme = await carousel('theme').locator('.carousel-choice').innerText();
    await page.keyboard.press('ArrowRight');
    assert.notEqual(await carousel('theme').locator('.carousel-choice').innerText(), rtlTheme, 'Arabic carousel arrows follow RTL');
    if(capture)await page.screenshot({path: path.join(shots, 'themes-ar.png')});
    await page.keyboard.press('Escape');
    await page.locator('[data-action=section][data-value=display]').click();
    await page.locator('[data-action=quickLabels]').click();
    await page.waitForFunction(() => JSON.parse(localStorage.getItem('fixture')).prefs.quickLabels);
    await page.keyboard.press('Escape'); await page.keyboard.press('Escape');
    for (const lang of ['ar', 'en']) {
      for (const layout of ['mihrab', 'compact', 'hidden']) {
        for (const position of layout === 'compact' ? ['top', 'bottom'] : ['top']) {
          await page.evaluate(({lang, layout, position}) => {
            const fixture = JSON.parse(localStorage.getItem('fixture'));
            Object.assign(fixture.prefs, {lang, homeLayout: layout, homeBar: position});
            localStorage.setItem('fixture', JSON.stringify(fixture));
          }, {lang, layout, position});
          await page.reload(); await inputRestored();
          await page.keyboard.press('Enter'); await page.locator('[data-action=home]').click();
          for (const label of await page.locator('.quick-caption').all()) {
            assert.ok(await label.isVisible(), 'quick labels stay visible without focus');
            assert.ok(await label.evaluate(el => { const a = el.getBoundingClientRect(), b = el.parentNode.getBoundingClientRect(); return a.left >= b.left && a.right <= b.right && a.top >= b.top && a.bottom <= b.bottom; }), 'quick label fits its button');
          }
          for (const control of await page.locator('.home button').all()) {
            const b = await control.boundingBox(); assert.ok(b.y >= 0 && b.y + b.height <= 1080, 'labeled Home fits the screen');
          }
          const prayer = await page.locator('.home-prayers').boundingBox(), quick = await page.locator('.home-quick button').first().boundingBox();
          assert.ok(quick.y + quick.height < prayer.y, 'labeled buttons stay above prayer times: ' + [lang, layout, position, quick.y, prayer.y].join('/'));
          if (layout === 'compact') {
            const arch = await page.locator('.arch').boundingBox(), links = await page.locator('.home-links').boundingBox();
            assert.ok(arch.y > links.y + links.height || arch.y + arch.height < prayer.y, 'countdown stays clear of prayer times and app controls');
          }
          await homeNavigation();
          if(capture)await page.screenshot({path: path.join(shots, 'labels-' + lang + '-' + layout + '-' + position + '.png')});
        }
      }
    }
    async function scheduled(mode, external) {
      await page.evaluate(({mode, external}) => {
        const state = JSON.parse(localStorage.getItem('fixture'));
        state.pendingAlert = {id: 'scheduled:' + Date.now(), mode, prayer: 0, test: true, background: true, returnToPrevious: external};
        localStorage.setItem('fixture', JSON.stringify(state));
        window.mediaCalls = [];
        document.dispatchEvent(new CustomEvent('webOSRelaunch', {detail: {alertId: state.pendingAlert.id}}));
      }, {mode, external});
      await page.waitForSelector('#overlay button');
    }
    await tests();
    await scheduled('adhan', false);
    await page.waitForSelector('audio', {state: 'attached'});
    await page.evaluate(() => document.querySelector('audio').dispatchEvent(new Event('ended')));
    await page.waitForSelector('[data-action=test-adhan]');
    assert.equal(await page.evaluate(() => window.closeCount), 0, 'scheduled adhan in foreground preserves Settings');
    for (const mode of ['adhan', 'banner']) {
      await scheduled(mode, true);
      if (mode === 'adhan') {
        await page.locator('#input-video source').waitFor({state: 'attached'});
        assert.equal(await page.locator('#input-video').evaluate(el => el.volume), 0.15, 'background adhan also lowers HDMI');
      }
      await page.evaluate(() => { window.mediaCalls = []; });
      if (mode === 'adhan') {
        await page.waitForSelector('audio', {state: 'attached'});
        await page.evaluate(() => document.querySelector('audio').dispatchEvent(new Event('ended')));
      } else { await page.locator('[data-action=dismiss-alert]').click(); }
      await page.waitForFunction(expected => window.closeCount === expected, mode === 'adhan' ? 1 : 2);
      await page.waitForTimeout(550);
      assert.equal(await page.evaluate(() => window.mediaCalls.filter(c => c.tag === 'VIDEO' && c.op === 'play').length), 0, 'external alert closes without starting HDMI again');
    }
    await page.evaluate(() => {
      const state = JSON.parse(localStorage.getItem('fixture'));
      state.pendingAlert = {id: 'cold:1', mode: 'adhan', prayer: 0, test: true, returnToPrevious: false};
      localStorage.setItem('fixture', JSON.stringify(state));
    });
    await page.reload();
    await page.waitForSelector('#overlay button');
    await page.evaluate(() => document.querySelector('audio').dispatchEvent(new Event('ended')));
    await page.waitForFunction(() => window.closeCount === 1);
    await page.evaluate(() => {
      const state = JSON.parse(localStorage.getItem('fixture')); state.expiredAlert = true;
      localStorage.setItem('fixture', JSON.stringify(state));
    });
    await page.reload(); await inputRestored();
    await tests();
    await page.locator('[data-action=test-adhan]').click();
    await page.waitForSelector('#overlay button');
    await page.locator('[data-action=dismiss-alert]').click();
    assert.equal(await page.evaluate(() => window.closeCount), 0, 'expired cold-start alerts do not make later in-app tests exit');
    assert.equal(await page.locator('.settings-page').count(), 1);
    await page.evaluate(() => {
      const state = JSON.parse(localStorage.getItem('fixture')); state.prefs.volume = 15;
      localStorage.setItem('fixture', JSON.stringify(state));
    });
    await page.reload(); await inputRestored();
    await page.evaluate(() => { window.legacyVolume = true; window.mockMasterVolume = 50; window.mediaCalls = []; });
    await tests(); await page.locator('[data-action=test-adhan]').click();
    await page.waitForFunction(() => window.mediaCalls.some(c => c.op === 'play' && c.tag === 'AUDIO'));
    const legacyPlayback = await page.evaluate(() => window.mediaCalls.find(c => c.op === 'play' && c.tag === 'AUDIO'));
    assert.equal(legacyPlayback.masterVolume, 15, 'old LG applies 15/100 independently of the captured TV level before adhan playback');
    assert.equal(legacyPlayback.elementVolume, 1, 'native volume fallback does not apply attenuation twice');
    assert.equal(legacyPlayback.inputSource, undefined, 'old LG releases HDMI before starting adhan because it ignores element mute');
    assert.match(await page.locator('#overlay').evaluate(el => getComputedStyle(el).backgroundColor), /^rgb\(/, 'legacy LG uses an opaque call page');
    assert.equal(await page.locator('[data-action=quiet-input]').isVisible(), false, 'released HDMI has no sound action');
    await page.locator('[data-action=dismiss-alert]').click();
    await page.waitForFunction(() => window.mockMasterVolume === 50);
    assert.equal(await page.locator('.call-frame').count(), 0, 'Close stops and dismisses the adhan');
    await inputRestored();
    await page.evaluate(() => { window.mediaCalls = []; window.deferVolume = true; });
    await tests(); await page.locator('[data-action=test-adhan]').click();
    await page.waitForFunction(() => window.volumeReplies && window.volumeReplies.length === 1);
    const owners = await page.evaluate(() => window.volumeCalls.filter(call => call.action === 'prepare').map(call => call.id));
    assert.equal(new Set(owners).size, owners.length, 'each alert has a distinct volume owner');
    await page.waitForTimeout(700); // Cross the 450 ms decoder-release delay while preparation is pending.
    assert.equal(await page.evaluate(() => window.mediaCalls.filter(c => c.op === 'play' && c.tag === 'AUDIO').length), 0, 'the decoder delay cannot bypass volume preparation');
    await page.evaluate(() => { window.deferVolume = false; window.volumeReplies.shift()(); });
    await page.waitForFunction(() => window.mockMasterVolume === 15 && document.querySelector('audio')?.getAttribute('src'));
    await page.keyboard.press('Escape');
    await page.waitForFunction(() => window.mockMasterVolume === 50);
    await inputRestored();
    await page.evaluate(() => { window.failVolume = true; window.mediaCalls = []; });
    await tests(); await page.locator('[data-action=test-adhan]').click();
    await inputRestored();
    assert.equal(await page.evaluate(() => window.mediaCalls.filter(c => c.op === 'play' && c.tag === 'AUDIO').length), 0, 'preparation failure never falls back to loud playback');
    assert.equal(await page.evaluate(() => window.mockMasterVolume), 50);
    await page.evaluate(() => { window.failVolume = false; window.deferVolume = true; window.mediaCalls = []; });
    await tests(); await page.locator('[data-action=test-adhan]').click();
    await page.waitForFunction(() => window.volumeReplies && window.volumeReplies.length === 1);
    await page.keyboard.press('Escape');
    await page.evaluate(() => { window.deferVolume = false; window.volumeReplies.shift()(); });
    await page.waitForFunction(() => window.mockMasterVolume === 50 && !document.querySelector('audio'));
    assert.equal(await page.evaluate(() => window.mediaCalls.filter(c => c.op === 'play' && c.tag === 'AUDIO').length), 0, 'cancelled preparation cannot start audio after its reply arrives');
    await inputRestored();
    await tests(); await page.locator('[data-action=test-adhan]').click();
    await page.waitForFunction(() => window.mockMasterVolume === 15 && document.querySelector('audio').getAttribute('src'));
    await page.evaluate(() => { window.mockMasterVolume = 22; });
    await page.keyboard.press('Escape');
    await inputRestored();
    assert.equal(await page.evaluate(() => window.mockMasterVolume), 22, 'manual remote level survives dismissal');
    await page.evaluate(() => { window.mockMasterVolume = 50; });
    await tests(); await page.locator('[data-action=test-adhan]').click();
    await page.waitForFunction(() => window.mockMasterVolume === 15 && document.querySelector('audio').getAttribute('src'));
    await page.evaluate(() => {
      Object.defineProperty(document, 'hidden', {configurable: true, value: true});
      document.dispatchEvent(new Event('visibilitychange'));
    });
    await page.waitForFunction(() => window.mockMasterVolume === 50 && !document.querySelector('audio'));
    await page.evaluate(() => {
      Object.defineProperty(document, 'hidden', {configurable: true, value: false});
      document.dispatchEvent(new Event('visibilitychange'));
    });
    await inputRestored();
    await page.evaluate(() => {
      const state = JSON.parse(localStorage.getItem('fixture')); state.prefs.callClose = 1;
      localStorage.setItem('fixture', JSON.stringify(state));
    });
    await page.reload(); await inputRestored();
    await page.evaluate(() => { window.legacyVolume = true; window.mockMasterVolume = 50; });
    await tests(); await page.locator('[data-action=test-full]').click();
    await page.waitForFunction(() => window.mockMasterVolume === 15 && document.querySelector('audio').getAttribute('src'));
    await page.evaluate(() => document.querySelector('audio').dispatchEvent(new Event('ended')));
    await page.waitForFunction(() => window.mockMasterVolume === 50);
    assert.equal(await page.locator('.call-frame').count(), 1, 'completed full adhan restores level while keeping its timed call screen');
    await page.locator('[data-action=dismiss-alert]').click();
    await inputRestored();
    await tests(); await page.locator('[data-action=test-adhan]').click();
    await page.waitForFunction(() => window.mockMasterVolume === 15 && document.querySelector('audio')?.getAttribute('src'));
    await page.evaluate(() => {
      const audio = document.querySelector('audio');
      Object.defineProperty(audio, 'error', {value: {code: 4}}); audio.dispatchEvent(new Event('error'));
    });
    await inputRestored();
    assert.equal(await page.evaluate(() => window.mockMasterVolume), 50, 'native volume also restores after decoder failure');
    assert.deepEqual(errors, []);
    async function fits(selector) {
      await page.locator(selector).first().waitFor({state:'visible'});
      assert.deepEqual(await page.evaluate(() => {
        const screen = document.getElementById('screen'), overlay = document.getElementById('overlay');
        return [document.documentElement.scrollWidth, document.documentElement.scrollHeight, screen.scrollLeft, screen.scrollTop, overlay.scrollLeft, overlay.scrollTop];
      }), [1920,1080,0,0,0,0], 'UI zoom must not enlarge or scroll the root viewport');
      const centre = await page.evaluate(() => {
        const panel = document.getElementById('screen').firstElementChild;
        if (!panel || !panel.getClientRects().length) return null;
        const box = panel.getBoundingClientRect(); return (box.left + box.right) / 2;
      });
      assert.ok(centre === null || Math.abs(centre - 960) < 0.5, 'scaled content stays horizontally centred in its physical viewport');
      const originalSection = await page.evaluate(()=>{const selected=document.querySelector('.settings-nav .selected');return selected?selected.getAttribute('data-value'):null;});
      const items = await page.locator(selector).all();
      assert.ok(items.length, 'expected visible controls: ' + selector);
      const contentItems=[], sidebarItems=[];
      for(const item of items)(await item.evaluate(el=>!!el.closest('.settings-nav'))?sidebarItems:contentItems).push(item);
      // Sidebar focus now replaces the content, so check its rows before browsing other sections.
      for (const item of [...contentItems,...sidebarItems]) {
        await item.evaluate(el => { if (el.closest('button')) { el.closest('button').focus(); document.getElementById('screen').scrollLeft = document.getElementById('screen').scrollTop = 0; document.getElementById('overlay').scrollLeft = document.getElementById('overlay').scrollTop = 0; } else if (document.getElementById('stage').getAttribute('data-large-ui') === 'true') el.scrollIntoView({block:'center', inline:'nearest'}); });
        const b = await item.boundingBox();
        assert.ok(b && b.x >= -0.1 && b.y >= -0.1 && b.x + b.width <= 1920.1 && b.y + b.height <= 1080.1, selector + ' stays on screen: ' + JSON.stringify(b));
        const ring = await item.evaluate(el => {
          if (document.activeElement !== el) return 0;
          const css = getComputedStyle(el), scale = JSON.parse(localStorage.getItem('fixture')).prefs.uiScale / 100;
          return (parseFloat(css.outlineWidth) + parseFloat(css.outlineOffset)) * scale;
        });
        assert.ok(b.x - ring >= -0.5 && b.y - ring >= -0.5 && b.x + b.width + ring <= 1920.5 && b.y + b.height + ring <= 1080.5, 'focused outline stays on screen: ' + selector + ' ' + JSON.stringify({box:b,ring}));
      }
      if(originalSection&&sidebarItems.length)await page.locator('[data-action=section][data-value='+originalSection+']').focus();
    }
    for (const lang of ['ar', 'en']) for (const size of [150, 80, 90, 100, 110, 120, 130, 140]) {
      await page.evaluate(lang => {
        localStorage.setItem('fixture', JSON.stringify({prefs: window.Miqat.prefs({lang, volume: 30, dockAutoHide: 0}), input: 'HDMI_2', startup: {enabled: true}}));
      }, lang);
      await page.reload(); await inputRestored();
      await page.evaluate(() => document.fonts.ready);
      await page.keyboard.press('Enter'); await page.locator('[data-action=settings]').click();
      await page.locator('[data-action=section][data-value=display]').click();
      const beforeSize = await page.evaluate(() => JSON.parse(localStorage.getItem('fixture')));
      await page.locator('[data-ui="display.size"]').click();
      assert.equal(await page.locator('.settings-page').count(), 1, 'size editing stays in the settings row');
      assert.equal(await page.locator('.picker,.stepper').count(), 0, 'no size editor page');
      await page.keyboard.press(lang === 'ar' ? 'ArrowLeft' : 'ArrowRight');
      assert.equal(await page.locator('[data-ui="display.size"] strong').innerText(), lang === 'ar' ? '١١٠%' : '110%');
      await page.keyboard.press('Escape');
      assert.equal(await page.locator(':focus').getAttribute('data-action'),'section','Back returns to the sidebar');
      assert.equal(await page.evaluate(() => JSON.parse(localStorage.getItem('fixture')).prefs.uiScale),110,'Back retains the immediate size');
      await page.locator('[data-action=settings-back]').click();
      assert.equal(await page.evaluate(() => JSON.parse(localStorage.getItem('fixture')).prefs.uiScale),110,'leaving keeps the size');
      await page.locator('[data-action=settings]').click();
      await page.locator('[data-action=section][data-value=display]').click();
      await page.locator('[data-ui="display.size"]').click();
      for (let i = 0; i < 6; i++) await page.locator('[data-number-step="-1"]').click();
      assert.equal(await page.locator('[data-ui="display.size"] strong').innerText(), lang === 'ar' ? '٨٠%' : '80%');
      for (let i = 0; i < 9; i++) await page.locator('[data-number-step="1"]').click();
      assert.equal(await page.locator('[data-ui="display.size"] strong').innerText(), lang === 'ar' ? '١٥٠%' : '150%');
      for (let i = 150; i > size; i -= 10) await page.locator('[data-number-step="-1"]').click();
      await page.keyboard.press('Enter');
      await page.waitForSelector('[data-ui="display.size"]:not(.editing)');
      assert.equal(await page.locator(':focus').getAttribute('data-ui'), 'display.size', 'saving size restores its row');
      const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('fixture')));
      assert.deepEqual(saved.prefs, {...beforeSize.prefs, uiScale: size}, 'only global UI size changes');
      assert.deepEqual(saved.startup, beforeSize.startup); assert.equal(saved.input, beforeSize.input);
      await page.locator('[data-action=section][data-value=reminders]').click();
      const volumeRow = page.locator('[data-ui="reminders.volume"]');
      await volumeRow.click();
      assert.equal(await page.locator('.settings-page').count(), 1, 'volume editing stays in its settings row');
      assert.equal(await page.locator('.picker,.stepper').count(), 0, 'no separate numeric editor page');
      await page.keyboard.press(lang === 'ar' ? 'ArrowLeft' : 'ArrowRight');
      assert.equal(await volumeRow.locator('strong').innerText(), lang === 'ar' ? '٣٥%' : '35%', 'shared 5% step follows RTL once');
      assert.deepEqual(await page.evaluate(() => JSON.parse(localStorage.getItem('fixture')).prefs), {...saved.prefs,volume:35}, 'numeric adjustment saves immediately');
      await page.keyboard.press('Escape');
      assert.equal(await page.locator(':focus').getAttribute('data-action'), 'section', 'Back returns to the sidebar');
      assert.equal(await volumeRow.locator('strong').innerText(), lang === 'ar' ? '٣٥%' : '35%');
      await volumeRow.locator('[data-number-step="1"]').click();
      await page.locator('[data-action=section][data-value=display]').click();
      assert.deepEqual(await page.evaluate(() => JSON.parse(localStorage.getItem('fixture')).prefs), {...saved.prefs,volume:40}, 'leaving retains both immediate steps');
      await page.locator('[data-action=section][data-value=reminders]').click();
      await volumeRow.click(); await page.keyboard.press(lang === 'ar' ? 'ArrowRight' : 'ArrowLeft');
      await page.keyboard.press('Enter');
      await page.locator('[data-ui="reminders.volume"]:not(.editing)').waitFor();
      assert.deepEqual(await page.evaluate(() => JSON.parse(localStorage.getItem('fixture')).prefs), {...saved.prefs, volume:35}, 'saving changes only the bound number');
      await volumeRow.locator('[data-number-step="-1"]').click(); await page.keyboard.press('Enter');
      await page.locator('[data-ui="reminders.volume"]:not(.editing)').waitFor();
      assert.deepEqual(await page.evaluate(() => JSON.parse(localStorage.getItem('fixture'))), saved, 'pointer decrement restores original state without other writes');
      await fits('[data-ui="reminders.volume"],.settings-nav button');
      await page.locator('[data-action=section][data-value=display]').click();
      await fits('[data-ui="display.size"],.settings-page .round-back,.settings-nav button');
      const positionRow = page.locator('[data-ui="display.bar-position"]');
      await positionRow.focus(); await page.keyboard.press('Enter');
      await positionRow.locator('[data-choice-index="0"]').click();
      await page.keyboard.press(lang === 'ar' ? 'ArrowLeft' : 'ArrowRight');
      assert.equal(await positionRow.locator('.segment.selected').getAttribute('data-choice-index'), '1', 'inline choice translates RTL once');
      assert.deepEqual(await page.evaluate(() => JSON.parse(localStorage.getItem('fixture')).prefs), {...saved.prefs,dockPosition:'bottom'}, 'segment saves immediately');
      await page.keyboard.press('Escape');
      assert.equal(await page.locator(':focus').getAttribute('data-action'), 'section', 'Back leaves the saved segment');
      assert.equal(await page.locator('.picker').count(), 0, 'short choices stay in their settings card');
      await fits('[data-ui="display.language"] .segments,[data-ui="display.clock"] .segments,[data-ui="display.bar-position"] .segments');
      assert.ok(await page.locator('[data-ui="display.quick-labels"] .switch').evaluate(el => {
        const box = el.getBoundingClientRect(), knob = el.firstElementChild.getBoundingClientRect(), row = el.parentElement.getBoundingClientRect();
        return box.width > 0 && box.height > 0 && knob.left >= box.left && knob.right <= box.right && knob.top >= box.top && knob.bottom <= box.bottom
          && box.left >= row.left && box.right <= row.right && box.top >= row.top && box.bottom <= row.bottom;
      }), 'scaled switch track and knob stay inside their row');
      if (size === 80 || size === 110 || size === 150) if(capture)await page.screenshot({path:path.join(shots, 'size-' + lang + '-' + size + '-settings.png')});
      await page.locator('[data-action=settings-back]').click();
      await fits('.home-quick button,.home-apps button,.home-links button,.verse,.city-name');
      assert.deepEqual(await page.locator('.arch .verse,.arch .verse-ref,.arch .prayer-name,.arch .prayer-time,.arch .countdown,.arch .city-name').evaluateAll(nodes => {
        const bad = [];
        for (const el of nodes) {
          const box = el.getBoundingClientRect(), text = document.createRange(); text.selectNodeContents(el);
          for (const ink of text.getClientRects()) if (ink.left < box.left - 1 || ink.right > box.right + 1) bad.push(el.className);
        }
        return bad;
      }), [], 'narrowed mihrab keeps its text inside');
      const artwork = await page.locator('.home .arch').boundingBox();
      const safeEdge = size <= 110 ? 31.5 : 0;
      assert.ok(artwork.x >= 31.5 && artwork.x + artwork.width <= 1920 - 31.5, 'Home mihrab retains horizontal TV-safe clearance');
      if (size <= 110) assert.ok(artwork.y >= safeEdge && artwork.y + artwork.height <= 1080 - safeEdge, 'fixed Home mihrab retains vertical TV-safe clearance');
      const rememberedApp = page.locator('.home-apps .app-tile').nth(1);
      await rememberedApp.focus(); await page.keyboard.press('ArrowUp');
      assert.equal(await page.locator('.home-quick button:focus').count(), 1, 'Up reaches the visible top controls');
      await page.keyboard.press('ArrowDown');
      assert.equal(await rememberedApp.evaluate(el => el === document.activeElement), true, 'Down returns to the same app');
      await page.keyboard.press('ArrowDown');
      assert.equal(await page.locator(':focus').getAttribute('data-action'), 'apps', 'Down reaches visible All apps');
      await fits('.home-links [data-action=apps]');
      assert.ok(Math.abs((await page.locator('.home-quick button').first().boundingBox()).width - 64 * size / 100) < 0.2, 'controls scale with the setting');
      if (size === 80 || size === 110 || size === 150) if(capture)await page.screenshot({path:path.join(shots, 'size-' + lang + '-' + size + '-home.png')});
      await page.locator('.home-links [data-action=apps]').click();
      await fits('.apps-heading,.apps-tools');
      await page.locator('.app-tile').nth(3).focus(); await page.keyboard.press('ArrowUp');
      assert.equal(await page.locator(':focus').getAttribute('data-action'), 'apps-back');
      await page.keyboard.press(lang === 'ar' ? 'ArrowLeft' : 'ArrowRight');
      assert.equal(await page.locator(':focus').getAttribute('data-action'), 'prayer-day');
      await page.keyboard.press('Enter'); await fits('.day-row,.day-row button,.day-details button');
      await page.keyboard.press('Escape'); await page.locator('[data-action=settings]').click();
      await page.locator('[data-action=section][data-value=prayer]').click();
      await page.locator('[data-action=method]').click(); await fits('.picker button'); await page.keyboard.press('Escape');
      await page.locator('[data-action=offsets]').click(); await page.locator('[data-action=offset-0]').click();
      await fits('[data-ui="offsets.fajr"]'); await page.keyboard.press('Escape');
      await page.locator('[data-action=online-prayer]').click(); await page.waitForSelector('.online-check table');
      await fits('.online-check .header,.online-check table,.online-note,[data-action=online-retry]');
      await page.locator('[data-action=online-back]').click();
      await page.locator('[data-action=section][data-value=appearance]').click();
      await fits('.appearance-preview,.settings-rows,.carousel-main,.carousel-arrow');
      await page.locator('[data-action=section][data-value=reminders]').click();
      await page.locator('[data-action=voice]').click(); await fits('.voice-list,.voice-footer button');
      await page.keyboard.press('Escape');
      await page.locator('[data-action=section][data-value=display]').click();
      await page.locator('[data-action=clock-view]').click(); await fits('.clock-page button'); await page.keyboard.press('Escape');
      await tests(); const videoCalls = await page.evaluate(() => window.mediaCalls.filter(c => c.tag === 'VIDEO').length);
      await page.locator('[data-action=test-banner]').click(); await fits('.banner-box,.banner-box button');
      const banner = await page.locator('.banner-box').boundingBox();
      assert.ok(Math.abs(banner.x - 64) < 0.2 && Math.abs(banner.y - 36) < 0.2 && Math.abs(banner.width - 1792) < 0.2, 'banner retains screen insets');
      assert.equal(await page.evaluate(() => window.mediaCalls.filter(c => c.tag === 'VIDEO').length), videoCalls, 'size does not disrupt HDMI behind a silent banner');
      await page.keyboard.press('Escape'); await tests();
      await page.locator('[data-action=test-card]').click(); await fits('.card-overlay .alert-box,.card-overlay button');
      await page.keyboard.press('Escape'); await tests();
      await page.locator('[data-action=test-adhan]').click(); await page.waitForFunction(() => document.querySelector('audio')?.getAttribute('src'));
      const quietInput = page.locator('[data-action=quiet-input]');
      assert.equal(await quietInput.innerText(), lang === 'ar' ? 'كتم صوت HDMI' : 'Mute HDMI');
      const quietBounds = await quietInput.boundingBox();
      assert.ok(quietBounds.x >= 0 && quietBounds.y >= 0 &&
        quietBounds.x + quietBounds.width <= 1920 && quietBounds.y + quietBounds.height <= 1080, 'HDMI sound action stays on screen');
      await fits('.call-column button,.call-verse,.call-name,.call-time,.call-status');
      assert.equal(await page.locator('.call-verse').innerText(), sacred.adhanCall);
      const insideArch = await page.locator('.call-frame .inner').evaluate(async arch => {
        await document.fonts.ready;
        const inverse = arch.getScreenCTM().inverse();
        const call = document.createRange();
        call.selectNodeContents(document.querySelector('.call-verse'));
        const {left, right, top, bottom} = call.getBoundingClientRect();
        return [left + 2, right - 2].every(x => [top + 1, bottom - 1].every(y =>
          arch.isPointInFill(new DOMPoint(x, y).matrixTransform(inverse))));
      });
      assert.ok(insideArch, 'the Arabic call and its diacritics fit inside the inner mihrab border');
      if (size === 80 || size === 110 || size === 150) if(capture)await page.screenshot({path:path.join(shots, 'size-' + lang + '-' + size + '-call.png')});
      await page.keyboard.press('Escape'); await inputRestored();
      await tests(); await page.locator('[data-action=settings-back]').click();
      if (await page.locator('[data-action=apps-back]').count()) { await page.locator('[data-action=apps-back]').click(); }
      await page.locator('.home-quick [data-action=inputs]').click();
      await page.locator('[data-action=refresh]:focus').waitFor(); await fits('.picker button'); await page.keyboard.press('Escape');
      await page.locator('.home-quick [data-action=radio]').click(); await fits('.page button'); await page.keyboard.press('Escape');
      for (const position of ['top','bottom']) {
        if (!(await page.locator('[data-action=settings]').count())) { await page.keyboard.press('Enter'); }
        await page.locator('[data-action=settings]').click(); await page.locator('[data-action=section][data-value=display]').click();
        await selectInlineChoice('dockPosition', position === 'top' ? 0 : 1);
        await page.locator('[data-action=bar]').click(); await fits('.bar,.bar button,.bar .prayer');
        const bar = await page.locator('.bar').boundingBox();
        assert.ok(Math.abs(bar.x) < 0.2 && Math.abs(bar.width - 1920) < 0.2, 'prayer bar fits full width');
        assert.ok(Math.abs(position === 'top' ? bar.y : bar.y + bar.height - 1080) < 0.2, 'bar remains attached to selected edge');
        if (size === 80 || size === 110 || size === 150) if(capture)await page.screenshot({path:path.join(shots, 'size-' + lang + '-' + size + '-' + position + '-bar.png')});
        await page.keyboard.press('Escape');
      }
      const video = await page.locator('#input-video').boundingBox();
      assert.deepEqual(video,{x:0,y:0,width:1920,height:1080},'HDMI video never scales with UI');
      const beforeReload = await page.evaluate(() => JSON.parse(localStorage.getItem('fixture')).prefs);
      await page.reload(); await inputRestored(); await page.keyboard.press('Enter');
      assert.deepEqual(await page.evaluate(() => JSON.parse(localStorage.getItem('fixture')).prefs), beforeReload, 'reopening preserves the saved size and other preferences');
      assert.ok(Math.abs((await page.locator('.bar .icon-button').first().boundingBox()).width - 72 * size / 100) < 0.2, 'saved scale is applied after reopening');
      await page.keyboard.press('Escape');
      console.log('PASS: global size',lang,size,'picker/save/cancel, Home/Apps/header/Today/online/settings/pickers/appearance/voices/clock/inputs/radio/alerts, top/bottom bar, unchanged HDMI');
    }
    for (const lang of ['ar', 'en']) for (const variant of [
      {homeLayout:'mihrab',archSide:'left'}, {homeLayout:'mihrab',archSide:'right'},
      {homeLayout:'compact',homeBar:'top'}, {homeLayout:'compact',homeBar:'bottom'}, {homeLayout:'hidden'}
    ]) {
      await page.evaluate(({lang,variant}) => {
        const fixture = JSON.parse(localStorage.getItem('fixture'));
        Object.assign(fixture.prefs, {lang,uiScale:150,quickLabels:true}, variant);
        localStorage.setItem('fixture',JSON.stringify(fixture));
      }, {lang,variant});
      await page.reload(); await inputRestored();
      await page.keyboard.press('Enter'); await page.locator('[data-action=home]').click();
      await fits('.home-quick button,.home-apps button,.home-links button');
      const quick = await page.locator('.home-quick').boundingBox(), prayer = await page.locator('.home-prayers').boundingBox();
      const apps = await page.locator('.home-actions').boundingBox();
      const clock = await page.locator('.home-clock').boundingBox();
      const intersects = (a,b) => Math.min(a.x+a.width,b.x+b.width)>Math.max(a.x,b.x)+1 && Math.min(a.y+a.height,b.y+b.height)>Math.max(a.y,b.y)+1;
      assert.ok(!intersects(clock,quick), 'large toolbar uses the space beside the clock');
      if (variant.homeLayout === 'mihrab') assert.ok(!intersects(await page.locator('.arch').boundingBox(),quick), 'large toolbar clears the mihrab');
      assert.deepEqual(await page.locator('.home-prayers .prayer').evaluateAll(nodes=>{
        const bad=[];
        for(const el of nodes){const box=el.getBoundingClientRect(),range=document.createRange();range.selectNodeContents(el);for(const ink of range.getClientRects())if(ink.left<box.left-1||ink.right>box.right+1||ink.top<box.top-1||ink.bottom>box.bottom+1){bad.push(el.textContent);break;}}
        return bad;
      }), [], 'larger Home prayer text fits inside its cells');
      assert.ok(quick.y + quick.height < prayer.y, 'large labelled controls clear prayer times');
      assert.ok(prayer.y + prayer.height < apps.y, 'large prayer times clear apps');
      if (variant.homeLayout === 'compact') {
        const arch = await page.locator('.arch').boundingBox();
        assert.ok(arch.y + arch.height < prayer.y || arch.y > apps.y + apps.height, 'large compact countdown clears prayer/app content');
      }
      if(capture)await page.screenshot({path:path.join(shots,'size-'+lang+'-150-home-'+variant.homeLayout+'-'+(variant.archSide||variant.homeBar||'none')+'.png')});
    }
    for (const lang of ['ar', 'en']) for (const theme of ['mihrab', 'glass', 'cinema', 'neon', 'signal', 'dusk', 'mosaic']) {
      await page.evaluate(({lang, theme}) => {
        const fixture = JSON.parse(localStorage.getItem('fixture'));
        fixture.prefs = window.Miqat.prefs({...fixture.prefs, lang, theme, uiScale:100, homeLayout:'mihrab', prayerMode:'next', background:'plain', quickLabels:false});
        localStorage.setItem('fixture', JSON.stringify(fixture));
      }, {lang, theme});
      await page.reload(); await page.waitForSelector('#input-video source', {state:'attached'});
      await page.keyboard.press('Enter'); await page.locator('[data-action=home]').click();
      await page.waitForSelector('.home-apps'); await page.evaluate(() => document.fonts.ready);
      const surface = await page.evaluate(theme => window.MiqatData.surfaces[theme], theme);
      const tile = await page.locator('.home-apps .app-tile').first().evaluate(el => ({radius:parseFloat(getComputedStyle(el).borderRadius), stroke:parseFloat(getComputedStyle(el).borderTopWidth)}));
      assert.equal(tile.radius, surface.radius === null ? 18 : surface.radius, 'render canonical theme corners: ' + theme);
      assert.equal(tile.stroke, surface.stroke, 'render canonical theme stroke: ' + theme);
      const nextStyle = await page.locator('.home-prayers .prayer.next').evaluate(el => ({stroke:parseFloat(getComputedStyle(el).borderTopWidth), radius:parseFloat(getComputedStyle(el).borderRadius)}));
      assert.equal(nextStyle.stroke, 3, 'next prayer keeps its semantic outline in every surface');
      assert.equal(nextStyle.radius, surface.radius === null ? 14 : Math.min(28, surface.radius), 'prayer corners retain the shared radius cap');
      await page.locator('[data-action=settings]').click();
      await page.locator('[data-action=section][data-value=display]').click();
      await fits('.settings-nav button,[data-ui="display.language"],[data-ui="display.clock"],[data-ui="display.quick-labels"]');
      const toggle = page.locator('[data-ui="display.quick-labels"]');
      assert.equal(await toggle.getAttribute('role'), 'switch');
      const geometry = await toggle.locator('.switch').evaluate(el => {
        const box = el.getBoundingClientRect(), knob = el.firstElementChild.getBoundingClientRect();
        return {width:box.width,height:box.height,knob:knob.width,inside:knob.left>=box.left && knob.right<=box.right && knob.top>=box.top && knob.bottom<=box.bottom};
      });
      assert.deepEqual(geometry, {width:88,height:48,knob:36,inside:true}, 'switch uses shared Android dimensions');
      if (theme === 'mihrab' || theme === 'neon') if(capture)await page.screenshot({path:path.join(shots,'unified-settings-'+lang+'-'+theme+'.png')});
      if (lang === 'en') {
        await page.evaluate(() => {
          const fixture = JSON.parse(localStorage.getItem('fixture')); fixture.prefs.homeLayout = 'compact';
          localStorage.setItem('fixture', JSON.stringify(fixture));
        });
        await page.reload(); await page.waitForSelector('#input-video source', {state:'attached'});
        await page.keyboard.press('Enter'); await page.locator('[data-action=home]').click();
        assert.equal(await page.locator('.countdown').evaluate(el => parseFloat(getComputedStyle(el).fontSize)), 48, 'theme typography preserves the compact countdown size');
      }
    }
    for (const lang of ['en', 'ar']) {
      await page.evaluate(lang => {
        const fixture = JSON.parse(localStorage.getItem('fixture'));
        fixture.prefs = window.Miqat.prefs({...fixture.prefs, lang, background:'plain'});
        localStorage.setItem('fixture', JSON.stringify(fixture));
      }, lang);
      await page.reload(); await page.waitForSelector('#input-video source', {state:'attached'});
      await page.keyboard.press('Enter');
      await page.locator('[data-action=settings]').click();
      await page.locator('[data-action=section][data-value=appearance]').click();
      const scenes = await page.evaluate(() => window.MiqatData.scenes);
      const choice = page.locator('[data-action=carousel][data-value=background]');
      const forward = lang === 'ar' ? 'ArrowLeft' : 'ArrowRight', back = lang === 'ar' ? 'ArrowRight' : 'ArrowLeft';
      const values = JSON.parse(await choice.getAttribute('data-ui-values'));
      assert.equal(values[0],'miqat_signature','Signature is the first background');
      for (const scene of scenes) {
        await choice.focus(); for(let i=0;i<values.length;i++) await page.keyboard.press(back);
        for(let i=0;i<values.indexOf(scene);i++) await page.keyboard.press(forward);
        const image = await page.locator('.appearance-model svg').evaluate(el => getComputedStyle(el).backgroundImage);
        assert.ok(image.includes('backgrounds/' + scene + '.jpg'), 'scenic selection follows its ID: ' + lang + ' ' + scene);
        assert.ok(await page.evaluate(id => new Promise(resolve => {
          const image = new Image(); image.onload = () => resolve(image.naturalWidth === 1920 && image.naturalHeight === 1080);
          image.onerror = () => resolve(false); image.src = 'backgrounds/' + id + '.jpg';
        }), scene), 'full landscape decodes');
      }
      assert.equal(await page.evaluate(() => JSON.parse(localStorage.getItem('fixture')).prefs.background), scenes.at(-1));
      await page.locator('[data-action=home]').click();
      assert.ok((await page.locator('.home').evaluate(el => getComputedStyle(el).backgroundImage)).includes('backgrounds/' + scenes.at(-1) + '.jpg'));
      await page.reload(); await page.waitForSelector('#input-video source', {state:'attached'}); await page.keyboard.press('Enter');
      await page.locator('[data-action=home]').click();
      assert.ok((await page.locator('.home').evaluate(el => getComputedStyle(el).backgroundImage)).includes('backgrounds/' + scenes.at(-1) + '.jpg'), 'saved landscape survives re-entry');
      assert.equal(await page.locator('.home-prayers .prayer.next').evaluate(el => getComputedStyle(el).backgroundColor.split(',').at(-1).trim()), '0.75)', 'next-prayer fill stays translucent');
      assert.equal(await page.locator('.home-prayers .prayer.next').evaluate(el=>getComputedStyle(el).opacity), '1', 'label and outline retain full opacity');
    }
    for (const lang of ['en','ar']) {
      await page.evaluate(lang => { const f=JSON.parse(localStorage.getItem('fixture')); f.prefs=window.Miqat.prefs({...f.prefs,lang,theme:'mihrab',homeLayout:'mihrab',mihrabOpacity:85});localStorage.setItem('fixture',JSON.stringify(f)); },lang);
      await page.reload();await page.waitForSelector('#input-video source',{state:'attached'});await page.keyboard.press('Enter');
      await page.locator('[data-action=settings]').click();await page.locator('[data-action=section][data-value=appearance]').click();
      const opacity=page.locator('[data-ui="appearance.mihrab-opacity"]');
      const next=lang==='ar'?'ArrowLeft':'ArrowRight',previous=lang==='ar'?'ArrowRight':'ArrowLeft';
      const alpha=()=>page.locator('.appearance-model svg > path').first().getAttribute('fill-opacity');
      assert.equal(await alpha(),'0.85');
      await opacity.focus();await page.keyboard.press('Enter');for(let i=0;i<20;i++)await page.keyboard.press(previous);
      assert.equal(await alpha(),'0.5','draft reaches and clamps at 50%');
      assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('fixture')).prefs.mihrabOpacity),50,'numeric adjustment saves immediately');
      await page.keyboard.press('Escape');assert.equal(await alpha(),'0.5','Back preserves the saved number');
      await opacity.focus();await page.keyboard.press('Enter');for(let i=0;i<20;i++)await page.keyboard.press(next);await page.keyboard.press('Enter');
      assert.equal(await alpha(),'1','100% is fully solid');
      assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('fixture')).prefs.mihrabOpacity),100,'no Apply is needed');
      assert.equal(await page.locator('[data-action=apply-appearance],[data-action=cancel-appearance]').count(),0,'immediate appearance has no Apply or Cancel');
      await opacity.focus();await page.keyboard.press('Enter');for(let i=0;i<20;i++)await page.keyboard.press(previous);await page.keyboard.press('Enter');
      assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('fixture')).prefs.mihrabOpacity),50);
      await page.locator('[data-action=home]').click();assert.equal(await page.locator('.arch-shape .outer').evaluate(el=>getComputedStyle(el).fillOpacity),'0.5');
      assert.equal(await page.locator('.arch-shape .outer').evaluate(el=>getComputedStyle(el).strokeOpacity),'1');
      await page.reload();await page.waitForSelector('#input-video source',{state:'attached'});await page.keyboard.press('Enter');await page.locator('[data-action=home]').click();
      assert.equal(await page.locator('.arch-shape .outer').evaluate(el=>getComputedStyle(el).fillOpacity),'0.5','saved fill survives re-entry');
      await page.locator('[data-action=prayer-day]').click();
      const paths=await page.locator('.day-row .prayer-icon path').evaluateAll(nodes=>nodes.map(el=>el.getAttribute('d')));
      assert.deepEqual(paths,await page.evaluate(()=>window.MiqatData.prayerIcons.prayers.map(p=>p.path)),'all six actual prayer labels use canonical Miqat geometry');
      const dayRows = await page.evaluate(() => {
        const now = new Date(), prefs = window.Miqat.prefs(JSON.parse(localStorage.getItem('fixture')).prefs);
        const day = window.Miqat.day(now, prefs), selection = window.Miqat.select(day, now);
        return [...document.querySelectorAll('.day-row')].map((row, i) => {
          const expected = i === selection.next && day.times[i] === selection.nextTime
            ? 'next' : i === selection.current ? 'current' : day.times[i] < +now ? 'past' : 'up';
          const style = getComputedStyle(row);
          return {expected, actual:[...row.classList].filter(c => c !== 'day-row'),
            fill:style.backgroundColor, text:style.color, border:style.borderTopColor,
            outlined:parseFloat(style.borderTopWidth) > 0 && style.borderTopStyle !== 'none'};
        });
      });
      assert.equal(dayRows.length, 6, 'Today keeps all six civil-day times');
      for (const row of dayRows) {
        assert.deepEqual(row.actual, [row.expected], 'Today row follows the existing prayer selection/date');
        if (row.expected === 'next') assert.ok(row.outlined && row.border !== row.fill,
          'Today next prayer has a visible outline: ' + JSON.stringify(row));
      }
      const currentRow = dayRows.find(row => row.expected === 'current');
      const otherRow = dayRows.find(row => row.expected === 'up' || row.expected === 'past');
      if (currentRow && otherRow) assert.equal(currentRow.fill, otherRow.fill,
        'ordinary prayer rows share a surface; next keeps the highlight');

    }
    assert.deepEqual(errors, []);
    console.log('PASS: Arabic/English controls, grouped settings, offsets and cancel, prayer bells, overlays preserving HDMI, audio stop/end/error recovery, radio, theme/layout, persistence and global sizing. Decoder and Luna boundaries are mocked; actual LG tests remain separate.');
    if(capture)console.log('Preview screenshots: ' + shots);
  } catch(error) {
    if(page&&!page.isClosed()) {
      if(capture)await page.screenshot({path:path.join(shots,'browser-failure.png')});
      if(capture)fs.writeFileSync(path.join(shots,'browser-failure.json'),JSON.stringify(await page.evaluate(()=>({body:document.body.innerText,fixture:localStorage.getItem('fixture'),companion:window.companionState,calls:window.companionCalls?.slice(-8)})),null,2));
    }
    throw error;
  } finally { if (browser) await browser.close(); server.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
