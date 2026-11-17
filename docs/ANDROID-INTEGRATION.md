# Android integration

The app uses Java Views, Adhan Java for astronomy, Android ICU for calendars and
the portable core for product rules. Read [Android build/instrumentation](ANDROID-TV.md)
and [setup/recovery](ANDROID-SETUP.md) for commands.

## Runtime

- MainActivity, AllAppsActivity, PrayerDayActivity and SettingsActivity render the
  main screens. NowActivity owns adhan; MiqatDream is the screensaver.
- RemoteKeys is the accessibility service for prayer scheduling, overlays and
  supported remote buttons. Prayer checks share its existing 15-second timer.
- Radio/RadioBars handle selected streams; ScreenOffActivity uses supported
  firmware Audio Only integration. No blur, decorative video or idle animation loops.
- SetupProvider exposes guarded setup operations over ADB with DUMP permission.
  Its authority is tv.miqat.home.setup. Keep provider and installer contracts aligned.
- Source strings/catalogs/design live under shared/. Regenerate projections;
  Android rendering and physical input handling remain in this shell.

## Remote keys and firmware

Historical local testing used TCL 50C641K / Android 11. Confirm the actual
model/firmware before device tests; the current package has no physical acceptance.
Injected ADB key events do not establish real accessibility/firmware key delivery.

On supported TCL firmware, TclKeys uses system_server's remapping rather than
accessibility filtering so held volume can repeat. With no supported TCL feature
or secure-settings grant, RemoteKeys falls back to key filtering.

TCL's CustomKeyList / CustomKey_<code> implementation lacks error handling.
Write the key list first, then values; retain list entries when clearing mappings.
Every intent must name its package, with a valid appnotinstall broadcast. Restore
only values still owned by this app. Incorrect writes can restart system UI.
Relevant hardware checks include an empty SYSTEM PROCESS crash log and held volume.

FirmwareKeys detects CVTE boards and leaves their key filtering alone. Source
opens CVTE's source menu where available. TCL and CVTE paths need separate tests;
neither establishes support for another brand. Remote mappings and setup state
must not overwrite later user choices.

## Device tests

Use an explicitly selected emulator/device and capture state first. Test only
authorized operations. Restore temporary test preferences and service selections,
preserving unrelated accessibility entries and later user changes.

Instrumentation or hierarchy inspection can leave the enabled accessibility
service unbound. Afterwards inspect dumpsys accessibility for the bound service,
not only the enabled setting; reconnect only Miqat if it was previously enabled.
Check prayer-bar action and physical remote behavior separately. Remove test-only
packages when the session finishes. Do not play audio or power-cycle without a watcher.
