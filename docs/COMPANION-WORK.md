# Miqat companion work

Updated: 2026-11-18. Branch: feature/companion-app; main synchronized at 9aaf542.

## Scope and decisions

Cherry-pick source commit 1c88e6f from IslamicTVLauncher, preserving Miqat's fresh
history, shared branding and tv.miqat identities. The original checkout and the
owner's staged README screenshot work remain untouched.

Android phone first, with shared rules and platform-owned rendering. All launcher
settings follow the TV's offered controls/capabilities. Reuse inline +/- editors,
carousels/anchored choices and canonical icons. Appearance stays a local
representative preview with Apply/Cancel. The TV owns saved values/calculation.
Read settings only on Settings entry; never re-send on reconnect. Baseline checks
preserve unrelated edits and local TV drafts.

Home LAN only: TV pairing page/QR, pinned HTTPS and physical remote approval,
individual revocation. Both shells participate only while the launcher is active.
Approved QR: connected rounded modules, medallion eyes and mihrab centres; colours
follow the TV theme. Scanner shows framing and explicit progress. Explicit Home,
input, radio and bounded adhan-preview/Stop commands use existing canonical IDs.
Legacy LG may use approved TLS 1.2 RSA/AES-GCM; no forward secrecy is an accepted
compatibility limitation. No plaintext or unverified trust fallback.

No cloud/accounts, background handling, Bluetooth/Wi-Fi Direct, iOS, settings
copy/import/export as a product feature, actual remote updates or firmware.
The owner separately authorized the one-time LG settings transfer, installation
and removal of the old launcher on 18 November 2026.
New Miqat installations/pairings are fresh; old identities are not aliases.
[WORK.md](WORK.md) remains the unchanged historical foundation ledger.

## Sequence and status

1. Cherry-pick onto Miqat main in an isolated worktree; resolve foundation conflicts.
2. Use canonical Miqat artwork, geometry, design paths and namespaces throughout.
3. Validate shared rules, native Android flows, LG adapters and preview.
4. Record evidence here, finish the cherry-pick and push the feature branch.

The port and automated/native validation are complete. Physical Miqat TV networking, camera,
remote usability, picture/sound and lifecycle acceptance remain pending owner
testing. Prior old-identity observations do not establish Miqat passes.

## Validation

Passed on the Miqat port (Windows, JDK 17; SDK 34 builds, owned API 37 emulators):

- `node tools/catalogs.mjs --check`, `node tools/catalogs.test.mjs`,
  `node tools/validate-shared.test.mjs`, `node tools/branding.mjs --check`.
- `./gradlew -p shared/core check jvmJar --no-daemon`: JVM/JS/common and 97 ES5
  assertions. Companion core `check jvmJar`, generator tests, JVM LAN helpers
  and shared JVM/ES5 compact QR vectors pass.
- `./gradlew :companion:assembleDebug :companion:assembleDebugAndroidTest
  :companion:lintDebug :app:verifyApkSize :app:assembleDebugAndroidTest
  :app:lintDebug --no-daemon`: pass; populated TV APK 21,657,943 bytes, below 25 MB.
  `python -S -B -m unittest discover -s tests -v`: 41 pass.
- Native phone UI in both languages, settings/draft/offline validation, pinned LAN,
  encrypted credentials/tamper rejection, QR frame decoding and virtual-camera
  open/release: pass. Rendered logo, Arabic preview and scanner frame inspected.
- Native TV companion instrumentation: bilingual pairing/QR and real TLS settings,
  conflicts/defaults, commands, listener lifecycle/revocation pass.
- `companion/check-live.ps1` with explicitly selected owned phone/TV emulators:
  real app pairing/settings/Apply/Cancel, recreation, reconnect without uploads,
  transport loss and Arabic cache pass; approval injected and local NAT bridge.
- `node companion/probe-legacy-lg.cjs <local-node-0.10.25.exe>` and
  `node companion/check-lg.cjs <owned-phone-serial> <private-adb-port> <local-node>`:
  shipped legacy LG modules/TLS and native phone UI interoperability pass on
  actual Node 0.10.25; loopback/emulator fixtures, approval injected.
