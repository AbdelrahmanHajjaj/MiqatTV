import { onScreen } from '../../scripts/lifecycle';
import { live } from '../../scripts/live';
import { screensaverModel } from '../screensaver';

onScreen('screensaver', (stage, signal) => {
  live(stage, signal, screensaverModel);
  const wake = stage.querySelector<HTMLAnchorElement>('.wake')!;
  const group = stage.querySelector<HTMLElement>('[data-group]')!;
  // Any key wakes it, as on the TV (arrows included, once the screen has focus).
  wake.addEventListener('keydown', (e) => {
    if (e.ctrlKey || e.metaKey || e.altKey || e.key === 'Tab') return;
    e.preventDefault();
    e.stopPropagation();
    wake.click();
  });
  // MiqatDream.drift: fade out, move somewhere new inside the safe margins, fade back in.
  const place = (x: number, y: number) => group.style.setProperty('translate', `${x}px ${y}px`);
  const fadeMs = () => parseFloat(getComputedStyle(group).transitionDuration) * 1000 || 0;
  place(1140, 250);
  let fadeTimer = 0;
  const driftTimer = window.setInterval(() => {
    group.style.opacity = '0';
    fadeTimer = window.setTimeout(() => {
      const factor = Number(stage.dataset.uiSize) / 100;
      place(96 + Math.random() * (1920 - 192 - 440 * factor), 56 + Math.random() * (1080 - 112 - 640 * factor));
      group.style.opacity = '1';
    }, fadeMs());
  }, Number(group.dataset.drift));
  signal.addEventListener('abort', () => { clearInterval(driftTimer); clearTimeout(fadeTimer); }, {once: true});
});
