# LG webOS

Miqat is a standalone Developer Mode app with an ES5 UI and a JavaScript service.
It can display a TV input inside its window. It does not replace LG Home, embed
arbitrary native apps or wake the TV from standby.

## Device status

The integration was developed on LG LF6300 / webOS 2.2, WebKit 538.2 and service
Node 0.10.25. Prior device checks confirmed HDMI picture/sound, physical remote,
Arabic shaping, sizing, startup, prayer audio and restoration. Miqat was installed on that TV on 18 November 2026. Live service readback verified
the one-time settings transfer and the old launcher was uninstalled. The owner confirmed the 0.2.24 splash, picture and sound on the physical TV.
The owner also reports working pairing and smooth connected controls/settings.
Detailed physical remote and power/lifecycle acceptance remain pending. Newer LG hardware is unverified.

## Build and install

On the Android Miqat phone app, choose **Your TVs → Set up my TV** (or **Pair a
new TV**) → **LG webOS**. Follow the preparation guide to enable LG Developer
Mode and Key Server. Select Latest (default), Stable or Preview, or choose a
local IPK through the Advanced icon. Enter the TV's private IPv4 address; public
IPK download follows the approved Developer Mode connection, with size/SHA-256
verification. If no public build exists, Miqat says so; it does not use private
CI artifacts. First confirm the SSH identity on your
trusted home network; then enter LG's case-sensitive passphrase and connect.
Review the installed/selected version before explicitly installing or updating.
The phone verifies transfer bytes and the installed version, refuses downgrades,
and can launch Miqat before ordinary phone pairing. It does not uninstall apps.

Only the accepted SSH public host key persists. The private key and passphrase
are session-memory only; leaving setup closes SSH. LG's key server uses local
HTTP for the encrypted private-key file; SSH identity checking remains strict.
Old Developer Mode servers may require legacy RSA/SHA-1 SSH algorithms, confined
to this installer. Ordinary companion pairing remains pinned HTTPS.

If interrupted or refused, reconnect to read the installed version before retrying;
do not uninstall Miqat to recover an update. Keep Developer Mode active and extend
its session in LG's app before expiry; expiry can remove developer-installed apps.
An expired session needs Developer Mode setup and installation again. This is not
root access, system Home replacement, Android-style cleanup or standby wake.
Current phone-install validation is a local SSH fixture, not physical LG acceptance.

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
Microsoft Edge and mocks Luna/media; its captures are not TV screenshots.

Share tested packages from the primary checkout's ignored `state/builds/webos/`
folder as `Miqat-latest.ipk`. Verify its SHA-256 matches before sharing. Keep
only the latest export and use the same location across worktrees. Do not create
new per-task evidence folders or duplicate exports; summarize checks concisely.

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

## Foreground phone pairing

The companion branch adds Settings / Phones with themed local QR and physical
remote approval/revocation. Its listener follows the foreground app subscription.
See [protocol and legacy TLS limits](COMPANION-LAN.md) and
[current companion evidence](COMPANION-WORK.md). The owner confirmed working
Miqat pairing and smooth connected controls/settings on the physical LF6300.
The 0.2.25 speed improvements still need a timed full pairing retest.

The approved pairing QR now uses the shared Miqat centre mark, theme body fill
and accent tint, no border and half-width (0.35-module) centre clearance with
high error correction. Automated SVG and mocked browser checks are not evidence
of SVG image/filter rendering or camera recognition on physical legacy LG hardware.

### Neutral landscapes (review draft)

Appearance also offers six bundled illustrated landscapes: Dusk peaks, Golden
coast, Moon lake, Aurora sky, Tea hills and Monsoon. These static choices retain
full color with a 20% dark overlay in Home and draft previews. Android's procedural
Mountains/Dunes and time-based light fields remain unported. Current validation
is a mocked browser check; physical LG picture acceptance remains pending.

The Appearance preset carousel now comes first, above Customize controls. Choosing
a different preset restores its supported canonical background in the draft;
manual overrides and Apply/Cancel remain available. Six scenic presets pair their
landscape, palette and surface. Unsupported procedural defaults use plain fill;
this does not add Android's procedural renderer to LG.

Miqat's default preset now pairs its branded palette with the original Signature
background. Fresh preferences select it; existing explicit background selections
remain unchanged. Choosing Miqat in the preset carousel restores the Signature
background in the draft. The scene is a bundled JPEG, not a runtime effect.

Home mihrab fill opacity is adjustable from 50–100% in 5% steps, recommended 85%,
using portable numeric rules and draft Apply/Cancel. It changes the opaque panel
fill only; text/outlines retain full opacity. Different presets restore 85%.
Prayer labels use the canonical shared vector family. Home icons occupy the
header strip so long labels retain their own line at larger interface sizes.
Validation is mocked browser/service testing; physical LG acceptance is pending.

Miqat quiet is an additional canonical scene; the original Signature stays default.
Home next-prayer fill is 75% of the selected surface panel color, with opaque gold
labels/outline. This alpha is independent of the Home mihrab setting and Glass.


The app displays the canonical splash artwork while its first screen loads, for
at least 600 ms, in addition to LG's configured splash image. It does not replay
on foreground resume. The owner confirmed the splash, picture and sound with 0.2.24 on 18 November 2026.


Legacy pairing preparation uses bounded native OpenSSL RSA-2048 generation when
Node lacks its own key-generation API. Stored identity is reused; keys are not
passed through shell arguments or temporary files. If the binary is absent, the
existing OS-random Forge path remains available. One-off physical identity-only
measurements were 65.3 seconds before and 2.0 seconds after; these are not full
pairing timings. The app also reuses unchanged QR artwork while awaiting approval.


On the pairing page, Down from Back enters the first enabled control; Up returns
to Back. Up/Down follow the visible control order, including Reject and Approve.
With a QR open, the controls scroll independently so the complete code remains
visible. Version 0.2.26 passes bilingual mocked navigation checks; physical remote
confirmation of this correction remains pending.


Version 0.2.27 preserves the QR element while pending/approval controls update.
Bilingual DOM-retention and focus regressions pass. The owner confirmed much
more responsive physical pairing with phone 0.1.3 and accepted the result.
This legacy TV profile uses Web Inspector console/timeline diagnostics;
`ares-log` is not supported for the TV profile.
