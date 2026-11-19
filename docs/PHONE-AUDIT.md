# Pass-3 engineering audit

In progress, 19 November 2026. Expected load: one household, local prayers and
foreground authenticated TV access. Requirements: [PHONE-PASS3.md](PHONE-PASS3.md).
This is an implementation audit, not physical acceptance or a claim that every
source line has been inspected. Finish the map and evidence before delivery.

## Concrete findings and corrections

1. Android local APK selection overwrote the current cache file before checking
   its package identity. A malformed/provider-failed replacement could destroy a
   previously valid selection. `PackageImport` now copies to a unique bounded
   temporary file, syncs, validates and atomically replaces it. LG uses the same
   boundary. Native release/package tests cover invalid, oversized and interrupted
   providers, retained previous bytes and partial cleanup. Post-extraction native
   public-release/LAN checks pass; exact logs are in PHONE-WORK.md.
2. `MainActivity` owned pairing polling/approval plus TV draft storage, specialized
   editors and app navigation. `PhoneUi` mixed location sessions, countdown/daily
   rendering, appearance bitmaps and settings editing. Extracted `PairingUi`,
   `TvSettingsUi`, `PhoneLocationUi`, `PhoneHome`, and `PhoneAppearance` by existing
   responsibilities, updating callers rather than retaining compatibility
   wrappers. Existing draft/recreation/native/LAN checks remain authoritative.
   Post-extraction bilingual bindings/drafts and first-use/widget-host journeys
   pass; see PHONE-WORK.md for logs and remaining native suites.
3. Artwork used as a match-parent ImageView gave wrap-content prayer cards the
   bitmap's intrinsic height. Actual screenshots reproduced oversized previews.
   One center-cropped background drawable now serves real Home and previews,
   without participating in content measurement. Portrait renders inspected;
   large-text/landscape acceptance remains pending.
4. Phone `display.digits=auto` fell through to the Android TV capability catalog,
   where auto is unsupported; users saw a technical identifier. The phone now
   labels its own additional supported choice explicitly. TV capability gaps
   remain unchanged.
5. A new LG channel selection could reuse an older cached IPK without knowing
   its channel. New source sessions no longer adopt cached packages. Only an
   explicitly saved in-progress selection is restored and revalidated; a new
   channel requires a new verified download. Preparation/source selection now
   live in the unified entry rather than duplicated in the connection page.
6. Embedded Android setup returned Back from connection to a second preparation
   page instead of the common source page. The existing handoff fixture exposed
   this after the new entry journey. Prepared hosts now return to their caller;
   the standalone installer's own preparation flow remains intact. Post-extraction
   bilingual handoff and cancelled Advanced file-picker checks pass.

7. Embedded Android setup used fixed default colours after selecting a different
   phone theme. InstallerPalette is immutable and owned by its host Activity;
   runtime controls and artwork use that palette, without mutable global theme
   state or a new dependency. The standalone default is unchanged. Native
   bilingual theme/handoff assertions pass; actual Dusk renders inspected.

## Boundaries retained

Phone and TV preferences remain independent. Pure Qibla bearing is added to
shared/core; sensor quality, display rotation, magnetic declination and lifecycle
stay native. No new runtime dependencies, background location service, idle media
service, account or feed. Canonical TV platform metadata and lock icon live in
shared sources. Existing media and religious text are unchanged.

## Test audit and remaining inspection

Retain tests that protect contracts actually consumed across shells, saved data,
trust/integrity, scheduler boundaries, actual bound controls and runtime lifecycle.
Do not delete a test because it is small; identify duplicate behavior before
removing it. New evidence is one complete native journey plus focused Qibla and
package/channel edge cases, not getter/constant coverage. Full test audit pending.

Still inspect repository entry/build/dependency map, TV/service/preview integration,
high-risk persistence/transport/media paths and remaining UI responsibilities.
Record exact results and limits here/EVIDENCE.md. Screenshots and raw failures
stay private under ignored state/; do not claim a whole-repo clean bill of health
from filename searches or passing CI.
