import { cityOf, DEFAULT_PREFS } from './data';
import { civilPrayerTimes, prayerDay } from './prayer';

export const PHONE_DEMO_CITIES = [
  { id: 'cairo', zone: 'Africa/Cairo' },
  { id: 'makkah', zone: 'Asia/Riyadh' },
  { id: 'london', zone: 'Europe/London' },
].map(place => ({ ...cityOf({ ...DEFAULT_PREFS, city: place.id }), ...place }));

export function phoneDemoDay(now: Date, id: string, offset = 0) {
  const place = PHONE_DEMO_CITIES.find(city => city.id === id) ?? PHONE_DEMO_CITIES[0];
  const dateParts = new Intl.DateTimeFormat('en', {
    timeZone: place.zone, day: 'numeric', month: 'numeric', year: 'numeric',
  }).formatToParts(now);
  const part = (name: string) => Number(dateParts.find(value => value.type === name)?.value);
  const date = new Date(part('year'), part('month') - 1, part('day'), 12);
  const prefs = { ...DEFAULT_PREFS, city: place.id, hanafi: place.hanafi };
  const today = prayerDay(now, prefs, date);
  const calendar = new Date(date);
  calendar.setDate(date.getDate() + offset);
  const times = civilPrayerTimes(date, prefs);
  return { place, today, calendar, times, calendarTimes: offset ? civilPrayerTimes(calendar, prefs) : times };
}
