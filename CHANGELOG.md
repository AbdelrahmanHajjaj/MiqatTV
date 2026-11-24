# Changelog

## Unreleased

- Added a Miqat iPhone development target with shared Kotlin prayer rules,
  a native Prayer screen, Hijri calendar, Qibla, alerts and companion views.
  The [iOS parity queue](docs/IOS-WORK.md) tracks remaining build and device checks.
- The iPhone companion development target can edit Android TV palettes and app redirects.
- The iPhone companion demo can simulate disconnection, a failed save, HDMI selection, radio and Adhan controls.
- The iPhone companion TV preview reflects countdown placement, prayer rows, favourites, and TV language and clock settings.
- iPhone prayer widgets can use a different Miqat palette, scene and background opacity for each placement.
- The iPhone prayer widget's 120% text option applies to all layouts.
- Opening a recent Adhan notification can start playback or reopen its controls on iPhone.
- The iPhone development target can download and share verified Miqat TV packages from Production and Staging.
- The iPhone development target can inspect and share local Miqat Android TV APKs and LG webOS IPKs.
- The iPhone development target can find advertised Android TV debugging and pairing ports.
- The iPhone development target adds Android TV APK installation through classic network debugging.
- The iPhone development target adds six-digit Android TV Wireless debugging setup.
- The iPhone development target can configure Android TV Home and remote buttons and restore its saved changes.
- The iPhone development target offers the optional Android TV radio, startup and Wi-Fi name controls.
- iPhone Android TV setup backups can be saved to Files and restored with the desktop tool.
- The iPhone development target adds reviewed Android TV cleanup and recovery, including apps disabled by earlier Miqat installers.
- The iPhone development target adds Android TV prayer preferences and Home favourites during setup.
- The iPhone development target adds LG Developer Mode setup, installation and pairing handoff.
- Miqat Phone TV setup defaults to Production and offers Staging for prereleases.
  Select a downloaded Development package in Advanced.
- Development and Staging builds run on demand. Production promotion tests
  the selected Staging candidate and publishes its website and screenshots.

## v0.7.7

- Miqat Phone shows seconds in the next-prayer countdown and previews adjusted
  times beside prayer and reminder controls.
- The Circle home-screen widget shows the next prayer's icon, name and scheduled
  time above the countdown and date. The ring refreshes automatically and the
  120% setting enlarges the text. Home-screen widgets show countdown seconds.
- Swipe the Hijri grid to change months and the timetable to change days.
- The Qibla map loads on selection, opens at the phone's precise location and
  supports pinch zoom, panning and recentering. Qibla uses the phone's location
  independently of the saved prayer city.

- Sideloaded Android phone and Android TV builds can check for stable updates
  from About Miqat and open the matching GitHub release.
- Miqat Phone uses its themed sheet for update checks.

- LG webOS shows the installed version, developer credit and a GitHub feedback
  QR code under About Miqat. Email feedback is currently disabled.

## v0.7.6

- LG keeps the Arabic adhan call inside the mihrab and moves focus to the first
  control when opening Reminders or Adhan Tests.
- The LG adhan page uses a single Close button to stop audio and return to the input.

- Miqat Phone shows download and transfer percentages during Android TV and LG
  setup, with separate status messages for installation and verification.
- LG setup can remember the TV address and encrypt its Developer Mode
  passphrase on the phone. Approving a new TV identity continues the connection
  with the passphrase already entered.
- Android phone and Android TV show the installed version, developer credit and
  GitHub feedback in About Miqat. Email feedback is disabled until a public address is set.
- LG webOS uses a solid full-screen adhan page when HDMI is released. Where
  per-player audio is available, Miqat lowers HDMI volume and offers Mute HDMI
  and Hide HDMI during the call.

## v0.7.5 — Android phone 0.5.21

- Use Miqat Phone for TV setup and recovery. New releases no longer include a
  separate installer APK.

## v0.7.4 — Android phone 0.5.20

- Android phone adds Quran Radio with a Home popup, quick playback icons, and
  background playback in Android's media controls.
  Adhan pauses radio, and listeners can resume the live station afterward.