- `npm --prefix platforms/webos test` and full browser suite plus pairing/remote suites pass,
  including Arabic/English QR visibility at 80/100/150% and theme bindings.
- `node companion/check-qr.cjs <cached-zxing-core-jar>`: all 2,880 exact decodes
  across 20 themes, 220/280/400 px, rotations, blur and lower contrast pass.
- Preview `tokens:check`, `test`, `build`, `test:browser`: pass. Browser pairing,
  approval/revocation/cancel, focus, hidden cleanup and no-network simulation
  verified in Arabic/English at 80/100/150%.

Raw logs, addresses, QR secrets, captures and packages stay under ignored state/.
An initial emulator-port collision was excluded from phone acceptance; tests were
rerun on a verified separate owned AVD. Task-added APKs on the other emulator were
removed; prior display overrides were unknown and are recorded privately.

Physical new-identity TV/phone camera, remote, picture/sound and background behavior
remain untested. Legacy RSA lacks forward secrecy. The foundation's existing nine
high preview-toolchain npm findings remain separate remediation; no site is deployed.
See [TESTING.md](TESTING.md) and [protocol](COMPANION-LAN.md).

## Delivery

Published to `AbdelrahmanHajjaj/MiqatTV` branch `feature/companion-app`.
Commit `6387f04` cherry-picks source `1c88e6f` directly onto main `e07a609`;
old source history is not ancestry. Original checkouts/README work were preserved.
All task-owned emulators were shut down after checks.

