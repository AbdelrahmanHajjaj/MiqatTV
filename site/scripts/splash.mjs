// Deterministic exports of the owner-selected Miqat artwork; never redraw the calligraphy.
import assert from 'node:assert/strict';
import { mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import sharp from 'sharp';

const root = new URL('../../', import.meta.url);
const source = 'shared/design/branding/miqat-tv-v1/';
for (const [input, output, width, height] of [
  ['miqat-tv-splash-1920x1080.png', 'shared/design/artwork/splash.png', 1920, 1080],
  ['miqat-tv-icon-source.png', 'shared/design/artwork/icon.png', 512, 512],
  ['miqat-tv-icon-source.png', 'platforms/webos/app/icon.png', 80, 80],
  ['miqat-tv-icon-source.png', 'platforms/webos/app/icon-large.png', 130, 130],
  ['miqat-tv-icon-source.png', 'platforms/android-tv/src/main/res/drawable-nodpi/miqat_icon.png', 512, 512],
  ['miqat-tv-icon-source.png', 'installer/src/main/res/drawable-nodpi/miqat_icon.png', 512, 512],
  ['miqat-tv-splash-1920x1080.png', 'platforms/android-tv/src/main/res/drawable-nodpi/miqat_splash.png', 1280, 720],
  ['miqat-tv-splash-1920x1080.png', 'platforms/android-tv/src/main/res/drawable-xhdpi/miqat_banner.png', 320, 180],
  ['miqat-tv-icon-source.png', 'site/public/branding/icon.png', 130, 130],
  ['miqat-tv-splash-1920x1080.png', 'site/public/branding/splash.png', 1920, 1080],
]) {
  const destination = new URL(output, root);
  await mkdir(new URL('./', destination), {recursive: true});
  const info = await sharp(fileURLToPath(new URL(source + input, root)))
    .resize(width, height).flatten({background: '#033B44'}).removeAlpha()
    .png({compressionLevel: 9}).toFile(fileURLToPath(destination));
  assert.equal(info.width, width);
  assert.equal(info.height, height);
  assert.equal(info.channels, 3);
}
console.log('Generated Miqat splash, banner, launcher and web icons.');
