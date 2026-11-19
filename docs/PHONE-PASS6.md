# Phone owner refinements — portrait, first use and widgets

Approved 19 November 2026. Continue sequentially on the existing companion
feature branch/draft PR #9. Candidate 0.4.5/code 14. Engineering requirements
in AGENTS.md and PHONE-PASS5.md remain authoritative. No merge, release,
physical deployment, iOS work or unrelated follow-up.

1. Generate a dedicated portrait interpretation of Miqat signature artwork.
   Preserve its teal/gold/mihrab identity; no landscape strip, flat sky padding
   or masked seam. Use the canonical phone asset for actual portrait bounds,
   retaining wide artwork for landscape cards/media. Share decoding/rendering
   across setup, prayer/appearance previews and adhan. Keep TV packages unchanged.
2. Reduce first use to Location, Prayer alerts, and Appearance. Confirming a
   location advances directly. Handle notification/exact-alarm access contextually
   on the alerts page, with meaningful Skip only while access is pending.
   Combine theme/background choices on Appearance with one live preview and
   compact choices. Finish needs no duplicate Skip. Preserve resumability,
   Back, save errors, Arabic/RTL, large text and all existing preference behavior.
3. Fix Android widget-picker previews. Add per-instance background opacity and
   shape controls and optional Hijri date. Widget text/fonts/default colors must
   match the app, using existing native formatters, fonts, palettes and renderer.
   Preserve independent instances, Cancel, resizing, launcher/system limits and
   privacy. No new dependency or duplicate formatting/policy implementation.
4. Validate affected native journeys and render actual previews/screenshots;
   focus tests on feature behavior and edge cases. Review touched code for
   redundant helpers/dead paths. Export one exact APK receipt, preserve commits,
   require final-head checks and keep the PR draft. Stop for owner feedback.

Existing notification layout remains native Android media controls. The central
swipe label, deliberate bottom handle, background playback, contextual actions
and concise-copy rules from pass 5 remain in force.
