import portable from '@miqat/core';
import tokens from '../../../shared/design/mihrab/tokens.json';
import themes from '../../../shared/design/themes.json';
import design from '../../../shared/design/appearance.json';
import aurora from '../../../shared/design/assets/themes/aurora.json';
import ember from '../../../shared/design/assets/themes/ember.json';
import forest from '../../../shared/design/assets/themes/forest.json';
import midnight from '../../../shared/design/assets/themes/midnight.json';
import rose from '../../../shared/design/assets/themes/rose.json';
import sand from '../../../shared/design/assets/themes/sand.json';
import slate from '../../../shared/design/assets/themes/slate.json';
import glass from '../../../shared/design/assets/themes/glass.json';
import frost from '../../../shared/design/assets/themes/frost.json';
import cinema from '../../../shared/design/assets/themes/cinema.json';
import carbon from '../../../shared/design/assets/themes/carbon.json';
import neon from '../../../shared/design/assets/themes/neon.json';
import blueprint from '../../../shared/design/assets/themes/blueprint.json';
import signal from '../../../shared/design/assets/themes/signal.json';
import arcade from '../../../shared/design/assets/themes/arcade.json';
import dusk from '../../../shared/design/assets/themes/dusk.json';
import oasis from '../../../shared/design/assets/themes/oasis.json';
import brass from '../../../shared/design/assets/themes/brass.json';
import mosaic from '../../../shared/design/assets/themes/mosaic.json';
import patterns from '../data/background-patterns.json';
import dusk_peaks from '../../../shared/design/assets/backgrounds/dusk_peaks.jpg?url';
import golden_coast from '../../../shared/design/assets/backgrounds/golden_coast.jpg?url';
import moon_lake from '../../../shared/design/assets/backgrounds/moon_lake.jpg?url';
import aurora_sky from '../../../shared/design/assets/backgrounds/aurora_sky.jpg?url';
import tea_hills from '../../../shared/design/assets/backgrounds/tea_hills.jpg?url';
import monsoon from '../../../shared/design/assets/backgrounds/monsoon.jpg?url';
import miqat_signature from '../../../shared/design/assets/backgrounds/miqat_signature.jpg?url';
import miqat_quiet from '../../../shared/design/assets/backgrounds/miqat_quiet.jpg?url';
const scenes: Record<string, string> = {dusk_peaks, golden_coast, moon_lake, aurora_sky, tea_hills, monsoon, miqat_signature, miqat_quiet};
import type { Prefs } from '../lib/data';
const presets: Record<string, typeof aurora & { surface?: string }> = { aurora, ember, forest, midnight, rose, sand, slate, glass, frost, cinema, carbon, neon, blueprint, signal, arcade, dusk, oasis, brass, mosaic };
export const mihrabOpacity = (value: unknown) => portable.tv.miqat.core.UiContract.mihrabOpacity(typeof value === 'number' ? value : NaN);
export const THEMES = themes.filter(id => id !== 'custom');
export const BACKGROUNDS: string[] = portable.tv.miqat.core.UiContract.choices('appearance.background');
const rendered = new WeakMap<HTMLElement, string>();
export const backgroundOf = (prefs: Prefs) => prefs.background && prefs.background !== 'theme' ? prefs.background : presets[prefs.theme ?? 'mihrab']?.background ?? design.defaultBackground;
export function appearance(stage: HTMLElement, prefs: Prefs) {
  const theme = presets[prefs.theme ?? 'mihrab'];
  const mode = backgroundOf(prefs);
  const key = JSON.stringify([prefs.theme, prefs.background, prefs.lighting, prefs.reduceMotion, mihrabOpacity(prefs.mihrabOpacity), mode === 'daylight' ? Math.floor(Date.now() / 60000) : 0]);
  if (key === rendered.get(stage)) return;
  rendered.set(stage, key);
  for (const name of design.colors) {
    if (theme) stage.style.setProperty('--color-' + name, theme.colors[name as keyof typeof aurora.colors]);
    else stage.style.setProperty('--color-' + name, tokens.color[name as keyof typeof aurora.colors].$value);
  }
  // Match Palette.apply: tinted alpha colours and aliases must resolve on this element,
  // rather than inherit the default palette already resolved on :root.
  const tint = (group: Record<string, any>, prefix = '') => {
    for (const [name, token] of Object.entries(group)) {
      const key = prefix + name;
      if (!('$value' in token)) { tint(token, key + '-'); continue; }
      const value = token.$value as string;
      const rgba = /^rgba\((\d+),(\d+),(\d+),([\d.]+)\)$/.exec(value);
      if (!rgba) continue;
      const hex = '#' + rgba.slice(1, 4).map(v => Number(v).toString(16).padStart(2, '0')).join('').toUpperCase();
      const base = Object.entries(tokens.color).find(([, t]) => '$value' in t && t.$value === hex)?.[0];
      if (base) stage.style.setProperty('--color-' + key, `color-mix(in srgb, var(--color-${base}) ${Number(rgba[4]) * 100}%, transparent)`);
    }
  };
  tint(tokens.color);
  for (const [name, spec] of Object.entries(tokens.border)) {
    if ('color' in spec) stage.style.setProperty('--border-' + name + '-color', `var(--${spec.color.$value.slice(1, -1).replaceAll('.', '-')})`);
  }
  const surface = theme?.surface ?? 'classic';
  stage.dataset.surface = surface;
  const modern = !['classic', 'frame'].includes(surface);
  stage.style.setProperty('--home-display', modern ? 'var(--font-family-ui)' : 'var(--display)');
  stage.style.setProperty('--home-display-weight', modern ? '600' : 'var(--display-weight)');
  stage.style.setProperty('--home-countdown-scale', String(modern ? tokens.surface['modern-countdown-scale'].$value : 1));
  const treatment = tokens.surface[surface as keyof typeof tokens.surface] as { radius?: { $value: string }; stroke?: { $value: string } } | undefined;
  for (const radius of ['cell', 'row', 'tile']) stage.style.setProperty('--radius-' + radius, treatment?.radius?.$value ?? tokens.radius[radius as 'cell' | 'row' | 'tile'].$value);
  const panel = surface === 'cinema' || surface === 'outline' ? 'lapis-950' : surface === 'frame' ? 'lapis-900' : surface === 'blocks' ? 'lapis-700' : 'lapis-800';
  const fill = surface === 'glass' ? `color-mix(in srgb, var(--color-${panel}) ${tokens.surface.glass.opacity.$value * 100}%, transparent)` : `var(--color-${panel})`;
  const edge = surface === 'glass' ? `color-mix(in srgb, var(--color-lapis-800), var(--color-plaster-100) ${tokens.surface.glass.edge.$value * 100}%)` : 'var(--color-gold-400)';
  stage.style.setProperty('--mihrab-fill', `var(--color-${panel})`);
  stage.style.setProperty('--mihrab-opacity', String(mihrabOpacity(prefs.mihrabOpacity) / 100));
  stage.style.setProperty('--surface-fill', fill);
  stage.style.setProperty('--surface-edge', edge);
  stage.style.setProperty('--surface-stroke', treatment?.stroke?.$value ?? '0px');
  stage.style.setProperty('--surface-arch-stroke', surface === 'classic' ? tokens.surface['arch-stroke'].$value : treatment?.stroke?.$value ?? '0px');
  stage.dataset.background = mode;
  const css = getComputedStyle(stage);
  const color = (name: string) => css.getPropertyValue('--color-' + name).trim();
  const dark = color('lapis-950'), ground = color('lapis-900'), accent = color('gold-400');
  const hour = new Date().getHours() + new Date().getMinutes() / 60;
  const daylight = mode === 'daylight' ? Math.max(.15, Math.sin((hour - 6) / 12 * Math.PI)) : .7;
  const light = (prefs.lighting ?? theme?.lighting ?? 35) / 100 * daylight;
  let art = '';
  const pattern = patterns[mode as keyof typeof patterns];
  if (pattern) art = `<path d="${pattern.path}" fill="none" stroke="${accent}" stroke-width="${pattern.stroke}" stroke-linecap="round" stroke-linejoin="round" opacity="${(Math.round(255 * tokens.background['pattern-opacity'].$value) + Math.floor((prefs.lighting ?? theme?.lighting ?? 35) / 6)) / 255}"/>`;
  else if (mode === 'mountains') {
    for (let layer = 0; layer < 3; layer++) {
      let points = '0,270 ';
      const ridge = [[0,202],[72,155],[148,183],[218,112],[304,186],[390,146],[480,192]];
      for (const [x,y] of ridge) points += `${x},${y + layer * 24} `;
      art += `<polygon points="${points}480,270" fill="${dark}" opacity="${.45 + layer * .2}"/>`;
    }
  } else if (mode === 'dunes') {
    for (let layer = 0; layer < 3; layer++) art += `<path d="M0 ${194 + layer * 24} C100 ${132 + layer * 24} 178 ${255 - layer * 10} 288 ${198 + layer * 18} C370 ${156 + layer * 28} 422 ${205 + layer * 18} 480 ${184 + layer * 26} V270 H0Z" fill="${dark}" opacity="${.4 + layer * .2}"/>`;
  } else if (mode === 'halo') art = `<ellipse cx="380" cy="84" rx="172" ry="172" fill="url(#glow)"/>`;
  else if (mode === 'night_sky') {
    for (let i = 0; i < 28; i++) art += `<circle cx="${(i * 73 + 11) % 480}" cy="${12 + (i * 47 + 19) % 156}" r="${i % 5 === 0 ? .8 : .4}" fill="${color('plaster-100')}" opacity="${.24 + light * .18}"/>`;
  }
  if (['panorama','daylight','night_sky'].includes(mode)) art += `<path d="M0 234 C100 208 175 255 278 226 C374 196 418 225 480 204 V270 H0Z" fill="${dark}"/>`;
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 480 270"><defs><linearGradient id="base" x2="1" y2="1"><stop stop-color="${dark}"/><stop offset="1" stop-color="${ground}"/></linearGradient><radialGradient id="glow"><stop stop-color="${accent}" stop-opacity="${light * .45}"/><stop offset="1" stop-color="${accent}" stop-opacity="0"/></radialGradient></defs><path fill="url(#base)" d="M0 0H480V270H0Z"/><ellipse cx="360" cy="190" rx="210" ry="210" fill="url(#glow)"/>${art}</svg>`;
  stage.style.setProperty('--scene-image', scenes[mode] ? `linear-gradient(#0003,#0003),url("${scenes[mode]}")` : mode === 'solid' ? 'none' : `url("data:image/svg+xml,${encodeURIComponent(svg)}")`);
  stage.dataset.reduced = String(!!prefs.reduceMotion);
  stage.style.setProperty('--ambient-light', `${Math.round((prefs.lighting ?? 35) * .3)}%`);
}
