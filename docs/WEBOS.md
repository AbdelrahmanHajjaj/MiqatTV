# LG webOS

Miqat is a standalone Developer Mode app with an ES5 UI and a JavaScript service.
It can display a TV input inside its window. It does not replace LG Home, embed
arbitrary native apps or wake the TV from standby.

## Device status

The integration was developed on LG LF6300 / webOS 2.2, WebKit 538.2 and service
Node 0.10.25. Prior device checks confirmed HDMI picture/sound, physical remote,
Arabic shaping, sizing, startup, prayer audio and restoration. The new tv.miqat.*
identities have not been installed on hardware. Newer LG hardware is unverified.

## Build and install

Use JDK 17 and Node 24, and prepare the local audio inputs in [BUILD.md](BUILD.md).
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
Microsoft Edge and mocks Luna/media; its captures are not TV screenshots.

## Controls and settings

OK on input opens the compact prayer bar. Home exposes Prayer, radio, Settings,
app shortcuts and All apps. Up from the first app row reaches the top controls;
Down from the last row reaches All apps. In All apps, Up from any first-row
column reaches Back; Left/Right stays in the header. Back restores return focus.

Settings follows the shared UI contract. Numeric values edit in their row with
OK and Left/Right; OK saves, Back cancels. Appearance uses a draft and Apply.
One global size control covers 80–150% in steps of 10. Arabic/English, RTL/LTR,
pointer input, row scrolling and focus remain part of rendering acceptance.
See [declared gaps](UI-GAPS.md) for unavailable controls or choices.

## Prayer, audio and startup

Prayer calculation works offline. The manual HTTPS comparison uses the selected
date/location/method/school and does not alter the schedule. The old-LG adapter
uses a bundled public USERTrust RSA root for this fixed host; source and full
notices ship in service/trust/ and service/licenses/online-ca.txt.

Silent banners retain HDMI sound. During adhan, HDMI uses video-only input so
the picture can keep moving while local audio plays behind the central mihrab.
Other inputs/audio modes release the decoder first. Completion, Stop, errors and
suspend use the existing cleanup and input-restoration handlers.

On SDKs below 4, adhan volume is an absolute TV level: 30% means 30/100, then the
app conditionally restores the captured level. It preserves a different level
chosen with the remote and never changes mute. Recovery after a process crash
waits for a new silent app instance. Newer SDK media attenuation is unverified.

Startup and scheduled alerts are off until enabled in Settings. Startup launches
once per new kernel boot. Prayer activities can reopen the closed app while the
TV is on; callbacks are claimed once, deduplicated and expired after 90 seconds.
The demand-started service is not a global remote listener. There is no supported
closed-app colored-button shortcut or standby wake.

An in-app alert restores its originating screen/focus. After interrupting another
app, Miqat closes its window so LG can return to app history. Browser return was
observed; streaming playback position is not guaranteed.

## Diagnostics and removal

Private state is under /media/internal/tv.miqat.webos. It must remain writable
after reboot; the installed service directory may become root-owned. status,
inputs and the bounded media/report records are local diagnostics, not telemetry.

Before removing the app, disable scheduled alerts/startup and cancel a pending
test through Settings. Remove only tv.miqat.webos using the configured CLI alias.
Do not use historical probes, firmware changes or root recipes for routine testing.

Physical picture, sound, remote and power checks require a ready watcher. LG
capture APIs do not reliably show the video plane. See [TESTING.md](TESTING.md)
and the platform [agent rules](../platforms/webos/AGENTS.md).
