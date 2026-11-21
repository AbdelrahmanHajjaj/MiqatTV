# Current work

Updated: 2026-11-22. Owner requested Adhan level against the selected stream's
maximum, independent of current media/alarm volume, then requested an in-card
playing wave, swipe-to-stop notifications, no manual timing step and dependable
foreground full screen. Branch
feature/companion-installation, draft PR #9. Review version is 0.5.11/code 27.

PhonePrayerRules maps the percentage to OS steps. PhoneVolume sets that level
without double attenuation, restores on pause/finish and preserves manual
changes. A private journal recovers interrupted restoration at the next Miqat
entry. Default/per-prayer levels, auditions and tests share the same path.

All focused and complete local runtime checks pass, including real background
swipe-to-stop with prior-volume restoration, automatic alarm timing, foreground
screen, motion/reduced-motion pixels and bilingual UI. Core/generator/canonical,
build/lint and the signed review export pass; see EVIDENCE.md. Android still
controls presentation over other apps/lock screens.

CI exposed test synchronization/expansion issues; the follow-up fixtures now pass
complete API 34 phone runtime and strict TV prayer/UI checks as well. App bytes
are unchanged. Next: exact-head CI and owner review in draft PR #9. Main must remain merged
before pushing. No PR merge, release or physical deployment requested.
