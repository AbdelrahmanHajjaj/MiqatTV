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
  const loaded=Object.keys(require.cache).filter(name=>name.includes('vendor')&&name.includes('forge'));
  assert.ok(!loaded.some(name=>/[\\/](tls|http|ed25519)\.js$/.test(name)),'only PKI uses Forge; native TLS and HTTP boundaries stay separate');
  console.log('PASS: LG OS-random RSA/SHA256 identity, public certificate/pin, restart, independent TVs, mismatch/corruption and failed-store rejection.');
})().catch(error=>{console.error(error);process.exitCode=1;});
