# Phone feedback pass 4

Owner feedback: 19 November 2026, after testing 0.3.0/code 8.
This pass continues on feature/companion-installation / draft PR #9. Pass 3 was
handed over with all final-head workflows green on f04c1c9. It was not UX acceptance.

## Approved checklist

1. Remove Al Afasy from selectable adhan voices; safely migrate any saved choice.
2. Flatten excess nesting and simplify common interactions.
3. First use and guided TV setup present one decision at a time, short copy,
   pinned primary action and explicit Skip for optional steps. Preserve resume.
4. Compact stacked options; use switches/rows/segments where appropriate instead
   of large dropdown cards. Research current official app design, with floating
   main navigation. Retain accessible hit targets and readable Arabic.
5. Reduce scrolling through hierarchy and density; short pages should fit.
   Small windows/large text must still reach every control safely.
6. Appearance/customization use horizontal carousels and actual previews.
7. Compose shared backgrounds for portrait phones; provide portrait variants
   where composition requires them rather than blindly stretching TV artwork.
8. Reduced motion must have clear observable effects; remove misleading exposure
   if no relevant animation. Respect system animation preference.
9. Improve Qibla compass and Kaaba direction artwork without altering bearing,
   sensor quality/calibration/fallback or RTL direction correctness.
10. Provide multiple native prayer widget variations and per-instance customization
    with real previews; preserve independent instances and resize handling.
11. LG guided setup must stay guided through connection, one field/decision at a
    time, instead of handing off to the old IP/passphrase/instruction wall.
12. TV setup fills available screen; Android scan animation is fully visible,
    including insets, smaller screens and large text.

## Engineering and acceptance

PHONE-PASS3.md and AGENTS.md quality rules remain binding: smallest complete
solution, cohesive readable classes/methods, existing helpers/native Java Views,
canonical shared contracts/rules, no speculative flexibility, new framework,
trivial tests or duplicated policy. Preserve validation, atomic writes, private
credentials, all audio/data guards and independent phone/TV preferences.
All approved owner requirements survive compaction; this file is the exact scope.

Work sequentially. Record milestones and exact next action in PHONE-WORK.md and
WORK.md; put concise results in EVIDENCE.md and private captures/logs in state/.
Verify actual rendered English/Arabic flows, RTL, large text, portrait/landscape,
widget host configuration/update and focused failure cases. Required affected
consumer checks and final-head CI must pass before one combined APK handoff.
Keep PR draft. No physical device operation, release publication or merge. Stop
at handoff and await owner feedback; do not invent additional features.

## Design grounding

- Google Material 3 Expressive research: floating controls, hierarchy and usable
  targets: https://design.google/library/expressive-material-design-google-research
- Apple current layout: progressive disclosure and content under floating chrome:
  https://developer.apple.com/design/human-interface-guidelines/layout
- Apply these principles in existing Miqat native Views and canonical palette;
  no dependency or glass effect is required to achieve the interaction.

## Implementation decisions

Al Afasy is removed from canonical selectable voices for phone and the next TV
build. Existing saved choices migrate at each platform storage boundary to
Al-Malki (regular) or Chebae (Fajr), preserving unrelated settings. Remove the retired clip from distributed packages. Older TV metadata remains
readable without changing the TV or offering the retired selection. Radio recitation is a separate catalogue and unchanged.
Phone portraits preserve the entire artwork scene along the bottom and extend
its sky above it, with the shared contrast overlay; no stretched TV bitmap or
new unreviewed raster asset. Reduced motion suppresses page fades and installer
scan/progress animation and also honors the system animator setting.

## Home follow-up after screenshot review

The owner explicitly allows a short Home scroll. Do not squeeze every control
into one viewport. Give dates, prayer cells and actions clear gaps; place locked
modules in a lower section. Keep tomorrow Fajr with prayer information before
secondary tools, and integrate the location action into the prayer card. The
setup-resume prompt is only for an unfinished guide; verify disappearance on
completion and after reopening. Capture the completed state for the next review.

Further owner steering: the prayer card retains theme color highlights with
balanced placement of prayer name/time/countdown, rather than all text stacked
at one edge. Scheduled adhan runs while other apps remain usable; opening Miqat
is optional. Do not force an activity at alarm time or tie scheduled output to
the prayer screen lifecycle. Native regression must cover actual background
alarm delivery and switching away during playback.

Latest clarification: add an optional full-screen phone adhan screen, off by
default. A notification stays during background playback; explicit dismissal
(where Android permits it) or Stop ends playback. Full-screen background taps
and app switching must not stop audio. Stop-and-close is explicit; Back asks
confirmation. Audio completion closes the optional screen. Respect Android 14
full-screen alarm access and older pinned foreground-service notifications,
with a Stop action always available. Do not add duplicate playback services,
overlays or idle processes. Add safe migration for the new phone-only setting.

Notification controls also provide Play/Pause on the existing session, preserving
play position and configured remaining duration. The requested volume-key stop
is optional; clarify screen-only versus cross-app capture before adding special
access. Use documented APIs; no hidden volume listener or silent accessibility
service. Notification Stop/dismissal and explicit screen close target the current
event so stale controls cannot silence a later adhan.
