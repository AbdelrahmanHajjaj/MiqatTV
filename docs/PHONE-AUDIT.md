# Pass-7 interaction and engineering review

20 November 2026. Native before/after captures: ignored `state/ux7-*`.
First use reduced to two populated pages; TV preparation grouped in numbered
rows with its source/inputs. App/widgets reuse one live preview and direct
visual rails. Short choices reuse compact controls; voices and long catalogs
keep searchable selection. Footer actions remain contextual and reachable with
large text; short scrolling is allowed. Native normal and enlarged portrait/
landscape renders reviewed, with RTL order and readable signature artwork.

Touched native classes reviewed for caller consistency, draft/save/cancel,
private inputs and removed paths. Removed obsolete per-step state, nested style
dialogs and unused labels. Shared view primitives consolidate compact choices,
numbered instructions and filtered selection. No dependency, transport, prayer
policy or new feature framework added. Full appearance edits now share coherent
Apply/Cancel semantics. Widget Add's existing invalid-ID mismatch corrected;
LG rejected input preserves typed private data in memory without persisting it.
Existing feature fixtures adapted; no blanket micro-test expansion. See
EVIDENCE.md for exact validation and hardware limits.

# Pass-3 engineering audit

Local implementation audit completed, 19 November 2026. Expected load: one household, local prayers and
foreground authenticated TV access. Requirements: [PHONE-PASS3.md](PHONE-PASS3.md).
This is an implementation audit, not physical acceptance or a claim that every
source line has been inspected. Final CI and hardware limits remain separate.

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

7. Voice sheets previously committed a tap before audition. PhoneSound now owns
   draft selection, audition/Stop/Cancel/Apply and private import; choice-row
   styling is shared with ordinary/appearance sheets. Preview source selection
   is independent of the saved voice and level override. Actual-service cases
   cover inherited global custom audio and private per-prayer audition while a
   different inheritance choice is saved.
8. Widget guide preview inherited the app's extra font scale and system fallback
   font, unlike the launcher. It now inflates with system text scale and declares
   the app font. The real six-prayer timetable passes enlarged-text clipping
   checks in verified portrait/landscape; the actual host bind/update check stays.

9. Embedded Android setup used fixed default colours after selecting a different
   phone theme. InstallerPalette is immutable and owned by its host Activity;
   runtime controls and artwork use that palette, without mutable global theme
   state or a new dependency. The standalone default is unchanged. Native
   bilingual theme/handoff assertions pass; actual Dusk renders inspected.

10. Mixed Arabic/English preparation paragraphs followed the system text locale
    even after choosing Arabic in Miqat. Shared phone text and choice rows now
    use the explicitly selected paragraph direction. Numbered preparation steps
    are separately spaced. Rendered Arabic instructions now start on the correct
    side and keep English menu names readable; the complete bilingual journey
    and both enlarged-text orientation matrices pass after this change.

11. Final runtime review split PhonePlayback into request validation, media-session/
    notification setup, player preparation and output start/cleanup. Private audio
    media validation has its own focused method. Error paths and ownership stay
    in the service/import owners. Two custom string callback interfaces were
    replaced by the existing standard Consumer type, including all callers.

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
package/channel edge cases, not getter/constant coverage. Location/quiet-hour cases were moved to shared common tests instead of duplicated
in phone core; both JVM and JS now run them. Existing native suites protect actual
bindings, saved drafts, trust failures, alarms and file preservation. Kept these:
none was proven redundant merely because it is short. New sound cases cover
unsaved audition/Cancel/Apply and custom inheritance selecting the correct file.
Sizing reuses the existing native matrix rather than adding a parallel harness.

## Repository audit map and retained limits

Inspected root settings/build inclusion and platform manifests/dependencies,
companion generation, canonical phone/TV catalogs and Apple-core workflow.
The phone shares installer/runtime and portable rules; Android TV/LG/preview
retain native shells. No new production dependency or framework was needed.
Shared generators remain sequential; Apple checks compile/test libraries only.

Traced the following high-risk paths to their callers and focused checks:

- Phone: MainActivity lifecycle → PhoneStore/PhoneAlarms/PrayerAlarm →
  PhonePlayback/PhoneAudio, plus Qibla sensor and PrayerWidget ownership. Alarm
  claims commit before output, events recheck current weekday/quiet settings,
  boot recovery only reschedules, private audio has bounded decode/import and
  foreground playback has Stop/focus/timeout cleanup. Native runtime covers these
  boundaries; OEM reliability and sensor calibration remain physical acceptance.
- Phone TV access: Phones uses atomic AES-GCM records under a shared lock;
  LanTv/PairingUi retain certificate pins, explicit approval and uncertain writes.
  HTTP/TlsServer bound headers/body/response/workers/deadlines; private LAN only.
  Existing native LAN checks cover tamper/recovery and no replay. No need for
  queues, accounts or infrastructure beyond one household.
- Installer: PublicRelease source/redirect allowlist, bounded metadata and SHA-256,
  PackageImport atomic replacement, Android package/version checks and LG
  LgPackage/LgInstall key pinning, transfer/version readback and explicit retry.
  Setup credentials remain session-only. Public downloads and physical installs
  remain separate acceptance; no published package exists yet.
- Android TV: CompanionApplication foreground listener ownership, PrayerWatch
  persisted claims, NowActivity/Adhans playback teardown and existing media
  packaging. Corrected the stale Adhans provenance comment to the approved
  shared/media source. This is inspection/build evidence, not TV sound acceptance.
- LG: companion-session foreground subscription/epoch cleanup, companion-server
  bounded TLS/requests and command uncertainty, companion-files owned POSIX
  atomic writes, service scheduling generations and volume restoration ownership.
  Existing ES5/service/browser checks remain required. No TV wake/Home capability
  or new phone-only UI is inferred from shared metadata.
- Preview: lifecycle AbortSignal cleanup and navigation replacement/history/focus,
  canonical shared data and token build. It remains a browser simulation.

One pre-existing TV finding is outside this phone release's runtime changes:
Android NowActivity restores its saved pre-adhan volume/mute unconditionally,
where LG checks ownership first. A user changing volume during an automatically
unmuted TV adhan may have that later change overwritten at Stop. Retain as a
specific next-TV-release correction with native volume-intervention regression
and watched hardware acceptance; do not silently change TV audio policy as phone
polish. Phone playback never changes global volume, verified natively.

This audit covers repository boundaries and the paths named above. It does not
claim a line-by-line review of every unchanged TV view, every astronomy/media
vendor source, operating-system implementation or dependency CVE database.
No dependency/security scan or physical acceptance is claimed. High-risk existing
checks plus affected consumer builds establish regression evidence for this pass;
they do not erase those limits. Remaining rendered layout and final consumer/CI
results belong in EVIDENCE.md; raw logs/captures stay under ignored state/.

## Pass-4 change review

Owner scope is PHONE-PASS4.md. The pass keeps Java Views and the existing
transport/media/storage owners. Compact controls and toggle tint reuse
CompanionViews; native widget configuration uses WidgetUi, WidgetOptions, WidgetRenderer and
one PrayerWidget implementation with the receiver components Android requires
for differently sized launcher entries. No new dependency or idle service.
Widget options share the phone's device-protected, backup-excluded store; TV
credentials stay separate. Configuration validates widget ownership, supports
Cancel, and only persists per-instance presentation after Apply/pin confirmation.

Retiring a canonical voice would reject an older TV's still-valid metadata.
LanTv therefore accepts that one retired value only for the two known voice
controls, retaining the true baseline for explicit edits and rejecting unknown
choices. Catalog/Choices hide it in selectors. Connecting never changes the TV.
Phone/new-TV storage migrates the retired choice at its own boundary. The
retired clip is removed from distribution; the remaining approved recordings
retain their exact checksums. No new audio is distributed.

Rendering remains in shells. Prayer/Qibla/quiet-hour calculations are unchanged.
The portrait artwork composition preserves the source scene without new raster
assets. No semantic tests were removed: fixtures follow the new visible steps;
real widget host/resize and retired-TV metadata regressions cover new boundaries.
Raw red/green logs and captures remain ignored under state/. Final results are
recorded in EVIDENCE.md after the final review build passes.