Draft PR creation was denied by both the connected GitHub integration and the
local CLI token (repository permission). The branch push succeeded; open the
[comparison](https://github.com/AbdelrahmanHajjaj/MiqatTV/compare/main...feature/companion-app?expand=1)
to create the PR with the repository template. No merge or release is authorized.

## Approved centre QR and companion naming

Companion product: `miqatTv`; Android launcher label: `miqat`. Package remains
`tv.miqat.companion`. Build/lint and aapt label verification pass.

The owner selected the half-gap treatment on 18 November 2026. Android TV and LG
now render the transparent Miqat mark with theme body fill, app-accent lettering,
no border and 0.35-module clearance around an 11-module tile. High error correction
H produces a 49x49 compact pairing symbol; outer quiet zone and pairing data,
credentials and approval rules are unchanged. Shared style/asset owns geometry
and crop; Android uses native alpha tint and LG embedded SVG 1.1 image/filter.
The original branding master remains intact. Native downsampling preserves smooth
alpha edges at small QR sizes.

Integration checks:

- Shared SVG: all 2,880 exact decodes pass across 20 themes, eight payloads,
  220/280/400 px, four rotations, blur and reduced contrast.
- Native Android: 540 exact frame decodes pass (actual pairing payload plus eight
  seeded payloads, five themes, three sizes, four rotations); final raster inspected.
  Native LAN suite and actual TV pairing UI/TLS/settings/lifecycle checks pass
  on the verified isolated read-only emulator. No physical device was used.
- Phone/TV debug and instrumentation builds and lint pass. Populated TV APK:
  22,151,813 bytes, below 25 MB. Required root Python regressions: 41 pass.
- LG npm test, targeted bilingual pairing DOM/theme/half-gap checks at 80/100/150%
  and full mocked-browser suite pass. Packaged alpha mark matches canonical bytes;
  rendered page inspected. Shared catalog and isolated-source validation pass.

Prior 0.7/0.35/zero-clearance comparisons are private evidence. Half-gap passed
576 exploration decodes; zero clearance failed rotated exact-payload checks in
three themes and was excluded. Earlier outline/light-correction candidates were
also rejected. Raw previews, logs and native raster captures remain in ignored
state/companion/. No QR credentials or generated packages are committed.

Physical phone-camera/TV scanning and legacy LG image/filter rendering remain
pending owner acceptance. Generic inert browser-demo QR is outside this renderer
change. No installation on a physical TV, merge or release is included.


## Quieter pattern exploration

Owner requested less visual noise after approving the centre. Compare A (current),
B (same rounded geometry, all data cells painted as one compound path to remove
anti-alias seams) and C (compound path plus less-rounded cells). QR payload, module
count, high correction, eyes and approved half-gap centre are identical.
Each passes 576 independent decodes across four themes (1,728 combined; same
size/rotation/degradation matrix). The owner selected A on 18 November 2026: keep
the current rounded pattern with the implemented theme-filled, half-gap centre.
B/C are not applied. Preview/scripts/evidence remain private under
state/companion/qr-patterns/. This decision changes documentation only; runtime
checks were not repeated. Physical TV/camera acceptance remains pending.


## Main rebranding merge (18 November 2026)

Normal merge of main `4ccf0ef` preserves the complete main and companion histories.
Conflicts combine the new themes/backgrounds, shared geometry/tile generation,
preview dependency fixes, delivery workflows and approved shared/media packaging
with companion routes, numeric bindings and pairing pages. Companion geometry is
regenerated from the same canonical source. QR pattern A and the approved half-gap
centre remain unchanged; colours now follow main's refined palettes.

Integration fixes: expose the new 50–100% / 5% mihrab-opacity control in both TV
adapters, validate steps, include it in appearance conflicts and phone drafts,
and show its fill alpha in the representative phone preview. Profiles include
Signature/Quiet choices and main's LG default. The phone packages the approved
shared media; CI watches that source. Original recordings/private media stay ignored.

Validation: shared core JVM/JS/ES5 and companion core checks; seven generator tests;
phone/TV/installer builds and lint; 43 Python regressions; exact 26-clip APK media
checks for TV and phone; preview tokens/test/build/bilingual browser flow; LG
unit/service, pairing/theme and complete browser suites all pass. Populated TV
APK is 22,815,303 bytes. Native phone UI/LAN/QR and TV companion/opacity routes pass
on a verified isolated read-only emulator; phone Arabic preview was inspected.

All 2,880 QR samples pass on the merged palettes. The test decoder is now QR-only,
matching the phone: unrestricted ZXing had returned a false 8-digit UPC-E for a
rotated Frost sample while QR-only returned the exact 112-character payload.
Exact-payload, format, rotation, blur and contrast checks remain enabled. This
also limits conclusions drawn from earlier unrestricted-decoder negative previews;
the owner's explicit design selection is unchanged.

Main and merged preview lockfiles both report 10 high npm audit findings; no
existing main dependency version was changed. No dependency remediation, physical
TV deployment, release or merge into main is included. Physical camera/picture/
sound and legacy LG rendering acceptance remain pending.


## LG replacement and latest main (18 November 2026)

Merged main `9aaf542`, moving preview pairing into the new settings controller
with abort/visibility cleanup. Preview unit/build and bilingual browser checks pass.
LG unit/service/build checks pass. Raw receipts and settings backups remain in
ignored state/companion/lg-migration/.

Live LG developer APIs verified a private export of all 31 old preference fields,
then imported them unchanged into Miqat with the new mihrab-opacity default.
Input, startup and automatic alerts were restored and read back. Old scheduling
and startup were disabled before enabling Miqat equivalents. The old launcher
was then uninstalled at the owner's request. Pairing credentials were not copied.
This is live service evidence, not human picture/sound acceptance.

The owner reported no visible splash with 0.2.23. Version 0.2.24 adds the canonical
splash inside the app until its first rendered screen, with a 600 ms minimum.
Error screens also dismiss the cover. LG unit/service and full bilingual browser
checks pass, including startup-cover visibility and dismissal. Version 0.2.24 is
installed and launched; service readback verifies all migrated settings survived
the update and the old app is absent. The owner confirmed on the physical LG
that the splash, picture and sound all work with 0.2.24 on 18 November 2026.
Fresh Miqat camera/pairing, phone commands, physical remote and power/lifecycle
acceptance remain pending.
