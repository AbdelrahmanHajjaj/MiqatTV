import { onScreen } from '../../scripts/lifecycle';
import { safeInline } from '../../lib/ui-scale';
import { live } from '../../scripts/live';
import { appsModel } from '../apps';

onScreen('apps', (stage, signal) => {
  live(stage, signal, appsModel, c => {
    const margin = safeInline(c.prefs), width = (1920 - 2 * margin - 4 * 32) / 5;
    stage.querySelectorAll<HTMLElement>('[data-app]').forEach(tile => {
      tile.style.insetInlineStart = `${margin + Number(tile.dataset.app) % 5 * (width + 32)}px`;
      tile.style.width = `${width}px`;
    });
  });

  const $ = <T extends HTMLElement = HTMLElement>(s: string) => stage.querySelector<T>(s)!;

  const toast = $('[data-toast-out]');
  let toastTimer = 0;
  const say = (text: string) => {
    toast.textContent = text;
    toast.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = window.setTimeout(() => (toast.hidden = true), 2600);
  };

  // The menu: for a favourite, move earlier/later; otherwise put it in place of one; always cancel.
  const menu = $('[data-menu]');
  const text = JSON.parse(menu.dataset.text!);
  let from: HTMLElement | null = null;
  const closeMenu = () => { menu.hidden = true; from?.focus({ preventScroll: true }); };
  const openMenu = (tile: HTMLElement) => {
    from = tile;
    const fav = Number(tile.dataset.fav);
    const items: string[] = [];
    if (fav > 1) items.push(text.earlier);
    if (fav && fav < 4) items.push(text.later);
    if (!fav) items.push(...text.instead);
    $('[data-menu-title]').textContent = tile.dataset.name!;
    $('[data-menu-items]').replaceChildren(...[...items, text.cancel].map((label, i, all) => {
      const b = document.createElement('button');
      b.className = 'ft row item';
      b.textContent = label;
      if (i === all.length - 1) b.dataset.back = '';
      // Focus stays in the menu: Up/Down walk the list, Left/Right stay put.
      const nth = (n: number) => `[data-menu-items] button:nth-child(${n})`;
      b.dataset.navUp = nth(Math.max(1, i));
      b.dataset.navDown = nth(Math.min(all.length, i + 2));
      b.dataset.navLeft = b.dataset.navRight = nth(i + 1);
      b.addEventListener('click', () => { closeMenu(); if (i < all.length - 1) say(text.done); });
      return b;
    }));
    menu.hidden = false;
    $<HTMLElement>('[data-menu-items] button').focus({ preventScroll: true });
  };

  // OK opens (a toast here); holding OK (or a right-click / long press) opens the menu.
  let holdTimer = 0;
  let held = false;
  stage.addEventListener('keydown', (e) => {
    const tile = (e.target as HTMLElement).closest<HTMLElement>('.tile');
    if (!tile || e.key !== 'Enter') return;
    e.preventDefault();
    if (e.repeat) return;
    held = false;
    holdTimer = window.setTimeout(() => { held = true; openMenu(tile); }, 600);
  });
  stage.addEventListener('keyup', (e) => {
    const tile = (e.target as HTMLElement).closest<HTMLElement>('.tile');
    if (e.key !== 'Enter' || !holdTimer) return;
    clearTimeout(holdTimer);
    holdTimer = 0;
    if (tile && !held) say(tile.dataset.toast!);
  });
  stage.addEventListener('click', (e) => {
    const tile = (e.target as HTMLElement).closest<HTMLElement>('.tile');
    if (tile && e.detail > 0) say(tile.dataset.toast!);
  });
  stage.addEventListener('contextmenu', (e) => {
    const tile = (e.target as HTMLElement).closest<HTMLElement>('.tile');
    if (!tile) return;
    e.preventDefault();
    tile.focus({ preventScroll: true });
    openMenu(tile);
  });
  signal.addEventListener('abort', () => { clearTimeout(toastTimer); clearTimeout(holdTimer); }, {once: true});
});
