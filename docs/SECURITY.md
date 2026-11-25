# Privacy and security

## Report a security issue

Include the device model, OS, app version, steps to reproduce and what access
the issue exposes. Keep credentials, Wi-Fi names, addresses and backups private.
[GitHub private reporting](https://github.com/AbdelrahmanHajjaj/MiqatTV/security)

## Network and location

Prayer calculation and city search work offline. Miqat uses the internet when
you play radio, request an AlAdhan comparison, open a Qibla map or check for
or download an update. Map providers receive your IP address and the area you
view. Android controls location access. Periodic background location starts
only after you enable it and grant permission.

TV pairing and control run on your home network while you use the relevant
screens. The website has no analytics or live TV connection. The preview saves
its settings in your browser.

## TV access and local data

Pairing requires TV remote approval, a pinned HTTPS identity and credentials
you can revoke per phone. After a disconnect, Miqat leaves cached edits and
unconfirmed commands unsent. [Pairing protocol](COMPANION-LAN.md)

TV installation uses guarded ADB or LG SSH and verifies downloaded packages.
Miqat keeps the LG SSH private key in memory. If you choose to save the
passphrase, Android Keystore protects it and Miqat ties it to the TV address
and approved SSH identity. Older LG servers may require legacy RSA/SHA-1 for
installation SSH. LG's local Key Server sends the encrypted key file over HTTP.

Phone prayer settings, imported audio and encrypted TV credentials stay on
the phone and are excluded from Android backup and transfer. Resetting prayer
settings keeps your imported audio and paired TVs.