- Phone radio shows when it is buffering, allows four automatic reconnects,
  and brings back Play if the station remains unavailable.
- The Android radio media player shows artwork from the phone's active theme
  and background, including changes made during playback.
- The phone Hijri calendar numbers every day of Ramadan and marks the Eid
  holidays, Hajj dates and other common observances across their full ranges.
- Phone settings add opt-in day-before, suhoor and iftar notifications for
  Ramadan and optional fasts on Ashura, Arafah, the White Days, and Mondays and
  Thursdays.

## Stable release — Android TV 0.7.3 / phone 0.5.19 / installer 1.1.5

- Build phone, Android TV and installer release APKs with permanent Miqat signing keys for future updates.
- Verify release mode, package identity and signing certificates before release packaging.
- Remove automatic Android phone/TV emulator runs from routine CI; retain build, lint, portable-rule, browser and release-package checks.
- Promote the verified signed packages to stable v0.7.3, preserving their checksums and the published preview.

## Product website and documentation

- Rewrite the English/Arabic website around daily prayer features, phone-to-TV controls and device-labelled downloads, informed by product website research.
- Showcase standalone Android phone prayers, TV companion mode, Android/Google TV and LG across the bilingual website and guides, with current screenshots.
- Replace filler and repeated hardware disclaimers with concise feature descriptions in English and Arabic.
- Reuse the website build's Pages artifact when retrying deployment.
- Correct the phone TV-preview caption that still referred to the retired Appearance Apply step.

## TV UI alignment — Android TV 0.7.2 / LG 0.2.30 / phone 0.5.18

- Bring the phone’s immediate settings saving to both TVs. Adjust numbers and short choices directly; remove Appearance Apply/Cancel. Preserve saved values and paired-phone conflict protection.
- Put Miqat and Miqat signature first in appearance choices, keep selected artwork cards filled and readable, and preview backgrounds across the Appearance page.
- Use raised selected choices and inset inactive choices; share the same switch style between settings and individual prayer alerts.
- Place the Hijri date and city inside Home’s prayer card, remove the verse caption, and reserve the highlight border for the next prayer. LG’s Home Fajr slot now follows tomorrow’s next prayer overnight like Android TV.
- Use 12-hour time for new installations; retain existing explicit clock selections.

## 0.5.17 release preparation

- Show **Update prayer widget** when reopening an existing widget's settings; keep **Add prayer widget** for a new widget. Settings still save immediately and remain independent per widget.
- Include the Android phone app and its checksum in prepared draft release bundles alongside Android TV, the installer and LG webOS.

## 0.5.16 widget readability

- Keep a prominent Hijri date on wide one-row widgets instead of hiding it with compact metadata.
- Enlarge and center the remaining time inside safe bounds, including transparent and square styles. Use the smallest tile's spare space for its countdown.
- Rebuild the prayer layout correctly when resizing between a square and a wide card, and refresh native picker previews.

## 0.5.15 one-cell widgets

- Start Next prayer at 1×1 and allow every widget to resize down to one cell on supported launchers.
- Fit the next prayer and time into compact bounds, hiding dates/icons until space permits. Retain the full time for accessibility and restore details when enlarged.
- Refresh native English/Arabic picker previews and keep widget settings intact through resizing.

## 0.5.14 notification identity

- Restore the Miqat mihrab in the status bar and notification header. Keep the matching prayer icon beside the expanded prayer heading.

## 0.5.13 guided location and prayer icons

- Show the matching prayer icon beside the expanded notification heading and in its compact system header.
- Automatically request location access and detect location on the guided location step, once after Welcome. Keep manual country/city selection available after denial.
- Include optional automatic location updates on that step. Require separate background consent and location confirmation before scheduling updates; preserve the choice during setup edits.

## 0.5.12 clearer Adhan notifications

- Remove decorative artwork from the notification. Keep the prayer and scheduled time prominent and show the actual saved custom recording name.
- Replace text action buttons with accessible Pause/Play and Stop icons in the expanded notification. Keep the compact system layout, one progress bar and swipe-to-stop behavior.

