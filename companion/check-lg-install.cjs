// Local SSH/HTTP fixture for one explicitly owned Android emulator. No real TV.
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),http=require('node:http');
const {createRequire}=require('node:module'),{generateKeyPairSync,createHash,timingSafeEqual}=require('node:crypto');
const {gunzipSync}=require('node:zlib');
const {execFile}=require('node:child_process'),{promisify}=require('node:util');
const exec=promisify(execFile);
const [serial,avd,cliRoot,ipk,adb='adb']=process.argv.slice(2);
if(!/^emulator-\d+$/.test(serial||'')||!avd||!cliRoot||!ipk)throw Error('Usage: node companion/check-lg-install.cjs emulator-SERIAL AVD webos-cli-directory Miqat.ipk [adb]');
const {Server,utils}=createRequire(path.join(path.resolve(cliRoot),'package.json'))('ssh2');
const host=generateKeyPairSync('rsa',{modulusLength:2048}),identity=generateKeyPairSync('rsa',{modulusLength:2048});
const hostKey=host.privateKey.export({type:'pkcs1',format:'pem'});
const privateKey=Buffer.from(identity.privateKey.export({type:'pkcs1',format:'pem',cipher:'aes-128-cbc',passphrase:'miqat-fixture-only'}));
const allowed=utils.parseKey(privateKey,'miqat-fixture-only'),content=fs.readFileSync(ipk),files=new Map(),clients=new Set();
let packageVersion;
for(let offset=8;offset+60<=content.length;){
  const name=content.subarray(offset,offset+16).toString().trim().replace(/\/$/,''),size=Number(content.subarray(offset+48,offset+58).toString().trim());
  assert(Number.isSafeInteger(size)&&size>=0&&offset+60+size<=content.length);
  if(name==='control.tar.gz')packageVersion=gunzipSync(content.subarray(offset+60,offset+60+size)).toString().match(/Version: ([0-9.]+)\n/)?.[1];
  offset+=60+size+(size%2);
}
assert(packageVersion,'Choose an actual Miqat IPK');
let installs=0,launches=0,authenticated=0,keyDownloads=0,version='0.2.0';
const api=http.createServer((req,res)=>{
  if(req.url==='/webos_rsa'){keyDownloads++;res.writeHead(200);res.end(privateKey);}
  else if(req.url==='/fixture.ipk'){res.writeHead(200);res.end(content);}
  else {res.writeHead(404);res.end();}
});
const ssh=new Server({hostKeys:[hostKey]},client=>{
  clients.add(client);client.on('error',()=>{});client.on('close',()=>clients.delete(client));
  client.on('authentication',ctx=>{
    if(ctx.username!=='prisoner'||ctx.method!=='publickey'||ctx.key.data.length!==allowed.getPublicSSH().length||
       !timingSafeEqual(ctx.key.data,allowed.getPublicSSH())||(ctx.signature&&allowed.verify(ctx.blob,ctx.signature,ctx.hashAlgo)!==true))return ctx.reject(['publickey']);
    ctx.accept();
  });
  client.on('ready',()=>{authenticated++;client.on('session',accept=>accept().on('exec',(accept,reject,info)=>{
    const stream=accept(),command=info.command;
    const done=value=>{if(value)stream.write(value);stream.exit(0);stream.end();};
    const json=value=>done(JSON.stringify(value)+'\n');
    if(command==='mkdir -p /media/developer/temp')return done();
    let match=command.match(/^cat > (\/media\/developer\/temp\/miqat-[a-f0-9-]+\.ipk)$/);
    if(match){const chunks=[];let count=0;stream.on('data',b=>{count+=b.length;assert(count<=content.length);chunks.push(b);});stream.on('end',()=>{files.set(match[1],Buffer.concat(chunks));done();});return;}
    match=command.match(/^md5sum (\/media\/developer\/temp\/miqat-[a-f0-9-]+\.ipk)$/);
    if(match){assert.deepEqual(files.get(match[1]),content);return done(createHash('md5').update(content).digest('hex')+'  '+match[1]+'\n');}
    match=command.match(/^rm -f (\/media\/developer\/temp\/miqat-[a-f0-9-]+\.ipk)$/);
    if(match){assert(files.delete(match[1]));return done();}
    if(command.includes('luna://com.webos.applicationManager/dev/listApps'))return json({returnValue:true,apps:[{id:'tv.miqat.webos',version}]});
    if(command.includes('luna://com.webos.appInstallService/dev/install')){
      const payload=JSON.parse(command.slice(command.indexOf(" '{")+2,-1));assert.equal(payload.id,'tv.miqat.webos');assert.deepEqual(files.get(payload.ipkUrl),content);
      installs++;if(installs===1)return json({returnValue:true,details:{state:'FAILED',reason:'fixture refusal'}});
      version=packageVersion;
      stream.write(JSON.stringify({returnValue:true,details:{state:'STARTING'}})+'\n');
      stream.write(JSON.stringify({returnValue:true,details:{state:'INSTALLED'}})+'\n');return;
    }
    if(command.includes('luna://com.webos.applicationManager/launch')){launches++;return json({returnValue:true});}
    throw Error('Unexpected fixture command');
  }));});
});
const listen=(server,port)=>new Promise((resolve,reject)=>{server.once('error',reject);server.listen(port,'127.0.0.1',resolve);});
(async()=>{
  assert.equal((await exec(adb,['-s',serial,'shell','getprop','ro.kernel.qemu'])).stdout.trim(),'1');
  assert.equal((await exec(adb,['-s',serial,'emu','avd','name'])).stdout.split(/\r?\n/)[0].trim(),avd);
  await listen(api,9991);await listen(ssh,9922);
  const result=await exec(adb,['-s',serial,'shell','am','instrument','-r','-w','-e','suite','lg-install','tv.miqat.companion.test/tv.miqat.companion.CompanionCheck'],{timeout:240000,maxBuffer:1024*1024});
  process.stdout.write(result.stdout);assert.match(result.stdout,/INSTRUMENTATION_CODE: -1/);assert.match(result.stdout,/PASS: LG SSH identity/);
  assert.equal(authenticated,3);assert.equal(installs,2);assert.equal(launches,1);assert.equal(files.size,1);
  assert.equal(keyDownloads,5,'the remembered setup reopens without requesting the Developer Mode key again');
  console.log('PASS: local SSH fixture observed only explicit Miqat operations; no arbitrary commands or automatic retry');
})().catch(error=>{console.error(error);process.exitCode=1;}).finally(()=>{for(const c of clients)c.destroy();ssh.close();api.close();api.closeAllConnections();privateKey.fill?.(0);});
