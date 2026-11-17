// Local historical-runtime validation. Never contacts a TV or reads developer credentials.
const assert=require('node:assert/strict'),path=require('node:path'),crypto=require('node:crypto'),tls=require('node:tls');
const {spawn,execFileSync}=require('node:child_process');
function startLegacy(executable,phone=false) {
  assert.equal(execFileSync(executable,['--version'],{encoding:'utf8',windowsHide:true}).trim(),'v0.10.25');
  const child=spawn(executable,[path.join(__dirname,'legacy-lg-host.cjs'),...(phone?['--phone']:[])],{windowsHide:true,stdio:['pipe','pipe','pipe']});
  let text='',diagnostic='',failure;
  const queued=new Map(),waiting=new Map();
  function fail(error){failure=error;for(const queue of waiting.values())for(const entry of queue)entry.reject(error);waiting.clear();}
  child.on('error',fail);child.on('exit',code=>fail(Error('Historical fixture exited: '+code+' '+diagnostic)));
  child.stderr.on('data',chunk=>diagnostic=(diagnostic+chunk).slice(-2000));
  child.stdout.on('data',chunk=>{
    text+=chunk.toString('utf8');let split;
    while((split=text.indexOf('\n'))>=0){const line=text.slice(0,split);text=text.slice(split+1);let value;
      try{value=JSON.parse(line);}catch(error){fail(error);return;}
      if(value.event==='failure'){fail(Error(value.message));return;}
      const queue=waiting.get(value.event);if(queue&&queue.length)queue.shift().resolve(value);else{if(!queued.has(value.event))queued.set(value.event,[]);queued.get(value.event).push(value);}
    }
  });
  function wait(event){
    const values=queued.get(event);if(values&&values.length)return Promise.resolve(values.shift());
    if(failure)return Promise.reject(failure);
    return new Promise((resolve,reject)=>{
      const timer=setTimeout(()=>reject(Error('Historical fixture timed out: '+event)),60000);
      if(!waiting.has(event))waiting.set(event,[]);
      waiting.get(event).push({resolve:value=>{clearTimeout(timer);resolve(value);},reject:error=>{clearTimeout(timer);reject(error);}});
    });
  }
  return {child,wait,command(action,extra={}){child.stdin.write(JSON.stringify({action,...extra})+'\n');},async close(){
    if(child.exitCode!==null||child.signalCode!==null)return;
    const stopped=wait('stopped');child.stdin.write('{"action":"stop"}\n');
    try{await stopped;}finally{if(child.exitCode===null)child.kill();}
  }};
}
async function main(executable){
  if(!executable)throw Error('Pass an already downloaded Node 0.10.25 executable. Build LG first.');
  const host=startLegacy(executable);
  try{
    const {qr,certificate:encoded}=await host.wait('ready'),url=new URL(qr.url);
    const certificate=Buffer.from(encoded,'base64'),ca='-----BEGIN CERTIFICATE-----\n'+encoded.match(/.{1,64}/g).join('\n')+'\n-----END CERTIFICATE-----\n';
    assert.equal(crypto.createHash('sha256').update(certificate).digest('hex'),qr.pin);
    function raw(text,overrides={}){return new Promise((resolve,reject)=>{
      const socket=tls.connect({host:url.hostname,port:Number(url.port),minVersion:'TLSv1.2',maxVersion:'TLSv1.2',ciphers:'AES128-GCM-SHA256',ca,rejectUnauthorized:true,
        checkServerIdentity:(_name,peer)=>crypto.createHash('sha256').update(peer.raw).digest('hex')===qr.pin?undefined:Error('Wrong TV pin'),...overrides});
      let reply='',cipher,protocol;socket.setTimeout(4000,()=>socket.destroy(Error('Probe timeout')));socket.on('error',reject);
      socket.on('secureConnect',()=>{cipher=socket.getCipher().name;protocol=socket.getProtocol();socket.write(text);});socket.on('data',chunk=>reply+=chunk);
      socket.on('end',()=>{try{assert.ok(reply.includes('\r\n\r\n'));resolve({status:Number(reply.split(' ')[1]),body:JSON.parse(reply.split('\r\n\r\n')[1]),cipher,protocol});}catch(error){reject(error);}});
    });}
    function call(method,route,body,credential){const text=body===undefined?'':JSON.stringify(body);return raw(method+' '+route+' HTTP/1.1\r\nHost: local\r\n'+(credential?'Authorization: Bearer '+credential+'\r\n':'')+(body===undefined?'':'Content-Type: application/json\r\nContent-Length: '+Buffer.byteLength(text)+'\r\n')+'\r\n'+text);}
    const nonce=crypto.randomBytes(32).toString('base64url');
    assert.equal((await call('GET','/v1/settings')).status,403);
    assert.equal((await call('POST','/v1/pair',{token:qr.token,nonce,name:'هاتف الاختبار'})).body.state,'pending');
    assert.equal((await call('POST','/v1/pair-status',{token:qr.token,nonce})).body.state,'pending');
    host.command('approve',{nonce});await host.wait('approved');
    const grant=(await call('POST','/v1/pair-status',{token:qr.token,nonce})).body,credential=grant.id+'.'+grant.secret;
    assert.equal((await call('GET','/v1/settings',undefined,credential)).status,403);
    assert.equal((await call('POST','/v1/pair-complete',{},credential)).body.state,'paired');
    const info=await call('GET','/v1/info',undefined,credential);assert.equal(info.protocol,'TLSv1.2');assert.equal(info.cipher,'AES128-GCM-SHA256');
    const before=(await call('GET','/v1/settings',undefined,credential)).body;
    assert.equal((await call('POST','/v1/settings',{expected:before.values,changes:{'reminders.volume':'30'}},credential)).body.status,'saved');
    assert.equal((await call('POST','/v1/settings',{expected:before.values,changes:{'reminders.volume':'35'}},credential)).body.status,'conflict');
    const get='GET /v1/info HTTP/1.1\r\nHost: local\r\n\r\n';
    await assert.rejects(raw(get,{checkServerIdentity:()=>Error('Wrong TV pin')}),/Wrong TV pin/);
    await assert.rejects(raw(get,{ca:[]}),/self.signed|certificate/i);
    await assert.rejects(raw(get,{minVersion:'TLSv1.1',maxVersion:'TLSv1.1'}));
    assert.equal((await raw(get.replace('Host: local','Host: local\r\nOrigin: null'))).status,400);
    host.command('revoke',{id:grant.id});await host.wait('revoked');assert.equal((await call('GET','/v1/settings',undefined,credential)).status,403);
    console.log('PASS: actual Node 0.10.25 shipped modules: OS-random RSA key generation, QR payload, TLS 1.2 RSA/AES-GCM, approval/completion, settings save/conflict, pin/certificate/browser rejection and revocation. Loopback fixture; approval injected.');
  }finally{await host.close();}
}
module.exports={startLegacy};
if(require.main===module)main(process.argv[2]).catch(error=>{console.error(error);process.exitCode=1;});
