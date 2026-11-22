# Current work

Updated: 2026-11-22. Approved scope: align Android TV and LG's common UI with
phone: immediate settings/appearance saves, raised selected/inset inactive short
choices, Miqat-first theme/background lists, filled artwork cards, and Home's
in-card Hijri date/city, clean verse and next-prayer emphasis. Preserve TV launcher,
input/audio and saved preference behaviour; existing capability gaps stay explicit.

Implementation and local native/browser validation are complete. Versions:
Android TV 0.7.2/code 13, LG 0.2.30, phone 0.5.18/code 34. EVIDENCE.md records
checks, the corrected scroll bounds and emulator versus physical limits.
Latest main was fetched/merged before the PR push. Next: complete required PR
checks and normal merge, then prepare an updated preview from successful main CI.
Publication and physical TV deployment still need specific instruction.

The existing v0.7.1-preview.1 draft covers the preceding revision. Stable signing
remains unconfigured. Notification automation checks actual dismissibility and
cleanup; no fixed OEM swipe gesture is required by the owner.

Overwrite only the existing Miqat-latest review exports under state/builds/.
Retain the phone's existing local signer; private logs/captures stay under state/.
