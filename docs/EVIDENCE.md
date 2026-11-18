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
Workflow actionlint and documentation checks passed. Live deployment remains
pending the owner's normal-merge instruction and a successful main run.
