# Miqat · ميقات

<p align="center"><img src="shared/design/branding/miqat-tv-v1/miqat-tv-logo-master.png" width="240" alt="Miqat · ميقات"></p>

A TV home screen with prayer times, adhan and Quran radio, in Arabic and English.

[Try the interactive preview](https://abdelrahmanhajjaj.github.io/MiqatTV/) · [English](https://abdelrahmanhajjaj.github.io/MiqatTV/en/)

<p dir="rtl">واجهة للتلفاز تعرض مواقيت الصلاة والأذان والقبلة وإذاعة القرآن الكريم، بالعربية والإنجليزية.</p>

- Prayer times work offline, with city, calculation method and individual adjustments.
- Adhan, reminders, Qibla, Friday and Ramadan displays.
- Themes, Home customization and adjustable interface size.
- Controls designed for a TV remote, with Arabic and English layouts.

## Screenshots

Android TV app running in a 1920 × 1080 Android emulator.

**English Home**

![Miqat Android TV Home in English, with prayer times, countdown and favourite apps](docs/screenshots/android-home-en.png)

**الرئيسية بالعربية**

![شاشة ميقات الرئيسية بالعربية مع مواقيت الصلاة والعد التنازلي والتطبيقات المفضلة](docs/screenshots/android-home-ar.png)

## Platforms

| Platform | Status |
| --- | --- |
| Android TV / Google TV | Native Home launcher. The rewritten app has emulator coverage; physical-TV confirmation is pending. |
| LG webOS | Separate app with embedded HDMI. Device integration was tested on LG LF6300 / webOS 2.2; the new Miqat package needs a fresh-install check. It does not replace LG Home. |
| Browser | Interactive preview with simulated TV integration. |

Sony Android/Google TV uses the Android app. Samsung/Tizen and newer LG hardware
are unverified. The companion app is developed separately.

## Install

Build from source or use [CI test builds](docs/CI.md#checks-and-artifacts). See the
[installation and build guide](docs/BUILD.md).
Android and LG use different packages. Miqat uses new app identities and installs
as a new application. No release downloads are published from this repository yet.

## Privacy

No accounts, ads or analytics. Settings stay on the device. Prayer calculation
works offline; radio streams and the optional manual online prayer comparison
use the internet. The Android installer uses ADB during setup.

## Development

Start with the [documentation](docs/README.md), [architecture](docs/ARCHITECTURE.md)
and [contribution guide](CONTRIBUTING.md).

[MIT licence](LICENSE) · [Credits and third-party licences](docs/THIRD-PARTY.md)

The Android phone [companion](companion/README.md) adds foreground home-LAN pairing,
settings and explicit TV controls on its separate feature branch.
