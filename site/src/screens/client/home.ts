import { onScreen } from '../../scripts/lifecycle';
import { live, loadPrefs } from '../../scripts/live';
import { homeModel, archModel } from '../home';
import { homeLayout, homePrayers, HOME } from '../../lib/home-layout';
import { stationOf } from '../../lib/data';
import { makeText, type Lang } from '../../lib/text';

onScreen('home', (stage, signal) => {
  const $ = <T extends HTMLElement = HTMLElement>(s: string) => stage.querySelector<T>(s)!;
  const card = $('[data-card]');
  const forced = stage.dataset.mode === 'reminder';
  let dismissedFor = '';
  const prefs = loadPrefs();
  const t = makeText(document.documentElement.lang as Lang, prefs.digits === 'auto' ? document.documentElement.lang === 'ar' : prefs.digits === 'eastern');

  const showCard = (show: boolean) => {
    if (card.hidden === !show) return;
    card.hidden = !show;
    // The card takes the default focus while it shows, as on the TV.
    $(show ? '.fav' : '[data-card-ok]').removeAttribute('data-focus-start');
    $(show ? '[data-card-ok]' : '.fav').setAttribute('data-focus-start', '');
    if (show && stage.contains(document.activeElement)) $('[data-card-ok]').focus({ preventScroll: true });
  };
  live(stage, signal, homeModel, (c) => {
    $('.pills').classList.toggle('icons-only', !c.prefs.quickLabels);
    stage.dataset.homeLayout = c.prefs.homeLayout === 'classic' ? 'classic' : 'two_rows';
    const h = homeLayout(c.prefs, stage.querySelectorAll('.fav').length, c.t.ar);
    const header = $('.home-header');
    header.style.marginInlineStart = `${h.margin}px`; header.style.width = `${h.full}px`;
    header.style.setProperty('--home-meta-width', `${h.full * HOME['header-clock-share']}px`);
    const column = $('.home-column');
    column.style.marginInlineStart = `${h.x}px`; column.style.width = `${h.width}px`;
    $('.home-favourites').style.gridTemplateColumns = `repeat(${h.columns}, minmax(0, 1fr))`;
    stage.dataset.countdown = String(h.mode);
    const rect = (el: HTMLElement, x: number, y: number, w?: number, height?: number) => {
      el.style.insetInlineStart = `${x}px`; el.style.top = `${y}px`;
      if (w !== undefined) el.style.width = `${w}px`;
      if (height !== undefined) el.style.height = `${height}px`;
    };
    rect($('.when'), h.x, 56, h.width);
    rect($('.pills'), h.x, h.quickY, h.width - 72 - HOME.gap);
    rect($('.gear'), h.x + h.width - 72, h.quickY);
    const panel = $('.home-countdown'); panel.hidden = h.mode === 2;
    const large = (c.prefs.uiScale ?? 100) > 110;
    $('.home-scroll').classList.toggle('scrolling', large);
    const countdownOwner = large && h.mode === 1 ? $(c.prefs.homeBar === 1 ? '.home-countdown-bottom' : '.home-countdown-top') : $('.home-canvas');
    if (panel.parentElement !== countdownOwner) countdownOwner.append(panel);
    rect(panel, h.mode === 0 ? h.panelX : h.x, h.panelY, h.mode === 0 ? h.panelWidth : h.width, h.mode === 0 ? HOME['mihrab-height'] : HOME['bar-height']);
    const cells = $('.cells'), slots = homePrayers(h.table, -1, 0).length;
    cells.hidden = slots === 0;
    const columns = large ? Math.max(1, Math.min(3, slots)) : Math.max(1, slots);
    const cw = Math.min(HOME['tile-max-width'], (h.width - (columns - 1) * 12) / columns);
    rect(cells, h.x, h.tableY, slots * cw + Math.max(0, slots - 1) * 12);
    cells.style.gridTemplateColumns = `repeat(${columns}, minmax(0, 1fr))`;
    rect($('.fav-label'), h.x, h.labelY); $('.fav-label').hidden = h.count === 0;
    stage.querySelectorAll<HTMLElement>('.fav').forEach((tile, i) => {
      tile.hidden = i >= h.count;
      rect(tile, h.x + i % h.columns * (h.tileWidth + HOME.gap), h.favouritesY + Math.floor(i / h.columns) * (h.tileHeight + HOME.gap), h.tileWidth, h.tileHeight);
    });
    rect($('.more-label'), h.x, h.recentLabelY);
    stage.querySelectorAll<HTMLElement>('.more').forEach((tile, i) => rect(tile, h.x + i * (h.width + 20) / 6, h.recentY, (h.width - 100) / 6));
    const left = Math.ceil((c.day.nextTime.getTime() - c.now.getTime()) / 1000);
    const remind = c.prefs.remind * 60;
    const due = (forced || (remind > 0 && left > 0 && left <= remind)) && card.dataset.for !== dismissedFor;
    showCard(due);
  });
  stage.addEventListener('focusin', (event) => {
    const item = event.target as HTMLElement;
    const scroll = item.closest<HTMLElement>('.home-scroll');
    if (stage.dataset.largeUi !== 'true' || !scroll) return;
    item.scrollIntoView({ block: 'nearest', inline: 'nearest' });
    scroll.scrollLeft = 0;
  });
  $('[data-card-ok]').addEventListener('click', () => {
    dismissedFor = card.dataset.for ?? '';
    showCard(false);
    $('.fav').focus({ preventScroll: true });
  });

  // Toasts stand in for launching apps.
  const toast = $('[data-toast-out]');
  let toastTimer = 0;
  stage.addEventListener('click', (e) => {
    const b = (e.target as HTMLElement).closest<HTMLElement>('[data-toast]');
    if (!b) return;
    toast.textContent = b.dataset.toast!;
    toast.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = window.setTimeout(() => (toast.hidden = true), 2600);
  });

  // Quran Radio: the chosen station's live stream.
  const audio = $<HTMLAudioElement>('[data-audio]');
  let quickTrigger: HTMLElement | null = null;
  const closeQuick = () => {
    stage.querySelectorAll<HTMLElement>('[data-quick]').forEach(panel => panel.hidden = true);
    quickTrigger?.focus({ preventScroll: true });
  };
  for (const name of ['inputs']) {
    const trigger = $(`[data-open-${name}]`), panel = $(`[data-quick="${name}"]`);
    trigger.addEventListener('click', () => {
      closeQuick(); $('[data-radio-card]').hidden = true;
      quickTrigger = trigger; panel.hidden = false;
      panel.querySelector<HTMLElement>('.quick-item')?.focus({ preventScroll: true });
    });
    panel.querySelector('[data-quick-close]')?.addEventListener('click', closeQuick);
    panel.querySelector('[data-quick-home]')?.addEventListener('click', closeQuick);
    panel.addEventListener('keydown', (event) => {
      if (['Escape','Backspace'].includes(event.key)) { event.preventDefault(); event.stopPropagation(); closeQuick(); }
      if (['ArrowUp','ArrowDown','ArrowLeft','ArrowRight'].includes(event.key)) {
        event.preventDefault(); event.stopPropagation();
        const rows = [...panel.querySelectorAll<HTMLElement>('.quick-more, .quick-item')];
        const at = rows.indexOf(document.activeElement as HTMLElement);
        const step = event.key === 'ArrowDown' ? 1 : event.key === 'ArrowUp' ? -1 : 0;
        rows[Math.max(0, Math.min(rows.length - 1, at + step))]?.focus({ preventScroll: true });
      }
    });
  }
  const rc = $('[data-radio-card]');
  const st = stationOf(prefs);
  const status = () => {
    const on = !audio.paused;
    stage.classList.toggle('radio-on', on);
    $('[data-station-where]').textContent = t.stationWhere(st) + t.sep() + t.radioStatus(on);
    $('[data-radio-play-label]').textContent = on ? t.s('radioStop') : t.s('radioPlay');
    $('.radio-label').textContent = on ? t.radioStatus(true) : t.s('radio');
  };
  audio.addEventListener('playing', status);
  audio.addEventListener('pause', status);
  $('[data-station-name]').textContent = t.station(st);
  $('[data-screen-off-note]').innerHTML = `${t.station(st)}`;
  $('[data-open-radio]').addEventListener('click', () => {
    rc.hidden = false;
    $('[data-radio-toggle]').focus({ preventScroll: true });
  });
  $('[data-radio-close]').addEventListener('click', () => {
    rc.hidden = true;
    $('[data-open-radio]').focus({ preventScroll: true });
  });
  $('[data-radio-toggle]').addEventListener('click', () => {
    if (audio.paused) { audio.src = st.url; audio.play().catch(() => status()); }
    else { audio.pause(); audio.removeAttribute('src'); audio.load(); }
  });
  const off = $('[data-screen-off-view]');
  $('[data-screen-off]').addEventListener('click', () => {
    if (audio.paused) { audio.src = st.url; audio.play().catch(() => status()); }
    off.hidden = false;
    off.focus({ preventScroll: true });
  });
  const wake = () => { off.hidden = true; $('[data-screen-off]').focus({ preventScroll: true }); };
  off.addEventListener('keydown', (e) => { e.preventDefault(); e.stopPropagation(); wake(); });
  off.addEventListener('click', wake);
  signal.addEventListener('abort', () => { clearTimeout(toastTimer); audio.pause(); audio.removeAttribute('src'); audio.load(); }, {once: true});
});
