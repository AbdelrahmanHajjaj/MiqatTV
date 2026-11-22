// Every page of the preview: one per TV screen or mode, in Arabic (default, at the root) and English (/en/).
import type { Lang } from './text';
import { CITIES } from './data';

const cityCount = CITIES.length;
const countryCount = new Set(CITIES.map((city) => city.countryEn)).size;

export const REPO = 'https://github.com/AbdelrahmanHajjaj/MiqatTV';
export const INSTALL = `${REPO}#install`;

type L = Record<Lang, string>;
export type Page = {
  slug: string; // '' is Home
  screen: 'home' | 'prayer-day' | 'apps' | 'settings' | 'call' | 'screensaver';
  mode?: 'friday' | 'ramadan' | 'reminder' | 'call';
  nav: L;
  title: L;
  description: L;
  body: Record<Lang, string[]>;
};

export const PAGES: Page[] = [
  {
    slug: '', screen: 'home',
    nav: { ar: 'الرئيسية', en: 'Home' },
    title: { ar: 'ميقات · مواقيت الصلاة على هاتف أندرويد والتلفاز', en: 'Miqat · Prayer times for Android phones and TVs' },
    description: {
      ar: 'مواقيت الصلاة والأذان والتقويم الهجري والقبلة والويدجت على هاتف أندرويد، مع وضع رفيق التلفاز وتطبيقَي Android TV وLG webOS. بالعربية والإنجليزية.',
      en: 'Offline prayer times, Adhan, Hijri calendar, Qibla and widgets on Android phones, with TV companion mode and Android TV / LG webOS apps. Arabic and English.',
    },
    body: {
      ar: [
        'الصلاة القادمة والعدّ التنازلي داخل المحراب، ومواقيت اليوم وتطبيقاتك المفضلة بجانبه. يعمل ميقات كشاشة رئيسية على أندرويد TV، وكتطبيق مستقل يعرض HDMI على LG.',
        `المواقيت تُحسب على التلفاز نفسه دون إنترنت، لـ ${cityCount} مدينة في ${countryCount} دولة، مع اختيار طريقة الحساب ومذهب العصر وتعديل التاريخ الهجري.`,
      ],
      en: [
        'The next prayer and countdown sit inside the mihrab, with today’s times and your favourite apps alongside. Android TV uses Miqat as Home; LG runs it as a separate app with HDMI.',
        `Times are calculated on the TV, fully offline, for ${cityCount} cities in ${countryCount} countries, with your choice of calculation method, Asr school and Hijri date offset.`,
      ],
    },
  },
  {
    slug: 'prayer-day', screen: 'prayer-day',
    nav: { ar: 'مواقيت اليوم والقبلة', en: 'Today & Qibla' },
    title: { ar: 'مواقيت الصلاة اليوم والقبلة على التلفاز · ميقات', en: 'Today’s prayer times and Qibla on your TV · Miqat' },
    description: {
      ar: 'مواقيت اليوم مع الوقت المتبقي ومفاتيح الأذان لكل صلاة واتجاه القبلة من مدينتك.',
      en: 'Today’s prayer times, countdowns, per-prayer Adhan switches and the Qibla bearing from your city.',
    },
    body: {
      ar: [
        'شاشة «مواقيت اليوم» تعرض المواقيت الست مع الوقت المتبقي، ومفتاحًا مستقلًا لأذان كل صلاة.',
        'تعرض البوصلة اتجاه القبلة من مدينتك، مع ضبط اتجاه الغرفة على أندرويد TV.',
      ],
      en: [
        'Today’s prayers shows all six times with the time left to each, and an independent Adhan switch for each prayer.',
        'The compass shows the Qibla bearing from your city, with room-facing calibration on Android TV.',
      ],
    },
  },
  {
    slug: 'reminder', screen: 'home', mode: 'reminder',
    nav: { ar: 'تذكير قبل الصلاة', en: 'Reminder' },
    title: { ar: 'تذكير قبل الصلاة على التلفاز · ميقات', en: 'A reminder before each prayer on your TV · Miqat' },
    description: {
      ar: 'تظهر التذكيرات على شاشة ميقات الرئيسية. ويمكن لخدمة الصلاة المفعّلة على أندرويد إظهار شريط فوق التطبيقات والمداخل المدعومة.',
      en: 'Prayer reminders appear on Miqat Home. Android’s enabled prayer service can also show a banner over supported apps and inputs.',
    },
    body: {
      ar: ['تختار في الإعدادات متى يظهر التذكير: قبل الصلاة بعشر دقائق أو بخمس عشرة، أو إيقافه. ويتحوّل إطار المحراب إلى الذهبي الفاتح في آخر ربع ساعة.'],
      en: ['Settings picks when the reminder comes: 10 or 15 minutes before the prayer, or off. The arch outline turns light gold in the last quarter hour.'],
    },
  },
  {
    slug: 'call', screen: 'call', mode: 'call',
    nav: { ar: 'الأذان', en: 'The call' },
    title: { ar: 'الأذان على التلفاز في وقته · ميقات', en: 'The adhan on your TV, on time · Miqat' },
    description: {
      ar: 'أذان مجدول مع ١٣ صوتًا مدمجًا وتسجيل خاص للفجر.',
      en: 'Scheduled Adhan with 13 built-in voices and a separate Fajr recording.',
    },
    body: {
      ar: ['اضبط مستوى الأذان من الإعدادات. أغلق شاشته بزر الرجوع، أو انتظر الإغلاق التلقائي بعد عشر دقائق.'],
      en: ['Set the Adhan level in Settings. Press Back to dismiss its screen, or let it close after ten minutes.'],
    },
  },
  {
    slug: 'friday', screen: 'home', mode: 'friday',
    nav: { ar: 'الجمعة', en: 'Friday' },
    title: { ar: 'وضع الجمعة: صلاة الجمعة وسورة الكهف · ميقات', en: 'Friday mode: Jumu’ah and Surat Al-Kahf · Miqat' },
    description: {
      ar: 'يوم الجمعة تحلّ صلاة الجمعة محل الظهر في المحراب وفي المواقيت، مع تذكير بقراءة سورة الكهف.',
      en: 'On Fridays Jumu’ah replaces Dhuhr in the arch and the times, with a reminder to read Surat Al-Kahf.',
    },
    body: {
      ar: ['يبدأ وضع الجمعة تلقائيًا كل جمعة.'],
      en: ['Friday mode starts each Friday.'],
    },
  },
  {
    slug: 'ramadan', screen: 'home', mode: 'ramadan',
    nav: { ar: 'رمضان', en: 'Ramadan' },
    title: { ar: 'وضع رمضان: العد التنازلي للإفطار والسحور · ميقات', en: 'Ramadan mode: countdown to iftar and suhoor · Miqat' },
    description: {
      ar: 'في رمضان يعدّ المحراب تنازليًا إلى الإفطار، ويعرض موعد انتهاء السحور.',
      en: 'In Ramadan the arch counts down to iftar and shows when suhoor ends.',
    },
    body: {
      ar: ['يبدأ وضع رمضان وحده من التاريخ الهجري (أم القرى)، ويمكن تعديل التاريخ يومًا أو يومين في الإعدادات حسب رؤية الهلال في بلدك.'],
      en: ['Ramadan mode starts by itself from the Hijri date (Umm al-Qura), which Settings can shift a day or two to match the moon sighting where you live.'],
    },
  },
  {
    slug: 'apps', screen: 'apps',
    nav: { ar: 'كل التطبيقات', en: 'All apps' },
    title: { ar: 'كل التطبيقات · ميقات', en: 'All apps · Miqat' },
    description: {
      ar: 'كل التطبيقات المثبتة على التلفاز في شبكة واحدة، مع الصلاة القادمة في الأعلى. اضغط OK مطولًا لوضع تطبيق في المفضلة.',
      en: 'Every app on the TV in one grid, with the next prayer at the top. Hold OK to put an app in favourites.',
    },
    body: {
      ar: ['المعاينة تعرض أسماء تطبيقات للتوضيح؛ على التلفاز تظهر تطبيقاتك أنت.'],
      en: ['The preview shows example apps; on the TV you see your own.'],
    },
  },
  {
    slug: 'settings', screen: 'settings',
    nav: { ar: 'الإعدادات', en: 'Settings' },
    title: { ar: 'إعدادات ميقات: المدينة وطريقة الحساب والأذان والإذاعة', en: 'Miqat settings: city, method, adhan and radio' },
    description: {
      ar: 'اختر مدينتك وطريقة حساب المواقيت ومذهب العصر والتذكير وصوت الأذان ومحطة إذاعة القرآن الكريم واللغة والأرقام.',
      en: 'Choose your city, calculation method, Asr school, reminders, adhan voice, Quran radio station, language and digits.',
    },
    body: {
      ar: ['غيّر المدينة أو طريقة الحساب لعرض مواقيتك. تُحفظ إعدادات المعاينة في متصفحك.'],
      en: ['Choose a city and calculation method to see your prayer times. Preview settings stay in your browser.'],
    },
  },
  {
    slug: 'screensaver', screen: 'screensaver',
    nav: { ar: 'شاشة التوقف', en: 'Screensaver' },
    title: { ar: 'شاشة التوقف: محراب خافت والصلاة القادمة · ميقات', en: 'Screensaver: a dim arch and the next prayer · Miqat' },
    description: {
      ar: 'شاشة توقف خافتة: محراب ينتقل ببطء مع الساعة والصلاة القادمة، والإذاعة إن كانت تعمل.',
      en: 'A dim screensaver: a slowly drifting arch with the clock and the next prayer, plus the radio when it plays.',
    },
    body: {
      ar: ['تتحرك كل دقيقة لتحمي الشاشة، وتبقى خافتة لتناسب الغرفة ليلًا.'],
      en: ['It moves every minute to protect the panel and stays dim for a dark room.'],
    },
  },
];

export const BASE = import.meta.env.BASE_URL.replace(/\/?$/, '/');
/** Site-relative URL of a page in a language: Arabic at the root, English under /en/. */
export const href = (slug: string, lang: Lang) => BASE + (lang === 'en' ? 'en/' : '') + (slug ? slug + '/' : '');
export const pageOf = (slug: string) => PAGES.find((p) => p.slug === slug)!;
