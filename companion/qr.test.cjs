const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const qr=require('./qr');
const vectors=fs.readFileSync(path.join(__dirname,'android-shared/src/test/resources/qr-vectors.tsv'),'utf8').trim().split('\n');
for(const line of vectors){const [url,pin,token,text]=line.trim().split('\t');const value={type:'miqat-tv-pair',version:3,url,pin,token};assert.equal(qr.encode(value),text);assert.deepEqual(qr.decode(text),value);}
for(let i=0;i<256;i++){
  const value={type:'miqat-tv-pair',version:3,url:'https://192.168.50.'+i+':45761',pin:crypto.randomBytes(32).toString('hex'),token:crypto.randomBytes(32).toString('base64url')};
  const text=qr.encode(value);assert.equal(text.length,112);assert(text.startsWith('MIQ3:FL0'));assert.deepEqual(qr.decode(text),value);
}
const sample=qr.decode(vectors[0].split('\t')[3]);const text=qr.encode(sample);
for(let i=0;i<text.length;i++)assert.throws(()=>qr.decode(text.slice(0,i)));
for(const invalid of [text.slice(0,5)+'TF0'+text.slice(8),text.slice(0,11)+'000000'+text.slice(17),text+'0','miq3:'+text.slice(5),'ITV3:'+text.slice(5),'ITV2:'+text.slice(5),text.slice(0,5)+'GGW'+text.slice(8),text.slice(0,-2)+'::',text.slice(0,-1)+'_'])assert.throws(()=>qr.decode(invalid));
for(const url of ['https://192.168.1.1:0','https://256.1.1.1:45761','https://01.1.1.1:45761','https://1.1.1.1:045761','https://host:45761','http://1.1.1.1:45761','https://1.1.1.1:45761/path','https://1.1.1.1:65536'])assert.throws(()=>qr.encode({...sample,url}));
for(const change of [{type:'islamic-tv-pair'},{version:2},{certificate:'x'},{pin:sample.pin.toUpperCase()+'A'},{token:'_'.repeat(43)}])assert.throws(()=>qr.encode({...sample,...change}));
console.log('PASS: compact QR shared vectors, full pin/token round trips and strict framing/alphabet/overflow/endpoint rejection');
