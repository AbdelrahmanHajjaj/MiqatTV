import {spawnSync} from 'node:child_process';
import {mkdirSync,readdirSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {join} from 'node:path';

const root=fileURLToPath(new URL('../../',import.meta.url));
const output=join(root,'companion/android-shared/build/check');
mkdirSync(output,{recursive:true});
const source=join(root,'companion/android-shared/src/main/java/tv/miqat/companion/lan');
const tests=join(root,'companion/android-shared/src/test/java/tv/miqat/companion/lan');
const run=(name,args)=>{
  const result=spawnSync(name,args,{cwd:root,encoding:'utf8'});
  if(result.error)throw result.error;
  process.stdout.write(result.stdout);process.stderr.write(result.stderr);
  if(result.status!==0)throw new Error(name+' failed');
};
run('javac',['-encoding','UTF-8','-d',output,...['Pairing','Http','Endpoint','PairingQr'].map(name=>join(source,name+'.java')),...readdirSync(tests).map(name=>join(tests,name))]);
for(const name of ['PairingCheck','HttpCheck','EndpointCheck','PairingQrCheck'])run('java',['-cp',output,'tv.miqat.companion.lan.'+name]);
