# Repository setup

Miqat lives at https://github.com/AbdelrahmanHajjaj/MiqatTV. This is a new independent
repository populated from the platform-core implementation, without the companion
branch, inherited workflows, previous Git history or private device state.
Original MIT copyright and third-party notices remain with reused code.

The local checkout is D:/Dev/MiqatTv. It has its own .git directory and does not
depend on the previous repository's worktree links. Its origin points to MiqatTV.
No local-folder move or remote rename is needed.

There are no inherited GitHub Actions. Local checks are documented in TESTING.md;
CI and publishing can be added for this repository when requested. Do not claim
checks run automatically. The preview targets the MiqatTV Pages path; Pages and
public downloads need separate publication setup.

Main starts with the validated foundation and fresh Git history, as approved
by the owner on 17 November 2026. Future changes use feature branches. Releases,
branch protections and CI need their own setup. Keep credentials, ignored
recordings, build outputs and device configuration outside source control.
