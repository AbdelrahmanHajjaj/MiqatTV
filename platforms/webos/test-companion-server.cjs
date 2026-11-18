const assert=require('node:assert/strict');
const tls=require('node:tls'), crypto=require('node:crypto');
const server=require('./service/companion-server'), {Pairing,token}=require('./service/companion-pairing');
const identities=require('./build/service/companion-identity'), adapter=require('./build/service/companion-settings');
const core=require('./build/service/core'), config={...require('../../companion/protocol/lan.json'),port:0,timeoutMillis:800};
const now=()=>{const t=process.hrtime();return t[0]*1000+t[1]/1e6;};
const identity=()=>new Promise((resolve,reject)=>identities.load({read:()=>null,write(){}},(e,v)=>e?reject(e):resolve(v)));
(async()=>{
  assert.equal(server.supported(),true);
  for(const address of ['10.1.2.3','172.16.0.1','172.31.255.254','192.168.1.5'])assert.equal(server.privateAddress(address),true);
  for(const address of ['127.0.0.1','172.15.0.1','172.32.0.1','192.168.01.1','192.168.1.999','8.8.8.8','localhost','::1'])assert.equal(server.privateAddress(address),false);
  let saved=[], notifications=0, reads=0, writes=0, commands=0;
  const pairing=new Pairing({read:()=>saved,write(value){saved=value;}},config), id=await identity();
  const state={prefs:core.prefs({volume:25})};
  const settings={
    info(done){done(null,{protocol:1,profile:'lg',name:'LG fixture',version:'test',systemLanguage:'ar'});},
    read(done){reads++;done(null,adapter.snapshot(state));},
    remote(done){done(null,{commands:config.commands,inputs:[]});},
    command(id,value,done){assert.ok(config.commands.includes(id));assert.equal(value,'');commands++;done(null,{status:'accepted'});},
    apply(expected,changes,done){const edit=adapter.patch(state,expected,changes);if(!edit.conflicts.length){state.prefs=edit.prefs;writes++;}done(null,{...adapter.snapshot(state),status:edit.conflicts.length?'conflict':'saved',conflicts:edit.conflicts});}
  };
  let listener;
  await new Promise((resolve,reject)=>{listener=server.listen(id,pairing,settings,()=>notifications++,'127.0.0.1',config,e=>e?reject(e):resolve(),true);});
  const options={host:'127.0.0.1',port:listener.port(),ca:id.cert,rejectUnauthorized:true,
    checkServerIdentity(host,cert){return crypto.createHash('sha256').update(cert.raw).digest('hex')===id.pin?undefined:Error('Wrong pin');}};
  function raw(text,extra={}) {
    return new Promise((resolve,reject)=>{
      const socket=tls.connect({...options,...extra},()=>socket.write(text));let reply='';
      const timer=setTimeout(()=>{socket.destroy();reject(Error('Fixture request timed out'));},3000);
      socket.on('data',chunk=>reply+=chunk.toString('utf8'));
      socket.on('error',error=>{clearTimeout(timer);reject(error);});
      socket.on('end',()=>{clearTimeout(timer);const split=reply.indexOf('\r\n\r\n');try{resolve({status:Number(reply.split(' ')[1]),body:JSON.parse(reply.slice(split+4)),protocol:socket.getProtocol()});}catch(e){reject(e);}});
    });
  }
  function call(method,path,body,credential,extra) {
    const text=body===undefined?'':JSON.stringify(body);
    return raw(method+' '+path+' HTTP/1.1\r\nHost: 127.0.0.1\r\n'+(credential?'Authorization: Bearer '+credential+'\r\n':'')+
      (method==='POST'?'Content-Type: application/json\r\nContent-Length: '+Buffer.byteLength(text)+'\r\n':'')+'\r\n'+text,extra);
  }
  try {
    assert.equal((await call('GET','/v1/settings')).status,403);assert.equal(reads,0);
    assert.equal((await call('POST','/v1/command',{id:'device.home',value:''})).status,403);assert.equal(commands,0);
    const window=pairing.open(now()), nonce=token();
    assert.equal((await call('POST','/v1/pair',{token:window,nonce,name:'Home phone'})).body.state,'pending');
    assert.equal((await call('POST','/v1/pair-status',{token:window,nonce})).body.state,'pending');
    assert.equal(saved.length,0);pairing.approve(nonce,now());
    const grant=(await call('POST','/v1/pair-status',{token:window,nonce})).body, credential=grant.id+'.'+grant.secret;
    assert.equal((await call('GET','/v1/settings',undefined,credential)).status,403,'approval alone cannot read settings');
    assert.equal((await call('POST','/v1/pair-complete',{},credential)).body.state,'paired');
    assert.equal(saved.length,1);assert.ok(notifications>=2);
    const info=await call('GET','/v1/info',undefined,credential,{minVersion:'TLSv1.2',maxVersion:'TLSv1.2'});
    assert.equal(info.protocol,'TLSv1.2');assert.equal(info.body.profile,'lg');assert.equal(reads,0);assert.equal(writes,0,'connect never reads/writes a settings snapshot');
    assert.deepEqual((await call('GET','/v1/remote',undefined,credential)).body.commands,config.commands);
    assert.equal((await call('POST','/v1/command',{id:'device.home',value:''},credential)).body.status,'accepted');
    assert.equal((await call('POST','/v1/command',{id:'pairing.approve',value:''},credential)).status,400);assert.equal(commands,1);assert.equal(reads,0);assert.equal(writes,0);
    const before=(await call('GET','/v1/settings',undefined,credential)).body;
    assert.equal(before.values['reminders.volume'],'25');
    assert.equal((await call('POST','/v1/settings',{expected:before.values,changes:{'reminders.volume':'30'}},credential)).body.status,'saved');
    assert.equal(state.prefs.volume,30);
    assert.equal((await call('POST','/v1/settings',{expected:before.values,changes:{'reminders.volume':'35'}},credential)).body.status,'conflict');
    assert.equal(writes,1);
    assert.equal((await call('POST','/v1/settings',{expected:before.values,changes:{'display.size':'85'}},credential)).status,400);
    for(const path of ['/v1/approve','/v1/revoke'])assert.equal((await call('POST',path,{},credential)).status,404,'no LAN administration route');
    const duplicate='{"token":"x","token":"y"}';
    assert.equal((await raw('POST /v1/pair HTTP/1.1\r\nHost: x\r\nContent-Type: application/json\r\nContent-Length: '+duplicate.length+'\r\n\r\n'+duplicate)).status,400);
    assert.equal((await raw('GET /v1/info HTTP/1.1\r\nHost: x\r\nOrigin: null\r\n\r\n')).status,400);
    await assert.rejects(call('GET','/v1/info',undefined,credential,{ca:(await identity()).cert}));
    await assert.rejects(call('GET','/v1/info',undefined,credential,{checkServerIdentity:()=>Error('Wrong pin')}),/Wrong pin/);
    pairing.revoke(grant.id);assert.equal((await call('GET','/v1/settings',undefined,credential)).status,403);
    const start=Date.now();
    await new Promise((resolve,reject)=>{
      const socket=tls.connect(options,()=>socket.write('GET /v1/info HTTP/1.1\r\nHost: '));
      const drip=setInterval(()=>socket.write('x'),60), timer=setTimeout(()=>{socket.destroy();reject(Error('Absolute deadline failed'));},2500);
      socket.on('error',()=>{});socket.on('close',()=>{clearInterval(drip);clearTimeout(timer);resolve();});
    });
    assert.ok(Date.now()-start<1800,'drip traffic cannot extend the absolute deadline');
    await new Promise((resolve,reject)=>{
      const socket=tls.connect(options,()=>listener.close(resolve));socket.on('error',reject);
    });
    await assert.rejects(call('GET','/v1/info'));
    console.log('PASS: LG actual pinned native TLS/1.2, approval/completion/auth/revocation, real settings patches, no connect uploads, bounded framing/deadline and listener shutdown. Local fixture, not a TV.');
  } finally { await new Promise(resolve=>listener.close(resolve)); }
})().catch(error=>{console.error(error);process.exitCode=1;});
