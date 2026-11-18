// Local native LG endpoint + explicitly selected owned Android emulator. No TV/camera/remote evidence.
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {execFileSync,spawn}=require('node:child_process');
const server=require('../platforms/webos/service/companion-server');
const {Pairing}=require('../platforms/webos/service/companion-pairing');
const identities=require('../platforms/webos/build/service/companion-identity');
const adapter=require('../platforms/webos/build/service/companion-settings'),core=require('../platforms/webos/build/service/core');
const config=require('./protocol/lan.json'),phone=process.argv[2]||'127.0.0.1:5581',adbPort=Number(process.argv[3]||15037);
const historical=process.argv[4];
assert.match(phone,/^(emulator-[0-9]+|127\.0\.0\.1:[0-9]+)$/);assert.ok(Number.isInteger(adbPort)&&adbPort>=1024&&adbPort<=65535);
const sdk=process.env.ANDROID_HOME||path.join(process.env.LOCALAPPDATA||'', 'Android/Sdk');
const adb=path.join(sdk,'platform-tools',process.platform==='win32'?'adb.exe':'adb');
const options={encoding:'utf8',windowsHide:true,stdio:'pipe',timeout:15000,maxBuffer:524288};
function command(...args){return execFileSync(adb,['-P',String(adbPort),'-s',phone,...args],options);}
function marker(name){try{command('shell','run-as','tv.miqat.companion','test','-f','files/'+name);return true;}catch(error){if(error.status===1)return false;throw error;}}
const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
const now=()=>{const t=process.hrtime();return t[0]*1000+t[1]/1e6;};
(async()=>{
  if(phone.startsWith('127.0.0.1:'))execFileSync(adb,['-P',String(adbPort),'connect',phone],options);
  assert.equal(command('shell','getprop','ro.kernel.qemu').trim(),'1','This test refuses physical devices');
  for(const name of ['paired-tvs.bin','paired-tvs.bin.new','paired-tvs.bin.bak','companion-live-qr.json','companion-ui-offline-ready','companion-ui-offline-go'])assert.equal(marker(name),false,'Fresh emulator fixture required: '+name);
  const evidence=path.resolve(__dirname,'../state/companion');fs.mkdirSync(evidence,{recursive:true});
  const directory=fs.mkdtempSync(path.join(evidence,'lg-phone-')),qrFile=path.join(directory,'qr.json');
  let listener,legacy,legacyStats,child,closed=false,output='',stderr='',cut=false,writes=0,reads=0,saved=[];
  const state={prefs:core.prefs({lang:'en',volume:25}),input:'HDMI_3',startup:{enabled:false,launches:7},audioVolume:{fixture:'preserve'}};
  const outer=JSON.stringify({input:state.input,startup:state.startup,audioVolume:state.audioVolume});
  const pairing=new Pairing({read:()=>saved,write(value){saved=value;}},config);
  const identity=await new Promise((resolve,reject)=>identities.load({read:()=>null,write(){}},(error,value)=>error?reject(error):resolve(value)));
  const settings={
    info(done){done(null,{protocol:1,profile:'lg',name:'LG local integration fixture',version:'test',systemLanguage:''});},
    read(done){reads++;done(null,adapter.snapshot(state));},
    apply(expected,changes,done){
      const edit=adapter.patch(state,expected,changes);
      if(!edit.conflicts.length){assert.equal(edit.startup,null,'This fixture does not simulate OS operations');state.prefs=edit.prefs;writes++;}
      done(null,{...adapter.snapshot(state),status:edit.conflicts.length?'conflict':'saved',conflicts:edit.conflicts});
    }
  };
  try {
    let qr;
    if(historical){
      legacy=require('./probe-legacy-lg.cjs').startLegacy(historical,true);
      qr=(await legacy.wait('ready')).qr;
    }else{
      await new Promise((resolve,reject)=>{listener=server.listen(identity,pairing,settings,()=>{
        const pending=pairing.state(now()).pending;
        if(pending&&pending.name==='Companion integration fixture'&&!pairing.state(now()).approved)pairing.approve(pending.nonce,now());
      },'127.0.0.1',config,error=>error?reject(error):resolve(),true);});
      qr={type:'miqat-tv-pair',version:config.qrVersion,pin:identity.pin,token:pairing.open(now())};
    }
    qr.url='https://10.0.2.2:'+config.port;
    fs.writeFileSync(qrFile,require('./qr').encode(qr),{flag:'wx',mode:0o600});
    command('push',qrFile,'/data/local/tmp/companion-live-qr.json');
    command('shell','run-as','tv.miqat.companion','mkdir','-p','files');
    command('shell','run-as','tv.miqat.companion','cp','/data/local/tmp/companion-live-qr.json','files/companion-live-qr.json');
    assert.equal(reads,0);assert.equal(writes,0);
    child=spawn(adb,['-P',String(adbPort),'-s',phone,'shell','am','instrument','-w','-e','suite','live-lg','tv.miqat.companion.test/tv.miqat.companion.CompanionCheck'],{windowsHide:true,stdio:['ignore','pipe','pipe']});
    child.stdout.on('data',chunk=>output+=chunk.toString('utf8'));child.stderr.on('data',chunk=>stderr+=chunk.toString('utf8'));
    let spawnError;child.on('error',error=>{spawnError=error;closed=true;});child.on('close',()=>closed=true);
    const deadline=Date.now()+180000;
    while(!closed&&Date.now()<deadline) {
      if(!cut&&marker('companion-ui-offline-ready')) {
        cut=true;
        if(legacy){legacy.command('cut');legacyStats=(await legacy.wait('cut')).stats;}
        else await new Promise(resolve=>listener.close(resolve));
        command('shell','run-as','tv.miqat.companion','touch','files/companion-ui-offline-go');
      }
      await sleep(250);
    }
    fs.writeFileSync(path.join(evidence,'phone-lg-native.txt'),output+stderr);
    if(spawnError)throw spawnError;assert.equal(closed,true,'Bounded phone test must finish');
    assert.match(output,/PASS: phone UI paired with the native LG TLS/);assert.equal(cut,true,'Actual transport loss must be exercised');
    const actual=historical?legacyStats.state:state;
    assert.equal(actual.prefs.uiScale,110);assert.equal(actual.prefs.volume,30);assert.ok((historical?legacyStats.writes:writes)>=4);
    assert.equal(JSON.stringify({input:actual.input,startup:actual.startup,audioVolume:actual.audioVolume}),outer,'LG patch preservation');
    for(const name of ['paired-tvs.bin','companion-live-qr.json','companion-ui-offline-ready','companion-ui-offline-go'])assert.equal(marker(name),false,'Phone fixture cleans its own state: '+name);
    console.log('PASS: owned Android phone UI with '+(historical?'actual Node 0.10.25 LG modules':'native LG RSA/TLS')+' and actual settings metadata/patches, Apply/Cancel, 5% volume, reconnect without upload, real transport loss and bilingual stale cache. Approval injected; no physical TV/phone/OS acceptance.');
  } finally {
    if(listener)await new Promise(resolve=>listener.close(resolve));
    if(legacy)await legacy.close();
    if(child&&!closed)child.kill();
    try{command('shell','rm','-f','/data/local/tmp/companion-live-qr.json');}finally{if(fs.existsSync(qrFile))fs.unlinkSync(qrFile);fs.rmdirSync(directory);}
  }
})().catch(error=>{console.error(error);process.exitCode=1;});
