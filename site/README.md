# Miqat website

Bilingual product showcase and interactive TV preview, published by GitHub Pages
at [MiqatTV](https://abdelrahmanhajjaj.github.io/MiqatTV/). Arabic is at the root;
English is under `/en/`. The static Astro site presents four product surfaces:
standalone Android phone, TV companion mode, Android/Google TV and LG webOS.

## Product copy

Write for someone meeting Miqat for the first time. Introduce prayer times and
Adhan, show what people can do on each device, then offer the matching download.
Each screenshot caption should explain a useful feature. Keep catalog order,
internal preset names, geometry, save behaviour and implementation history in
the development guides. Use the same approach in Arabic and English.

Research reviewed on 22 November 2026:

- [Pillars](https://thepillarsapp.com/): prayer features presented through daily
  use, with screenshots and an app download.
- [Things](https://culturedcode.com/things/): the app's purpose before feature
  exploration, with downloads grouped by device.
- [Flighty](https://flighty.com/): features explained through travel situations
  and their usefulness to the traveller.

Miqat applies these patterns to its own features and screenshots. Do not copy
competitor wording, ratings, awards or unimplemented features.
`src/lib/stable-release.json` provides the local build baseline. Production
promotion generates the public tag, LG asset filename and Android version
metadata from the verified packages. It reuses the approved screenshots from
the Staging release and checks all three device links and `/updates.json`.

## Content and assets

- `src/components/ProductShowcase.astro`: phone feature gallery, companion mode,
  TV platform captures, package information and product hero, in both languages.
- `src/layouts/Layout.astro`: shared navigation, accessibility, metadata/social
  cards, structured data, footer and the interactive TV stage.
- `src/lib/site.ts`: existing TV screen routes and their bilingual copy.
- `../docs/screenshots/`: canonical current PNGs and capture provenance. Astro
  creates responsive WebP images; full-size links keep the original screenshot.
- `public/branding/product-{en,ar}.png`: social cards captured from the product hero.

Phone screenshots show the native app. The TV preview has local prayer calculation/settings,
with simulated device/network/media boundaries and no Adhan playback. Its screen
links update the TV stage in place; existing deep links and browser Back remain.
Product links navigate the website separately. Normal page arrow keys keep
scrolling when the TV is off screen.

## Build and review

With JDK 17 and Node 24, from the repository root:

```sh
npm --prefix shared/core ci --ignore-scripts
npm --prefix site ci
npm --prefix site run data:check
npm --prefix site run tokens:check
npm --prefix site test
npm --prefix site run build
npm --prefix site run test:browser
```

Run shared-core, site and LG generators sequentially. `npm --prefix site run dev`
starts local development. Keep the `/MiqatTV/` base path in asset and route URLs.
PR CI validates the site; only successful main validation deploys it. See
[website CI](../docs/CI.md#website).

For focused product-page review and social-card refresh, after building:

```sh
node site/scripts/test-pairing-browser.cjs --showcase-only --capture
npm --prefix site run build
```

This uses the existing Playwright/Edge browser setup. It checks Arabic/English at
390, 900 and 1440 px, image loading, original-image links, normal page keys, anchors
and language switching. Captures under ignored state/ support visual review;
`--capture` also refreshes the two tracked 1200×630 social cards. Rebuild afterwards
to include them, then run browser checks without `--capture` against final output.
The default browser suite also retains TV settings, pairing and focus regressions.

Keep app capabilities, preview simulation, source builds, private CI artifacts and
public releases distinct. Link downloads to published releases. Preserve screenshot
provenance and exact religious text. Use [PHONE.md](../docs/PHONE.md) and the TV
[capability report](../docs/UI-GAPS.md) for supported behavior.
