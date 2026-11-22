# Themes and ambient backgrounds

In Phone settings → Appearance or TV Settings → Home & appearance, **Miqat** and
**Miqat signature** appear first. Fresh installs use the Signature artwork and
petrol/gold palette. Explicit saved selections remain intact. Phone backgrounds
are composed for portrait; TV scenes fill the landscape viewport.

Theme/background cards have filled artwork previews, and the Appearance page
shows the active colors/background. Ordinary changes save immediately, including
leaving with Back. Short choices use visible segmented controls and on/off values
use switches; only large visual catalogs use carousels. Special import/palette
or audio-selection dialogs still validate/confirm their complete value.

The common design has 20 built-in themes, plus imported Custom support where the
shell implements it. See [capabilities](UI-GAPS.md) for LG differences. Quran text
keeps Amiri; app labels use bundled Alexandria, with display fonts appropriate to
each surface. No wallpaper download is needed. TV procedural/daylight effects
and line patterns use local sources; the browser preview shares those definitions.
Phone Home dims its artwork and can turn it off independently. Widgets retain
per-instance background, opacity, shape and date choices; see [PHONE.md](PHONE.md).

## Built-in themes

The original eight retain their saved IDs and classic treatment: **Miqat, Aurora, Ember, Forest, Midnight, Rose, Sand and Slate** (Miqat retains its saved `mihrab` ID). The twelve additions bring six other surface styles. All use dark TV-friendly backgrounds and the existing focus ring. Background choice remains independent; choose **Theme default** to use the preset’s background.

| Theme | العربية | Treatment | Default background |
|---|---|---|---|
| Glass | زجاج | Translucent dark panels, rounded corners, fine highlights | Halo |
| Frost | صقيع | Translucent dark panels, rounded corners, fine highlights | Mountains |
| Cinema | سينما | Near-black panels, small corners, no resting border | Solid |
| Carbon | كربون | Near-black panels, small corners, no resting border | Panorama |
| Neon | نيون | Thin accent outlines, dark interiors | Solid |
| Blueprint | مخطط | Thin accent outlines, dark interiors | Octagon Lattice |
| Signal | إشارة | Square blocks and accent underlines | Solid |
| Arcade | أركيد | Square blocks and accent underlines | Night Sky |
| Dusk | غسق | Large rounded cards without borders | Dunes |
| Oasis | واحة | Large rounded cards without borders | Panorama |
| Brass | نحاس أصفر | Square frames with accent outlines | Arches |
| Mosaic | فسيفساء | Square frames with accent outlines | Star Tiles |

### TV references and cost

