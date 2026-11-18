# Current work

Updated: 2026-11-18.

## Approved scope and next action

The owner approved the complete 20-theme/24-background library and authorized
commit and normal merge of `design/miqat-themes` into MiqatTV main. Preserve Miqat
signature as default and Miqat quiet as an additional choice, the 75% next-prayer
fill, simpler prayer icons and 50–100% Home mihrab control (85% recommended).
The preset carousel precedes the existing customization controls. Changes cover
Android, LG and preview; no companion or physical-TV deployment is requested.

The design commit is integrated with current main. Resolve integration checks,
open the PR, wait for required CI and merge normally. Main's existing Pages
workflow updates the bilingual preview after successful validation. Essential
verification belongs in EVIDENCE.md; raw captures stay under ignored state/.

## Previous delivery and remaining limits

[PR #1](https://github.com/AbdelrahmanHajjaj/MiqatTV/pull/1) was normally merged as
2bb348f. [Main validation](https://github.com/AbdelrahmanHajjaj/MiqatTV/actions/runs/44301830392)
passed all six jobs; [draft publication](https://github.com/AbdelrahmanHajjaj/MiqatTV/actions/runs/44304525540)
created an unpublished full-audio prerelease with verified checksums.
[PR #2](https://github.com/AbdelrahmanHajjaj/MiqatTV/pull/2) added the approved
GitHub Pages publication and preview security fixes, merged as 3624b10.

Physical Miqat installation, complete phone-installer testing, settings transfer
and companion integration remain separate. Browser mocks and emulator captures
are not physical-TV acceptance. CI APKs use temporary debug keys. EVIDENCE.md
records the remaining build-tool dependency advisory. No release is requested.
