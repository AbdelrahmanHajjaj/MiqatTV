# Repository setup

Miqat lives at https://github.com/AbdelrahmanHajjaj/MiqatTV. This is a new independent
repository populated from the platform-core implementation, without the companion
branch, inherited workflows, previous Git history or private device state.
Original MIT copyright and third-party notices remain with reused code.

The local checkout is D:/Dev/MiqatTv. It has its own .git directory and does not
depend on the previous repository's worktree links. Its origin points to MiqatTV.
No local-folder move or remote rename is needed.

The new workflows are owned here: Validate checks the shared core and all three
consumers; Draft build release publishes verified artifacts to a draft prerelease.
Main protection and token defaults follow [CI.md](CI.md). No inherited workflow
is retained, and Pages deployment is not part of this setup.

Main starts with the validated foundation and fresh Git history, as approved
by the owner on 17 November 2026. Future changes use feature branches. Stable releases still need explicit
authorization and persistent signing. Keep credentials, unreviewed recordings,
build outputs and device configuration outside source control.