## 0.5.11 Adhan playback refinements

- Set the Adhan level against the selected media/alarm stream's maximum, independent of its previous volume. Apply default and per-prayer levels to playback, Listen and length previews.
- Restore the previous stream level on pause or finish, preserving manual volume changes. Recover an interrupted restoration at the next Miqat entry.
- Show an animated playing band across the prayer card's bottom edge, with accessible playback controls and reduced-motion support. Remove the detached square Stop button.
- Let supported Android versions swipe the playback notification to stop Adhan, retaining Pause/Stop and progress.
- Open the optional Adhan screen while Miqat is active, independently of lock-screen permission. Use Android 13+'s automatically granted alarm permission for core prayer timing.

## 0.5.10 Adhan volume

- Use media volume for Adhan by default. Add the optional **Use alarm sound** toggle in Prayer alerts to use alarm volume instead.
- Keep the player, media session and phone volume buttons on the same selected stream. Preserve existing settings without opting users into alarm sound.

## 0.5.9 audio library and phone testing

- Keep multiple custom adhans in a reusable named private library; default names come from the selected file. Preserve earlier imports.
- Listen plays the full recording from the beginning. Length preview starts at the beginning and stops at the selected percentage.
- Add cancellable Adhan and notification tests with a 30-second grace period, independent of the real prayer schedule.
- Remove the redundant phone Reduce motion setting and fix the Coming soon sheet expanding sideways after appearing.

## 0.5.8 device adhan selection

- Keep **Choose audio from device** visible in every phone adhan voice picker, without first selecting Custom audio from the list. Reuse the validated private import for default and individual prayer voices.

## 0.5.7 immediate appearance and widgets

- Save phone appearance, display and widget changes immediately, including existing placed widgets. Back retains changes; page-level Apply/Cancel is removed.
- Save TV appearance selections through the existing confirmed, conflict-aware path. Keep validated custom-color dialogs and Android widget Add completion.

## 0.5.6 immediate controls and quieter Home

- Save numeric +/− changes immediately across phone settings and TV controls, preserving earlier adjustments when switching fields. Valid phone numeric typing saves without replacing the field; invalid values stay rejected.
- Keep full-page preview Apply/Cancel while removing extra per-number confirmation steps.
- Remove the welcome ad/account label, the verse reference line and Home location success/confirmation copy. Keep location setup and meaningful errors accessible.

## 0.5.5 strip highlight

- Outline the strip’s next prayer with a theme-colored rectangular border.
- After Isha, highlight Fajr with tomorrow’s actual time instead of leaving every column plain.

## 0.5.4 phone refinement

- Remove “excerpt” from the verse reference.
- Keep Hijri browsing inside the popup and return directly to the full live Home.
- Default fresh phone settings to 12-hour time; keep saved clock choices.
- Show both enabled dates on the prayer strip and fill its height with prayer columns.

## 0.5.3 phone Home, calendar and location

- Keep both the welcome greeting and canonical Quran excerpt; move Hijri date and
  one city action into the prayer card with compact Location settings.
- Mark five recurring Hijri events in the calendar, show selected-day event details
  with prayer times, and add one relevant current/upcoming event on Home.
- Add optional location refresh while the app is closed, with explicit Android
  background permission, manual fallback and protection from stale fixes.
- Preserve existing settings and foreground-only travel consent during upgrades.

## 0.5.2 phone refinement

- Simplified the Home date entry and retained calendar/timetable browsing.
- Kept weekdays on one row and restored settings scroll before drawing.
- Filled next-prayer widgets with the prayer hero instead of an inset outline.
- Put the default Miqat Signature background and color theme first.

## Unreleased

- Run PR checks for affected platforms, cancel superseded CI runs, and include
  affected Companion checks in the merge gate. Downloadable review builds come
  from main or manual branch runs; PR compilation and package checks remain.

- Gently reduce rumble and background noise in all 14 adhan voices, preserving
  existing wording, duration and bitrate. The recordings are slightly smaller;
  spoken reminders are unchanged.

