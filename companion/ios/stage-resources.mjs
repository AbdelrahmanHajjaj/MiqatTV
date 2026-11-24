import { copyFileSync, cpSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';
import { generate, generatePhone, shortTone } from '../generate.mjs';

const here = fileURLToPath(new URL('.', import.meta.url));
const root = resolve(here, '../..');
const destination = process.argv[2];
if (!destination) throw new Error('Missing iOS resource destination');

mkdirSync(join(destination, 'Notices'), { recursive: true });
for (const name of [
  'cities', 'methods', 'strings', 'sacred-text', 'phone-ui-contract', 'feedback',
  'hijri-events', 'stations', 'voices',
]) {
  copyFileSync(join(root, 'shared/data', name + '.json'), join(destination, name + '.json'));
}
copyFileSync(join(root, 'companion/strings.json'), join(destination, 'phone-strings.json'));
const { themes, profiles, sections, themeKeys, themeSurfaces } = generate();
const { backgrounds, voices } = generatePhone();
const themeOrder = JSON.parse(readFileSync(join(root, 'shared/design/themes.json'), 'utf8'));
writeFileSync(join(destination, 'phone-appearance.json'), JSON.stringify({
  themes, backgrounds, themeOrder: themeOrder.filter(id => id in themes),
}));
writeFileSync(join(destination, 'tv-profiles.json'), JSON.stringify({ profiles, sections, themeKeys, themeSurfaces }));
copyFileSync(join(root, 'companion/protocol/lan.json'), join(destination, 'lan.json'));
copyFileSync(join(root, 'companion/qr.js'), join(destination, 'PairingQr.js'));
for (const id of backgrounds.filter(id => id !== 'plain')) {
  copyFileSync(join(root, 'shared/design/assets/backgrounds', id + '.jpg'), join(destination, id + '.jpg'));
}
copyFileSync(join(root, 'shared/design/phone/portrait/miqat_signature.png'), join(destination, 'miqat_signature.png'));
writeFileSync(join(destination, 'miqat_short_tone.wav'), shortTone());
mkdirSync(join(destination, 'Adhan'), { recursive: true });
const checksums = new Map(readFileSync(join(root, 'shared/media/SHA256SUMS'), 'utf8').trim().split(/\r?\n/)
  .map(line => line.trim().split(/\s+/).reverse()));
for (const { id } of voices) {
  const path = 'shared/media/adhan/' + id + '.mp3';
  const bytes = readFileSync(join(root, path));
  if (createHash('sha256').update(bytes).digest('hex') !== checksums.get(path)) {
    throw new Error('Unapproved Adhan audio: ' + path);
  }
  writeFileSync(join(destination, 'Adhan', id + '.mp3'), bytes);
}
copyFileSync(join(root, 'LICENSE'), join(destination, 'Notices/Miqat.txt'));
cpSync(join(root, 'shared/core/assets/licenses/kotlin'), join(destination, 'Notices/Kotlin'), { recursive: true });
copyFileSync(join(here, 'node_modules/adhan/LICENSE'), join(destination, 'Notices/Adhan.txt'));
copyFileSync(join(here, 'node_modules/adhan/lib/bundles/adhan.umd.js'), join(destination, 'adhan.js'));
copyFileSync(join(here, 'Astronomy.js'), join(destination, 'Astronomy.js'));
