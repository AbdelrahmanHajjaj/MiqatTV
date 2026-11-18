const assert=require('node:assert/strict'), fs=require('node:fs'), vm=require('node:vm'), crypto=require('node:crypto');
const wire=require('./service/companion-wire');
const directory='/private', file=directory+'/companion-phones.json', nodes=new Map(), descriptors=new Map();
let serial=1, failure='';
const flags={O_RDONLY:0,O_WRONLY:1,O_CREAT:64,O_EXCL:128,O_DIRECTORY:65536,O_NOFOLLOW:131072};
const node=(type,mode=0o600,bytes=Buffer.alloc(0))=>({type,mode,uid:5901,ino:serial++,dev:1,bytes});
nodes.set(directory,node('directory',0o700));
function error(code){return Object.assign(Error(code),{code});}
function info(value){return {...value,size:value.bytes.length,isFile:()=>value.type==='file',isDirectory:()=>value.type==='directory'};}
const fake={constants:flags,
  lstatSync(path){if(!nodes.has(path))throw error('ENOENT');return info(nodes.get(path));},
  openSync(path,options,mode){
    if(options&flags.O_CREAT){assert.ok(options&flags.O_EXCL);assert.ok(options&flags.O_NOFOLLOW);if(nodes.has(path))throw error('EEXIST');nodes.set(path,node('file',mode));}
    if(!nodes.has(path))throw error('ENOENT');if(nodes.get(path).type==='link')throw error('ELOOP');
    if(failure==='open')throw error('EACCES');const fd=serial++;descriptors.set(fd,path);return fd;
  },
  fstatSync(fd){return info(nodes.get(descriptors.get(fd)));},
  readSync(fd,bytes,offset,length,position){if(failure==='read')throw error('EACCES');const source=nodes.get(descriptors.get(fd)).bytes;const count=Math.min(length,7,source.length-position);source.copy(bytes,offset,position,position+count);return count;},
  writeSync(fd,bytes,offset,length,position){if(failure==='write')throw error('ENOSPC');const target=nodes.get(descriptors.get(fd)),count=Math.min(length,7),next=Buffer.alloc(Math.max(target.bytes.length,position+count));target.bytes.copy(next);bytes.copy(next,position,offset,offset+count);target.bytes=next;return count;},
  fsyncSync(fd){if(failure==='sync'||failure==='directory-sync'&&descriptors.get(fd)===directory)throw error('EIO');},
  closeSync(fd){assert.ok(descriptors.delete(fd));},
  renameSync(from,to){if(failure==='rename')throw error('EACCES');nodes.set(to,nodes.get(from));nodes.delete(from);},
  unlinkSync(path){assert.ok(nodes.delete(path));}
};
const moduleFixture={exports:{}};
vm.runInNewContext(fs.readFileSync(require.resolve('./service/companion-files'),'utf8'),{
  module:moduleFixture,Buffer,process:{getuid:()=>5901},require(name){return name==='fs'?fake:name==='crypto'?crypto:name==='constants'?flags:wire;}
});
const create=moduleFixture.exports, store=create(directory,'companion-phones.json');
assert.equal(store.read(),null);assert.equal(nodes.size,1);
store.write({phones:[]});assert.equal(JSON.stringify(store.read()),'{"phones":[]}');
const old=Buffer.from(nodes.get(file).bytes), base=nodes.get(file);
for(const fail of ['open','read']) {failure=fail;assert.throws(()=>store.read(),/EACCES/);assert.deepEqual(nodes.get(file).bytes,old);}
for(const fail of ['write','sync','rename']) {
  failure=fail;assert.throws(()=>store.write({phones:['new']}));assert.deepEqual(nodes.get(file).bytes,old,'failed atomic write retains previous trust');
  assert.equal(descriptors.size,0);assert.equal(nodes.size,2,'failed writes clean only their own temporary file');
}
failure='';
for(const bad of [node('link'),node('file',0o644),{...node('file'),uid:0},node('directory'),node('file',0o600,Buffer.alloc(65537))]) {
  nodes.set(file,bad);assert.throws(()=>store.read(),/Unsafe/);assert.throws(()=>store.write({phones:[]}));assert.equal(nodes.get(file),bad);
}
nodes.set(file,base);nodes.get(file).bytes=Buffer.from('{"phones":[],"phones":[]}');assert.throws(()=>store.read(),/Duplicate/);nodes.get(file).bytes=old;
const originalDirectory=nodes.get(directory);
for(const bad of [node('link',0o700),node('directory',0o755),{...node('directory',0o700),uid:0}]) {
  nodes.set(directory,bad);assert.throws(()=>create(directory,'companion-phones.json'),/Unsafe/);assert.throws(()=>store.write({phones:[]}));
}
nodes.set(directory,originalDirectory);assert.throws(()=>create(directory,'../settings.json'));
failure='directory-sync';assert.throws(()=>store.write({phones:['uncertain']}),/EIO/);failure='';
assert.equal(JSON.stringify(store.read()),'{"phones":["uncertain"]}','post-rename sync error is uncertain, never a reported success');
assert.equal(descriptors.size,0);
fake.constants=undefined;
const legacyModule={exports:{}};
vm.runInNewContext(fs.readFileSync(require.resolve('./service/companion-files'),'utf8'),{
  module:legacyModule,Buffer,process:{getuid:()=>5901},require(name){return name==='fs'?fake:name==='crypto'?crypto:name==='constants'?flags:wire;}
});
const legacyStore=legacyModule.exports(directory,'companion-phones.json');
legacyStore.write({phones:[]});assert.equal(JSON.stringify(legacyStore.read()),'{"phones":[]}');
assert.equal(descriptors.size,0,'legacy constants and short descriptor reads/writes preserve private atomic IO');
console.log('PASS: LG private owned storage, symlink/permission/size rejection, strict reads, atomic file/directory sync and failed-write preservation. POSIX filesystem is mocked.');
