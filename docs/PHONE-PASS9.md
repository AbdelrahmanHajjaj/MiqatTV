# Phone refinement after 0.5.0

Owner feedback on 21 November authorizes refining the native implementation
towards the approved English mockups, fixing bugs and carrying suitable UI
improvements to TV. Continue the existing branch/draft PR; no framework change.

- Compare current native captures with the approved gallery, including actual
  typography, geometry, grouped prayer rows, controls and widget presentations.
- Widgets respond to allocated dimensions across old/new Android: compact next
  prayer, horizontal timetable and tall daily timetable. Keep canonical fonts,
  prayer glyphs, highlights and saved per-instance style/date preferences.
- Space weekday circles without stretching wrapped rows; reduce oversized and
  uneven controls. Language has one concise entry with supported choices.
- Position sheets before their first visible frame. Home arrows change civil
  day. A calendar selection immediately updates the timetable beneath its grid.
- Weekday overrides enable and expand in the same view without another day page.
- Adapt existing artwork to portrait composition; show it dimly on Home with an
  optional off switch. Preserve canonical landscape masters and native rendering.
- Reuse applicable TV semantics/spacing; preserve remote focus, shell limits and
  existing prayer/media/quiet-hour behavior.

Target 0.5.1/code17, additive schema5 for the Home background toggle. Review both
languages, 80–150% app scale, enlarged Android text, portrait/landscape and widget
resize states. Browser references, native/emulator tests and physical acceptance
remain separate evidence. Keep logs/captures under ignored state/. Export the
fixed latest review APK, update draft PR9 and wait for final-head CI. No merge,
public release or physical-device deployment is requested.