- Refine phone 0.5.1/code17 towards the approved mockups: grouped prayer/settings
  rows, spaced circular weekdays, compact numeric editors and a language picker.
  Place sheets before their first frame; fix Home date-arrow touch targets and
  show the selected timetable directly beneath the Hijri calendar.
- Expand weekday overrides in place. Add a dimmed Home artwork toggle and adapt
  landscape-only scenes to portrait without changing the canonical TV artwork.
- Make placed widgets adapt among next-prayer, horizontal and daily layouts on
  resize, including older launchers. Add canonical icons, serif prayer heroes,
  next-prayer highlights and matching native English/Arabic picker images.
- Carry choice spacing to the TV preview and current/next/past prayer styling to
  LG's Today page in 0.2.29. Keep scheduling and media policy unchanged.

- Implement the approved phone design in 0.5.0/code16: welcome-first setup,
  labeled country/city and compact detection, a single notification decision,
  full-page style previews, visible short choices/weekdays and inline numbers.
- Add Hijri month/day prayer browsing and an explicit online Qibla map with
  attribution, alongside the offline compass. Use the transparent Miqat mark.
- Unify per-prayer enable, Adhan/Short tone/Silent, independent vibration and
  weekday overrides. Add recording-relative length and ending preview; preserve
  legacy quiet windows, saved modes and seconds caps until deliberately edited.
- Keep alarm access out of onboarding, with a disclosed approximate scheduling
  fallback. Add Widgets in settings, default new widgets to Hijri, and refine
  supported-only TV setup, prayer icons and spaced media/action rows.

- Refine phone 0.4.6/code15: two-page first use, single live appearance/widget
  previews with direct carousels, compact inline choices and searchable voices.
  Show numbered TV preparation together with release selection and LG credentials;
  remove single-instruction pages and nested visual-choice dialogs. Keep a coherent
  appearance Apply/Cancel draft and expose language directly in Phone settings.

- Add purpose-generated portrait Miqat signature artwork in phone-only packaging
  for 0.4.5/code 14. Reduce first use to three pages, combining theme/background
  choices with one live preview and contextual alert permissions. Add populated
  Android widget-picker previews, per-widget scene/opacity/corner controls and an
  optional Hijri date, using the app’s fonts, palettes and date formatter.

- Replace the optional phone adhan Stop button with a deliberate bottom swipe-up
  handle in 0.4.4/code13. Unify bottom primary/secondary action footers and dialog
  surfaces, preserving accessibility, large text and keyboard clearance. Remove
  redundant instructional copy and simplify first-use permission actions. Center
  the swipe label and use contextual actions without duplicate navigation. Use
  the signature portrait background across first use, with readable controls and
  a smooth sky/artwork transition.

- Add selected background/theme and canonical mihrab artwork to the optional
  phone adhan screen and notification in 0.4.3/code 12. Show the scheduled prayer
  time and effective playing voice in notification/media metadata, using shared
  native scene composition and clock formatting.
- Restore balanced, theme-highlighted prayer-card text in phone 0.4.2/code 11.
  Add notification Play/Pause/Stop and dismissal cancellation where Android
  supports it, with pause preserving position and remaining playback duration.
  Offer a default-off adhan screen with explicit closing/Back confirmation and
  an optional volume-key stop while visible over the lock screen. Preserve
  background/screen-off playback; migrate prior phone settings safely.
- Give phone Home (0.4.1/code 10) more breathing room and allow short scrolling.
  Integrate location in the prayer card, show tomorrow Fajr before tools, and
  move locked features below separately spaced actions. Verify completed setup
  hides its resume prompt.
- Refine the Android phone review build to 0.4.0 (code 9), with compact settings,
  floating main navigation, fewer Home rows and responsive prayer columns.
- Simplify first use to one decision per screen with Skip; add real theme and
  portrait scene carousels. Explain reduced-motion effects and improve Qibla's
  Kaaba marker. Preserve sensor/calculation behavior.
