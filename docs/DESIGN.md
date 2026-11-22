# Shared design resources

The **Miqat / ميقات** identity uses the owner-selected Arabic calligraphy,
English Miqat in the frame baseline and separate plain TV. Source rasters and
provenance live in [`branding/miqat-tv-v1/`](../shared/design/branding/miqat-tv-v1/README.md).
`node site/scripts/splash.mjs` exports the shared full-HD splash, LG 80/130 px
icons, Android/installer 512 px icons, Android launch artwork/TV banner and the
preview favicon/splash. Regenerate them together; never redraw the lettering.
The product website's phone/TV social cards are captured from its own hero;
see [site instructions](../site/README.md) and [screenshot provenance](screenshots/README.md).
Android's initial window uses the splash; newer Android versions control their
own system splash treatment with the new application icon. LG maps both
`splashBackground` and legacy `bgImage` to the shared full-HD PNG.

[`mihrab.json`](../shared/design/mihrab.json) owns the new crown/shoulder geometry. Android TV,
the phone, installer, LG and preview use it for the main prayer arch, call and dim
screensaver where those screens exist. `node tools/catalogs.mjs` also projects
it into both native `MiqatShape` classes; `--check` detects stale projections.
The geometry preserves existing content positions and focus treatments.
Phone Home, widgets and Adhan reuse canonical icons, fonts, theme colors and
geometry while composing scenes for portrait. The phone's TV preview is
representative; each native TV shell still owns its actual layout.

The default tokens now use petrol, gold and ivory. All eight SVG masks under
`shared/design/mihrab/patterns/` are original Miqat portal/diamond/arcade compositions;
`node site/scripts/patterns.mjs` renders Android's cached masks. Saved selector
IDs are retained, with new bilingual display names. Existing alternative theme
palettes, custom imports and user settings remain available. The default tiled
background also uses the new crown/shoulder outline, drawn once into a native
bitmap or an SVG tile. No extra runtime service, dependency or animation is added.

`resources/font/` owns the seven unchanged Alexandria, Amiri and Marcellus TTFs.
`assets/licenses/` owns their existing full OFL notices. Keep the filenames, bytes
and notices together; see `docs/THIRD-PARTY.md` for attribution.

The same asset directory distributes generated `Miqat-MIT.txt` and
`CleanYourTV-MIT.txt`. Their canonical sources remain root `LICENSE` and
`docs/CLEANYOURTV-LICENSE.txt`; `tools/catalogs.mjs` generates/checks the exact copies
here and in installer assets. Do not edit generated license copies by hand.

Android adds these resource/asset directories to its main source set, retaining
the existing `R.font` names with no generated font copies or Node prerequisite.
The preview copies fonts and notices during generation; LG reads them directly.
LG also bundles the existing Alexandria medium/semibold faces for shared UI
hierarchy. Font choice, Arabic shaping, CSS and native drawing remain in adapters.

`themes.json` owns the exact ordered IDs: 20 built-ins followed by Android's reserved
`custom` import slot. `assets/themes/` owns the 19 preset files; Miqat
uses the canonical design tokens. Android packages them as `assets/themes/<id>.json`
and uses generated `ThemeCatalog.PRESETS` through the existing Appearance API.
Preview/LG filter out `custom`, keeping their existing feature sets. Palette,
surface, background and lighting behavior stays in each adapter.

`appearance.json` owns the eight procedural background IDs, eight SVG pattern IDs, six static scene IDs,
seven surface IDs and eight theme-v1 color slots. Generated `ThemeCatalog` arrays
feed native `ThemeSpec`; preview uses the same background order/color keys. LG
retains its alphabetic SVG-pattern list and separate plain-background choice.
Its build applies each preset's canonical classic/glass/cinema/outline/blocks/soft/
frame treatment, including shared corners, strokes, type hierarchy and the
prayer-cell radius cap. Android's procedural backgrounds and custom import remain
unimplemented there. Contrast, import bounds and actual drawing remain in adapters.

