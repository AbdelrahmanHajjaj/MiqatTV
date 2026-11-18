# Current work

Updated: 2026-11-18.

## Approved scope

Publish the existing Arabic/English browser preview on GitHub Pages and update it
automatically when changes reach main. Reuse the tested static artifact, retain
main protection and normal merges, and apply available preview security fixes.
No app, companion, release-signing or TV deployment changes.

## Status and next action

Pages is enabled with GitHub Actions as its source. feature/github-pages adds a
main-only deployment job after all required validation passes. The deployed site
uses /MiqatTV/ (Arabic) and /MiqatTV/en/ (English). PRs do not deploy.

Local preview/workflow checks and Arabic/English browser acceptance passed.
[PR #2](https://github.com/AbdelrahmanHajjaj/MiqatTV/pull/2) owns the final CI,
merge and initial deployment receipts. Await the owner's normal-merge instruction
and passing required checks, then confirm the live site after the main run.
Essential local evidence belongs in EVIDENCE.md; raw logs and captures stay private.

## Previous delivery and remaining limits

[PR #1](https://github.com/AbdelrahmanHajjaj/MiqatTV/pull/1) was normally merged as
2bb348f. [Main validation](https://github.com/AbdelrahmanHajjaj/MiqatTV/actions/runs/44301830392)
passed all six jobs; [draft publication](https://github.com/AbdelrahmanHajjaj/MiqatTV/actions/runs/44304525540)
created an unpublished full-audio prerelease with verified checksums.

Physical Miqat installation, complete phone-installer testing, settings transfer
and companion integration remain separate. CI APKs use temporary debug keys.
EVIDENCE.md records the remaining unpatched build-tool dependency advisory.
