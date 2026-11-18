const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {execFileSync} = require('node:child_process');

const readJson = file => JSON.parse(fs.readFileSync(path.join(__dirname, file), 'utf8'));
const version = readJson('package.json').version;
for (const file of ['package-lock.json', 'app/appinfo.json', 'service/package.json']) {
  assert.equal(readJson(file).version, version, file + ' must match the app version');
}
assert.equal(readJson('package-lock.json').packages[''].version, version);
assert.equal(readJson('app/appinfo.json').bgImage, readJson('app/appinfo.json').splashBackground,
  'webOS 1.x/2.x activation background must use the same loading artwork');

const out = path.join(__dirname, 'build');
const sentinels = ['app', 'service'].map(part => path.join(out, part, 'stale-test-output.txt'));
if (fs.existsSync(out)) assert.equal(fs.realpathSync(out), out, 'test output must stay inside the project');
for (const file of sentinels) {
  fs.mkdirSync(path.dirname(file), {recursive: true});
  assert.equal(fs.realpathSync(path.dirname(file)), path.dirname(file));
  fs.writeFileSync(file, 'An earlier build left this file behind.');
}
execFileSync(process.execPath, [path.join(__dirname, 'build.cjs')], {stdio: 'inherit'});
for (const file of sentinels) assert.ok(!fs.existsSync(file), 'stale generated output must not be packaged');
const appinfo = readJson('build/app/appinfo.json');
assert.equal(appinfo.title, 'Miqat');
assert.equal(appinfo.id, 'tv.miqat.webos', 'independent Miqat application identity');
const serviceInfo = readJson('build/service/services.json');
assert.equal(serviceInfo.id, appinfo.id + '.service');
assert.equal(serviceInfo.services[0].name, serviceInfo.id);
assert.equal(readJson('build/service/package.json').name, serviceInfo.id);
for (const [property, source, width, height] of [
  ['splashBackground', '../../shared/design/artwork/splash.png', 1920, 1080],
  ['icon', 'app/icon.png', 80, 80],
  ['largeIcon', 'app/icon-large.png', 130, 130]
]) {
  const png = fs.readFileSync(path.join(out, 'app', appinfo[property]));
  assert.deepEqual(png, fs.readFileSync(path.join(__dirname, source)), 'package the exact ' + property + ' image');
  assert.equal(png.subarray(0, 8).toString('hex'), '89504e470d0a1a0a', property + ' must be PNG');
  assert.equal(png.subarray(12, 16).toString(), 'IHDR');
  assert.equal(png.readUInt32BE(16), width, property + ' width');
  assert.equal(png.readUInt32BE(20), height, property + ' height');
  assert.equal(png[25], 2, property + ' uses opaque RGB');
}
assert.deepEqual(fs.readFileSync(path.join(out, 'service/trust/usertrust-rsa.pem')),
  fs.readFileSync(path.join(__dirname, 'service/trust/usertrust-rsa.pem')), 'package the exact verified public HTTPS root');
const data = require('node:vm').runInNewContext(fs.readFileSync(path.join(out, 'app/data.js'), 'utf8') + ';MiqatData;');
const root = path.resolve(__dirname, '../..');
assert.equal(JSON.stringify(data.prayerIcons), JSON.stringify(JSON.parse(fs.readFileSync(path.join(root,'shared/design/prayer-icons.json'),'utf8'))), 'LG ships exact canonical prayer icons');
for (const theme of data.themes) {
  const preset = theme === 'mihrab' ? {background: data.defaultBackground} : JSON.parse(fs.readFileSync(path.join(root, 'shared/design/assets/themes', theme + '.json')));
  if (data.scenes.includes(preset.background)) assert.equal(data.presetBackgrounds[theme], preset.background, 'scenic theme carries its canonical paired background');
  assert.equal(data.surfaces[theme].name, preset.surface || 'classic', 'theme keeps its canonical surface: ' + theme);
  const css = fs.readFileSync(path.join(out, 'app', theme === 'mihrab' ? 'style.css' : 'theme-' + theme + '.css'), 'utf8');
  assert.ok(!css.includes('{{'), 'all design tokens resolved');
  assert.ok(css.includes('background:' + data.surfaces[theme].fill), 'actual stylesheet consumes its surface fill');
  for (const [key, value] of Object.entries(preset.colors || {})) assert.equal(data.palettes[theme][key], value, 'retain all canonical theme colors');
  for (const pattern of data.patterns) {
    const file = theme + '-' + pattern + '.svg';
    const svg = fs.readFileSync(path.join(out, 'app/patterns', file), 'utf8');
    assert.ok(css.includes('patterns/' + file), 'Home loads its own palette mask');
    assert.ok(svg.includes('stroke="' + data.palettes[theme]['gold-400'] + '"'), 'mask ink follows the selected palette');
    const opacity = Number(/stroke-opacity="([^"]+)"/.exec(svg)[1]);
    assert.ok(opacity >= 0.13 && opacity <= 0.22, 'decoration remains visible without overpowering content');
  }
}
for (const id of data.scenes) {
  const name = id + '.jpg';
  assert.deepEqual(fs.readFileSync(path.join(out, 'app/backgrounds', name)), fs.readFileSync(path.join(root, 'shared/design/assets/backgrounds', name)), 'ship the canonical local landscape');
  const css = fs.readFileSync(path.join(out, 'app/style.css'), 'utf8');
  assert.ok(css.includes('[data-pattern=' + id + '] .home') && css.includes('backgrounds/' + name), 'Home binds the scenic choice');
}
for (const font of ['alexandria_regular' , 'alexandria_medium', 'alexandria_semibold']) {
  assert.deepEqual(fs.readFileSync(path.join(out, 'app/fonts', font + '.ttf')),
    fs.readFileSync(path.join(root, 'shared/design/resources/font', font + '.ttf')), 'package the shared typeface: ' + font);
}
console.log('PASS: rebuild removes stale app/service output.');
