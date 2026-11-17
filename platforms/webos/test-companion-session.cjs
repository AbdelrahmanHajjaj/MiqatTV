const assert=require('node:assert/strict'), fs=require('node:fs'), vm=require('node:vm');
const pairingModule=require('./service/companion-pairing'), config=require('../../companion/protocol/lan.json');
function fixture(supported=true) {
  const handlers={}, cancels={}, activities=[], keys=[], listeners=[], completed=[], files=new Map(), timers=new Map();
  let token=0, storeReads=0, closed=0, writes=0, pendingApply=null;
  const env={activities,keys,listeners,completed,files,timers,get closed(){return closed;},get storeReads(){return storeReads;},get writes(){return writes;},defer:false};
  const service={register(name,request,cancel){handlers[name]=request;cancels[name]=cancel;},activityManager:{
    create(name,callback){assert.equal(name,'tv.miqat.webos.companion-foreground');activities.push(callback);},
    complete(activity,options,done){assert.equal(options.restart,false);completed.push(activity);done();}
  }};
  const io={directory:'/private',info:()=>({profile:'lg'}),snapshot:()=>({values:{volume:String(writes)}}),current:()=>{if(env.failCurrent)throw Error('EACCES');return {prefs:{volume:writes},startup:false};},
    apply(expected,changes,done){writes++;if(env.defer)pendingApply=done;else done(null,{status:'saved',values:{volume:String(writes)}});}};
  const moduleFixture={exports:{}};
  vm.runInNewContext(fs.readFileSync(require.resolve('./service/companion-session'),'utf8'),{
    module:moduleFixture,Buffer,process:{hrtime:()=>[100,0]},
    setTimeout(callback){const id=++token;timers.set(id,callback);return id;},clearTimeout(id){timers.delete(id);},
    require(name){
      if(name==='./companion-server')return {supported:()=>supported,privateAddress:address=>address==='192.168.1.5',
        listen(identity,pairing,settings,changed,host,config,ready){assert.equal(host,'192.168.1.5');const listener={pairing,settings,changed,ready,close(done){closed++;if(done)done();}};listeners.push(listener);return listener;}};
      if(name==='os')return {networkInterfaces:()=>({eth0:[{family:'IPv4',address:'192.168.1.5',internal:false}]})};
      if(name==='./companion-pairing')return pairingModule;
      if(name==='./companion-lan.json')return config;
      if(name==='./companion-files')return (directory,name)=>({read(){storeReads++;return files.has(name)?files.get(name):null;},write(value){files.set(name,JSON.parse(JSON.stringify(value)));}});
      if(name==='./companion-identity')return {load(store,callback){keys.push({store,callback});}};
      assert.fail(name);
    }
  });
  moduleFixture.exports(service,io);
  env.send=(name,payload={},options={})=>{const replies=[];handlers[name]({payload,sender:'tv.miqat.webos',uniqueToken:'request-'+ ++token,...options,respond(reply){replies.push(reply);}});return replies;};
  env.subscribe=(id)=>env.send('companionForeground',{}, {uniqueToken:id,isSubscription:true});
  env.ui=(session,editing=false,pairingPage=true)=>env.send('companionUi',{session,editing,pairingPage});
  env.command=(session,action,extra={},options={})=>env.send('companionPairing',{session,action,...extra},options);
  env.cancel=id=>cancels.companionForeground({uniqueToken:id});
  env.open=id=>{env.ui(id);const reply=env.command(id,'open');assert.equal(keys.length,1);keys.shift().callback(null,{pin:'a'.repeat(64),certificate:'public-fixture'});assert.equal(listeners.length,1);listeners[0].ready(null);assert.equal(reply[0].returnValue,true);return listeners[0];};
  env.finishApply=()=>{pendingApply(null,{status:'saved'});pendingApply=null;};
  return env;
}
const env=fixture();
for(const sender of [undefined,'other.app','tv.miqat.webos.service','tv.miqat.webos.evil'])assert.equal(env.send('companionForeground',{applicationID:'tv.miqat.webos'},{sender,isSubscription:true})[0].returnValue,false);
assert.equal(env.send('companionForeground',{subscribe:true},{isSubscription:false})[0].returnValue,false);
assert.equal(env.storeReads,0);assert.equal(env.activities.length,0,'untrusted calls never initialize storage/activities');
const updates=env.subscribe('foreground-a');env.activities.shift()({activityId:1});assert.equal(env.storeReads,1);assert.equal(updates.at(-1).companion.listening,false);
assert.equal(env.command('foreground-a','open')[0].returnValue,false,'pairing page required');
const listener=env.open('foreground-a');const qr=updates.at(-1).companion.qr;assert.equal(qr.url,'https://192.168.1.5:'+config.port);
assert.equal(env.send('companionLeave',{session:'foreground-a'},{sender:'other.app'})[0].returnValue,false);assert.equal(env.closed,0);
const nonce=pairingModule.token();listener.pairing.request(qr.token,'Home phone',nonce,100000);listener.changed();
assert.equal(updates.at(-1).companion.pending.code,pairingModule.code(qr.token,nonce));
assert.equal(env.command('foreground-a','approve',{nonce},{sender:'other.app'})[0].returnValue,false);
assert.equal(env.command('foreground-a','approve',{nonce:pairingModule.token()})[0].returnValue,false);
assert.equal(env.command('foreground-a','approve',{nonce})[0].returnValue,true);
const grant=listener.pairing.poll(qr.token,nonce,100000);assert.equal(listener.pairing.authenticate(grant.id,grant.secret),false);
listener.pairing.finish(grant.id,grant.secret,100000);listener.changed();assert.equal(updates.at(-1).companion.qr,null);
assert.equal(updates.at(-1).companion.phones[0].name,'Home phone');assert.equal(env.timers.size,0,'completion clears the expiring window');
const second=env.subscribe('foreground-b');let result;
listener.settings.apply({}, {},(error,value)=>result=value);assert.equal(result.status,'busy');assert.equal(env.writes,0);
env.ui('foreground-a',false,false);env.ui('foreground-b',false,false);listener.settings.apply({}, {},(error,value)=>result=value);
assert.equal(result.status,'saved');assert.equal(env.writes,1);assert.equal(second.at(-1).current.prefs.volume,1);
env.defer=true;listener.settings.apply({}, {},()=>{});listener.settings.apply({}, {},(error,value)=>result=value);assert.equal(result.status,'busy');assert.equal(env.writes,2);env.finishApply();
env.defer=false;env.failCurrent=true;listener.settings.apply({}, {},(error,value)=>result=value);assert.equal(result.status,'saved');assert.equal(second.at(-1).currentUnavailable,true,'notification read failure must not crash or reset TV settings');env.failCurrent=false;
env.ui('foreground-b');assert.equal(env.command('foreground-b','revoke',{id:grant.id})[0].returnValue,true);assert.equal(listener.pairing.authenticate(grant.id,grant.secret),false);
env.ui('foreground-a');env.command('foreground-a','open');assert.equal(updates.at(-1).companion.ownsPairing,true);
env.ui('foreground-a',false,false);assert.equal(second.at(-1).companion.open,false,'leaving the owning page cancels the pending window for other sessions');
env.cancel('foreground-b');assert.equal(env.closed,0);env.cancel('foreground-a');assert.equal(env.closed,1);assert.equal(env.completed.length,1);
const legacy=fixture(false);const unsupported=legacy.subscribe('old-tv');assert.equal(unsupported.at(-1).companion.supported,false);assert.equal(legacy.storeReads,0);assert.equal(legacy.activities.length,0);legacy.cancel('old-tv');
const early=fixture();early.subscribe('early');early.cancel('early');early.activities.shift()({activityId:2});assert.equal(early.storeReads,0);assert.equal(early.completed.length,1);
const generating=fixture();generating.subscribe('keygen');generating.activities.shift()({activityId:3});generating.ui('keygen');generating.command('keygen','open');
const pending=generating.keys.shift();generating.cancel('keygen');assert.throws(()=>pending.store.write({}),/inactive/);pending.callback(null,{});assert.equal(generating.listeners.length,0);assert.equal(generating.completed.length,1);
const slow=fixture();const slowUpdates=slow.subscribe('slow-keygen');slow.activities.shift()({activityId:30});slow.ui('slow-keygen');
const acknowledged=slow.command('slow-keygen','open');assert.equal(acknowledged.length,1);assert.equal(acknowledged[0].returnValue,true,'key preparation is acknowledged without waiting for RSA');
assert.equal(slowUpdates.at(-1).companion.starting,true);assert.equal(slowUpdates.at(-1).companion.open,false);
slow.keys.shift().callback(null,{pin:'a'.repeat(64),certificate:'public-fixture'});slow.listeners[0].ready(null);
assert.equal(acknowledged.length,1,'completed preparation uses foreground subscription, not a second RPC reply');
assert.equal(slowUpdates.at(-1).companion.open,true);assert.equal(slowUpdates.at(-1).companion.starting,false);slow.cancel('slow-keygen');
const leaving=fixture();leaving.subscribe('leaving');leaving.activities.shift()({activityId:4});leaving.open('leaving');assert.equal(leaving.send('companionLeave',{session:'leaving'})[0].returnValue,true);assert.equal(leaving.closed,1);leaving.cancel('leaving');assert.equal(leaving.completed.length,1);
const remote=fixture(),remoteUpdates=remote.subscribe('remote');remote.activities.shift()({activityId:40});const remoteListener=remote.open('remote');remote.ui('remote',false,false);
let commandResult;remoteListener.settings.command('device.home','',(_,value)=>commandResult=value);
const sent=remoteUpdates.at(-1).command;assert.equal(sent.id,'device.home');assert.equal(commandResult,undefined,'wait for actual foreground UI acknowledgement');
assert.equal(remote.send('companionResult',{session:'remote',request:sent.request,status:'accepted'},{sender:'other.app'})[0].returnValue,false);
assert.equal(commandResult,undefined);remote.send('companionResult',{session:'remote',request:sent.request,status:'accepted'});assert.equal(commandResult.status,'accepted');
remote.ui('remote',true,false);remoteListener.settings.command('device.home','',(_,value)=>commandResult=value);assert.equal(commandResult.status,'busy','navigation never discards local edits');
remoteListener.settings.command('radio.stop','',(_,value)=>commandResult=value);assert.equal(remoteUpdates.at(-1).command.id,'radio.stop','Stop remains available while media owns the busy state');
const waiting=remoteUpdates.at(-1).command;remoteListener.settings.command('device.home','',(_,value)=>commandResult=value);assert.equal(commandResult.status,'busy');
remote.send('companionResult',{session:'remote',request:waiting.request,status:'accepted'});
remote.ui('remote',false,false);remoteListener.settings.command('device.home','',(_,value)=>commandResult=value);remote.timers.values().next().value();assert.equal(commandResult.status,'uncertain','late/missing acknowledgements are never replayed');
remoteListener.settings.command('device.home','',(_,value)=>commandResult=value);remote.cancel('remote');assert.equal(commandResult.status,'uncertain','foreground loss ends pending commands');
console.log('PASS: LG sender/session/page ownership, approval/revocation, editor/write busy guard, explicit-write notifications, unsupported runtime and foreground/initialization cleanup. Service/TLS lifecycle is mocked.');
