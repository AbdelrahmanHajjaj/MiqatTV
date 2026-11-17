# Miqat: agent instructions

Read [docs/WORK.md](docs/WORK.md) for approved scope and the next action. Keep it
short; record essential validation in [docs/EVIDENCE.md](docs/EVIDENCE.md), with
private logs, captures, device details and credentials under ignored state/.

Read [architecture](docs/ARCHITECTURE.md), [contributing](docs/CONTRIBUTING.md) and
[testing](docs/TESTING.md) before substantial changes. Work sequentially within
the approved scope. Companion work belongs to its separate project/branch.

## Structure

- shared/core/: pure Kotlin rules and semantic UI/input contracts, compiled for JVM and ES5.
- shared/data/: canonical catalogs, bilingual strings and exact sacred text.
- shared/design/: canonical tokens, geometry, themes, patterns, artwork and notices.
- platforms/android-tv/: Java and Android Views; [Android notes](docs/ANDROID-INTEGRATION.md).
- platforms/webos/: ES5 app and JS service; read its [AGENTS.md](platforms/webos/AGENTS.md).
- installer/: Android phone installation/recovery tool; separate from the companion.
- site/: browser demonstration and resource generators, with simulated TV APIs.
- docs/: setup, development, technical references and validation status.

## Changes

- Use Miqat / ميقات and tv.miqat.* identifiers. Migration from the previous app
  identity uses a fresh installation; do not add legacy aliases.
- Put deterministic policy in core, with explicit inputs/results. Keep astronomy,
  calendars, persistence, rendering, media, networking and OS APIs in shells.
- Define controls/choices in shared/data/ui-contract.json and behavior in core.
  Reuse shared numeric/choice editors, save/cancel rules, focus and design tokens.
  Declare missing shell capabilities; a catalog entry does not implement a UI.
- Edit canonical sources and regenerate consumers. Check Android, LG and preview
  when they consume a shared change. Run shared JS generators sequentially.
- Preserve Arabic/English, RTL/LTR, joined Arabic and exact religious text.
  Mirror horizontal physical input once at the shell boundary.
- Keep runtime networking limited to selected radio and manual advisory prayer
  comparison. Installer ADB/downloads are separate. No ads, analytics or feeds.
- Keep keys, local device configuration, private state, outputs and restricted
  recordings out of Git. Retain MIT and third-party notices in distributed builds.
- Keep the portable core independent of any SDK or renderer. Android stays Java
  Views; LG app/service must run on WebKit 538.2 and Node 0.10.25.

## Validation and delivery

- Follow docs/TESTING.md for affected-consumer checks and focused regressions.
  Check prose-only edits for links, paths and claims; they need no device tests.
- Browser mocks, emulators, live diagnostics and human TV acceptance are separate
  evidence. Physical remote, picture/sound and power checks need a ready watcher.
- Use a feature branch and the PR template. Review staged files for private data.
  Merging, releasing and deploying require the owner's corresponding instruction.
- Update CHANGELOG.md for visible changes. Keep current support limits accurate.
- Use Context7 for SDK/library/CLI behavior; prefer official documentation when
  it lacks coverage. Ordinary refactoring and product logic need no lookup.
