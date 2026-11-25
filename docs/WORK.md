# Current work

## Android phone and LG feedback

Deliver direct QR pairing, paired-TV updates, the resizable Countdown circle,
optional Android phone update checks and the LG startup destination. Run the
required PR checks, build signed Staging and test that candidate in Device
simulation. Physical acceptance remains separate.

## iPhone

Follow the [iPhone parity queue](IOS-WORK.md). The unsigned app builds, fixtures,
eight simulator journeys and package verification passed. Signed iPhone tests,
system-managed behavior and live TV acceptance remain open. The next installer
check is LG SSH against a local Developer Mode fixture. Android TV recovery,
cleanup, personalisation and wireless debugging pairing still need native acceptance.

## Stable release

As of 2026-11-24, stable v0.7.9 ships Android phone and Android TV version
0.7.9 (code 7009003) and LG version 0.7.9. The five Production digests matched
signed Staging, both Android emulator simulations and the LG browser mock
passed, and the site points to v0.7.9. See [validation evidence](EVIDENCE.md)
and [release procedure](RELEASING.md).

Physical phone launcher, TV setup, legacy LG layout and newer LG audio checks
remain open. Keep review exports in the existing ignored `state/builds/`
locations and preserve the existing debug review signer and private release keys.
