const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict');
const {chromium}=require('playwright');
const base=path.resolve(__dirname,'../dist'),shots=path.resolve(__dirname,'../../state/companion/preview');fs.mkdirSync(shots,{recursive:true});
const server=http.createServer((request,response)=>{
  let route=new URL(request.url,'http://localhost').pathname.replace(/^\/MiqatTV/,'');if(route.endsWith('/'))route+='index.html';
  const file=path.resolve(base,'.'+route);
  if(!file.startsWith(base+path.sep)||!fs.existsSync(file)){response.writeHead(404);response.end();return;}
  response.setHeader('Content-Type',({'.js':'text/javascript','.html':'text/html','.css':'text/css','.svg':'image/svg+xml','.ttf':'font/ttf','.woff2':'font/woff2'})[path.extname(file)]||'application/octet-stream');response.end(fs.readFileSync(file));
});
(async()=>{
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));let browser,page;
  try{
    browser=await chromium.launch({channel:process.env.PLAYWRIGHT_CHANNEL||'msedge',headless:true});
    for(const language of ['ar','en'])for(const size of [80,100,150]){
      page=await browser.newPage({viewport:{width:1600,height:1200}});const errors=[],requests=[];
      page.on('pageerror',error=>errors.push(error.message));page.on('request',request=>{if(['fetch','xhr'].includes(request.resourceType()))requests.push(request.url());});
      await page.addInitScript(size=>localStorage.setItem('miqat-prefs',JSON.stringify({uiScale:size,extra:'preserve'})),size);
      await page.goto('http://127.0.0.1:'+server.address().port+'/MiqatTV/'+(language==='en'?'en/':'')+'settings/');
      await page.locator('[data-nav="6"]').click();const entry=page.locator('[data-ui="device.companion"]');await entry.waitFor();
      const before=await page.evaluate(()=>localStorage.getItem('miqat-prefs'));const seen=new Set(['device.companion']);
      const collect=async()=>{for(const id of await page.locator('[data-pairing]:not([hidden]) [data-ui]').evaluateAll(items=>items.map(el=>{if(el.dataset.uiKind!=='action')throw Error('Wrong pairing binding');return el.dataset.ui;})))seen.add(id);};
      await entry.click();await page.locator('[data-pairing]:not([hidden])').waitFor();await collect();
      assert.match(await page.locator('[data-pairing]').innerText(),language==='ar'?/عرض تجريبي/:/Browser demo/);
      await page.locator('[data-pair-action=open]').click();await page.locator('.pairing-demo-qr svg').waitFor();await collect();
      await page.locator('[data-pair-action=request]').click();assert.equal(await page.locator(':focus').getAttribute('data-pair-action'),'reject');await collect();
      assert.ok(await page.locator('[data-pairing]').evaluate(el=>{const panel=el.getBoundingClientRect(),stage=el.closest('.tv'),frame=stage.getBoundingClientRect();return Math.abs(panel.x-frame.x)<1&&Math.abs(panel.y-frame.y)<1&&Math.abs(panel.width-frame.width)<1&&Math.abs(panel.height-frame.height)<1&&stage.scrollTop===0&&stage.scrollLeft===0;}),'modal fills the fixed TV viewport without focus-induced stage scrolling');
      assert.ok(await page.locator(':focus').evaluate(el=>{const box=el.getBoundingClientRect(),panel=el.closest('[data-pairing]').getBoundingClientRect(),style=getComputedStyle(el);const stage=el.closest('.tv'),scale=stage.getBoundingClientRect().width/1920*Number(stage.dataset.uiSize)/100,ring=(parseFloat(style.outlineWidth)+parseFloat(style.outlineOffset))*scale;return box.left-ring>=panel.left&&box.right+ring<=panel.right&&box.top-ring>=panel.top&&box.bottom+ring<=panel.bottom;}),'complete focused control and ring remain visible');
      await page.locator('.tv-frame').screenshot({path:path.join(shots,'pairing-'+language+'-'+size+'.png')});
      await page.locator('[data-pair-action=reject]').click();assert.equal(await page.locator('.pairing-demo-code').count(),0);
      await page.locator('[data-pair-action=open]').click();await page.locator('[data-pair-action=request]').click();await page.keyboard.press('ArrowDown');
      assert.equal(await page.locator(':focus').getAttribute('data-pair-action'),'approve');await page.keyboard.press('Enter');
      assert.equal(await page.locator('[data-pair-action=revoke]').count(),0);await page.locator('[data-pair-action=complete]').click();await collect();
      await page.locator('[data-pair-action=revoke]').click();assert.equal(await page.locator('[data-pair-action=revoke]').count(),0);
      await page.locator('[data-pair-action=open]').click();await page.keyboard.press('Escape');await page.locator('[data-pairing][hidden]').waitFor({state:'attached'});
      assert.equal(await page.locator(':focus').getAttribute('data-ui'),'device.companion');
      await entry.click();await page.locator('[data-pair-action=open]').click();
      await page.evaluate(()=>{Object.defineProperty(document,'hidden',{configurable:true,value:true});document.dispatchEvent(new Event('visibilitychange'));});
      assert.equal(await page.locator('.pairing-demo-qr').count(),0);assert.equal(await page.evaluate(()=>localStorage.getItem('miqat-prefs')),before);
      assert.deepEqual([...seen].sort(),['device.companion','pairing.open','pairing.cancel','pairing.approve','pairing.reject','pairing.revoke'].sort());
      assert.deepEqual(errors,[]);assert.deepEqual(requests,[]);await page.close();console.log('PASS: actual preview pairing DOM '+language+' '+size+'%, pointer/keys, approval/revoke/cancel, focus, hidden cleanup, unchanged preferences and no network');
    }
    for(const language of ['ar','en'])for(const size of [80,100,150]){
      page=await browser.newPage({viewport:{width:1600,height:1200}});
      await page.addInitScript(size=>localStorage.setItem('miqat-prefs',JSON.stringify({uiScale:size})),size);
      for(const route of ['','apps/','prayer-day/','settings/','call/','friday/','ramadan/','reminder/','screensaver/']){
        const errors=[];const onError=error=>errors.push(error.message);page.on('pageerror',onError);
        await page.goto('http://127.0.0.1:'+server.address().port+'/MiqatTV/'+(language==='en'?'en/':'')+route);
        await page.locator('.tv').waitFor();
        const checked=await page.evaluate(()=>{
          const stage=document.querySelector('.tv');let count=0;
          for(const control of stage.querySelectorAll('button,a[href],input,select,[tabindex]')){
            if(!control.getClientRects().length||control.disabled)continue;
            control.focus();count++;
            if(stage.scrollTop!==0||stage.scrollLeft!==0)throw Error('Focus scrolled fixed viewport');
          }
          return count;
        });
        if(['','apps/','settings/','prayer-day/'].includes(route))assert.ok(checked>0,'interactive route has controls');
        assert.deepEqual(errors,[]);page.off('pageerror',onError);
      }
      await page.close();console.log('PASS: fixed viewport focus across nine preview screens '+language+' '+size+'%');
    }
  }catch(error){if(page&&!page.isClosed()){await page.screenshot({path:path.join(shots,'failure.png')});fs.writeFileSync(path.join(shots,'failure.txt'),await page.locator('body').innerText());}throw error;}
  finally{if(browser)await browser.close();server.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
