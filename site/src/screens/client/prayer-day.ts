import { onScreen } from '../../scripts/lifecycle';
import { live, bind } from '../../scripts/live';
import { prayerDayModel, pickerModel, prayerRules, facingStep, loadBells, saveBells, loadFacing, saveFacing } from '../prayer-day';
import type { Ctx } from '../../lib/ctx';

onScreen('prayer-day', (stage, signal) => {
  const $ = <T extends HTMLElement = HTMLElement>(s: string) => stage.querySelector<T>(s)!;
  const rows = [...stage.querySelectorAll<HTMLElement>('[data-row]')];
  const room = $('[data-room]');
  const toStart = document.documentElement.lang === 'ar' ? 'navRight' : 'navLeft';
  let last: Ctx | null = null;
  let nextIndex = -1;

  const rerender = live(stage, signal, prayerDayModel, (c) => {
    last = c;
    // The default focus is the next prayer's row, as the app focuses it on arrival.
    if (c.day.nextIndex !== nextIndex) {
      nextIndex = c.day.nextIndex;
      rows.forEach((r, i) => r.toggleAttribute('data-focus-start', i === nextIndex));
    }
  });

  // OK on a row turns its adhan on or off. Focus on a row is remembered for Left/Right from the room button.
  rows.forEach((r, i) => {
    if (!prayerRules.isPrayer(i)) return;
    r.addEventListener('click', () => {
      const b = loadBells();
      b[i] = !b[i];
      saveBells(b);
      rerender();
    });
    r.addEventListener('focus', () => { room.dataset[toStart] = `[data-row="${i}"]`; });
  });

  const picker = $('[data-picker]');
  let facing = 0;
  const renderPicker = () => last && bind(picker, pickerModel(last, facing));
  const open = () => {
    const f = loadFacing();
    facing = f < 0 ? 0 : f;
    renderPicker();
    picker.hidden = false;
    picker.focus({ preventScroll: true });
  };
  const close = (save: boolean) => {
    if (save) saveFacing(facing);
    picker.hidden = true;
    rerender();
    room.focus({ preventScroll: true });
  };
  room.addEventListener('click', open);
  picker.addEventListener('keydown', (e) => {
    e.preventDefault();
    e.stopPropagation(); // the picker has the keys: no spatial navigation, no Back to Home
    const k = e.key;
    if (k === 'Enter' || k === ' ') return close(true);
    if (k === 'Escape' || k === 'Backspace') return close(false);
    let v = facing;
    // Clockwise is always Right, as on a compass, in either language.
    if (k === 'ArrowRight') v += facingStep;
    else if (k === 'ArrowLeft') v -= facingStep;
    else if (k === 'ArrowUp') v = (Math.floor(v / 45) + 1) * 45;
    else if (k === 'ArrowDown') v = (Math.floor((v + 44) / 45) - 1) * 45;
    else return;
    facing = ((v % 360) + 360) % 360;
    renderPicker();
  });
  picker.addEventListener('click', (e) => { if (e.target === picker) close(false); });
});
