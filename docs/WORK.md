# Current work

Updated: 2026-11-22. PR #9 is merged normally and local main is synced.
Phone 0.5.17/code 33 fixes existing-widget Update labeling; four hosted widget
instances retain independent immediate settings. Owner accepts widget readability.

Full PR Validate and Apple core pass at 77f51ca. Android TV/LG supported UI
contracts and generated catalogs pass native/browser checks; their documented
platform gaps remain. Notification automation checks SystemUI dismissibility,
actual playback stop and volume restoration. OEM gestures are outside automation
scope by explicit owner instruction. EVIDENCE.md owns validation details.

Next: prepare the owner's preview release candidate from successful current-main
Validate artifacts, including phone, TV, installer, LG and SHA256SUMS. The draft
workflow enforces this source. Public publication and physical deployment need
specific instruction. Stable signing remains unconfigured for this preview.

Overwrite only state/builds/companion/Miqat-latest.apk for local review builds.
Retain the existing local signer and keep private logs/captures under state/.
