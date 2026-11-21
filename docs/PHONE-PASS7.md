# Phone UX refinement — 20 November 2026

Owner feedback supersedes the three-page/single-instruction setup of pass 6.
Continue sequentially on feature/companion-installation, draft PR #9. Target
0.4.6/code15. No merge, release, physical deployment, iOS or unrelated features.

- First use: two useful pages at most. Combine location and alert access; combine
  theme/background personalization with one live preview. No empty one-control
  pages, tutorial narration or duplicate navigation. Keep Skip and resumability.
- TV installation/pairing: all preparation instructions numbered on one page per
  platform, readable without developer knowledge. Keep relevant fields and source
  choices together; preserve public latest default, Advanced local files, trust,
  explicit installation, cancellation and actual operational progress.
- Appearance and widgets: one live preview, compact directly selectable carousels
  for visual choices. No thumbnail -> dialog -> another carousel navigation.
  Keep widget instance independence, background opacity/shapes/dates, Apply/Cancel,
  canonical typography/colors, RTL, large text and accessibility.
- Refine the wider phone UI: consolidate related settings, reduce nesting and
  over-sized repeated controls; short choices inline, long lists searchable.
  Preserve prayer/background playback, notifications, core rules and TV isolation.
- Use current primary design references, inspect actual before/after native
  renders and exercise affected bilingual journeys and sizing edges. Tests protect
  features/failures, not tiny implementation details. Reuse existing components,
  keep cohesive methods, remove superseded paths; add no framework/dependency.

Research: Google Material 3 Expressive research (group related controls, preserve
familiar actions, contrast and touch targets):
https://design.google/library/expressive-material-design-google-research
Apple onboarding (brief, interactive, optional):
https://developer.apple.com/design/human-interface-guidelines/onboarding
Material carousel and toolbar guidance:
https://m3.material.io/components/carousel/guidelines
https://m3.material.io/components/toolbars/guidelines

Next: capture baseline; implement two-page setup and direct visual selectors;
consolidate TV guide/credentials and refine shared phone rows/choice lists;
validate native flows/renders, review code, export exact APK and update draft PR.
Stop for owner feedback after this approved pass.
