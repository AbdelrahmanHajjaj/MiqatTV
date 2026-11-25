# Current work

## Android update experience

Show download progress in the Android phone and TV self-updater. Show the
bundled changelog once after an upgrade and keep it in About Miqat.

## iPhone

Follow the [iPhone parity queue](IOS-WORK.md). The unsigned app builds, fixtures,
eight simulator journeys and package verification passed. Signed iPhone tests,
system-managed behavior and live TV acceptance remain open. The next installer
check is LG SSH against a local Developer Mode fixture. Android TV recovery,
cleanup, personalisation and wireless debugging pairing still need native acceptance.

## Stable release

Production v0.7.10 was published on 2026-11-25 from v0.7.10-staging.2.
Both Android emulator simulations and the LG browser mock passed for that
candidate. See [validation evidence](EVIDENCE.md) and [release procedure](RELEASING.md).

Physical phone launcher, TV setup, legacy LG layout and newer LG audio checks
remain open. Keep review exports in the existing ignored `state/builds/`
locations and preserve the existing debug review signer and private release keys.
