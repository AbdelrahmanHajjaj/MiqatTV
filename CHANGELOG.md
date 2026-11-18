# Changelog

## Unreleased

- Expand the phone app into standalone Miqat prayer (0.2.0): independent offline daily
  times, tomorrow's Fajr, city/custom location and timezone, Hijri display,
  per-prayer voices/modes/adjustments/reminders, native scheduled alerts and
  foreground adhan with Stop. Add permission repair and local recovery/duplicate
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
