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
- The preview's Astro advisories were fixed in the GitHub Pages pass by updating
  the lockfile to Astro 7.4.12. npm audit still reports 10 high findings propagated
  from the unpatched picomatch build dependency (GHSA-fc5q-39xc-vjpf). It processes
  repository file patterns in the Node build/dev tools, not visitor input in the
  published static site. Pages serves only site/dist; no Node server, dependencies,
  source checkout or private state is deployed. Do not use npm audit fix --force:
  its proposed Astro downgrade reintroduces unrelated fixed advisories.

No physical TV was changed. The old platform-core and companion checkouts remain
clean and unchanged. No companion code, inherited Actions, previous history,
credentials, private device state or recordings are part of this repository's sources.

## Appearance review draft — 18 November 2026

Branch `design/miqat-themes`, based on `e612c11`, in an isolated linked worktree.
All work remains unstaged and uncommitted pending owner review. Other main-checkout
and companion work is separate and was not modified. Local review: `state/theme-review/index.html`, revision 6.

- Revised after owner feedback: seven non-arch ornamental families, one fully
  framed arch composition and a star/diamond default tile. All SVG coordinates
  and control points stay within an eight-unit margin, covered by a regression.
  Existing selection IDs remain stable; bilingual background labels were updated.
  Aggregate text hashes changed intentionally for those labels only; religious
  text and formatter expectations are unchanged.
- Canonical freshness, tokens/data checks, preview tests and 18-route build pass.
  All 20 themes render in both languages without page errors; all 23 background
  views were regenerated. Actual-opacity overview sheets were inspected; gallery
  theme/language/background/detail controls and image loading pass at desktop/mobile.
- Android build/size/lint/AndroidTest assembly and all 41 Python/Java regressions
  pass, including palette contrast and full-resolution mask checks. Complete
  local-audio APK: **21,976,961 bytes**.
- LG build/ES5/adapter/service/mock HTTPS checks pass. Palette-tinted masks,
  opacity bounds, draft-theme image loading and full-composition sizing are checked.
  Full browser checks pass in Arabic/English at 80–150%, including all six scenic
  carousel previews, full-resolution decoding, Apply and saved Home re-entry. The
  new test mirrors physical arrows for Arabic; decoder and Luna are mocked.

Six neutral landscape assets are reused unmodified under CleanYourTV's MIT license,
with source filenames/revision recorded in THIRD-PARTY.md. They total 367,296 bytes.
All six render natively in the task-owned read-only API 37 phone emulator at
1920×1080/density 160, alternating English and Arabic; captures were inspected.
Only this emulator was installed/configured, then closed. These are emulator
spot checks, not physical-TV or real-remote acceptance. The review gallery also
contains 12 bilingual browser Home examples using complementary palettes. Raw
captures and private media stay ignored. Physical TV, media/remote/power acceptance,
deployment, commit and push are outside this review. Existing preview dependency
advisories remain separate work. Reload the local gallery to see revision 6;
versioned image URLs avoid stale copies.

### Handcrafted presets and customization

Six existing IDs now pair the landscapes with dedicated palettes, bilingual names
and surface treatments (see DESIGN.md). All palette roles and glass-over-white
checks pass. Appearance places the complete preset carousel first, then Customize
with the existing controls. Selecting a different preset restores its bundled
background and lighting only in the draft. Clamped input preserves manual changes.

Preview regressions/build and actual bilingual page checks pass: draft-only preset
selection, manual background override and Apply are verified. Android build, size,
lint and AndroidTest assembly pass, along with all 41 Python/Java regressions.
The native UI contract check passes on the task-owned read-only API 37 emulator,
including preset defaults, customization preservation, row order and both languages.
Six final native theme captures were inspected; the emulator was then closed.
LG build/service suites and full bilingual browser checks pass, including preset
background selection, unsaved draft isolation and later manual overrides. These
remain mocked LG checks, not physical picture/remote acceptance. Current gallery
includes the new presets, before/after palette comparisons and customization page.
No files are staged, committed or pushed. Other ongoing checkout work is preserved.

### Miqat flagship

The canonical default now selects original `miqat_signature` artwork rather than
the lattice. Built-in ImageGen produced the petrol/jade ceramic relief with gold
inlay; the app bundles a 115,784-byte 1920×1080 JPEG. The generation brief is in
`shared/design/artwork/miqat-signature.md`. Existing manual backgrounds stay intact.
Canonical/catalog/token checks, preview regressions/18-route build and all 41
Python/Java regressions pass. Android build/size/lint/AndroidTest assembly pass;
all seven local scene files are confirmed in the APK. Actual browser and native
API 37 emulator renders were inspected in both languages with Theme resolving
the new default. The read-only task-owned emulator was closed afterward.
LG build/service and full bilingual browser suites pass for the new default,
including fresh preferences, manual-background preservation and all seven scene
previews/save/re-entry. LG remains mocked. Review revision 6 includes before/after
and current native flagship captures.
No physical-TV acceptance, commit or push is claimed.

### Home opacity and clearer prayer icons — review revision 7

