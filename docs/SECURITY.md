# Security scope

Report unsafe access through Android phone/TV components, accessibility, imported
audio/documents, LAN pairing, guarded setup, LG service methods or browser content. Include platform/model,
OS, app version, reproduction and the access an attacker would gain. Use the
repository's [private reporting options](https://github.com/AbdelrahmanHajjaj/MiqatTV/security).

Validate external input, restrict supported LG launches/inputs, preserve later
user changes during setup/recovery, and keep temporary audio/input ownership bounded.
Unexpected remote settings changes, exposed pairing keys or unsafe firmware writes
are bugs. Credentials, Wi-Fi names, addresses and backups belong outside public reports.

Prayer calculation and city catalogs work offline. Runtime networking is limited
to selected radio, requested AlAdhan comparison, explicitly loaded/attributed Qibla
maps and foreground authenticated household-LAN TV setup/control. Map providers
receive the IP/viewed area. Android location APIs require permission; optional
periodic background location is off until explicitly enabled. It adds no app-owned
continuous listener or idle foreground service.

Phone prayer preferences/claims and private imported audio are separate from
encrypted TV credentials. Neither is included in Android backup/transfer. Resetting
phone prayers keeps paired TVs. Imports copy validated audio into private storage;
there is no public audio upload or cloud sync.

Pairing requires physical remote approval, pinned HTTPS and individually revocable
credentials. Reconnect must never upload cached settings or replay uncertain commands.
Installation separately uses guarded ADB or LG SSH and verified package downloads;
LG credentials remain session-only. Unrequested traffic, trust bypasses or remotely
supplied setup commands are bugs. Provider servers and TV firmware fall outside
the repository's control. The website has no analytics or live TV connection;
preview settings remain in its browser.