- Add next-prayer, timetable and strip widget entries with independent layout,
  theme, background, date and text customization, draft Cancel and resize handling.
- Keep TV setup full screen with one preparation step at a time and separate LG
  address/passphrase screens. Fit the complete scan artwork; shared installer
  changes also ship in standalone installer 1.1.4 (code 7).
- Remove Al Afasy from adhan choices and migrate local saved selections. Read
  older TVs without rewriting their settings or offering the retired choice.


- Complete the Android phone review build 0.3.0 (code 8); shared installer changes
  also ship in standalone installer 1.1.3 (code 6). Physical acceptance remains
  open for the combined owner review.

- Audition a draft adhan voice before Apply, with Stop/Cancel and private audio
  reuse/import. Keep global and per-prayer custom previews independent of saved
  inheritance. Match widget text to the app and preview its actual system scale.

- Add resumable first-use location, alert and personalization setup, real phone
  appearance previews, a Qibla compass and native prayer timetable widgets.
  Keep broader worship modules visibly locked as Coming soon.
- Unify TV setup/pairing through supported-platform preparation and default latest,
  stable or preview releases; keep local APK/IPK selection in Advanced. Android
  setup follows the selected phone theme. Unsupported TVs show Coming soon.
- Preserve valid local packages when a replacement fails validation or is
  interrupted; cancel downloads without publishing late results. Keep Back and
  cancelled file selection within the common setup journey.

- Extend phone adhan settings (0.2.1) with per-prayer level/duration, weekday
  schedules, quiet hours, silent/vibration modes and private custom audio imports.
  Preserve existing settings and add opt-in foreground travel-location refresh.
  Shared installer download changes ship as standalone installer 1.1.2.

- Compact phone settings into saved-value rows, focused number editors and
  choice sheets. Keep the three root tabs stable while using nested TV controls.

- Download public Android TV/LG packages from phone setup with size/SHA-256
  verification, labelled previews and local files under Advanced. Protect newer
  Android installs using the APK version code rather than the release tag.

- Add one-shot phone location detection with approximate access, confirmation,
  city fallback and manual coordinates under Advanced.

- Expand the phone app into standalone Miqat prayer (0.2.0): independent offline daily
  times in a compact daily overview, tomorrow's Fajr, city/custom location and
  timezone, Hijri display,
  per-prayer voices/modes/adjustments/reminders, native scheduled alerts and
  foreground adhan with Stop. Add permission/battery help and local recovery/duplicate
  protection, shared phone themes/artwork and phone display preferences.
  Preserve Android/LG installation and authenticated TV controls.

- Add LG Developer Mode installation/update from the phone, with explicit SSH
  identity approval, local Miqat IPK checks, downgrade protection and installed
  version verification. Keep setup credentials in memory for the session.
- Match Android setup to the companion's canonical palette, Alexandria headings,
  filled cards and compact hero; avoid idle decorative animation. Respect OS
  reduced motion and the embedded phone preference (standalone installer 1.1.1).

- Add Set up Android TV inside the Miqat phone companion (0.1.4), reusing the
  standalone installer's guarded installation, cleanup and recovery. Follow the
  phone language and offer normal pairing after successful setup. Correct the
  installer download/catalogue repository URL.

- Load TV settings sections as sidebar focus moves Up/Down, with no OK press.
  Keep focus in the sidebar until moving toward the content, in both languages
  (Android TV 0.7.1 and LG 0.2.28).

- Keep LG QR artwork mounted while phone approval changes, and let the phone
  finish connecting before optional control discovery completes (LG 0.2.27,
  phone 0.1.3). Reuse stored pinned certificates when rescanning known TVs.
- Add Forget TV to the phone's TV-list and Remote menus; remove only that
  phone's saved TV record/cache. TV-side revocation still uses its remote.
- Make native/LG test screenshots opt-in instead of creating evidence folders.

- Center the phone pairing-button icons beside their labels (0.1.2), and let
  Up/Down navigate LG pairing from Back through enabled controls while the QR
  stays visible (0.2.26).

