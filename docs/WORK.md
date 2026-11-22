# Current work

Updated: 2026-11-22. Owner accepts widget readability and requests release
preparation, full CI fixes and Android TV/LG contract verification. Correct the
existing-widget Add label to Update; retain immediate per-instance saves.
Branch feature/companion-installation, PR #9. Phone: 0.5.17/code 33.

The location request is once per guide history, with manual fallback. Background
updates remain off without explicit consent and cannot schedule before location
confirmation. Setup edits retain the update choice and cancel pending detection.
Reuse canonical prayer geometry and native notification layout/direction.

The widget-label regression passes natively. Per owner instruction, require
notification dismissibility and real stop/volume cleanup, with no OEM gesture test. Include the phone APK in the
existing verified-source draft-release workflow. Supported TV contracts currently
pass canonical/native/browser CI; retain explicit LG/OS capability gaps.

Next: finish local focused checks, push and require the full CI gate before
release preparation completes. Preserve earlier CI failure evidence. Merge latest
main normally before pushing. Overwrite only state/builds/companion/Miqat-latest.apk
for review exports. Prepare a preview candidate as selected by the owner.
Public release publication and physical deployment require specific instruction.
