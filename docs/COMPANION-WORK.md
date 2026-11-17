# Miqat companion work

Updated: 2026-11-18. Branch: feature/companion-app, based on MiqatTV main e07a609.

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
copy/import/export, actual remote updates, physical-TV installation or firmware.
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
