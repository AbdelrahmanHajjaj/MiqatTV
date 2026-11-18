// Project the original Miqat arch into Android's two independent renderers.
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';
import { readFileSync, writeFileSync } from 'node:fs';

const root = new URL('../', import.meta.url);
const shape = JSON.parse(readFileSync(new URL('shared/design/mihrab.json', root)));
const tile = JSON.parse(readFileSync(new URL('shared/design/background-tile.json', root)));

const arities = { M: 2, L: 2, C: 6, Z: 0 };
function java(path) {
  const parts = path.match(/[MLCZ]|-?\d+(?:\.\d+)?/g), lines = ['        path.reset();'];
  while (parts.length) {
    const op = parts.shift();
    assert.ok(Object.hasOwn(arities, op), 'only explicit local M/L/C/Z geometry');
    const values = parts.splice(0, arities[op]).map(Number);
    assert.equal(values.length, arities[op]);
    assert.ok(values.every(Number.isFinite));
    const method = { M: 'moveTo', L: 'lineTo', C: 'cubicTo', Z: 'close' }[op];
    lines.push(`        path.${method}(${values.map((n, i) => `${n}f * ${i % 2 ? 'y' : 'x'}`).join(', ')});`);
  }
  return lines.join('\n');
}
export function generateBranding(check = false) {
for (const target of ['platforms/android-tv/src/main/java/tv/miqat/home', 'installer/src/main/java/tv/miqat/installer']) {
  const pkg = target.endsWith('home') ? 'home' : 'installer';
  const tileMethod = pkg === 'home' ? `
    static void tile(android.graphics.Path path, float width, float height) {
        float x = width / ${tile.width}f, y = height / ${tile.height}f;
${java(tile.path)}
    }
` : '';
  const source = `package tv.miqat.${pkg};

// Generated from shared/design geometry by tools/branding.mjs. Do not edit.
final class MiqatShape {
    private MiqatShape() {}
    static void set(android.graphics.Path path, float width, float height, boolean inner) {
        float x = width / ${shape.width}f, y = height / ${shape.height}f;
        if (inner) {
${java(shape.inner)}
        } else {
${java(shape.outer)}
        }
    }
${tileMethod}
}
`;
  const url = new URL(target + '/MiqatShape.java', root);
  if (check) assert.equal(readFileSync(url, 'utf8'), source, target + ' is stale');
  else writeFileSync(url, source);
}
console.log(check ? 'Miqat geometry projections match' : 'Generated Miqat geometry for TV and installer');
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) generateBranding(process.argv.includes('--check'));
