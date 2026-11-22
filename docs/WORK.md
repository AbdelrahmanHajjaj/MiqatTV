# Current work

Updated: 2026-11-22. Owner confirms 0.5.11 swipe dismissal stops Adhan on physical
hardware. Current scope is notification polish: remove decorative artwork,
display the actual custom recording name, emphasize prayer/time and replace
text actions with icon controls. Branch feature/companion-installation,
draft PR #9. Review version is 0.5.12/code 28.

PhonePrayerRules maps the percentage to OS steps. PhoneVolume sets that level
without double attenuation, restores on pause/finish and preserves manual
changes. A private journal recovers interrupted restoration at the next Miqat
entry. Default/per-prayer levels, auditions and tests share the same path.

All focused and complete local runtime checks pass, including real background
swipe-to-stop with prior-volume restoration, automatic alarm timing, foreground
screen, motion/reduced-motion pixels and bilingual UI. Core/generator/canonical,
build/lint and the signed review export pass; see EVIDENCE.md. Android still
controls presentation over other apps/lock screens.

Keep the compact system template and a decorated expanded view with wrapping
recording text, accessible Pause/Play/Stop icons and one native progress bar.
Resolve custom labels from the actual playing file identity, including unsaved
auditions. Focused native light/dark and bilingual control checks, swipe-to-stop,
build/lint and canonical checks pass. The signed review APK is exported; see
EVIDENCE.md. Next: owner hardware review of the notification.

Owner requested hardware review instead of prolonged emulator investigation;
do not hold APK handoff for repeated broad CI or emulator-version experiments.
Preserve unresolved CI evidence. Main must remain merged before pushing.
Keep PR #9 draft. No merge, release or physical deployment requested.
