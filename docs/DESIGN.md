# Shared design resources

The **Miqat / ميقات** identity uses the owner-selected Arabic calligraphy,
English Miqat in the frame baseline and separate plain TV. Source rasters and
provenance live in [`branding/miqat-tv-v1/`](../shared/design/branding/miqat-tv-v1/README.md).
`node site/scripts/splash.mjs` exports the shared full-HD splash, LG 80/130 px
icons, Android/installer 512 px icons, Android launch artwork/TV banner and the
preview favicon/social image. Regenerate them together; never redraw the lettering.
Android's initial window uses the splash; newer Android versions control their
own system splash treatment with the new application icon. LG maps both
`splashBackground` and legacy `bgImage` to the shared full-HD PNG.

[`mihrab.json`](../shared/design/mihrab.json) owns the new crown/shoulder geometry. Android TV,
the installer, LG and preview use it for the main prayer arch, call and dim
screensaver where those screens exist. `node tools/catalogs.mjs` also projects
it into both native `MiqatShape` classes; `--check` detects stale projections.
The geometry preserves existing content positions and focus treatments.

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
`custom` import slot. `assets/themes/` owns the 19 unchanged preset files; Mihrab
uses the existing design tokens. Android packages them as `assets/themes/<id>.json`
and uses generated `ThemeCatalog.PRESETS` through the existing Appearance API.
Preview/LG filter out `custom`, keeping their existing feature sets. Palette,
surface, background and lighting behavior stays in each adapter.

`appearance.json` owns the eight procedural background IDs, eight SVG pattern IDs,
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
