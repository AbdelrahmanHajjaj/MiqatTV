import { onScreen, navigate } from '../../scripts/lifecycle';
import { homePreview } from '../../lib/home-layout';
import { THEMES }  from '../../scripts/appearance';
import { armCycle } from '../../lib/appearance-cycle';
import { live } from '../../scripts/live';
import { href } from '../../lib/site';
import { CITIES, METHODS, cityOf, stationOf } from '../../lib/data';
import { APPS } from '../apps';
import type { Ctx } from '../../lib/ctx';
import type { Lang } from '../../lib/text';
import {pairingDemo, pairingHtml, pairingStep, type PairingAction} from '../pairing';
import {fetchComparison, onlineRequest} from '../../lib/online-prayer';
import {
  APPEARANCE, DEVICE, DOCK_MODES, REMIND, VOICES, chooserHtml, citiesOf, commit, countries, methodItems, regionOf, moveDraft, pick, rowsFor, rowsHtml,
  savedDraft, sectionName, stationChoice, stationsOf, onlineHtml, type SPrefs,
} from '../settings';

onScreen('settings', (stage, signal) => {
  const $ = <T extends HTMLElement = HTMLElement>(s: string) => stage.querySelector<T>(s)!;
  const root = $('[data-settings]');
  const rowsEl = $('[data-rows]');
  const chooserEl = $('[data-chooser]');
  const toastEl = $('[data-toast-out]');
  const audio = $<HTMLAudioElement>('[data-audio]');
  const navs = [...root.querySelectorAll<HTMLElement>('[data-nav]')];
  const lang = document.documentElement.lang as Lang;
  const rtl = document.documentElement.dir === 'rtl';
  const FOCUS = 'miqat-settings-focus';

  let section = 0;
  let offsetsPage = false;
  let offsetsParentRow = 0;
  const rowCtx = () => ctx;
  let lastRows = rowsEl.innerHTML;
  let radioOn = false;
  let ctx!: Ctx;
  const onlineEl = $('[data-online]'), onlineBody = $('[data-online-body]');
  const onlineBack = $('[data-online-back]'), onlineRetry = $('[data-online-retry]');
  type OnlineView = {row: number; request: AbortController | null};
  let online: OnlineView | null = null;
  const onlineBackground = [...root.children].filter(el => el !== onlineEl) as HTMLElement[];

  const aboutEl = $('[data-about]'), aboutBack = $('[data-about-back]');
  const aboutBackground = [...root.children].filter(el => el !== aboutEl) as HTMLElement[];
  let aboutRow: number | null = null;
  const closeAbout = () => {
    if (aboutRow === null) return;
    const row = aboutRow;
    aboutRow = null;
    aboutEl.hidden = true;
    aboutBackground.forEach(el => el.inert = false);
    focusRow(row);
  };

  const pairingEl=$('[data-pairing]'),pairingBody=$('[data-pairing-body]'),pairingBack=$('[data-pair-action=back]');
  let pairingRow:number|null=null,pairingState=pairingDemo();
  const pairingBackground=[...root.children].filter(el=>el!==pairingEl) as HTMLElement[];
  const renderPairing=()=>{
    pairingBody.innerHTML=pairingHtml(ctx.t,pairingState);
    (pairingBody.querySelector<HTMLElement>(pairingState.phase==='pending'?'[data-pair-action=reject]':pairingState.phase==='approved'?'[data-pair-action=cancel]':'[data-pair-action]')??pairingBack).focus({preventScroll:true});
  };
  const closePairing=(restoreFocus=true)=>{
    if(pairingRow===null)return;const row=pairingRow;pairingRow=null;pairingState=pairingStep(pairingState,'cancel');pairingEl.hidden=true;pairingBody.replaceChildren();
    pairingBackground.forEach(el=>el.inert=false);if(restoreFocus)focusRow(row);
  };

  const rowEls = () => [...rowsEl.querySelectorAll<HTMLElement>('[data-row]')];
  const focusRow = (i: number) => {
    const r = rowEls()[Math.max(0, Math.min(rowEls().length - 1, i))];
    if (!r) return;
    r.focus({ preventScroll: true });
    if (rowsEl.classList.contains('scrolling')) {
      const pad = parseFloat(getComputedStyle(rowsEl).paddingTop);
      if (r.offsetTop - pad < rowsEl.scrollTop) rowsEl.scrollTop = r.offsetTop - pad;
      else if (r.offsetTop + r.offsetHeight + pad > rowsEl.scrollTop + rowsEl.clientHeight)
        rowsEl.scrollTop = r.offsetTop + r.offsetHeight + pad - rowsEl.clientHeight;
    }
  };
  const focusedRow = () => { const a = document.activeElement as HTMLElement | null; return a && rowsEl.contains(a) ? +(a.dataset.row ?? -1) : -1; };
  const focusNav = () => navs[section].focus({ preventScroll: true });

  // The rows are re-rendered only when their HTML changes (a pref, the date), keeping focus on the same row.
  const rerender = () => {
    $('[data-appearance-preview]').hidden = section !== APPEARANCE;
    root.classList.toggle('live-appearance', section === APPEARANCE);
    $('[data-theme-name]').textContent = ctx.t.list('themeNames')[Math.max(0, THEMES.indexOf(rowCtx().prefs.theme ?? 'mihrab'))];
    rowsEl.classList.toggle('scrolling', true);
    rowsEl.classList.toggle('appearance', section === APPEARANCE);
    if (section === APPEARANCE) $('[data-home-plan]').innerHTML = homePreview(ctx.prefs, rtl);
    $('[data-label]').textContent = offsetsPage ? ctx.t.s('prayerOffsets') : sectionName(ctx.t, section);
    const h = rowsHtml(rowCtx(), section, radioOn, null, offsetsPage);
    if (h === lastRows) return;
    const fi = focusedRow();
    rowsEl.innerHTML = h;
    lastRows = h;
    if (fi >= 0) focusRow(fi);
  };
  const update = live(stage, signal, () => ({ html: {} }), (c) => { ctx = c; rerender(); });
  // ✓ Saved at the end of the hint line after every save, as long as motion.saved-lifetime.
  const savedEl = $('[data-saved]');
  const lifetime = parseFloat(getComputedStyle(savedEl).getPropertyValue('--motion-saved-lifetime')) * 1000 || 2000;
  let savedTimer = 0;
  const showSaved = () => {
    savedEl.classList.add('on');
    clearTimeout(savedTimer);
    savedTimer = window.setTimeout(() => savedEl.classList.remove('on'), lifetime);
  };
  const save = (p: SPrefs | null) => {
    if (!p) return;
    update(p.appearanceCycle !== ctx.prefs.appearanceCycle ? armCycle(p, Date.now()) : p);
    showSaved();
  };

  const setSection = (n: number) => {
    if (n === section && !offsetsPage) return;
    offsetsPage = false;
    rowsEl.scrollTop = 0;
    section = n;
    navs.forEach((b, i) => {
      b.classList.toggle('open', i === n);
      b.toggleAttribute('data-focus-start', i === n);
    });
    $('[data-label]').textContent = sectionName(ctx.t, n);
    rerender();
  };

  let toastTimer = 0;
  const toast = (s: string) => {
    toastEl.textContent = s;
    toastEl.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = window.setTimeout(() => (toastEl.hidden = true), 2600);
  };

  // ------------------------------------------------------------ radio (the TV's toggle plays the stream)
  const setRadio = (on: boolean) => {
    radioOn = on;
    if (on) { audio.src = stationOf(ctx.prefs).url; audio.play().catch(() => { radioOn = false; rerender(); }); }
    else { audio.pause(); audio.removeAttribute('src'); audio.load(); }
    rerender();
  };
  audio.addEventListener('error', () => { if (radioOn && audio.getAttribute('src')) { radioOn = false; rerender(); } });

  // ------------------------------------------------------------ chooser
  let chooser: { back?: () => void; row: number } | null = null;
  const items = () => [...chooserEl.querySelectorAll<HTMLElement>('[data-item]')];
  const focusItem = (i: number) => {
    const all = items();
    const el = all[Math.max(0, Math.min(all.length - 1, i))];
    if (!el) return;
    el.focus({ preventScroll: true });
    const list = el.parentElement!;
    if (el.offsetTop < list.scrollTop) list.scrollTop = el.offsetTop - 10;
    else if (el.offsetTop + el.offsetHeight > list.scrollTop + list.clientHeight) list.scrollTop = el.offsetTop + el.offsetHeight - list.clientHeight + 10;
  };
  let onPick: (i: number) => void = () => {};
  const open = (title: string, list: { label: string; chosen: boolean }[], picked: (i: number) => void, back?: () => void) => {
    const row = chooser?.row ?? focusedRow();
    chooser = { back, row };
    onPick = picked;
    chooserEl.innerHTML = chooserHtml(ctx.t, title, list);
    chooserEl.hidden = false;
    focusItem(Math.max(0, list.findIndex((x) => x.chosen)));
  };
  const close = (runBack: boolean) => {
    if (!chooser) return;
    const { back, row } = chooser;
    if (runBack && back) { back(); return; }
    chooser = null;
    chooserEl.hidden = true;
    chooserEl.innerHTML = '';
    focusRow(row);
  };
  const cancelOnline = () => {
    if (!online?.request) return;
    const request = online.request; online.request = null; request.abort();
    onlineBody.textContent = ctx.t.s('onlineUnavailable'); onlineRetry.hidden = false;
  };
  const closeOnline = (restoreFocus = true) => {
    if (!online) return;
    const row = online.row; cancelOnline(); online = null; onlineEl.hidden = true;
    onlineBackground.forEach(el => { el.inert = false; });
    onlineBody.replaceChildren();
    if (restoreFocus) focusRow(row);
  };
  const checkOnline = async (row: number) => {
    cancelOnline();
    const controller = new AbortController(), snapshot = ctx;
    const owner: OnlineView = {row, request: controller};
    online = owner; onlineEl.hidden = false; onlineRetry.hidden = true;
    onlineBackground.forEach(el => { el.inert = true; });
    onlineBody.textContent = ctx.t.s('onlineLoading'); onlineBack.focus({preventScroll: true});
    try {
      const result = await fetchComparison(onlineRequest(snapshot.now, snapshot.prefs), controller.signal);
      if (online !== owner || !owner.request) return;
      onlineBody.innerHTML = onlineHtml(snapshot, result); onlineRetry.hidden = false;
    } catch {
      if (online !== owner || !owner.request) return;
      onlineBody.textContent = ctx.t.s('onlineUnavailable'); onlineRetry.hidden = false;
    } finally { if (online === owner) owner.request = null; }
  };
  document.addEventListener('visibilitychange', () => { if (document.hidden) { cancelOnline(); closePairing(false); } }, {signal});
  window.addEventListener('pagehide', () => { closeOnline(false); closePairing(false); }, {signal});
  const choose = (i: number) => {
    const before = chooser;
    onPick(i);
    if (chooser === before) close(false); // picking may open the next step instead
  };

  const p = () => ({ ...(rowCtx().prefs as SPrefs) });
  // Region, then country, then city, as on the TV.
  const openRegions = () => {
    const t = ctx.t, mine = regionOf(cityOf(ctx.prefs));
    open(t.s('region'), t.list('regions').map((r, i) => ({ label: r, chosen: i === mine })), (i) => openCountries(i));
  };
  const openCountries = (region: number) => {
    const t = ctx.t, list = countries(t, region), mine = t.ar ? cityOf(ctx.prefs).countryAr : cityOf(ctx.prefs).countryEn;
    chooser!.back = undefined;
    open(t.list('regions')[region], list.map((c) => ({ label: c, chosen: c === mine })), (i) => openCities(list[i], region), openRegions);
  };
  // A country with one city takes it at once. Picking a city puts the method and Asr school back to that city's own.
  const openCities = (country: string, region: number) => {
    const found = citiesOf(ctx.t, country);
    const take = (id: string) => save({ ...p(), city: id, method: null, hanafi: CITIES.find((c) => c.id === id)!.hanafi });
    if (found.length === 1) { take(found[0].id); return; }
    chooser!.back = undefined;
    open(country, found.map((c) => ({ label: ctx.t.ar ? c.ar : c.en, chosen: c.id === ctx.prefs.city })), (i) => take(found[i].id), () => openCountries(region));
  };
  const openStationGroups = () => {
    const t = ctx.t, st = stationOf(ctx.prefs);
    open(t.s('stationRow'), t.list('stationGroups').map((g, i) => ({ label: g, chosen: i === (st.reciter ? 1 : 0) })),
      (i) => openStations(i === 1));
  };
  const openStations = (reciters: boolean) => {
    const found = stationsOf(reciters), cur = ctx.prefs.station;
    open(ctx.t.list('stationGroups')[reciters ? 1 : 0], found.map((s) => ({ label: stationChoice(ctx.t, s), chosen: s.id === cur })), (i) => {
      save({ ...p(), station: found[i].id });
      if (radioOn) setRadio(true); // playing radio moves to the pick
    }, openStationGroups);
  };
  const openVoices = (fajr: boolean) => {
    const key = fajr ? 'voiceFajr' : 'voice', cur = (ctx.prefs as SPrefs)[key] ?? (fajr ? 'chebae_fajr' : 'almalki');
    open(ctx.t.s(fajr ? 'fajrVoiceRow' : 'voiceRow'), VOICES.map((v) => ({ label: ctx.t.ar ? v.ar : v.en, chosen: v.id === cur })),
      (i) => save({ ...p(), [key]: VOICES[i].id }));
  };

  // Example assignments stay in this browser. Android uses the TV's installed-app list.
  const remoteDefaults: Record<string, string> = { youtube: 'app:0', netflix: 'app:1', web: 'app:3', settings: '@settings', inputs: '@inputs', apps: '@apps', red: '@default', green: '@default', yellow: '@default', blue: '@default' };
  const remoteKeys = Object.keys(remoteDefaults);
  const remoteLabel = (key: string) => ({ youtube: 'YouTube', netflix: 'Netflix', web: ctx.t.list('buttonNames')[4],
    settings: ctx.t.list('buttonNames')[0], inputs: ctx.t.s('inputsTitle'), apps: ctx.t.list('buttonActions')[2], red: ctx.t.list('colourButtons')[0], green: ctx.t.list('colourButtons')[1], yellow: ctx.t.list('colourButtons')[2], blue: ctx.t.list('colourButtons')[3] })[key] ?? key;
  const remoteValues = ['@default', '@home', '@apps', '@inputs', '@settings', '@dock'];
  const assignmentLabel = (value: string) => value.startsWith('app:')
    ? APPS[Number(value.slice(4))]?.[ctx.t.ar ? 'ar' : 'en'] ?? value
    : ctx.t.list('buttonActions')[remoteValues.indexOf(value)] ?? value;
  const openRemoteButtons = () => {
    const bindings = ctx.prefs.remoteButtons ?? remoteDefaults;
    const labels = [ctx.t.s('pressButton'), ...remoteKeys.map(key => `${remoteLabel(key)} · ${assignmentLabel(bindings[key] ?? '@default')}`), ctx.t.s('restoreButtons')];
    open(ctx.t.s('remoteButtons'), labels.map(label => ({ label, chosen: false })), i => {
      if (i === 0) open(ctx.t.s('pressButton'), [{ label: ctx.t.s('buttonPreviewHelp'), chosen: false }], openRemoteButtons, openRemoteButtons);
      else if (i <= remoteKeys.length) openRemoteApps(remoteKeys[i - 1]);
      else { save({ ...p(), remoteButtons: Object.fromEntries(remoteKeys.map(key => [key, '@default'])) }); openRemoteButtons(); }
    });
  };
  const openRemoteApps = (key: string) => {
    const values = [...remoteValues, ...APPS.map((_, i) => `app:${i}`)];
    const current = (ctx.prefs.remoteButtons ?? remoteDefaults)[key] ?? '@default';
    open(remoteLabel(key), values.map(value => ({ label: assignmentLabel(value), chosen: current === value })), i => {
      save({ ...p(), remoteButtons: { ...(ctx.prefs.remoteButtons ?? remoteDefaults), [key]: values[i] } });
      openRemoteButtons();
    }, openRemoteButtons);
  };

  // ------------------------------------------------------------ rows
  const W = lang === 'ar' ? 'على التلفاز يفتح هذا' : 'On the TV this opens';
  const goLang = (i: number) => {
    const to: Lang = i === 0 ? (navigator.language.toLowerCase().startsWith('ar') ? 'ar' : 'en') : i === 1 ? 'ar' : 'en';
    if (to === lang) return;
    try { sessionStorage.setItem(FOCUS, JSON.stringify({ section, row: focusedRow() })); } catch { /* fine */ }
    navigate(href('settings', to) + '#tv');
  };
  const adjust = (i: number, direction: number) => {
    const row = rowsFor(ctx, section, radioOn, offsetsPage)[i];
    const next = moveDraft(row, savedDraft(ctx, row), direction);
    if (row.id === 'lang') goLang(next);
    else save(commit(ctx, row, next));
  };

  /** OK opens or edits a row; carousels preview directly with Left/Right. */
  const act = (i: number) => {
    const r = rowsFor(rowCtx(), section, radioOn, offsetsPage)[i];
    if (!r) return;
    if(r.id==='device.companion'){pairingRow=i;pairingEl.hidden=false;pairingBackground.forEach(el=>el.inert=true);renderPairing();return;}
    if (r.id === 'device.about') {
      aboutRow = i;
      aboutEl.hidden = false;
      aboutBackground.forEach(el => el.inert = true);
      aboutBack.focus({preventScroll: true});
      return;
    }
    if (r.id === 'prayer.online') { void checkOnline(i); return; }
    if (r.id === 'homeReset') save({ ...ctx.prefs, homeCountdown: 0, homeSide: 0, homeBar: 0, homePrayers: 0, homeLayout: 'two_rows' });
    else if (r.kind === 'carousel') return;
    else if (r.kind === 'seg' || r.kind === 'step') return;
    else if (r.id === 'radio') setRadio(!radioOn);
    else if (r.id === 'resume') save({ ...p(), radioResume: p().radioResume === false });
    else if (r.id === 'quickLabels') save({ ...p(), quickLabels: !p().quickLabels });
    else if (r.id === 'reduceMotion') save({ ...p(), reduceMotion: !p().reduceMotion });
    else if (r.id === 'repairService') save({ ...p(), repairService: !ctx.prefs.repairService });
    else if (r.id === 'adhan') save({ ...p(), adhan: !ctx.prefs.adhan });
    else if (r.kind === 'value') {
      if (r.id === 'offsets') { offsetsPage = true; offsetsParentRow = i; rerender(); focusRow(0); }
      else if (r.id === 'remoteButtons') openRemoteButtons();
      else if (r.id === 'dockMode') open(ctx.t.s('prayerDock'), ctx.t.list('dockModes').map((label, k) => ({ label, chosen: DOCK_MODES[k] === (ctx.prefs.dockMode ?? 'hidden') })), k => save({ ...p(), dockMode: DOCK_MODES[k] }));
      else if (r.id === 'city') openRegions();
      else if (r.id === 'method') {
        open(ctx.t.s('method'), methodItems(ctx.t, ctx.prefs), (k) => save({ ...p(), method: METHODS[k] }));
      } else if (r.id === 'remind') {
        open(ctx.t.s('remind'), ctx.t.list('remindChoices').map((s, k) => ({ label: ctx.t.d(s), chosen: k === r.selected })),
          (k) => save({ ...p(), remind: REMIND[k] }));
      } else if (r.id === 'station') openStationGroups();
      else if (r.id === 'voice' || r.id === 'voiceFajr') openVoices(r.id === 'voiceFajr');
      else if (r.id === 'room') navigate(href('prayer-day', lang) + '#tv');
      else toast(`${W} ${r.label}`);
    }
  };

  // ------------------------------------------------------------ keys: the D-pad map of SettingsActivity
  root.addEventListener('keydown', (e) => {
    if (e.altKey || e.ctrlKey || e.metaKey || e.shiftKey && e.key !== 'Tab') return;
    const k = e.key;
    const a = document.activeElement as HTMLElement;
    const ok = k === 'Enter' || k === ' ';
    const back = k === 'Escape' || k === 'Backspace';
    const vert = k === 'ArrowUp' ? -1 : k === 'ArrowDown' ? 1 : 0;
    const horiz = k === 'ArrowRight' ? 1 : k === 'ArrowLeft' ? -1 : 0;
    const toEnd = rtl ? -horiz : horiz; // +1 toward the end of the line, mirrored in RTL
    const handled = () => { e.preventDefault(); e.stopPropagation(); };

    if (aboutRow !== null) {
      if (ok || back || vert || horiz || k === 'Tab') handled();
      if (ok || back) closeAbout();
      return;
    }
    if(pairingRow!==null){
      if(!ok&&!back&&!vert&&!horiz&&k!=='Tab')return;handled();
      if(back)closePairing();
      else if(ok&&pairingEl.contains(a))a.click();
      else if(vert||k==='Tab'){
        const buttons=[...pairingEl.querySelectorAll<HTMLButtonElement>('button:not(:disabled)')];
        buttons[Math.max(0,Math.min(buttons.length-1,buttons.indexOf(a as HTMLButtonElement)+(vert||1)))]?.focus({preventScroll:true});
      }
      return;
    }

    if (online) {
      if (!ok && !back && !vert && !horiz && k !== 'Tab') return;
      handled();
      if (back) closeOnline();
      else if (ok && (a === onlineBack || a === onlineRetry)) a.click();
      else if (vert || k === 'Tab') {
        const retry = !onlineRetry.hidden && (vert > 0 || k === 'Tab' && a === onlineBack);
        (retry ? onlineRetry : onlineBack).focus({preventScroll: true});
      }
      return;
    }
    if (!ok && !back && !vert && !horiz) return;

    if (chooser) {
      handled();
      const i = items().indexOf(a);
      if (back) close(true);
      else if (vert) focusItem(i < 0 ? 0 : i + vert);
      else if (ok && i >= 0) choose(i);
      return;
    }
    if (a?.dataset.nav != null) {
      if (back) return; // tv.ts takes Back to [data-home]
      handled();
      const i = +a.dataset.nav;
      if (vert) navs[Math.max(0, Math.min(navs.length - 1, i + vert))].focus({ preventScroll: true });
      else if (ok || toEnd > 0) focusRow(0);
      return;
    }
    const row = a?.dataset.row;
    if (row == null) return;
    handled();
    // Carousels consume Left/Right; other rows return to sections toward the start.
    if (back && offsetsPage) { offsetsPage = false; rerender(); focusRow(offsetsParentRow); }
    else if (back) focusNav();
    else if (vert) focusRow(+row + vert);
    else if (horiz) {
      const r = rowsFor(rowCtx(), section, radioOn, offsetsPage)[+row];
      if (r.kind === 'carousel') save(pick(rowCtx(), r.id, Math.max(0, Math.min(r.segs!.length - 1, r.selected! + toEnd))));
      else if (r.kind === 'seg' || r.kind === 'step') adjust(+row, toEnd);
      else if (toEnd < 0) focusNav();
    }
    else act(+row);
  });

  // Focus on a section shows it at once, as on the TV.
  root.addEventListener('focusin', (e) => {
    if(pairingRow!==null){if(!pairingEl.contains(e.target as Node))pairingBack.focus({preventScroll:true});return;}
    if (online) {
      if (!onlineEl.contains(e.target as Node)) onlineBack.focus({preventScroll: true});
      return;
    }
    const n = (e.target as HTMLElement).dataset?.nav;
    if (n != null) setSection(+n);
  });

  // Pointer: the same actions.
  root.addEventListener('click', (e) => {
    const el = e.target as HTMLElement;
    if (aboutRow !== null) {
      if (el.closest('[data-about-back]')) closeAbout();
      return;
    }
    if(pairingRow!==null){
      const button=el.closest<HTMLElement>('[data-pair-action]');if(!button)return;
      const action=button.dataset.pairAction;if(action==='back')closePairing();
      else {pairingState=pairingStep(pairingState,action as PairingAction,Number(button.dataset.pairPhone));renderPairing();}
      return;
    }

    if (online) {
      if (el.closest('[data-online-back]')) closeOnline();
      else if (el.closest('[data-online-retry]')) void checkOnline(online.row);
      return;
    }
    if (chooser) {
      const item = el.closest<HTMLElement>('[data-item]');
      if (item) choose(+item.dataset.item!);
      else if (!el.closest('.ch-card')) close(false);
      return;
    }
    const nav = el.closest<HTMLElement>('[data-nav]');
    if (nav) { setSection(+nav.dataset.nav!); focusRow(0); return; }
    const rowEl = el.closest<HTMLElement>('[data-row]');
    if (!rowEl) return;
    const i = +rowEl.dataset.row!;
    const seg = el.closest<HTMLElement>('[data-seg]');
    const step = el.closest<HTMLElement>('[data-step]');
    const r = rowsFor(rowCtx(), section, radioOn, offsetsPage)[i];
    if (seg && (r.kind === 'seg' || r.kind === 'carousel')) {
      if (r.id === 'lang') goLang(+seg.dataset.seg!);
      else save(pick(rowCtx(), r.id, +seg.dataset.seg!));
    } else if (step) save(commit(rowCtx(), r, moveDraft(r, savedDraft(rowCtx(), r), +step.dataset.step!)));
    else act(i);
  });

  // Back from the other language's page (Language row): the same section and row.
  try {
    const f = JSON.parse(sessionStorage.getItem(FOCUS) ?? 'null');
    sessionStorage.removeItem(FOCUS);
    if (f) requestAnimationFrame(() => { if (signal.aborted) return; setSection(f.section); if (f.row >= 0) focusRow(f.row); else focusNav(); });
  } catch { /* fine */ }
  signal.addEventListener('abort', () => { clearTimeout(savedTimer); clearTimeout(toastTimer); closeOnline(false); closePairing(false); audio.pause(); audio.removeAttribute('src'); audio.load(); }, {once: true});
});
