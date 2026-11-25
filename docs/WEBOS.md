# LG webOS

Miqat runs alongside LG Home and can show HDMI inside the app while the TV
is on. The legacy target is the LF6300 on webOS 2.2 (WebKit 538.2 and
Node 0.10.25).

## Build and install

### Install from Miqat Phone

1. Open **Your TVs → Set up my TV → LG webOS**. Follow the preparation steps
   to enable LG Developer Mode and Key Server.
2. Choose **Production** for the stable release, **Staging** for a prerelease
   or **Advanced** for a local IPK.
3. Enter the TV's private IPv4 address and LG's case-sensitive passphrase.
   Approve the SSH identity on your home network.
4. Review the installed and selected versions before confirming. Miqat
   verifies the download, transfer and installed version and blocks downgrades.
5. Launch Miqat on the TV and pair it to enable everyday remote control.

To reuse credentials, turn on **Remember address and passphrase** before
connecting. Miqat encrypts the passphrase and ties it to the approved TV
identity. **Forget installation identity** clears the saved passphrase and SSH
identity. If LG displays a new passphrase, enter it again.

If installation stops, reconnect and check the installed version before
retrying. Extend the Developer Mode session in LG's app before it expires.
Expiry can remove developer-installed apps and require setup again.

### Build from source

Use JDK 17 and Node 24, and prepare the shared audio inputs in [BUILD.md](BUILD.md).
Run from the repository root:

```sh
npm --prefix shared/core ci --ignore-scripts
npm --prefix platforms/webos ci
npm --prefix platforms/webos test
npm --prefix platforms/webos run test:browser
ares-package platforms/webos/build/app platforms/webos/build/service --outdir platforms/webos/build --no-minify
```

Pair a Developer Mode TV using LG's CLI and your local device alias. Package
version comes from platforms/webos/package.json; use that version in the IPK path:

```sh
ares-install --device YOUR_LG_ALIAS platforms/webos/build/tv.miqat.webos_VERSION_all.ipk
ares-launch --device YOUR_LG_ALIAS tv.miqat.webos
```

The service ID is tv.miqat.webos.service. App and service IDs must share that
prefix. Keep the four package/version declarations aligned. --no-minify keeps
the prebuilt ES5 files checked by the suite. The browser suite uses installed
Microsoft Edge with Luna/media mocks.

Share tested packages from the primary checkout's ignored `state/builds/webos/`
folder as `Miqat-latest.ipk`. Verify its SHA-256 matches before sharing. Keep
only the latest export and use the same location across worktrees. Do not create
new per-task evidence folders or duplicate exports; summarize checks concisely.

## Controls and settings

Open **Settings → TV & startup → About Miqat** for the installed version and
developer credit. Scan the feedback QR code to open the public GitHub form on
your phone. GitHub sign-in is required. Email feedback is currently disabled.

OK on input opens the compact prayer bar. Home exposes Prayer, radio, Settings,
app shortcuts and All apps. Up from the first app row reaches the top controls;
Down from the last row reaches All apps. In All apps, Up from any first-row
column reaches Back; Left/Right stays in the header. Back restores return focus.

Settings follows the shared UI contract. Numeric values edit in their row with
Left/Right and save immediately; Back retains them. Appearance saves each selection
and previews the background across the page. Short choices use segments; only
themes/backgrounds use filled artwork carousels, with Miqat first.
One global size control covers 80–150% in steps of 10. Arabic/English, RTL/LTR,
pointer input, row scrolling and focus remain part of rendering acceptance.
See [declared gaps](UI-GAPS.md) for unavailable controls or choices.

## Prayer, audio and startup

Prayer calculation works offline. The manual HTTPS comparison uses the selected
date/location/method/school and does not alter the schedule. The old-LG adapter
uses a bundled public USERTrust RSA root for this fixed host; source and full
notices ship in service/trust/ and service/licenses/online-ca.txt.

Silent banners retain HDMI sound. On webOS 4 and later, Miqat requests a lower
HDMI element volume during the adhan so the picture can keep moving. If the
element rejects the volume change, Miqat attempts mute. Some HDMI pipelines
accept a property change without changing audible output. Use **Mute HDMI**
if the programme is still too loud and **Hide HDMI** if the sound continues.

Older TVs, other input types and any released HDMI source use an opaque
full-screen adhan page. **Close** stops the adhan and restores the input at
normal volume.
Other audio modes release the decoder before playing.

On SDKs below 4, adhan volume is an absolute TV level: 30% means 30/100, then the
app conditionally restores the captured level. It preserves a different level
chosen with the remote and never changes mute. Recovery after a process crash
waits for a new silent app instance.

Startup and scheduled alerts are off until enabled in Settings. Startup launches
once per new kernel boot. Prayer activities can reopen the closed app while the
TV is on; callbacks are claimed once, deduplicated and expired after 90 seconds.
The demand-started service is not a global remote listener. There is no supported
closed-app colored-button shortcut or standby wake.

An in-app alert restores its originating screen/focus. After interrupting another
app, Miqat closes its window so LG can return to app history.

## Diagnostics and removal

Private state is under /media/internal/tv.miqat.webos. It must remain writable
after reboot; the installed service directory may become root-owned. status,
inputs and the bounded media/report records are local diagnostics, not telemetry.

Before removing the app, disable scheduled alerts/startup and cancel a pending
test through Settings. Remove only tv.miqat.webos using the configured CLI alias.
Do not use historical probes, firmware changes or root recipes for routine testing.

Physical picture, sound, remote and power checks require a ready watcher. LG
capture APIs do not reliably show the video plane. On the legacy profile, use
Web Inspector for console and timeline logs. See [TESTING.md](TESTING.md)
and the platform [agent rules](../platforms/webos/AGENTS.md).

## Foreground phone pairing

Open **Paired phones → Pair phone** to show the pairing QR, approve a phone with the
remote or revoke access. The listener runs while the app is in the foreground.
[Pairing protocol and legacy TLS limits](COMPANION-LAN.md)

## Appearance

Choose a preset or customize its background and palette in **Appearance**.
Changes save immediately. The Miqat preset restores the Signature background.
LG includes six illustrated landscapes. If a preset's procedural background
is unavailable on LG, it uses a plain fill.

Adjust **Home mihrab fill opacity** from 50% to 100% in steps of 5%. A preset
change restores 85%. The setting controls the panel fill. Text and outlines
stay opaque. [Themes and backgrounds](THEMES.md)
