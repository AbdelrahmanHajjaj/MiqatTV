# Current work

Updated: 2026-11-17. Checkout: D:/Dev/MiqatTv. Branch: main.

## Approved scope

Set up the independent MiqatTV repository from the platform-core implementation.
Use Miqat / ميقات and tv.miqat.* identities, with fresh installation. Retain MIT
and third-party notices. Remove legacy branding, links, workflows, obsolete
prototypes and migration diaries. Keep README short and technical guides in docs/.

## Completed

Source import, namespace/design-path renaming, new docs/agent rules and PR template
are complete. Shared core, Android/installer, LG and preview checks pass; bilingual
rendered output was inspected. See [EVIDENCE.md](EVIDENCE.md) for commands/results
and remaining limits. No inherited Actions or companion implementation is included.
The old checkouts are clean. Private test media and evidence stay ignored.

## Publication and remaining work

The owner approved the initial main commit and push on 17 November 2026. This
foundation starts a fresh Git history in AbdelrahmanHajjaj/MiqatTV. No release
or TV deployment is part of that publication. Bootstrap implementation and
validation are complete; no further bootstrap changes are queued.

Physical new-identity installation, full phone-installer acceptance, portable
settings transfer, companion integration and preview dependency remediation remain
separate work. No new platform framework, cloud/accounts, feeds or analytics.

## Appearance review — 18 November 2026

Owner requested background/palette refinement in an isolated worktree, with a
visual review before committing. Branch `design/miqat-themes`, based on `e612c11`.
Twenty palettes, all eight masks and scenic backgrounds are refined, including
seven non-arch ornamental families, one complete-arch composition and a new
geometric default tile. Six neutral CleanYourTV illustrated landscapes are added
for review alongside the existing procedural scenery. Six existing presets now
pair those scenes with handcrafted palettes/surfaces. Appearance puts the complete
preset carousel first and retains the existing controls under Customize. The
Miqat default now has original sculpted petrol/jade-and-gold flagship artwork. No mask crops its motifs.
LG pattern ink/scale follows the chosen palette. Review assets are local under ignored
`state/theme-review/`. Keep this work uncommitted and unpushed until the owner
reviews it. Main and companion work stay separate; no TV operation is authorized.

Owner additionally requested a Home mihrab fill-opacity control (50–100% in 5%
steps; default/recommended 85%), followed by a cohesive rework of all six prayer
icons. Preserve opaque text/outlines, existing Apply/Cancel semantics and all
non-Home prayer/alert frames. Continue visual review before committing.

The owner rejected the first decorative icon proposal as unclear. Review revision
7 replaces it with simpler, heavier solar/lunar symbols and includes actual-size
samples; acceptance remains pending. Opacity is implemented and checked in all
three renderers. See EVIDENCE.md for verification and physical-TV limits.

Owner selected 75% next-prayer fill and approved adding Miqat quiet alongside the
original flagship. Keep Miqat signature as default; no automatic language-based
background flip. Changes remain uncommitted pending the overall visual review.

## Delivery approval — 18 November 2026

The owner approved the full 20-theme/24-background library and authorized commit
and normal merge into MiqatTV main. Preserve the current flagship default, quiet
alternative, 75% next-prayer fill and 50–100% Home mihrab control (85% recommended).
Run required CI before merging; no release or physical-TV deployment is requested.
