import { onScreen, navigate } from '../../scripts/lifecycle';
import { live } from '../../scripts/live';
import { callModel } from '../call';

onScreen('call', (stage, signal) => {
  live(stage, signal, callModel);
  const col = stage.querySelector<HTMLElement>('.col')!;
  const play = stage.querySelector<HTMLButtonElement>('[data-play]')!;
  const note = stage.querySelector<HTMLElement>('[data-note]')!;
  const labels = JSON.parse(col.dataset.labels!);
  // No audio on the web: Play only flips to Stop (as while the adhan plays) and says so.
  let playing = false;
  play.addEventListener('click', () => {
    playing = !playing;
    play.textContent = playing ? labels.stop : labels.play;
    note.hidden = !playing;
  });
  // As on the TV, the call closes by itself (motion.call-auto-dismiss).
  const dismissTimer = window.setTimeout(() => navigate(col.dataset.homeHref!), Number(col.dataset.autoDismiss));
  signal.addEventListener('abort', () => { clearTimeout(dismissTimer); }, {once: true});
});