Home fill now supports 50–100% in 5% steps, default/recommended 85%, preserving
opaque text/outlines and non-Home frames. Shared JVM/JS/legacy checks pass. Native
focused opacity and UI-contract instrumentation passed on the task-owned read-only
API 37 emulator: bilingual limits, live draft, both cancellation layers, Apply,
saved re-entry and actual fill-only paint alpha. The emulator was closed after
final bilingual native captures. No physical TV was operated.

Owner rejected the first decorative icon proposal as unclear. The revised review
set removes diamond ornaments and uses familiar solar/lunar symbols with a heavier
2-unit stroke. Canonical paths feed Android, preview and LG. LG Home icons share
the tag header strip, preserving long labels at 150% sizing. This replaces the
failed inline placement that wrapped Maghrib. Final full LG bilingual browser
suite passes at all supported sizes, including opacity persistence and all six
canonical paths; service/ES5/mock HTTPS suites pass. Decoder/Luna remain mocked.

Preview regressions and 18-route build pass. Actual bilingual page checks verify
50–100% bounds, live preview, numeric/outer cancellation, Apply/re-entry, opaque
text/outlines and six canonical paths. Android build/size/lint pass; current local
APK is 21,835,655 bytes. All 41 Python/Java regressions pass. Review revision 7
contains refreshed Home/preset images, opacity controls, a rejected/revised icon
comparison with small-size samples, and final native flagship captures. Gallery
controls/image loading pass. Native instrumentation was run before the final
path-only icon revision; the final APK was installed and visually inspected.

No physical-TV acceptance, staging, commit or push. Existing dependency advisories
remain separate work. Main-checkout work was preserved.

### Miqat quiet addition and 75% next-prayer fill — review revision 8

Owner selected a 75% next-prayer fill and approved keeping both original Miqat
scenes. `miqat_quiet` is added with English/Arabic labels; `miqat_signature`
remains the canonical default. Neither background is automatically mirrored.
The added 1920×1080 JPEG is 90,742 bytes. Android/preview/LG apply absolute 75%
alpha to the selected surface panel RGB, without dimming text/outline or
compounding Glass. The mihrab's separate opacity control is unchanged.

Canonical freshness/token checks, preview regressions/18-route build, Android
size/lint/AndroidTest assembly and all 41 Python/Java regressions pass. Current
local-media APK: 22,040,731 bytes, below 25,000,000; both Miqat JPEGs are confirmed
byte-for-byte in the APK. Aggregate text hashes were intentionally regenerated
for the additional background label; sacred text/formatter fixtures are unchanged.
Actual bilingual preview checks pass for preserved default, quiet carousel draft,
Apply/re-entry, 75% fill and opaque labels. Both scenes were rendered in English
and Arabic on the isolated read-only API 37 emulator; native captures were
inspected and that emulator was closed. No physical TV was operated.

LG packaging/ES5/service/mock HTTPS and full bilingual browser checks pass,
including all eight scene decodes, saving/re-entry and computed 75% card alpha.
Decoder/Luna remain mocked. Gallery revision 8 includes the added background and
updated previews; gallery controls/images pass. Native original/quiet captures
are retained under ignored state. No staging, commit, push or physical acceptance.

credentials or private device state are part of this repository's sources.
The maintainer approved publishing the reviewed 26-clip set on 18 November 2026;
those copies now live under shared/media/, with exact SHA-256 checksums.

## GitHub delivery setup, 18 November 2026

Local core, Android/installer lint and builds, 43 Python/Java regressions, audio
preparation fixtures, full LG browser/service tests and preview checks passed.
The TV APK contains all 26 approved recordings byte-for-byte and is below 25 MB;
LG app/service IPK packaging also passed. No recording was re-encoded in this pass.

