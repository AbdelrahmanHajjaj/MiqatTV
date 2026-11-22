# Product screenshots

Current set: 22 November 2026. Screenshots show the standalone Android phone,
its TV companion mode, Android TV and LG webOS in English and Arabic. Canonical
PNGs here are unmodified captures with sanitized Cairo/demo fixtures; they contain
no real household addresses, credentials, private recordings or pairing QR codes.

## Sources

| Files, with `-en.png` / `-ar.png` | Source |
| --- | --- |
| `phone-home`, `phone-calendar` | PhoneHomeCheck's completed-Home and Arafah/calendar fixtures. |
| `phone-prayer` | PhonePolishCheck's real per-prayer controls. |
| `phone-voices` | PhoneLibraryCheck's named demonstration audio library. |
| `phone-location` | PhoneUiCheck's real Location settings. |
| `phone-qibla`, `phone-widgets`, `phone-style` | PhoneJourneyCheck's Qibla, widget configuration and setup-style screens. |
| `companion-tvs`, `companion-remote`, `companion-appearance` | CompanionCheck's native demo-TV interfaces, including the corrected preview caption. |
| `android-home` | Native Android TV Home from the 0.7.2 UI-alignment review. |
| `lg-home` | LG browser-test's aligned Home capture from the 0.2.30 review. |

Phone captures use Miqat 0.5.18/code 34 on an Android 14/API 34 Google APIs
emulator at 1080×2400, density 420. Fixtures provide dates, locations, themes,
audio names, sensor headings and demo TVs.

Android TV 0.7.2/code 13 captures use native Views in a landscape API 34 Google APIs
emulator at 1920×1080/density 160. Original PNGs include its system navigation
area; website frames show the app viewport above it. LG 0.2.30 images are
1920×1080 browser renders with mocked Luna/media/device APIs.
TV renderings come from the [PR #13](https://github.com/AbdelrahmanHajjaj/MiqatTV/pull/13)
source tree; the subsequent correction in that PR affected thumbnail proportions.

The website imports these PNGs and generates responsive WebP variants. Full-size
links open the originals. Its widget-strip images are the canonical native picker
renders in `companion/android/src/main/res/drawable{,-ar}-nodpi/`. Website social cards in
`site/public/branding/product-{en,ar}.png` are browser captures of the product hero.

## Refresh

Use an explicitly selected, task-owned emulator. Install matching app/test APKs,
then opt into the existing capture journeys; each restores its private fixtures:

```sh
adb -s "$PHONE" shell am instrument -w -e suite phone-ui -e capture true tv.miqat.companion.test/tv.miqat.companion.CompanionCheck
adb -s "$PHONE" shell am instrument -w -e suite phone-journey -e capture true tv.miqat.companion.test/tv.miqat.companion.CompanionCheck
adb -s "$PHONE" shell am instrument -w -e capture true tv.miqat.companion.test/tv.miqat.companion.CompanionCheck
```

Check portrait dimensions before selecting phone images. Captures are written to
that emulator's app external-files directory. Review images for clipping, actual
state and private information before copying the selected PNGs here. Keep raw
capture sets and logs under ignored state/. Follow [testing](../TESTING.md) for
LG `--capture` and native TV checks, and [site instructions](../../site/README.md)
for responsive review/social-card regeneration.
