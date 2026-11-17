# Miqat artwork

The shared identity uses Arabic calligraphy inside the shaped mihrab, with Miqat
in the base and TV below. The app icon uses the Arabic word without tiny English.

- miqat-tv-logo-master.png: selected raster wordmark.
- miqat-tv-icon-source.png: square icon master.
- miqat-tv-splash-source.png: splash source.
- miqat-tv-splash-1920x1080.png: full-HD splash.
- miqat-tv-icon-{32,48,80,130,256,512,1024}.png: size exports.
- manifest.json: dimensions, hashes and generation provenance.

Run node site/scripts/splash.mjs from the repository root to regenerate runtime
icons, splash, Android banner and preview artwork together. export.ps1 recreates
the source variants; its parameters are Master, Icon and Splash.

Keep the selected lettering intact. These are raster masters with slight tonal
variation; there is no vector calligraphy master or Android adaptive-icon layer.
The icon-size review image is a desktop comparison, not device acceptance.
See [design sources](../../../../docs/DESIGN.md) and
[current validation](../../../../docs/EVIDENCE.md).
