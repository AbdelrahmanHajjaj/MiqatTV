# Security scope

Report unsafe access through Android accessibility, exported components, guarded
setup operations, LG service methods or browser content. Include platform/model,
OS, app version, reproduction and the access an attacker would gain. Use the
repository's [private reporting options](https://github.com/AbdelrahmanHajjaj/MiqatTV/security).

Validate external input, restrict supported LG launches/inputs, preserve later
user changes during setup/recovery, and keep temporary audio/input ownership bounded.
Unexpected remote settings changes, exposed pairing keys or unsafe firmware writes
are bugs. Credentials, Wi-Fi names, addresses and backups belong outside public reports.

Runtime networking is limited to selected radio and a requested AlAdhan comparison.
The installer separately uses ADB and optional project downloads, with bundled
setup commands. Unrequested traffic or remotely supplied setup commands are bugs.
Radio-provider servers and TV firmware fall outside the repository's control.