Final review caught widget previews using the Activity's compounded app/system
text scale. They now inflate using application context, matching launcher text.
A native rendered-font assertion reproduced the fault and passes all 80–150%
portrait/wide cases in both languages. The API-34 dialog fixture now waits for
its actual active window before injecting Back; dismissal/focus assertions stay.
No new production abstraction or dependency was needed for either correction.

## Prayer-card and adhan-control follow-up

PhoneAdhanScreen owns only the optional native view and deliberate input. It
observes the existing session, stops polling when backgrounded and never stops
scheduled audio in a lifecycle callback. PhonePlayback remains the sole player,
MediaSession and foreground-notification owner. Notification controls include
the event in immutable PendingIntent identity; old callbacks cannot stop a new
session. Pause retains player position, releases focus and excludes paused time
from configured playback duration. OS full-screen access is optional and checked;
there is no overlay, hidden volume observer or accessibility service.

Both new toggles are canonical phone-only controls. Strict format3 migration
uses the exact known field set for v1/v2 and defaults new controls off without
resetting prior audio/location/appearance. Lock-screen language can use the
existing device-protected value before unlock; TV credentials stay separate.
Tests target real alarm/screen-off/app-switching, media controls/duration,
stale dismissal, deliberate close, optional key input and migration failures.
They reuse the existing native runner and add no broad getter/coverage suite.

Android13+ derives media controls from PlaybackState slots, so ACTION_STOP plus
a Notification action alone does not expose its button. One media-state publisher
now declares a Stop custom action for initial/resumed states, with a callback to
end the same service. Older notification actions remain. Native tests invoke the
actual custom control and capture the expanded system notification. Source:
https://developer.android.com/media/implement/surfaces/mobile

## 0.4.3 adhan artwork and metadata

PhoneBackdrop extracts the existing scene compositor unchanged, preserving bitmap
validation and wrap-content sizing. Home, previews and AdhanArtwork reuse it;
the screen and notification reuse generated MiqatShape geometry and canonical
palette/voice labels. PhoneTime centralizes the existing native clock/timezone
formatter used by UI, widgets and notification. No portable calculation or TV
contract changed, and no dependency or duplicate player/service was added.
Playback captures the effective voice ID and scheduled prayer timestamp, so media
metadata names the clip playing rather than a later default or current wall time.
Selected artwork failures retain the themed arch and do not prevent playback.
Focused native checks inspect actual media metadata, SystemUI Stop and selected
nondefault-theme screen output; physical OEM notification behavior remains open.

## Pass-5 changes

The footer stays in the existing activity shell outside its ScrollView; editors
reuse that host and shared primary/secondary helpers. SwipeStop is one native
View with explicit gesture cancellation and standard accessibility activation.
It delegates to the existing session stop action. No new service, player,
gesture dependency or portable policy duplication. Widget Cancel clears its
unsaved draft. Location save failures preserve the editor. Focused native checks
cover accidental dismissal, actual playback, Save/Cancel, keyboard and text-size
boundaries; trivial copy changes have no new mirror tests. Redundant narration
and unused catalog entries were deleted, with the copy rule recorded in AGENTS.


## Pass-6 portrait, first use and widgets

Dedicated portrait signature artwork is packaged only by the phone; wide assets
remain unchanged. Existing PhoneBackdrop chooses by actual render bounds, shared
by first use, appearance and adhan. First use has three pages and one combined
live appearance preview; missing alert access stays contextual on its page.

WidgetRenderer retains native accessible text and canonical Alexandria font
resources. WidgetBackground owns only a bounded background bitmap (under 1 MB),
so opacity does not dim text. Per-instance options validate scene/shape/opacity
and preserve old transparent backgrounds. PhoneTime reuses Home’s existing
Umm al-Qura formatter, timezone and Hijri adjustment. No calendar, player,
background service or dependency was duplicated. Configuration uses compact
choice rows, a live opacity slider, independent dates and fixed Apply/Cancel.
Native populated XML previews and bilingual rendered PNG fallbacks replace the
empty picker; retired binary-opacity labels were removed. Focused host checks
cover preview contents, independent save/cancel, old settings, invalid options,
background-only alpha, rounded transparency and actual font binding.