`icons.json` owns the 26 SVG fragments used by the preview and LG.
`prayer-icons.json` owns the six ordered prayer paths, their 24-unit grid and
1.7-unit stroke. The catalog generator emits Android's existing vector resource
names; preview labels read the same geometry. `npm --prefix site run data:check`
checks the committed vectors without writing. SVG fragments allow only local
geometric shapes/attributes, with no active markup or external references.

Android's procedural `Icon` canvas renderer stays native; its mihrab now follows
the Miqat shape, retaining its existing RTL, arc/fill and geometry differences. `PrayerIcon` keeps native sizing, tint,
baseline and accessibility behavior. Web stroke/focus rendering also stays in
its adapters; this resource extraction does not replace native drawing APIs.

Tokens and SVG patterns still come from `shared/design/mihrab/`; do not duplicate or
hand-edit their generated Java/XML/CSS outputs. Remaining asset extraction and
the migration queue are recorded only in `docs/WORK.md`.

## Pairing QR mark

`shared/design/assets/qr-mark.png` is the approved transparent derivative of the
shared Miqat icon. Original masters remain intact. Both TV renderers tint this
alpha mark to the active app accent over the QR's theme-derived body fill.
`assets/qr-style.json` owns high error correction, centre size/crop, borderless
fill and 0.35-module half-gap clearance; do not duplicate these design values.
The SVG renderer uses embedded local PNG data and SVG 1.1 image/filter attributes;
Android draws the same crop and alpha tint with native Canvas. QR/frame decoding
and rendered shell checks remain separate from physical TV/camera acceptance.

## Palette/background refinement (review draft)

## Palette and background refinement

The default uses deeper petrol, warm gold and ivory. Built-in palettes keep their IDs, with six landscape presets gaining paired
artwork, names, palettes and surface styles. The others retain distinct mineral/earth/night colors and calmer
lighting. Every text/accent role passes the existing 4.5:1 validator against all
four surface colors; glass also passes over white video. User imports are unchanged.

The default tile uses complete four-point stars and small diamonds, from
`shared/design/background-tile.json`. Eight full-composition masks offer stars,
woven lattice, botanical scrolls, diamond tiles, floral rosettes, hexagon mosaic,
flowing lines and one complete-arch option. Every mask keeps an eight-unit margin;
none crops an arch at the top or bottom. Existing IDs preserve saved selections;
bilingual display names describe the new geometry. Mountains have broader ridges,
dunes use two connected curves, and night skies use fewer points. No blur, new idle loop or decorative video is added.

`background.tile-size`, `tile-stroke` and `pattern-opacity` in the canonical tokens
set the common treatment. Android rasterizes the selected mask once; the preview
renders the same SVG path. LG builds small palette-tinted SVGs for each theme,
using full-screen proportions on Home and in its appearance preview. This avoids
the former bright white, densely repeated LG masks. Source SVGs remain white
alpha masks for Android; never bake a particular theme into their canonical files.

Base pattern opacity is 14%, with the existing lighting adjustment; the default repeating tile uses 9% ink. Solid is explicitly
plain, while Halo/Daylight are light-field styles.

Neutral landscape choices use the six local JPEGs in
`shared/design/assets/backgrounds/`, selected by `appearance.json.scenes`.
They keep their original colors under a 20% black readability overlay; lighting
continues to affect generated backgrounds only. Android decodes only the selected
scene, downsampling settings previews and retaining the existing screen cache.
Preview/LG use the same assets as CSS backgrounds, including draft previews.
Attribution and source filenames are in [THIRD-PARTY.md](THIRD-PARTY.md).

### Complete presets and customization

The preset carousel is the first Appearance control. A Customize heading separates
it from the existing layout, background, lighting and other fine controls; these
remain directly accessible. Selecting a different preset restores its background
and lighting in the draft, without changing prayer settings or layout. Apply saves
and Back/cancellation discards the draft. Repeated input at a carousel boundary
preserves manual changes. Saved settings are not reset on page entry or upgrade.

