import { copyFileSync, cpSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
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
const { themes } = generate([]);
const { backgrounds } = generatePhone();
const themeOrder = JSON.parse(readFileSync(join(root, 'shared/design/themes.json'), 'utf8'));
writeFileSync(join(destination, 'phone-appearance.json'), JSON.stringify({
  themes, backgrounds, themeOrder: themeOrder.filter(id => id in themes),
}));
for (const id of backgrounds.filter(id => id !== 'plain' && id !== 'miqat_signature')) {
  copyFileSync(join(root, 'shared/design/assets/backgrounds', id + '.jpg'), join(destination, id + '.jpg'));
}
copyFileSync(join(root, 'shared/design/phone/portrait/miqat_signature.png'), join(destination, 'miqat_signature.png'));
writeFileSync(join(destination, 'miqat_short_tone.wav'), shortTone());
copyFileSync(join(root, 'LICENSE'), join(destination, 'Notices/Miqat.txt'));
cpSync(join(root, 'shared/core/assets/licenses/kotlin'), join(destination, 'Notices/Kotlin'), { recursive: true });
copyFileSync(join(here, 'node_modules/adhan/LICENSE'), join(destination, 'Notices/Adhan.txt'));
copyFileSync(join(here, 'node_modules/adhan/lib/bundles/adhan.umd.js'), join(destination, 'adhan.js'));
copyFileSync(join(here, 'Astronomy.js'), join(destination, 'Astronomy.js'));
