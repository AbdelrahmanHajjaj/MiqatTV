const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const babel = require('@babel/core');
const acorn = require('acorn');
const adhanRoot = path.resolve(path.dirname(require.resolve('adhan')), '../..');
const root = path.resolve(__dirname, '../..');
const out = path.join(__dirname, 'build');
const read = p => fs.readFileSync(path.join(root, p), 'utf8');
const json = p => JSON.parse(read(p));
const methods = json('shared/data/methods.json');
// Keep the old web extractor's compact literal spacing.
const compact = value => Array.isArray(value) ? value.map(text => text.replace(/\s+/g, ' ')) : value.replace(/\s+/g, ' ');
const strings = Object.fromEntries(Object.entries(json('shared/data/strings.json')).map(([key, entry]) => [key, {...entry, ar: compact(entry.ar), en: compact(entry.en)}]));
const write = (p, text) => { fs.mkdirSync(path.dirname(p), { recursive: true }); fs.writeFileSync(p, text); };
execFileSync(process.execPath, [path.join(root, 'tools/validate-shared.mjs')], { stdio: 'inherit' });
execFileSync(process.execPath, [path.join(root, 'tools/prepare-adhans.mjs'), '--check'], { stdio: 'inherit' });
execFileSync(process.execPath, [path.join(root, 'shared/core/build-js.cjs')], { stdio: 'inherit' });
const outputs = ['app', 'service'].map(part => path.join(out, part));
for (const directory of [out, ...outputs]) {
  if (fs.existsSync(directory) && fs.realpathSync(directory) !== directory) throw new Error('Build output must stay inside the project');
}
for (const directory of outputs) fs.rmSync(directory, {recursive: true, force: true});
for (const part of ['app', 'service']) fs.cpSync(path.join(__dirname, part), path.join(out, part), { recursive: true });
write(path.join(out, 'app/splash.png'), fs.readFileSync(path.join(root, 'shared/design/artwork/splash.png')));
execFileSync(process.execPath, [path.join(root, 'companion/generate.mjs'), '--lg-tv'], {stdio: 'inherit'});
write(path.join(out, 'service/companion-lan.json'), read('companion/protocol/lan.json'));
const forgeRoot = path.join(__dirname, 'node_modules/node-forge');
for (const name of fs.readdirSync(path.join(forgeRoot, 'lib')).filter(n => n.endsWith('.js'))) {
  const source = fs.readFileSync(path.join(forgeRoot, 'lib', name), 'utf8');
  const compiled = babel.transformSync(source, {configFile:false, babelrc:false, comments:true,
    presets:[[require.resolve('@babel/preset-env'), {targets:{safari:'7'}, modules:false}]]}).code;
  acorn.parse(compiled, {ecmaVersion:5});
  write(path.join(out, 'service/vendor/forge', name), compiled);
}
write(path.join(out, 'service/licenses/node-forge.txt'), fs.readFileSync(path.join(forgeRoot, 'LICENSE')));
write(path.join(out, 'app/qrcode.js'), fs.readFileSync(require.resolve('qrcode-generator')));
write(path.join(out, 'app/qr-style.js'), read('shared/design/qr.js'));
write(path.join(out, 'app/pairing-qr.js'), read('companion/qr.js'));
  const data = {
    qrStyle: json('shared/design/assets/qr-style.json'),
    qrMark: 'data:image/png;base64,' + fs.readFileSync(path.join(root, 'shared/design/assets/qr-mark.png')).toString('base64'),
    companionCommands: JSON.parse(read('companion/protocol/lan.json')).commands,
  cities: json('shared/data/cities.json'), methods: methods.map(method => method.id),
  presets: Object.fromEntries(methods.filter(method => method.preset).map(method => [method.id, {...method.preset, ...(method.methodAdjustments ? {methodAdjustments: method.methodAdjustments} : {})}])),
  strings,
  formatText: json('shared/data/format-text.json'),
  sacred: json('shared/data/sacred-text.json'),
  stations: json('shared/data/stations.json'), apps: JSON.parse(fs.readFileSync(path.join(__dirname, 'tv-apps.json'), 'utf8')),
  voices: json('shared/data/voices.json').map(({id, ar, en}) => ({id, ar, en})), themes: [], hijri: []
};
for (const voice of data.voices) {
  write(path.join(out, 'app/audio/adhan', voice.id + '.mp3'), fs.readFileSync(path.join(root, 'shared/media/adhan', voice.id + '.mp3')));
}
for (const prayer of ['fajr', 'dhuhr', 'asr', 'maghrib', 'isha', 'jumuah']) {
  for (const kind of ['before', 'now']) {
    const name = kind + '_' + prayer + '.mp3';
    write(path.join(out, 'app/audio/announce', name), fs.readFileSync(path.join(root, 'shared/media/announce', name)));
  }
}
data.themes = json('shared/design/themes.json').filter(id => id !== 'custom');
// ICU is available at build time; ship month boundaries for the TV's pre-Intl runtime.
const calendar = new Intl.DateTimeFormat('en-u-ca-islamic-umalqura-nu-latn', {timeZone: 'UTC', year: 'numeric', month: 'numeric', day: 'numeric'});
for (let at = Date.UTC(1999, 0, 1); at < Date.UTC(2102, 0, 1); at += 86400000) {
  const parts = Object.fromEntries(calendar.formatToParts(new Date(at)).map(p => [p.type, p.value]));
  if (+parts.day === 1) data.hijri.push([at / 86400000, +parts.year, +parts.month]);
}
data.methodNames = Object.fromEntries(methods.map(method => [method.id, {ar: method.ar, en: method.en}]));
data.icons = json('shared/design/icons.json');
data.mihrab = json('shared/design/mihrab.json');
data.prayerIcons = json('shared/design/prayer-icons.json');
const appIds = new Set();
for (const app of data.apps) {
  if (!/^[a-z0-9][a-z0-9._-]{0,127}$/.test(app.id) || appIds.has(app.id) || !app.ar || !app.en || !data.icons[app.icon]) throw new Error('Invalid TV app shortcut');
  appIds.add(app.id);
}
const tokens = json('shared/design/mihrab/tokens.json');
data.settingsLayout = {sidebarWidth: parseFloat(tokens.layout.settings['sidebar-width'].$value), gap: parseFloat(tokens.layout.settings['content-start'].$value) - parseFloat(tokens.layout.settings['sidebar-width'].$value)};
data.homeMihrab = {width: parseFloat(tokens.layout.home['mihrab-width'].$value), minWidth: parseFloat(tokens.layout.home['mihrab-min-width'].$value), gap: parseFloat(tokens.layout.home['panel-gap'].$value)};
data.homeHeader = {clockShare: tokens.layout.home['header-clock-share'].$value, gap: parseFloat(tokens.layout.home.gap.$value)};
data.palettes = {};
data.surfaces = {};
data.presetBackgrounds = {};
for (const theme of data.themes) {
  const preset = theme === 'mihrab' ? {background: json('shared/design/appearance.json').defaultBackground} : json('shared/design/assets/themes/' + theme + '.json');
  const colors = preset.colors || {};
  const appearance = json('shared/design/appearance.json');
  data.presetBackgrounds[theme] = [...appearance.patterns, ...appearance.scenes].includes(preset.background) ? preset.background : 'plain';
  data.palettes[theme] = {};
  for (const key of json('shared/design/appearance.json').colors) {
    data.palettes[theme][key] = colors[key] || tokens.color[key].$value;
  }
  const name = preset.surface || 'classic', treatment = tokens.surface[name] || {}, c = data.palettes[theme];
  const panel = c[name === 'cinema' || name === 'outline' ? 'lapis-950' : name === 'frame' ? 'lapis-900' : name === 'blocks' ? 'lapis-700' : 'lapis-800'];
  let fill = panel, edge = c['gold-400'];
  if (name === 'glass') {
    const rgb = color => [1, 3, 5].map(at => parseInt(color.slice(at, at + 2), 16));
    const panelRgb = rgb(panel), inkRgb = rgb(c['plaster-100']), amount = tokens.surface.glass.edge.$value;
    fill = 'rgba(' + panelRgb.join(',') + ',' + tokens.surface.glass.opacity.$value + ')';
    edge = 'rgb(' + panelRgb.map((channel, i) => Math.trunc(channel * (1 - amount) + inkRgb[i] * amount)).join(',') + ')';
  }
  data.surfaces[theme] = {name, fill, archFill:panel, edge, radius: treatment.radius ? parseFloat(treatment.radius.$value) : null,
    prayerRadius: Math.min(parseFloat(tokens.surface['prayer-radius-max'].$value), parseFloat(treatment.radius ? treatment.radius.$value : tokens.radius.cell.$value)),
    stroke: treatment.stroke ? parseFloat(treatment.stroke.$value) : 0,
    archStroke: name === 'classic' ? parseFloat(tokens.surface['arch-stroke'].$value) : treatment.stroke ? parseFloat(treatment.stroke.$value) : 0,
    archEdge: name === 'cinema' || name === 'soft' ? panel : edge};
}
data.patterns = json('shared/design/appearance.json').patterns.slice().sort();
data.scenes = json('shared/design/appearance.json').scenes;
data.defaultBackground = json('shared/design/appearance.json').defaultBackground;
for (const id of data.scenes) {
  write(path.join(out, 'app/backgrounds', id + '.jpg'), fs.readFileSync(path.join(root, 'shared/design/assets/backgrounds', id + '.jpg')));
}
write(path.join(out, 'app/data.js'), 'var MiqatData = ' + JSON.stringify(data) + ';\n');
write(path.join(out, 'service/data.json'), JSON.stringify(data));
for (const part of ['app', 'service']) fs.copyFileSync(path.join(__dirname, 'core.js'), path.join(out, part, 'core.js'));
for (const part of ['app', 'service']) {
  for (const name of ['kotlin-kotlin-stdlib.js', 'Miqat-core.js']) {
    fs.copyFileSync(path.join(root, 'shared/core/build/compileSync/js/main/productionExecutable/kotlin', name), path.join(out, part, name));
  }
  fs.cpSync(path.join(root, 'shared/core/assets/licenses'), path.join(out, part, 'licenses'), {recursive: true});
}
const source = fs.readFileSync(path.join(adhanRoot, 'lib/bundles/adhan.umd.js'), 'utf8');
const compiled = babel.transformSync(source, {
  configFile: false, babelrc: false, comments: false,
  presets: [[require.resolve('@babel/preset-env'), { targets: { safari: '7' }, modules: false }]]
}).code;
for (const part of ['app', 'service']) {
  write(path.join(out, part, 'adhan.js'), '/* adhan-js 4.4.6, MIT; see licenses/adhan.txt */\n' + compiled);
  write(path.join(out, part, 'compat.js'), fs.readFileSync(path.join(__dirname, 'app/compat.js'), 'utf8'));
  write(path.join(out, part, 'licenses/adhan.txt'), fs.readFileSync(path.join(adhanRoot, 'LICENSE'), 'utf8'));
}
for (const theme of data.themes) {
  const preset = theme === 'mihrab' ? {background: json('shared/design/appearance.json').defaultBackground} : json('shared/design/assets/themes/' + theme + '.json');
  const colors = preset.colors || {};
  const css = fs.readFileSync(path.join(__dirname, 'app/style.css'), 'utf8').replace(/\{\{([\w.-]+)\}\}/g, (_, key) => {
    if (key.startsWith('color.') && colors[key.slice(6)]) return colors[key.slice(6)];
    let value = tokens;
    for (const part of key.split('.')) value = value[part];
    if (key.startsWith('color.')) {
      const rgba = /^rgba\((\d+),(\d+),(\d+),([\d.]+)\)$/.exec(value.$value);
      if (rgba) {
        const hex = '#' + rgba.slice(1, 4).map(channel => Number(channel).toString(16).padStart(2, '0')).join('').toUpperCase();
        const base = Object.keys(colors).find(name => tokens.color[name] && tokens.color[name].$value.toUpperCase() === hex);
        if (base) return 'rgba(' + [1, 3, 5].map(at => parseInt(colors[base].slice(at, at + 2), 16)).join(',') + ',' + rgba[4] + ')';
      }
    }
    return value.$value;
  });
  const c = data.palettes[theme];
  const patternOpacity = (Math.round(255 * tokens.background['pattern-opacity'].$value) + Math.floor((preset.lighting ?? 35) / 6)) / 255;
  const scenes = data.scenes.map(id => '[data-pattern=' + id + '] .home { background-image:linear-gradient(rgba(0,0,0,.2),rgba(0,0,0,.2)),url("backgrounds/' + id + '.jpg");background-size:1920px 1080px; }').join('\n');
  const patterns = data.patterns.map(name => {
    const filename = theme + '-' + name + '.svg';
    const svg = read('shared/design/mihrab/patterns/' + name + '.svg')
      .replace('stroke="white"', 'stroke="' + c['gold-400'] + '" stroke-opacity="' + patternOpacity + '"');
    write(path.join(out, 'app/patterns', filename), svg);
    return '[data-pattern=' + name + '] .home { background-image: url("patterns/' + filename + '"); background-size:' + tokens.background['max-width'].$value + ' ' + tokens.background['max-height'].$value + '; }';
  }).join('\n');
  const surface = data.surfaces[theme];
  const surfaceCss = ['.settings-rows button,.app-tile,.prayer,.day-row { background:' + surface.fill + ';border:' + surface.stroke + 'px solid ' + surface.edge + '; }',
    ...[['.settings-rows button', 'row'], ['.app-tile', 'tile'], ['.prayer', 'cell'], ['.day-row', 'row']].map(([selector, radius]) => selector + ' { border-radius:' + (selector === '.prayer' ? surface.prayerRadius + 'px' : surface.radius === null ? tokens.radius[radius].$value : surface.radius + 'px') + '; }'),
    '.home-prayers .prayer.next { background:rgba(' + [1, 3, 5].map(at => parseInt(surface.archFill.slice(at, at + 2), 16)).join(',') + ',' + tokens.surface['next-prayer-opacity'].$value + ');color:' + c['gold-400'] + '; }',
    '.prayer.next { border:' + tokens.border['next-prayer'].width.$value + ' solid ' + c['gold-400'] + '; }',
    '.settings-rows button:focus,.app-tile:focus { background:' + c['gold-400'] + ';color:' + c['lapis-900'] + ';border-color:transparent; }',
    '.arch-shape { fill:' + surface.fill + ';stroke:' + surface.archEdge + ';stroke-width:' + surface.archStroke + '; }',
    '.arch-shape .inner { display:' + (surface.name === 'classic' || surface.name === 'frame' ? 'block' : 'none') + '; }'];
  if (surface.name === 'blocks') surfaceCss.push('.settings-rows button,.app-tile,.prayer,.day-row { border-bottom:' + tokens.surface.blocks.stripe.$value + ' solid ' + c['gold-400'] + '; }');
  if (surface.name !== 'classic' && surface.name !== 'frame') surfaceCss.push('.home .prayer-name,.home .countdown { font-family:Alexandria,sans-serif;font-weight:600; }[data-layout=mihrab] .home .countdown { font-size:' + 102 * tokens.surface['modern-countdown-scale'].$value + 'px; }');
  write(path.join(out, 'app', theme === 'mihrab' ? 'style.css' : 'theme-' + theme + '.css'), css + '\n' + surfaceCss.join('\n') + '\n' + patterns + '\n' + scenes);
}
for (const font of ['alexandria_regular', 'alexandria_medium', 'alexandria_semibold', 'amiri_regular', 'marcellus_regular']) {
  write(path.join(out, 'app/fonts', font + '.ttf'), fs.readFileSync(path.join(root, 'shared/design/resources/font', font + '.ttf')));
}
write(path.join(out, 'app/licenses/adhan.txt'), fs.readFileSync(path.join(adhanRoot, 'LICENSE'), 'utf8'));
write(path.join(out, 'app/licenses/media.txt'), read('docs/THIRD-PARTY.md'));
fs.cpSync(path.join(root, 'shared/design/assets/licenses'), path.join(out, 'app/licenses'), { recursive: true });
for (const name of ['Miqat-MIT.txt', 'CleanYourTV-MIT.txt']) {
  fs.copyFileSync(path.join(root, 'shared/design/assets/licenses', name), path.join(out, 'service/licenses', name));
}
for (const part of ['app', 'service']) {
  for (const name of fs.readdirSync(path.join(out, part)).filter(n => n.endsWith('.js'))) {
    acorn.parse(fs.readFileSync(path.join(out, part, name), 'utf8'), { ecmaVersion: 5 });
  }
}
console.log('Built platforms/webos/build: ES5 app + service, shared product data, tokens and fonts.');