Design references: [Apple TV materials](https://developer.apple.com/design/human-interface-guidelines/materials), [Projectivy](https://play.google.com/store/apps/details?id=com.spocky.projengmenu), [Dispatch](https://play.google.com/store/apps/details?id=com.spauldhaliwal.dispatch), [Emotn UI](https://app.emotn.com/ui/), and [Premium TV Launcher](https://play.google.com/store/apps/details?id=com.premium.tv.launcher.ui).

Glass uses an 86%-opaque dark fill with a highlight outline. Android caches one active background bitmap, capped at 1920×1080 (7.91 MiB). Settings uses a 0.49 MiB preview. Pattern masks occupy about 245 KB on disk and are released after compositing.

`./gradlew :app:verifyApkSize` builds the debug APK and fails unless it is **strictly below 25,000,000 bytes**. It is included in `check` and the launcher CI workflow. See [build results](EVIDENCE.md).

Cinema/Carbon, Signal/Arcade and Brass/Mosaic share a 12-pixel surface radius. It applies to app cards, prayer boxes, Settings rows and navigation tabs; circular quick controls and pill-shaped selectors keep their shapes. Native and browser previews read the same theme tokens.

Prayer cards align their name and time to the start edge: left in English, right in Arabic. The icon sits above the name, with Now/Next or Ramadan status at the opposite end of the top row. This retains the card height and gives long names their own full-width line. Prayer cards use 20-pixel side padding and cap their corner radius at 28 pixels; larger app tiles retain the full theme radius. Current/next state colours stay distinct in every style.

## Custom themes

The JSON-file workflow below belongs to Android TV/tooling. Phone appearance uses
its native custom-palette dialog; LG import/export remains a declared gap.

Custom themes are local JSON, imported over ADB. Copy a preset from `shared/design/assets/themes/`, edit it and run:

```powershell
python -S -B tools/device.py --tv 192.168.1.50:5555 theme my-theme.json
```

```json
{
  "version": 1,
  "colors": {
    "lapis-950": "#081921",
    "lapis-900": "#0B202C",
    "lapis-800": "#10313E",
    "lapis-700": "#173D49",
    "gold-400": "#8EDBD2",
    "gold-200": "#B9F0D8",
    "plaster-100": "#EFF5ED",
    "mist-400": "#B3D0D8"
  },
  "surface": "glass",
  "background": "panorama",
  "lighting": 35
}
```

The optional `surface` field accepts `classic` (default for old files), `glass`, `cinema`, `outline`, `blocks`, `soft` or `frame`. Glass also validates text contrast over the panel composited on white. The eight colour names retain their original token meanings: dark surfaces, accent, pressed accent, primary and secondary text. Omitted colours keep the defaults. Colours must be opaque `#RRGGBB`; unknown fields, files over 16 KiB, out-of-range lighting and insufficient text contrast are rejected before saving. Select Mihrab to reset the look. No image downloads, video, blur, telemetry or extra runtime dependencies were added.

`shared/design/mihrab/tokens.json` remains the source for the default design. `npm run tokens` in `site/` generates both immutable defaults and the theme palette. Do not edit `Tokens.java` or `Palette.java` directly. The browser preview reads the same preset files.

## Remote buttons and screensavers

**Settings → Device settings → Remote buttons** lets you select a named button or choose **Press a button to assign**, then select an installed app, Home, All apps, Inputs or Settings. Installed apps are refreshed when the picker opens, and changing a button replaces its previous assignment. On TCL, branded buttons may be intercepted before Android delivers them to the dialog: use their named entries instead. The supported TCL firmware codes are offered even when Android does not expose their key names. An **Enable the remote button service** entry appears when the service is stopped. Choose **Original** for a button to return it to the firmware; **Restore original TV buttons** turns off launcher remapping while leaving the prayer service available. Adding a new assignment after this does not re-enable the old assignments. Other applications' TCL mappings are preserved. Home, power, volume and D-pad navigation are excluded. Test assignments and held volume keys with the TV’s remote.

**Screensaver** lists installed Android dream services and opens the TV's screensaver/photo settings. Choose **Miqat**, a custom installed saver, or Google's Ambient/Backdrop when the firmware provides it. The launcher does not download Google's private photo stream. Installing Miqat preserves your current saver; cleanup protects its package.

**Prayer and reminder service** reports whether the service is running. The optional **Recover prayer service** switch follows CleanYourTV's force-stop recovery pattern when secure-settings access was granted: reopening Home can restore a previously connected service while preserving other accessibility helpers. Deliberately disabling the service clears its recovery marker. This needs a force-stop and physical-remote test on the TV.

## App lighting, favourites and prayer offsets

**Home & appearance → App focus lighting** adds a subtle wash using the focused app's banner or icon colours. It is off by default. Colours are sampled from a 12×12 image and cached for the current Home view, following CleanYourTV's colour sampling approach (MIT; `CLEANYOURTV-LICENSE.txt`). Ambient light controls its strength. A focus change transitions for 280 ms, then stops; Reduce motion makes the change immediate. There is no idle animation loop. It works with panorama, daylight and the other backgrounds.

Home supports up to nine favourites in two rows. **Home & appearance → Favourite rows** offers **Two favourite rows** (default) and **Classic — one row**, which displays the first four saved favourites as larger tiles. The short layout choice saves immediately; Back retains it. Switching layouts preserves the full favourites list, and both retain recent apps and All apps. Six favourites use four columns when the mihrab is compact or hidden; Arabic mirrors the layout and navigation. Below them, up to five recently opened apps appear beside All apps. Recent history stays on the TV and records launches made through the launcher, including its assigned remote buttons; apps opened directly by the TV firmware are not tracked. **All apps → hold OK** adds or reorders a favourite; extra favourites can also be removed. **Replace a favourite** returns to Home: select a favourite tile and press OK to replace it, or Back to cancel. **Uninstall** opens Android’s confirmation for removable apps; system apps and the launcher are excluded. The phone installer reads the TV's supported capacity, retaining compatibility with older four-slot builds.

**Display & language → Hide system apps in All apps** filters out system utilities while keeping media apps, games and Play Store visible, including preinstalled YouTube, YouTube Music, Play Movies, Play Games, Netflix and TCL Gallery/Media Player. It uses Android app categories with a small fallback list for those common TV apps. It is off by default. Home favourites and recent apps remain visible, and nothing is disabled or removed from the TV. All apps displays four rows and scrolls by row; tiles are created as they enter view. The Home, All apps, prayer and settings footer instructions and adhan dismissal hints have been removed; buttons and automatic dismissal still work.

**Prayer times → Calculation method** offers [19 offline presets](PRAYER-METHODS.md), shared by all prayer displays and alerts. **Reminders & adhan → Test reminders & adhan** previews the reminder banner or adhan popup using the current sound settings, without changing scheduled alerts.

**Prayer times → Prayer time offsets** shows one **− / number / +** row per prayer (including the sunrise marker), matching Hijri date adjustment. The adjusted prayer time appears in larger type at the centre of each card, in the same relative position in English and Arabic. It follows the selected 12-hour (AM/PM) or 24-hour format. Left/Right or the visible −/+ controls adjust between −30 and +30 minutes and save immediately. Back keeps the change and returns to Prayer times. Negative means earlier, positive means later, and zero restores the selected calculation method's time. Displays, reminders and adhan use the same offsets. Offsets are preserved when changing location or method, so review them then. A fixed offset is not a seasonal mosque timetable. Leamington Spa is available in the UK city list; location discovery is not implemented.

The browser preview mirrors the shared Home & appearance page, both favourite layouts and prayer-offset editing. The Asr school row shows today’s Asr time; choices save immediately and Back retains them. The separate Asr sidebar card has been removed.


## Quick controls and prayer bar

Home has Today & Qibla, Inputs, Radio and Wi-Fi icons beside Settings. Today & Qibla is at the outer end, beyond Inputs, and remains available when both prayer displays are hidden. **Display & language → Quick control labels** controls labels on both Home and the prayer bar; icons are the default. All apps also has a Settings cog beside the next-prayer chip.

**Inputs** lists Home, the TV's visible HDMI/passthrough sources and its hardware Live TV tuner. Connected HDMI-CEC devices replace their parent port entry to avoid duplicates. The **⋯ Advanced inputs** button beside the popup title opens the firmware's source chooser. The first selected row remains Home; press Up to reach Advanced inputs. This uses the TV's registered inputs rather than assuming a number of HDMI ports. Home returns to the first favourite; Down moves through favourites and recent apps, then highlights All apps. Up from the first favourite reaches the quick-action row; move toward its outer end for Today & Qibla. Returning from an app can retain the previous tile when Home has not rebuilt.

**Wi-Fi** opens Android's network panel directly, with Wi-Fi settings as a fallback on older TVs. The panel shows the connected network and available networks and handles passwords. Android restricts network names and scans when location is off; the system panel works without enabling location for the launcher. The launcher does not scan in the background or save network credentials.

The prayer/button accessibility service hosts a slim, full-width **Prayer bar** above other apps and TV inputs. Its expanded view uses one compact row: six bordered prayer tiles fill the available space, with Home, Inputs, Wi-Fi, Settings and a **−** minimize button at the right. The current prayer has a light fill and **Now** label; the next prayer has a theme-coloured border and **Next** label. Arabic mirrors the prayer order, controls and popup layout, placing the controls at the left. Back also minimizes it; Back from its Inputs list returns to the bar. Settings are under **Display & language**:

- **Hidden until opened** (default): no bar until its assigned button is pressed.
- **Small prayer tab**: a thin strip with the next prayer and time while another app/input is visible. It cannot receive focus, touches or remote navigation.
- **Off**: disables the bar, independently of reminders and adhan.
- **Prayer bar position → Top / Bottom**: applies to both the strip and expanded bar; Bottom is the default.
- **Open prayer bar**: opens it from Settings. If its service is unavailable, it opens accessibility settings, falling back to the TV's main Settings when the dedicated shortcut is unsupported.

Under **Device settings → Remote buttons**, assign Red, Green, Yellow, Blue or another supported shortcut to **Prayer bar**. There is no universal default button; a device's saved assignment survives updates. Essential Home, Back, power, volume and navigation keys remain protected. The bar reuses the existing prayer clock check and refreshes its text once per minute; it adds no idle animation or new service.

Reminder banners continue over the current app/input, and adhan opens its existing call screen. Back from the call returns to the previous activity. HDMI audio follows the TV’s audio routing; check it with the selected input during setup. Apps can restrict overlays.

### Random appearance

In **Settings → Home & appearance → Random appearance**, choose **Off**, **Every 15 minutes** or **Every 30 minutes**. The choice saves immediately and is off by default. The first change follows the selected interval after enabling it.

Each change picks from the 20 built-in themes and 16 backgrounds, excluding the current theme and visible background. Custom theme data is retained but is not included in random selection. Layout, favourites, language, lighting preferences and prayer settings stay unchanged. Turning rotation off keeps the currently displayed theme and background.

Rotation uses the existing Home clock tick and runs only while Home has focus and no reminder, radio card, quick menu or favourite replacement is open. Other apps, Settings and the screensaver are not interrupted. Returning to Home after an overdue interval makes one change, then starts a new interval; it does not replay missed changes. No alarm, wake-up, extra timer or background service is added. The saved deadline survives app restarts, and moving the system clock backwards restarts the interval safely.

### Prayer icons

Six line icons, sized to the neighbouring text, identify Fajr (dawn), Sunrise (rising sun), Dhuhr (midday sun), Asr (afternoon sun), Maghrib (setting sun) and Isha (crescent and star). Names remain visible. The same vectors appear in Home, Today & Qibla, All apps' next-prayer chip, offsets, the settings preview, reminders, adhan, the prayer bar and the launcher's screensaver. Friday uses Dhuhr's icon. Icons inherit the text colour, including theme and focus states. The browser preview extracts the paths from the Android vector resources; there are no icon downloads or new dependencies.

### Pattern design references

The original generated linework draws on the repeated stars, polygons and vegetal forms described in [the Met's geometric-pattern introduction](https://www.metmuseum.org/essays/geometric-patterns-in-islamic-art) and [the V&A's Islamic tile examples](https://www.vam.ac.uk/articles/design-and-make-your-own-islamic-tile-and-printed-pattern). Museum photographs are references only and are not bundled. Corner medallions leave the centre quiet; the other patterns repeat across the screen. All are selectable independently of the colour palette.
