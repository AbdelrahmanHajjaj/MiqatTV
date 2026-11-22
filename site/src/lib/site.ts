// Every page of the preview: one per TV screen or mode, in Arabic (default, at the root) and English (/en/).
import type { Lang } from './text';
export const REPO = 'https://github.com/AbdelrahmanHajjaj/MiqatTV';
export const RELEASE_TAG = 'v0.7.2-preview.2';

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
    title: { ar: 'ميقات · مواقيت الصلاة والأذان على هاتفك وتلفازك', en: 'Miqat · Prayer times and Adhan for your phone and TV' },
    description: {
      ar: 'تابع مواقيت صلاتك واستمع إلى الأذان على هاتف أندرويد وتلفاز البيت. تقويم هجري وقبلة وويدجت، مع تطبيقَي Android TV وLG webOS.',
      en: 'Prayer times and Adhan on your Android phone and TV. Discover the Hijri calendar, Qibla, home-screen widgets and apps for Android TV and LG webOS.',
    },
    body: {
      ar: [
        'تابع الصلاة القادمة ومواقيت اليوم، واستمع إلى الأذان وإذاعة القرآن على شاشة البيت.',
      ],
      en: [
        'Keep the next prayer and today’s times in view, with Adhan and Quran radio on the TV at home.',
      ],
    },
  },
  {
    slug: 'prayer-day', screen: 'prayer-day',
    nav: { ar: 'مواقيت اليوم والقبلة', en: 'Today & Qibla' },
    title: { ar: 'مواقيت الصلاة اليوم والقبلة على التلفاز · ميقات', en: 'Today’s prayer times and Qibla on your TV · Miqat' },
    description: {
      ar: 'تابع مواقيت الصلاة اليوم واعرف اتجاه القبلة من مدينتك.',
      en: 'See today’s prayer times and find the Qibla direction from your city.',
    },
    body: {
      ar: [
        'اعرف الوقت المتبقي لكل صلاة، واختر الصلوات التي تريد سماع أذانها.',
      ],
      en: [
        'See how long remains until each prayer and choose which Adhan alerts to hear.',
      ],
    },
  },
  {
    slug: 'reminder', screen: 'home', mode: 'reminder',
    nav: { ar: 'تذكير قبل الصلاة', en: 'Reminder' },
    title: { ar: 'تذكير قبل الصلاة على التلفاز · ميقات', en: 'A reminder before each prayer on your TV · Miqat' },
    description: {
      ar: 'تذكير قبل الصلاة لتُنهي ما تشاهده وتستعد لها.',
      en: 'A reminder before prayer, so you can finish what you’re watching and get ready.',
    },
    body: {
      ar: ['اختر وقت التذكير المناسب لك قبل أن يحين الأذان.'],
      en: ['Choose how far ahead of the Adhan you’d like a reminder.'],
    },
  },
  {
    slug: 'call', screen: 'call', mode: 'call',
    nav: { ar: 'الأذان', en: 'Adhan' },
    title: { ar: 'استمع إلى الأذان على تلفازك · ميقات', en: 'Hear the Adhan on your TV · Miqat' },
    description: {
      ar: 'استمع إلى الأذان في البيت، مع ١٣ صوتًا للاختيار منها.',
      en: 'Hear the call to prayer at home, with 13 Adhan voices to choose from.',
    },
    body: {
      ar: ['اختر الصوت ومستواه المناسبين للبيت، مع تسجيل مستقل لأذان الفجر.'],
      en: ['Choose a voice and volume for your home, with a separate recording for Fajr.'],
    },
  },
  {
    slug: 'friday', screen: 'home', mode: 'friday',
    nav: { ar: 'الجمعة', en: 'Friday' },
    title: { ar: 'صلاة الجمعة وسورة الكهف · ميقات', en: 'Jumu’ah and Surat Al-Kahf · Miqat' },
    description: {
      ar: 'عرض ليوم الجمعة مع صلاة الجمعة وتذكير بقراءة سورة الكهف.',
      en: 'A Friday view with Jumu’ah and a reminder to read Surat Al-Kahf.',
    },
    body: {
      ar: [],
      en: [],
    },
  },
  {
    slug: 'ramadan', screen: 'home', mode: 'ramadan',
    nav: { ar: 'رمضان', en: 'Ramadan' },
    title: { ar: 'الإفطار والسحور على تلفازك في رمضان · ميقات', en: 'Ramadan: iftar and suhoor on your TV · Miqat' },
    description: {
      ar: 'تابع موعد الإفطار ونهاية وقت السحور على شاشة التلفاز في رمضان.',
      en: 'Keep track of iftar and the end of suhoor on your TV during Ramadan.',
    },
    body: {
      ar: ['اعرف الوقت المتبقي للمغرب وأنت تستعد للإفطار.'],
      en: ['See how long remains until Maghrib as you prepare for iftar.'],
    },
  },
  {
    slug: 'apps', screen: 'apps',
    nav: { ar: 'كل التطبيقات', en: 'All apps' },
    title: { ar: 'كل التطبيقات · ميقات', en: 'All apps · Miqat' },
    description: {
      ar: 'افتح تطبيقات تلفازك من مكان واحد، مع موعد الصلاة القادمة أمامك.',
      en: 'Open your TV apps from one place, with the next prayer in view.',
    },
    body: {
      ar: ['احتفظ بالتطبيقات التي تستخدمها كثيرًا على الشاشة الرئيسية.'],
      en: ['Keep the apps you use most on the Home screen.'],
    },
  },
  {
    slug: 'settings', screen: 'settings',
    nav: { ar: 'الإعدادات', en: 'Settings' },
    title: { ar: 'إعدادات الصلاة والمظهر · ميقات', en: 'Prayer and display settings · Miqat' },
    description: {
      ar: 'مواقيت وأذان وألوان وخلفيات تناسبك.',
      en: 'Prayer times, Adhan, colours and backgrounds to suit you.',
    },
    body: {
      ar: ['اضبط مواقيت مدينتك واختر صوت الأذان ومحطة إذاعة القرآن التي تحبّها.'],
      en: ['Set prayer times for your location and choose the Adhan voice and Quran radio station you enjoy.'],
    },
  },
  {
    slug: 'screensaver', screen: 'screensaver',
    nav: { ar: 'شاشة التوقف', en: 'Screensaver' },
    title: { ar: 'الساعة والصلاة القادمة على التلفاز · ميقات', en: 'Clock and next prayer on TV · Miqat' },
    description: {
      ar: 'شاهد الساعة والصلاة القادمة على شاشة توقف خافتة.',
      en: 'Keep the clock and next prayer visible on a dim screensaver.',
    },
    body: {
      ar: [],
      en: [],
    },
  },
];

export const BASE = import.meta.env.BASE_URL.replace(/\/?$/, '/');
/** Site-relative URL of a page in a language: Arabic at the root, English under /en/. */
export const href = (slug: string, lang: Lang) => BASE + (lang === 'en' ? 'en/' : '') + (slug ? slug + '/' : '');
export const pageOf = (slug: string) => PAGES.find((p) => p.slug === slug)!;