Six stable preset IDs now carry handcrafted scene themes:

| Display name | Stable ID | Palette / surface | Default scene |
| --- | --- | --- | --- |
| Dusk peaks | dusk | Violet / apricot, classic | dusk_peaks |
| Golden coast | sand | Coastal navy / peach, classic | golden_coast |
| Moon lake | midnight | Midnight navy / silver, cinema | moon_lake |
| Aurora sky | aurora | Blue-green / mint, glass | aurora_sky |
| Tea hills | forest | Tea green / pale leaf, soft | tea_hills |
| Monsoon | slate | Rain blue / amber, frame | monsoon |

Android/preview resolve the existing Theme background and inherited lighting;
manual overrides remain available. LG uses its explicit background selection:
choosing a preset sets the supported canonical mask/scene in its draft; an
unsupported procedural default resolves to plain, retaining the documented gaps.

### Miqat flagship default

`appearance.json.defaultBackground` selects `miqat_signature`, an original
material landscape made specifically for Miqat. Petrol ceramic and jade folds
with champagne-gold inlay borrow the logo's curve rhythm without repeating it
or adding another wordmark behind the controls. The default retains the Miqat
petrol/gold/ivory palette and classic surface; the new scene replaces the lattice.
The lattice remains a separate manual background.

The canonical JPEG is `shared/design/assets/backgrounds/miqat_signature.jpg`;
its creation notes/prompt are in `shared/design/artwork/miqat-signature.md`.
All renderers use the same bounded local asset and readability overlay. Android
and preview use it when the default preset follows Theme. LG uses it on fresh
preferences and when choosing Miqat again. Explicit saved background overrides
are preserved. No runtime AI, new network access, animation or blur is introduced.

### Home mihrab opacity and prayer symbols

Home supports 50–100% fill opacity in 5% steps, default/recommended 85%. The
percentage applies directly to the opaque panel color, including Glass; it does
not compound another surface alpha. Text and outlines remain opaque. Non-Home
prayer/alert frames retain their existing rendering. Invalid stored values resolve
to 85%. The inline editor previews its draft; numeric Back restores that draft,
and outer Cancel discards it. Apply persists it. A different preset restores 85%;
clamped selection preserves manual changes.

`shared/design/prayer-icons.json` owns all six symbols. The symbols use
familiar sun/horizon, rising/setting arrows, an afternoon shadow and a crescent,
with a consistent 2-unit stroke. Decorative diamond rays were removed after owner
feedback. Android vectors, browser SVG and LG SVG use the same paths; labels,
order, accessibility and prayer behavior stay intact.

### Second Miqat scene and next-prayer contrast

`miqat_quiet` / Miqat quiet / هدوء ميقات is an additional local scene. Its low
petrol/jade sculpted ribbons leave the upper reading area calm. Both original
Miqat artworks remain selectable; `defaultBackground` stays `miqat_signature`.
Neither artwork is automatically mirrored with language. Existing explicit scene
choices are preserved. Creation notes are in
[miqat-quiet.md](../shared/design/artwork/miqat-quiet.md).

The Home next-prayer card uses the selected surface's opaque panel RGB at an
absolute 75% alpha, from `surface.next-prayer-opacity`. It does not compound Glass
opacity. Labels and gold outline remain opaque; current/past cards and the
independent Home mihrab opacity control are unchanged. Android, preview and LG
use the same token and surface color. No blur or extra bitmap layer is required.

## Phone signature portrait

`shared/design/phone/portrait/miqat_signature.png` is the generated tall signature
composition, derived from Miqat’s own canonical landscape artwork. It is project
art under the repository’s MIT license. Android phone packaging includes this
separate source root; TV/LG/browser asset sets are unchanged. PhoneBackdrop
selects the portrait by actual drawable bounds and uses center-crop without sky
extension for it. Landscape cards and media artwork retain the original wide
scene. Setup, appearance/prayer previews and adhan reuse the same renderer and
shared decoded artwork; a preview does not change the selected background ID.
