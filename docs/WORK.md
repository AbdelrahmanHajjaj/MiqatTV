# Current work

Updated: 2026-11-21. Owner approved the combined greeting + canonical TV verse
in the [Home/calendar/location proposal](design/phone-polish/home-calendar.html)
and requested native implementation on feature/companion-installation, draft PR #9.

Implemented 0.5.3/code 19: date and single city action inside the prayer card,
compact Location fields, calendar events and optional location updates while the
app is closed. Background access requires fresh explicit opt-in; previous
foreground travel preferences do not grant it. Preserve manual calibration,
alert opt-outs, Arabic/English and existing TV behavior.

Native acceptance and review export are recorded in EVIDENCE.md. Check final-head
CI, then keep PR #9 draft for owner review. No merge, release or physical deployment requested.
