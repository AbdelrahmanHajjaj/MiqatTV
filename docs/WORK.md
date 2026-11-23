# Current work

Updated: 2026-11-23. Stable v0.7.4 includes phone 0.5.20/code 36,
Android TV 0.7.3/code 14, installer 1.1.5/code 8 and LG 0.2.30.
The phone adds Quran Radio, numbered Ramadan days and wider Eid and Hajj
coverage, and optional day-before, suhoor and iftar reminders. The signed
bundle passed download and package verification. The bilingual website
links to v0.7.4 and documents the new phone features.

The standalone Android TV installer has been retired from the build and release
workflow. Miqat Phone hosts TV setup and recovery. Next: publish v0.7.5 with phone 0.5.21/code 37 from successful main validation,
then update the bilingual website and current download guides. See EVIDENCE.md and
the release and PR records.

Keys stay outside Git and PR jobs, in private recovery and the main-only
android-release environment. Routine CI has no phone/TV emulator jobs; retain
build/lint, portable/JVM/browser and package/signature gates. Native device suites
remain targeted manual checks.

Transition from debug signing requires one fresh install and removes local data;
later signed releases preserve identity. Keep an off-device signing recovery copy.

Overwrite only the existing Miqat-latest review exports under state/builds/.
Keep the existing debug review signer. Release keys and recovery material are in
private state/signing/ and encrypted environment secrets; never print passwords.