- Refine the Miqat phone companion with persistent floating navigation, clearer
  selection, compact headers, rounded controls and stable in-place loading.
  Capitalize its launcher label to Miqat (phone version 0.1.1).
- Speed up legacy LG identity preparation with native OpenSSL, reuse unchanged
  QR artwork during approval and remove a redundant phone pairing request.
  Existing trust and physical TV approval remain required (LG version 0.2.25).

- Keep the branded LG splash visible until the first screen is ready.

- Sync companion branding/catalogs with the refreshed main design and expose its
  Home mihrab-opacity setting with shared bounds, drafts and conflict handling.

- Add the approved theme-matched Miqat centre mark with half-gap clearance and
  stronger error correction to Android TV and LG pairing QR codes.
- Use `miqat` as the Android companion launcher label (product name `miqatTv`).
- Port the Android phone companion and foreground TV pairing/settings controls to
  Miqat, using its shared artwork, geometry, palettes and tv.miqat identities.
- Add themed local pairing QR codes, physical TV approval, phone appearance
  preview with Apply/Cancel, and Home/input/radio/adhan-preview controls.

- Refresh the README with current Miqat Android screenshots and remove its header icon.

- Keep FFmpeg audio-processing tests local; CI retains approved media checksum checks.

- Refresh the bilingual website with Miqat branding, current artwork and themes.
  Switch demo screens in place, keep settings and restore focus without page loads.

- Add Miqat quiet as a second original Miqat background; keep Miqat signature as
  the default. Use 75% surface opacity for the Home next-prayer card while keeping
  its text and gold outline opaque.

- Add Home mihrab fill opacity from 50–100% in 5% steps, with 85% recommended.
  Preserve opaque text/outlines and draft Apply/Cancel behavior.
- Simplify all six prayer icons with heavier solar/lunar symbols, shared across
  Android, LG and browser preview.

- Give the default Miqat preset bespoke sculpted petrol/jade artwork with fine
  champagne-gold inlay. Keep its branded palette and classic panels, and preserve
  manual background choices. The geometric lattice remains available separately.

- Put complete presets first in Appearance, above the existing Customize controls.
  Selecting a different preset previews its palette, surface, paired background
  and lighting; Apply/cancel and manual adjustments remain available.
- Tailor Dusk, Sand, Midnight, Aurora, Forest and Slate into Dusk peaks, Golden coast,
  Moon lake, Aurora sky, Tea hills and Monsoon, keeping their stable setting IDs.

- Add six local, neutral illustrated backgrounds from CleanYourTV: Dusk peaks,
  Golden coast, Moon lake, Aurora sky, Tea hills and Monsoon. Available in Android,
  LG and browser preview, with bilingual choices and preserved MIT attribution.

- Refine all 20 built-in palettes around deeper petrol, mineral and earth colors,
  with quieter lighting and verified text/accent contrast. Redraw the decorative
  masks with geometric, botanical and flowing designs, keep the sole arch option
  complete, replace the default tile with stars and soften scenic backgrounds. LG patterns use
  palette-matched ink and the same full-screen proportions as Android/preview.


- Publish the bilingual interactive preview to GitHub Pages after main passes validation.
- Update the preview toolchain to include the available Astro security fixes.

- Preserve Android's arrange menu and selected app when the installed-app catalog refreshes.

- Add full-audio CI builds, validation Actions, a manual draft-release workflow
  and main-branch protection requirements.

- Show English and Arabic Android launcher Home screenshots in the README.
- Establish Miqat as an independent project with Android TV and LG applications,
  a portable Kotlin JVM/ES5 core, canonical data/design and a browser preview.
- Use Miqat names and tv.miqat.* identities throughout apps, services, setup
  tools and tests. Initial installation is a fresh install.
- Use the Miqat calligraphic artwork, icons, splash, mihrab geometry and patterns.
- Share prayer-minute flooring, numeric editors, UI/input contracts and sizing.
- Simplify the README and move technical guidance into docs/.

This repository starts with the platform-core foundation. Companion development,
release publication and physical acceptance of the new package identities are separate.
