const assert=require('node:assert/strict');
const crypto=require('node:crypto');
const identity=require('./build/service/companion-identity');
const load=store=>new Promise((resolve,reject)=>identity.load(store,(error,result)=>error?reject(error):resolve(result)));
(async()=>{
  let saved=null,writes=0;
  const store={read:()=>saved,write(value){writes++;saved=value;}};
  const first=await load(store);assert.equal(writes,1);assert.match(first.pin,/^[0-9a-f]{64}$/);
  const cert=new crypto.X509Certificate(Buffer.from(first.certificate,'base64'));
  assert.equal(cert.verify(cert.publicKey),true);assert.equal(cert.checkPrivateKey(crypto.createPrivateKey(first.key)),true);
  assert.equal(cert.ca,false);assert.equal(cert.publicKey.asymmetricKeyDetails.modulusLength,2048);
  assert.equal(first.pin,crypto.createHash('sha256').update(cert.raw).digest('hex'));
  assert.ok(cert.keyUsage.includes('1.3.6.1.5.5.7.3.1'));
  assert.equal((await load(store)).pin,first.pin);assert.equal(writes,1,'reconnect never regenerates identity');
  const second=await load({read:()=>null,write(){}});assert.notEqual(second.pin,first.pin);
  assert.throws(()=>identity.checked({...saved,key:second.key}),/mismatch/);
  for(const damaged of [{...saved,cert:saved.cert.replace(/MI/,'XX')},{...saved,key:'broken'},{},[],{...saved,extra:true}]) {
    await assert.rejects(load({read:()=>damaged,write(){throw Error('must not overwrite damaged trust');}}));
  }
  await assert.rejects(load({read(){throw Error('EACCES');},write(){throw Error('must not reset');}}),/EACCES/);
  await assert.rejects(load({read:()=>null,write(){throw Error('EACCES');}}),/EACCES/);
  const vm=require('node:vm'),fs=require('node:fs'),path=require('node:path');
  const source=fs.readFileSync(path.join(__dirname,'build/service/companion-identity.js'),'utf8');
  let legacyCalls=0;
  function legacy(exec){
    const module={exports:{}};
    vm.runInNewContext(source,{module,Buffer,require(name){
      if(name==='crypto')return {...crypto,generateKeyPair:undefined};
      if(name==='child_process')return {execFile(file,args,options,done){legacyCalls++;assert.equal(file,'/usr/bin/openssl');assert.equal(args.join(' '),'genrsa 2048');assert.equal(options.timeout,30000);assert.equal(options.maxBuffer,8192);exec(done);}};
      return require(name.startsWith('./')?path.join(__dirname,'build/service',name):name);
    }});return module.exports;
  }
  let nativeSaved=null;
  const native=legacy(done=>done(null,crypto.createPrivateKey(first.key).export({format:'pem',type:'pkcs1'})));
  const nativeLoad=store=>new Promise((resolve,reject)=>native.load(store,(error,value)=>error?reject(error):resolve(value)));
  const nativeIdentity=await nativeLoad({read:()=>null,write(value){nativeSaved=value;}});
  assert.ok(new crypto.X509Certificate(Buffer.from(nativeIdentity.certificate,'base64')).checkPrivateKey(crypto.createPrivateKey(nativeSaved.key)));
  await nativeLoad({read:()=>nativeSaved,write(){throw Error('Existing trust must remain unchanged');}});assert.equal(legacyCalls,1);
  for(const fault of [done=>done(Object.assign(new Error('timeout'),{killed:true})),done=>done(null,'invalid key')]){
    const broken=legacy(fault);
    await assert.rejects(new Promise((resolve,reject)=>broken.load({read:()=>null,write(){throw Error('Failed generation must not write');}},(error,value)=>error?reject(error):resolve(value))));
  }
  const fallback=legacy(done=>done(Object.assign(new Error('missing'),{code:'ENOENT'})));
  const fallbackIdentity=await new Promise((resolve,reject)=>fallback.load({read:()=>null,write(){}},(error,value)=>error?reject(error):resolve(value)));
  assert.match(fallbackIdentity.pin,/^[0-9a-f]{64}$/);
  const loaded=Object.keys(require.cache).filter(name=>name.includes('vendor')&&name.includes('forge'));
  assert.ok(!loaded.some(name=>/[\\/](tls|http|ed25519)\.js$/.test(name)),'only PKI uses Forge; native TLS and HTTP boundaries stay separate');
  console.log('PASS: LG OS-random RSA/SHA256 identity, public certificate/pin, restart, independent TVs, mismatch/corruption and failed-store rejection.');
})().catch(error=>{console.error(error);process.exitCode=1;});
