import { phoneDemoDay } from '../lib/phone-demo';
import { duration, gregorian, hijri, hijriOf, secondsUntil } from '../lib/prayer';
import { makeText, type Lang } from '../lib/text';

const demo = document.querySelector<HTMLElement>('[data-phone-demo]');
if (demo) {
  const $ = <T extends HTMLElement = HTMLElement>(selector: string) => demo.querySelector<T>(selector)!;
  const all = <T extends HTMLElement = HTMLElement>(selector: string) => [...demo.querySelectorAll<T>(selector)];
  const t = makeText(document.documentElement.lang as Lang);
  const names = t.list('prayerNames');
  const location = $<HTMLSelectElement>('[data-phone-city]');
  const prayer = $<HTMLSelectElement>('[data-phone-prayer-select]');
  const sound = $<HTMLSelectElement>('[data-phone-sound]');
  const alert = $<HTMLInputElement>('[data-phone-alert]');
  const radio = $<HTMLInputElement>('[data-phone-radio]');
  const inputs = all<HTMLButtonElement>('[data-phone-input]');
  const modes = new Map<string, string>();
  let calendarOffset = 0;
  let renderedMinute = -1;
  let next = new Date(0);

  function render(now = new Date()) {
    const { place, today, calendar, times, calendarTimes } = phoneDemoDay(now, location.value, calendarOffset);
    const format = new Intl.DateTimeFormat(t.ar ? 'ar-EG' : 'en-GB', {
      timeZone: place.zone, hour: 'numeric', minute: '2-digit', hour12: true,
    });
    const displayTime = (time: Date) => format.format(time);
    const calendarHijri = hijriOf(calendar);
    $('[data-phone-hijri]').textContent = hijri(t, today);
    $('[data-phone-next-name]').textContent = names[today.nextIndex];
    $('[data-phone-next-time]').textContent = displayTime(today.nextTime);
    next = today.nextTime;
    $('[data-phone-calendar-hijri]').textContent = t.d(
      `${calendarHijri.day} ${t.list('hijriMonths')[calendarHijri.month]} ${calendarHijri.year}${t.s('hijriSuffix')}`,
    );
    $('[data-phone-calendar-date]').textContent = gregorian(t, calendar);
    all('[data-phone-prayers] li').forEach((row, index) => {
      row.toggleAttribute('data-next', index === today.nextIndex && +times[index] === +next);
      row.querySelector('time')!.textContent = displayTime(times[index]);
    });
    all('[data-phone-calendar-prayers] time').forEach((time, index) => time.textContent = displayTime(calendarTimes[index]));
    $('[data-phone-bearing]').textContent = t.bearingLabel(Math.round(today.qibla));
    $('[data-phone-needle]').style.setProperty('--bearing', `${today.qibla}deg`);
    renderedMinute = Math.floor(+now / 60000);
    $('[data-phone-countdown]').textContent = t.d(duration(secondsUntil(next, now), true));
  }

  function open(panel: string) {
    for (const view of all('[data-phone-panel]')) view.hidden = view.dataset.phonePanel !== panel;
    for (const button of all('.phone-demo-nav button')) {
      const active = button.dataset.phoneOpen === panel
        || button.dataset.phoneOpen === 'prayer' && ['calendar', 'qibla'].includes(panel);
      if (active) button.setAttribute('aria-current', 'true');
      else button.removeAttribute('aria-current');
    }
    if (panel !== 'settings') $<HTMLAudioElement>('audio').pause();
    demo.querySelector<HTMLElement>('[data-phone-panel]:not([hidden]) h4[tabindex]')?.focus({ preventScroll: true });
  }

  for (const button of all('[data-phone-open]')) button.addEventListener('click', () => open(button.dataset.phoneOpen!));
  document.querySelector('[data-open-phone-tvs]')?.addEventListener('click', () => open('tvs'));
  for (const button of all('[data-phone-date]')) button.addEventListener('click', () => {
    const change = Number(button.dataset.phoneDate);
    calendarOffset = change ? calendarOffset + change : 0;
    render();
  });
  location.addEventListener('change', () => { calendarOffset = 0; render(); });
  alert.addEventListener('change', () => {
    $('[data-phone-alert-status]').textContent = (alert.checked ? demo.dataset.alertOn : demo.dataset.alertOff)!;
  });
  const updateSound = () => { sound.value = modes.get(prayer.value) ?? 'adhan'; };
  prayer.addEventListener('change', updateSound);
  sound.addEventListener('change', () => modes.set(prayer.value, sound.value));
  for (const button of all('[data-phone-prayer]')) button.addEventListener('click', () => {
    prayer.value = button.dataset.phonePrayer!;
    updateSound();
    open('settings');
  });
  function selectInput(input: HTMLButtonElement) {
    $('[data-phone-input-label]').textContent = input.textContent;
    for (const button of inputs) button.setAttribute('aria-pressed', String(button === input));
  }
  for (const input of inputs) input.addEventListener('click', () => selectInput(input));
  const pair = $('[data-phone-pair]');
  const remote = $('[data-phone-remote]');
  pair.addEventListener('click', () => {
    pair.hidden = true;
    remote.hidden = false;
    inputs[0].focus({ preventScroll: true });
  });
  const updateRadio = () => {
    $('[data-phone-radio-status]').textContent = (radio.checked ? demo.dataset.radioOn : demo.dataset.radioOff)!;
  };
  radio.addEventListener('change', updateRadio);
  $('[data-phone-unpair]').addEventListener('click', () => {
    remote.hidden = true;
    pair.hidden = false;
    radio.checked = false;
    updateRadio();
    selectInput(inputs[0]);
    pair.focus({ preventScroll: true });
  });

  render();
  window.setInterval(() => {
    const now = new Date();
    if (Math.floor(+now / 60000) !== renderedMinute) render(now);
    else $('[data-phone-countdown]').textContent = t.d(duration(secondsUntil(next, now), true));
  }, 1000);
}
