# Android TV setup and recovery

## Install from Miqat Phone

1. Open **Your TVs → Set up my TV → Android / Google TV** and follow the preparation steps.
2. Select **Production** for the stable release or **Staging** for a prerelease.
   To use your own APK, choose **Advanced → Choose a local package**.
   **Download from Miqat** switches back to public releases.
3. Review the setup and any optional cleanup. Miqat checks the downloaded
   APK's size, SHA-256 and package ID and blocks version-code downgrades.
4. After setup succeeds, choose **Pair TV** and approve with the TV remote.

If you used the standalone installer, export its recovery backup before
removing it. Authorize Miqat Phone separately when the TV prompts you.
[LG setup](WEBOS.md)

The iPhone development build exports its Home and accessibility changes from
**Install Miqat on Android TV → Save recovery backup**. Put `recovery.json`
in a private folder and point the current desktop tool's `--backup-dir`
at that folder when running `restore` against the same TV.

The installer requires Android TV API 26 or later and reads the version from
the TV. [Tested models and open checks](EVIDENCE.md)

## Install from Windows

Install Python 3.9+, JDK 17 and Android SDK 34 with platform-tools. The PC
installer builds Miqat from your checkout.

Enable debugging, connect with Android platform-tools and approve the TV prompt. Use the same address and backup folder throughout. Replace the example address with your TV address.

```powershell
adb connect 192.168.1.50:5555
$env:TV = '192.168.1.50:5555'
./tools/install.ps1
python -S -B tools/device.py configure
```

On Linux or macOS, set `TV`, run `bash tools/install.sh`, then run
`python3 tools/device.py configure`. The installer saves the original state,
keeps the screensaver and checks the selected Home. An update requires a
matching APK signature.

After checking Home, Settings, HDMI and the remote, disable known stock Home packages:

```powershell
python -S -B tools/device.py home --exclusive
```

For an initial install, you can also pass `-Exclusive` to `./tools/install.ps1`.
Wake the TV and leave Home visible. The tool checks that Miqat is in the
foreground and attempts to restore the original Home if handover fails.
Exclusive setup blocks unknown launchers and CVTE boards. On CVTE, verify
Home and ADB after a reboot before enabling any helper. An ordinary update
checks the existing Home and keeps earlier cleanup and helper choices.

## Back up and undo

The first operation saves packages, original Home, settings and device properties in ignored `state/backups/`. A retry preserves the original snapshot. `--backup-dir <folder>` before the command selects a private folder on the PC or external storage; keep using that same folder. Backups contain private device information: do not commit or publish them.

```powershell
python -S -B tools/device.py backup
python -S -B tools/device.py disable com.example.optional
python -S -B tools/device.py restore
```

Disable only apps the household has agreed to remove from use. Core services, the active Home, selected screensaver, input hardware, Wi-Fi-owning setup wizard and CVTE Home are protected. Each change is recorded before disabling it. Restore re-enables only recorded packages that were originally enabled, verifies the original Home, restores settings still owned by this setup, and leaves Miqat installed. Keep the backup after restore. A later user choice is preserved; inspect any skipped settings on the TV.

Miqat Phone writes the same recovery format into its private storage before setup. **Save recovery backup** exports `recovery.json` to the phone's chosen location. **Restore original TV setup** uses that record while connected. Keep an exported copy before clearing the app’s data. For PC recovery, put the exported file in a folder and pass that folder with `--backup-dir`; the device identity must match. The phone backup includes diagnostic snapshots inside the JSON.

Old `state/disabled.log` files are not automatically imported: they do not identify a TV or its original Home. Review those records manually before restoring an older installation.

## Screensavers

Setup preserves the existing selection, including Google Ambient/Backdrop if installed. List services or select Miqat:

```powershell
python -S -B tools/device.py screensaver
python -S -B tools/device.py screensaver miqat
```

Pass a listed `package/service` to select a custom installed screensaver. Google photo choices remain in Google's screensaver settings and depend on the TV firmware and installed services. Do not disable the package providing the selected saver.

To check adhan over the selected screensaver on a connected TV, enable adhan and its prayer service,
use a debug build containing audio assets, and stop any launcher radio playback first:

```powershell
python -S -B tools/check_adhan_screensaver.py --tv $env:TV
python -S -B tools/check_adhan_screensaver.py --tv $env:TV --home-only --dismiss home
```

This explicit hardware check plays adhan audio, tests Back or Home dismissal, and returns Home.
It uses the existing delayed call test and SystemUI's `Somnambulator` activity; firmware without that
activity needs a manual screensaver check. It verifies that the screensaver exits, the call remains
resumed and audio remains active beyond the temporary wake lock, dismissal stops playback, and
preferences/screensaver selection remain unchanged. Raw diagnostic snapshots stay in ignored `state/`.
It does not run as part of the offline unit suite. Confirm the sound with the TV's actual speakers too.

## Finish on the real TV

Check Home from another app and after reboot, HDMI/source selection, held volume keys, Bluetooth pairing, voice, Wi-Fi, prayer alerts, radio and screensaver wake-up. Test remapped buttons with the TV's remote. Keep the stock launcher enabled until setup works. After installation, turn debugging off, revoke authorisations, reboot and confirm ADB can no longer connect.

## Local checks

`python -S -B -m unittest discover -s tests -v` runs simulated-TV checks; it never connects to a TV. Build both apps with `./gradlew :app:assembleDebug :companion:assembleDebug`. Hardware checks remain separate.


### Device-test cleanup

Android instrumentation can stop the launcher process and leave its enabled accessibility service unbound. UI Automator hierarchy dumps can also suspend accessibility services temporarily and close their overlays. Capture prayer-bar screenshots directly with `screencap`, without a hierarchy dump between opening and capturing the bar.

After instrumentation or UI automation, inspect `dumpsys accessibility`: an enabled entry alone does not prove the prayer/button service is running. Check that Miqat is bound and absent from the crashed-services list. If it was enabled before the test but remains disconnected, reconnect only Miqat through TV accessibility settings, preserving every other service. Verify Open prayer bar in Miqat Settings and the physical blue button, restore changed test preferences, and remove the test-only APK if the test installed it. Do not enable services that were disabled before testing or change the user's automatic-repair preference.
