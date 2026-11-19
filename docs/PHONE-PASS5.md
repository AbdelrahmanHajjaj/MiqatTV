# Phone bottom actions and deliberate adhan dismissal

Approved 19 November 2026 on feature/companion-installation / draft PR #9.
Continue the existing standalone app scope and engineering requirements in
PHONE-PASS4.md and PHONE-AUDIT.md; no merge/release/physical deployment.

- Replace the optional adhan screen's centered Stop button with an approved
  bottom swipe-up handle. A tap, incomplete pull, horizontal pull, cancellation
  or interrupted gesture must not stop playback. Complete a deliberate upward
  pull and release to stop/close. Preserve an explicit accessible Stop action,
  optional volume shortcut, confirmed Back and notification controls.
- Keep the selected mihrab/background/theme and background/locked playback.
  Honor reduced/system motion; interrupted gestures reset safely.
- Unify screen confirmations/Continue/Finish at the bottom. Use one contextual
  primary action, optional Skip and draft/operation Cancel only. Existing top/
  system Back handles previous steps; do not duplicate Next/Previous controls. Dialog/sheet actions stay on their own bottom
  edge. Keep keyboard/system-gesture clearance and RTL/large-text reachability.
  In short landscape layouts, use a compact footer row and keep the app visible
  while editing instead of IME full-screen extraction.
- Move inline widget Apply/Add into this footer. Keep contextual actions next
  to their content; do not turn every setting or preview action into a footer.
- Reuse native Views, canonical bilingual labels/themes and existing callback/
  session ownership. No new dependency or gesture framework, second player,
  speculative abstraction or unrelated platform change. Focus regressions on
  accidental dismissal, accessible activation, footer reachability/navigation,
  keyboard and large text. Do not add mirror tests or loosen existing checks.

Candidate0.4.4/code13 supersedes0.4.3 after validation/export. Save new screenshots
and exact APK receipt, preserve commits, keep draft PR, stop for owner testing.

- Remove redundant instructions and narration throughout the phone app. Keep
  concise labels, useful status and necessary TV access/setup steps.

- Later owner refinement: center the swipe label in the prayer area, away from
  the bottom mihrab line; retain the bottom gesture handle. Keep Android media
  notification controls in their native layout. Per-app notification alignment
  and fixed bottom-center Play/Pause are not supported by SystemUI.

- First-use setup uses the canonical Miqat signature portrait composition on
  every step, with a subtle readability scrim. Keep brand chrome consistent;
  theme/background choices update their previews and saved personalization.
