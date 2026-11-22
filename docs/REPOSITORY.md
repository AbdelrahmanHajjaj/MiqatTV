# Repository

[MiqatTV](https://github.com/AbdelrahmanHajjaj/MiqatTV) contains the standalone
Android phone app, its TV companion/setup mode, Android TV, LG webOS, the shared
rules/data/design sources and the bilingual product website. The repository name
does not limit the product to TVs. iOS, Samsung/Tizen and Roku apps are unimplemented.

`companion/android` is the Android phone app. Its Prayer section works independently;
Your TVs owns optional household-LAN setup, pairing, settings and remote control.
`installer/runtime` is reused by that phone app and the optional standalone
installer. [Architecture](ARCHITECTURE.md) owns these boundaries.

Use feature branches and normal PR merges. Validate checks affected consumers;
GitHub Pages publishes the validated website from main. Draft build release
creates an unpublished candidate from successful current-main artifacts. See
[CI and website deployment](CI.md) and [release procedure](RELEASING.md).

The fresh Miqat history began with the approved platform foundation on
17 November 2026; phone and companion work is now integrated. Original MIT and
third-party notices remain. Keep credentials, private device state, raw logs and
build outputs outside Git. Public screenshot sources are the sanitized captures
under docs/screenshots/, with [provenance](screenshots/README.md).
