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

## Delivery

Published to `AbdelrahmanHajjaj/MiqatTV` branch `feature/companion-app`.
Commit `6387f04` cherry-picks source `1c88e6f` directly onto main `e07a609`;
old source history is not ancestry. Original checkouts/README work were preserved.
All task-owned emulators were shut down after checks.

Draft PR creation was denied by both the connected GitHub integration and the
local CLI token (repository permission). The branch push succeeded; open the
[comparison](https://github.com/AbdelrahmanHajjaj/MiqatTV/compare/main...feature/companion-app?expand=1)
to create the PR with the repository template. No merge or release is authorized.

## Centre QR exploration and companion naming

Owner requested a central Miqat mihrab/icon exploration on 18 November 2026.
Keep the approved runtime QR unchanged until a variant is selected. Compare the
current code with shared-outline and compact/larger official-artwork centre
badges; preserve the canonical lettering. Synthetic previews and decoder evidence
stay in ignored state/companion/qr-centre-exploration/.

Companion product name: `miqatTv`; Android launcher label: `miqat`.
Keep its `tv.miqat.companion` application identity stable.

Exploration result: A retains the current 37x37/M symbol; B uses the official
Miqat icon in an 11-module badge and C a 13-module badge, both 49x49/H. Each passes
144 exact independent ZXing decodes (eight synthetic payloads, 220/280/400 px,
four rotations, blur and lower contrast): 432 checks total. A shared-outline-only
badge and a lighter Q variant failed small samples and were excluded. This is
one-palette desktop raster evidence, not all-theme or physical-camera acceptance.
No runtime QR renderer, pairing wire format or TV integration was changed.

Naming check: `:companion:assembleDebug :companion:lintDebug` pass; Android aapt
reports application label `miqat` and unchanged package `tv.miqat.companion`.

Gold-only refinement: extracted a transparent PNG from the shared icon with the
built-in image-editing tool (remove petrol background; preserve gold Arabic mark
and arch). The canonical master is unchanged. Gold-only and previous-badge
comparisons each pass 144 exact decoder checks at 220/280/400 px with rotations,
blur and lower contrast. Preview/cutout/evidence live under ignored
state/companion/qr-centre-transparent/. Runtime QR remains unchanged; physical
camera and all-theme acceptance are pending selection.

Centre-ring refinement: compare unframed A, fine double rings B and matching-eye
medallion C. B/C derive rounding, rim colours and gold inlay from the approved
finder-eye style and keep the transparent gold mark on ivory. All three pass
144 independent decodes each (432 total; same payload/size/rotation/degradation
matrix). Candidates/evidence are in ignored state/companion/qr-centre-rings/.
No runtime change; selection, all-theme and physical-camera acceptance are pending.
