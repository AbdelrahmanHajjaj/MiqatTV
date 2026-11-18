# Validation evidence

Checked on 17 November 2026 in the new Miqat checkout, using JDK 17, Node 24.14.0
and SDK 34. Source versions: Android TV 0.7.0, installer 1.1.0, LG 0.2.23.

| Check | Result |
| --- | --- |
| Canonical catalogs, geometry, isolated validation and token freshness | Pass |
| Core check/jvmJar | Pass: JVM/Java/JS tests and 94 legacy assertions in browser/CommonJS ES5 hosts |
| Android TV verifyApkSize/lintDebug/assembleDebugAndroidTest | Pass; complete local-audio APK 21,290,236 bytes, below 25,000,000 |
| Installer assembleDebug/lintDebug | Pass |
| Python/Java regressions | 41 pass, including bilingual branding and forbidden legacy-name checks |
| Native prayer and UI instrumentation | Pass: core/reminder/ICU linkage and Arabic/English control coverage |
| Native size-windows | Pass: both languages at 80–150%, including popup/dialog/bar/banner/screensaver/screen-off |
| LG test and test:browser | Pass: ES5/build/service/volume/mock-HTTPS and Arabic/English at all eight sizes |
| Preview tokens:check, test and build | Pass; 18 static routes |
| Built preview in Edge | Arabic/English Home and Settings inspected; Home HARs have no failed asset requests at /MiqatTV/ |
| Source/doc audit | No old branding/links outside retained MIT notices and prohibited-brand test expressions; local Markdown links resolve |

Native checks used an isolated API 37 phone emulator at 1920×1080/density 160,
with system animations disabled. A cold-boot SystemUI ANR blocked the first UI
check; after closing that emulator dialog, the unchanged UI test and full window
suite passed. Home was visually inspected. This is not Android TV hardware evidence.
LG screenshots are browser simulations; Luna and decoder boundaries are mocked.
Raw logs, original/review recordings, HARs and captures remain under ignored state/.

## Open checks

- Fresh-install acceptance on physical Android TV and LG, including Home/startup,
  real remote, picture/sound and prayer/media restoration under the new identities.
- Complete installer operation on a real phone/TV. Newer LG and Samsung are unverified.
- Portable prayer/appearance settings export/import is not implemented. Installer
  recovery exports Android setup state only.
- The unchanged preview toolchain reports 9 high npm audit findings, chiefly
  picomatch dependency propagation, plus Astro advisories. Dependency remediation
  is separate work before public preview hosting. No preview was deployed.

No physical TV was changed. The old platform-core and companion checkouts remain
clean and unchanged. No companion code, inherited Actions, previous history,
credentials or private device state are part of this repository's sources.
The maintainer approved publishing the reviewed 26-clip set on 18 November 2026;
those copies now live under shared/media/, with exact SHA-256 checksums.
