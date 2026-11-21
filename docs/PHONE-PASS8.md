# Phone design review — 20 November 2026

Owner feedback on 0.4.6 supersedes pass 7's interaction choices. Research and
English mockups were approved for full implementation on 21 November. Continue on
`feature/companion-installation`; preserve draft PR #9 and its existing changes.

Review the [English gallery](design/phone-polish/index.html) and its
[source-backed decisions](design/phone-polish/research.html). Serve the checkout
root locally to resolve canonical artwork, fonts and icons. No new assets or
dependencies are required.

From this checkout: `python -S -m http.server 8766 --bind 127.0.0.1`, then open
`http://127.0.0.1:8766/docs/design/phone-polish/index.html`.

Scope: warm welcome; labeled country/city and compact detection; contextual
notification request with respected denial; full-page style preview; supported
TV systems only; visible short choices, weekdays and numeric editors; advanced
location dialog; unified alert schedule; Settings entry for widget customization
and pinning. Cover current phone/TV page families and important dialogs/states.

Schedule behavior and migration are proposals for review. Do not silently alter
existing quiet-hour or reminder-boundary behavior. The report records the edge
cases to resolve before implementation. Existing native Java Views, shared
contracts, phone/TV isolation, trust, recovery and draft rules remain requirements.

Validation for this pass: syntax, mock route/coverage assertions, source links,
canonical asset loading, rendered gallery and focused interactions. Mockups do
not establish native acceptance. No APK, merge, release or physical operation.

Next: owner reviews this complete design package; then implement the agreed
direction sequentially. Broader features and framework changes remain excluded.

## Revision 2 — 21 November

The owner’s sixteen follow-up points extend the mock scope: Hijri month/day
prayer browsing, compass/map Qibla, per-prayer enable plus Adhan/Short tone/Silent
and separate vibration, recording-relative length/ending preview, weekday
overrides, Hijri-first widgets, canonical prayer glyphs and spaced TV rows,
and the approved transparent mark. No alarm-access onboarding or redundant
playback labels. The gallery now has 125 states.

The research documents OS-owned prompt styling, exact-alarm fallback work,
legacy quiet-hour migration, fixture timetable limits, map provider/privacy
and non-continuous TV playback state. These are implementation constraints,
not reasons to add permission steps back to the mock onboarding.

## Implementation approved — 21 November

The owner approved revision 2 and requested full implementation. This supersedes
the design-only stop above. Implement the complete approved phone/companion
experience, preserving native Java Views, canonical bilingual labels/assets,
portable policy and existing trust/install boundaries. Target 0.5.0/code16.

Migration: preserve retired sound values and legacy duration caps until edited;
per-prayer enable, sound/vibration, day inheritance and percent length are additive.
Retain existing quiet-hour rules with their original actual-event boundaries until
the owner explicitly disables them in a clearly identified legacy-rule editor.
No automatic conversion to weekday rules that could change what a user hears.

Use exact alarms when allowed and an honest inexact fallback otherwise.
Notification denial blocks prayer output. Timing access remains optional
troubleshooting, outside onboarding; native fallback may be delayed. Qibla map
networking is foreground and explicit, with provider attribution/privacy; no
background location or map prefetch. Calendar uses the real native calculation
engine and existing Hijri correction, never gallery fixture prayer times.

Deliver required focused/core/native bilingual and size evidence, lint/build,
updated guides/changelog, latest review APK and updated draft PR #9. No public
release, merge or physical-device deployment is authorized by this pass.
