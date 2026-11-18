const fs = require('node:fs');
const assert = require('node:assert/strict');
const path = require('node:path');
const crypto = require('node:crypto');
const {execFileSync} = require('node:child_process');
const {chromium} = require('../platforms/webos/node_modules/playwright');
const qr = require('../platforms/webos/node_modules/qrcode-generator');
const svg = require('../shared/design/qr.js');
const style = require('../shared/design/assets/qr-style.json');
const codec = require('./qr');
const mark = 'data:image/png;base64,' + fs.readFileSync(path.resolve(__dirname, '../shared/design/assets/qr-mark.png')).toString('base64');
const tokens = require('../shared/design/mihrab/tokens.json').color;
const base = Object.fromEntries(Object.entries(tokens).filter(([,v])=>v.$type==='color'&&/^#[0-9a-f]{6}$/i.test(v.$value)).map(([k,v])=>[k,v.$value]));
const themes = require('../shared/design/themes.json').filter(id=>id!=='custom');
for (const unsafe of [undefined, 'https://example.invalid/logo.png', 'data:image/svg+xml,<svg/>'])
  assert.throws(() => svg(null, style, base, base['plaster-100'], unsafe), /Local QR mark required/);
const out = path.resolve(__dirname, '../state/companion/approved-qr');
const jar = process.argv[2];
if (!jar || !fs.existsSync(jar)) throw Error('Pass the cached ZXing core jar as the first argument');
fs.mkdirSync(out, {recursive:true});
(async () => {
  const browser = await chromium.launch({channel:process.env.PLAYWRIGHT_CHANNEL || 'msedge',headless:true});
  try {
    const page = await browser.newPage({viewport:{width:500,height:500},deviceScaleFactor:1});
    for (const theme of themes) {
      const colors=theme==='mihrab'?base:require('../shared/design/assets/themes/'+theme+'.json').colors;
      const directory=path.join(out,theme);fs.mkdirSync(directory,{recursive:true});
    for (let i = 0; i < 8; i++) {
      const hash = value => crypto.createHash('sha256').update(value).digest();
      const payload = codec.encode({type:'miqat-tv-pair',version:3,url:'https://192.168.50.'+(i+1)+':45761',pin:hash('pin'+i).toString('hex'),token:hash('token'+i).toString('base64url')});
      const code = qr(0,style.errorCorrection);code.addData(payload,'Alphanumeric');code.make();
      for (const size of [400,280,220]) {
        const name=i+'-'+size;
        await page.setContent('<body style="margin:0;background:white">'+svg(code,style,colors,base['plaster-100'],mark)+'</body>');
        await page.locator('body > svg').evaluate((el,size)=>{el.style.width=el.style.height=size+'px';},size);
        await page.locator('body > svg').screenshot({path:path.join(directory,name+'.png')});
        fs.writeFileSync(path.join(directory,name+'.txt'),payload);
      }
    }
    }
  } finally {await browser.close();}
  execFileSync('javac',['-cp',jar,'-d',out,path.join(__dirname,'QrStyleCheck.java')],{stdio:'inherit'});
  for(const theme of themes){process.stdout.write(theme+': ');execFileSync('java',['-cp',jar+path.delimiter+out,'QrStyleCheck',path.join(out,theme)],{stdio:'inherit'});}
})().catch(error=>{console.error(error);process.exitCode=1;});
