const assert=require('node:assert/strict');
const wire=require('./service/companion-wire');
const parse=text=>wire.json(Buffer.from(text),65536);
const good={string:'هاتف "\\ 📱',empty:{},array:[null,true,false,123,-0.25,1e9],nested:{x:1}};
assert.equal(JSON.stringify(parse(JSON.stringify(good))),JSON.stringify(good));
assert.equal(parse('{"__proto__":{"x":1}}').__proto__.x,1);assert.equal(Object.prototype.x,undefined);
for(const bad of ['', '[]','null','true','42','{}{}','{a:1}','{"a":1,}','{"a":1,"a":2}','{"a":1,"\\u0061":2}',
  '{"a":{"x":0,"x":1}}','{"a":01}','{"a":NaN}','{"a":1e999}','{"a":[1,]}','{"a":"bad\nline"}','{"a":"\\x00"}',
  '{"a":"unterminated}','{"a":'+ '['.repeat(17)+'0'+']'.repeat(17)+'}', '{"a":['+Array(4097).fill(0).join(',')+']}',
  '{'+Array.from({length:4097},(_,i)=>'"k'+i+'":0').join(',')+'}'])assert.throws(()=>parse(bad),undefined,bad.slice(0,80));
for(const bytes of [[0xc0,0x80],[0xed,0xa0,0x80],[0xf4,0x90,0x80,0x80],[0xe2,0x82]]) {
  assert.throws(()=>wire.json(Buffer.concat([Buffer.from('{"x":"'),Buffer.from(bytes),Buffer.from('"}')]),65536),/UTF-8/);
}
assert.throws(()=>wire.json(Buffer.from('{}'),1));
wire.keys(parse('{"a":0}'),['a']);assert.throws(()=>wire.keys(parse('{"a":0}'),[]));assert.throws(()=>wire.string({a:1},'a'));
const get='GET /v1/info HTTP/1.1\r\nHost: 192.168.1.1\r\n\r\n';
assert.equal(wire.request(Buffer.from(get),65536).path,'/v1/info');
const post='POST /v1/pair HTTP/1.0\r\nHost: x\r\nContent-Type: application/json\r\nContent-Length: 2\r\n\r\n{}';
assert.equal(JSON.stringify(wire.json(wire.request(Buffer.from(post),65536).body,65536)),'{}');
for(const complete of [get,post])for(let i=0;i<Buffer.byteLength(complete);i++)assert.equal(wire.request(Buffer.from(complete).subarray(0,i),65536),null,'partial frame waits');
for(const bad of [get+get,get.replace('/v1/info','/v1/info?x=1'),get.replace('GET','PUT'),get.replace('Host: 192.168.1.1\r\n',''),
  get.replace('\r\n\r\n','\r\nOrigin: null\r\n\r\n'),get.replace('\r\n\r\n','\r\nTransfer-Encoding: chunked\r\n\r\n'),
  get.replace('\r\n\r\n','\r\nExpect: 100-continue\r\n\r\n'),get.replace('\r\n\r\n','\r\nhost: duplicate\r\n\r\n'),
  get.replace('\r\n\r\n','\r\n Folded: value\r\n\r\n'),get.replace('Host:','Ho\nst:'),get.replace('Host:','Höst:'),
  get.replace('\r\n\r\n','\r\nContent-Length: 1\r\n\r\nx'),post.replace('Length: 2','Length: 02'),post.replace('Length: 2','Length: 65537'),
  post.replace('Content-Type: application/json\r\n',''),post.replace('Content-Length: 2\r\n',''),post.replace('Host: x','Host: x\nInjected: x'),
  'GET /v1/info HTTP/1.1\r\nHost: '+ 'x'.repeat(8192)])assert.throws(()=>wire.request(Buffer.from(bad),65536));
console.log('PASS: LG bounded strict JSON/UTF-8, duplicate/depth/item rejection and incremental HTTP framing/browser/proxy boundaries.');