The active [Protect main ruleset](https://github.com/AbdelrahmanHajjaj/MiqatTV/settings/rules/27542002)
requires a PR, the GitHub Actions Required checks result, an up-to-date branch,
resolved conversations and normal merge commits, as requested by the owner.
Squash/rebase merging is disabled. It blocks force pushes/deletion, has
no bypasses and requires zero approving reviewers. Actions requires full SHA pins;
the default token is read-only and cannot approve PRs.

[PR #1](https://github.com/AbdelrahmanHajjaj/MiqatTV/pull/1) contains the validation
and draft-publication workflows. GitHub-hosted checks must pass before merging;
manual draft publication can be exercised after the workflows reach main.
CI APKs use ephemeral debug signing keys; stable update signing is not configured.

API 34 checks exposed split UI observations and an arrange-menu/catalog-refresh
race. Header navigation and favourite-marker assertions now use coherent UI
snapshots, with queued-refresh regressions. Android defers catalog rebuilding
until the menu closes and preserves the originating app's focus. A direct native
regression failed before the fix; five complete Arabic/English UI runs passed
afterward on an isolated API 34 emulator. Build/lint, all 43 local regressions and
the 26-recording APK check passed. Temporary probes were removed.

[Validate run 44296900776](https://github.com/AbdelrahmanHajjaj/MiqatTV/actions/runs/44296900776)
passed all six jobs for 0229237: core, Android build, API 34 emulator, LG browser/
package checks, preview and the required aggregate. Full-audio Android/LG artifacts
were uploaded successfully. The owner then confirmed normal merges, never squash;
repository and ruleset merge options now match that preference.


## GitHub Pages, 18 November 2026

The existing Arabic/English static preview is configured for
https://abdelrahmanhajjaj.github.io/MiqatTV/. Pages uses GitHub Actions; the
created github-pages environment permits main only. The deployment job reuses
the current run's browser-preview artifact after Required checks passes, with
job-scoped Pages/OIDC permissions and pinned official actions. PRs skip deployment.

Local data/token freshness, preview regressions and the Astro 7.4.12 static build
passed. All 18 routes have correct canonical URLs; 450 internal link/asset
references resolve under /MiqatTV/. The artifact contains no Node dependencies,
private state, keys, app binaries or recordings. Edge checks passed for Arabic/
English Home, language switching, Settings tab changes and return navigation,
with joined Arabic, loaded branding and no browser console errors.
Workflow actionlint and documentation checks passed. The owner authorized a
normal merge of PR #2 after required checks pass, followed by live publication.
PR #2 and its Actions runs own the final merge/deployment receipts; initial live
acceptance follows the successful main run.


## Interactive website refresh, 18 November 2026

Based on main 4ccf0ef after the theme-library merge. The bilingual product page
uses current Miqat tokens/artwork. Nine screens mount locally without URL changes;
18 static direct-entry routes and separate language URLs remain available.
Replaced screens abort timers, observers, global listeners, media and online work.

Data/token freshness, the complete preview suite (including route boundaries and
screen cleanup), Astro static build and documentation checks passed. All 18
canonical URLs and 828 internal references resolve; no private state, Node
modules, app binaries or recordings are in the output. Local Node was 26.13.1;
CI uses the documented Node 24. No dependencies or shared/app sources changed.

Actual Edge checks passed for all nine screens in each language, same-URL screen
changes, direct Settings entry, selected-screen language switching, browser Back,
return focus, settings Apply/cancel/re-entry, saved inline size and restored test
preferences. Arabic is joined; the 390px page has no horizontal overflow. Default
artwork loads and console errors are absent. Captures are under ignored state/.
Radio streams and online prayer comparison were not requested in these browser
checks; their existing deterministic regressions passed. This is browser evidence,
with no physical-TV operations. The PR owns CI, merge and publication receipts.


### Website publication

[PR #4](https://github.com/AbdelrahmanHajjaj/MiqatTV/pull/4) was normally merged as
5d0e1c2 after six successful required jobs. [Main run 44332260024](https://github.com/AbdelrahmanHajjaj/MiqatTV/actions/runs/44332260024)
passed all seven jobs, including Pages deployment. Public Arabic/English Home,
same-URL Settings navigation, language links and Arabic appearance rendering were
verified in Edge with no console errors. Local verification server was stopped.

### CI audio-processing scope

The owner requested removing FFmpeg from Actions and retained the other Ubuntu
checks/builds. The workflow no longer installs FFmpeg or runs synthetic audio
processing. Approved-media hashes, prepared-output hashes and packaged APK byte
checks remain. FFmpeg preparation/decoding and listening remain local checks for
changed media/preparation code. No recording or package content was changed.

Workflow actionlint, documentation links/paths, the remaining prepared-output hash
check and diff checks passed locally. All workflows were scanned to confirm no
FFmpeg/ffprobe installation, invocation or audio-processing fixture test remains.
The existing required-job aggregate, build artifacts and publication guards are
unchanged. The focused PR owns the GitHub validation result.


## README branding refresh, 18 November 2026

The README removes its header icon and uses the reviewed PR #3 native Home captures
in English/Arabic, showing the default Miqat signature scene and current palette.
These are 1920×1080 API 37 emulator captures, not physical-TV photographs. The
capture worktree commit a567978 and main c6ad64f have identical Android, installer,
core, data and design sources. The PNGs are copied unchanged from the reviewed
captures; no new app build, emulator session or physical-TV operation was needed.
The design guide's obsolete uncommitted/review-draft claims are removed.

Both PNGs decode, match the reviewed bytes and retain 1920×1080 dimensions.
Arabic shaping, RTL placement and the current background/prayer symbols were
visually inspected. Documentation link/path checks and diff checks passed.


## Companion LG replacement (18 November 2026)

On the separately authorized companion branch, latest main `9aaf542` is merged.
Preview generated-data, unit, production build and bilingual navigation/pairing
checks pass. LG 0.2.24 unit/service/build/ES5 and full mocked bilingual browser
checks pass, including the added initial splash visibility/dismissal regression.

Live developer-service readback verifies all 31 old preference fields preserved,
plus the new default mihrab opacity; input, startup and automatic alerts restored.
Old scheduling was disabled before Miqat scheduling was enabled. Old launcher
uninstallation and settings preservation through the 0.2.24 update are verified.
Backups, logs and device configuration stay private under ignored state/.
The owner confirmed the branded splash, picture and sound all work on the
physical LG with 0.2.24 on 18 November 2026. Physical remote, new-identity
pairing/camera, phone commands and power/lifecycle acceptance remain pending.
