# Miqat: agent instructions

Read [docs/WORK.md](docs/WORK.md) for approved scope and the next action. Keep it
short; record essential validation in [docs/EVIDENCE.md](docs/EVIDENCE.md), with
private logs, captures, device details and credentials under ignored state/.

Read [architecture](docs/ARCHITECTURE.md), [contributing](docs/CONTRIBUTING.md) and
[testing](docs/TESTING.md) before substantial changes. Work sequentially within
the approved scope. The standalone phone app and its TV companion mode both live
in companion/ in this repository; keep phone prayer settings independent of TVs.

## Structure

- shared/core/: pure Kotlin rules and semantic UI/input contracts, compiled for JVM and ES5.
- shared/data/: canonical catalogs, bilingual strings and exact sacred text.
- shared/design/: canonical tokens, geometry, themes, patterns, artwork and notices.
- shared/media/: the approved, checksummed recordings packaged by Android and LG.
- platforms/android-tv/: Java and Android Views; [Android notes](docs/ANDROID-INTEGRATION.md).
- platforms/webos/: ES5 app and JS service; read its [AGENTS.md](platforms/webos/AGENTS.md).
- installer/: Android TV setup and recovery library used by Miqat Phone.
- companion/: standalone Android phone prayers and optional TV setup/control; see docs/PHONE.md and companion/README.md.
- site/: bilingual product website, current screenshots, interactive TV preview and resource generators; device APIs are simulated.
- docs/: setup, development, technical references and validation status.

## Changes

- Keep product copy brief: labels, useful status and required setup actions.
  Remove narration, redundant hints and obvious usage tutorials. Do not re-add
  explanatory filler during later work. Avoid generic marketing slogans and
  defensive hardware-verification disclaimers in product copy. Keep concrete
  requirements in setup guides and test history in EVIDENCE.md.
  Write the product website for first-time visitors: introduce what Miqat does,
  connect features to everyday use and provide device-labelled downloads. Keep
  release notes, catalog order, internal names and design/implementation choices
  out of marketing copy. See the research and copy guide in site/README.md.

- Use Miqat / ميقات and tv.miqat.* identifiers. Migration from the previous app
  identity uses a fresh installation; do not add legacy aliases.
- Put deterministic policy in core, with explicit inputs/results. Keep astronomy,
  calendars, persistence, rendering, media, networking and OS APIs in shells.
- Define TV controls/choices in shared/data/ui-contract.json, phone-only controls
  in shared/data/phone-ui-contract.json, and behavior in core.
  Reuse shared numeric/choice editors, save/cancel rules, focus and design tokens.
  Declare missing shell capabilities; a catalog entry does not implement a UI.
- Edit canonical sources and regenerate consumers. Check Android, LG and preview
  when they consume a shared change. Run shared JS generators sequentially.
- Preserve Arabic/English, RTL/LTR, joined Arabic and exact religious text.
  Mirror horizontal physical input once at the shell boundary.
- Keep runtime networking limited to selected radio, manual advisory prayer
  comparison, explicitly loaded phone Qibla maps and the authorized foreground
  household-LAN companion protocol. Phone location uses Android APIs with explicit
  optional background consent. Installer ADB/SSH/downloads are separate.
  No ads, analytics or feeds.
- Keep keys, local device configuration, private state, outputs, unreviewed audio
  and original recordings out of Git. Only the approved shared/media set is shipped. Retain MIT and third-party notices in distributed builds.
- Preserve the primary checkout's private state/signing/ during cleanup; it holds
  persistent release identities, not disposable build output. Never replace keys
  to fix a signing failure. See docs/RELEASING.md for signing and recovery.
- Keep the portable core independent of any SDK or renderer. Android stays Java
  Views; LG app/service must run on WebKit 538.2 and Node 0.10.25.

## Validation and delivery

- Keep classes responsible for one coherent job. Split mixed rendering, device
  transport and product policy at existing boundaries; avoid giant methods and
  pass-through abstractions. Search callers before deleting or consolidating code.
- Reuse canonical contracts and portable rules for features shared by phone/TV;
  shells declare and implement their capabilities. Do not duplicate shared policy
  or claim support from a catalog entry alone.
- Tests must protect an observable feature, failure or edge case. Do not add
  getter/constant/implementation-mirroring tests or blanket coverage targets.
  Remove redundant tests only after identifying the behavior still protected.
- Continue only the approved checklist. Update WORK.md and PHONE-WORK.md at a
  milestone/compaction boundary; never label a partial journey complete or start
  unrelated work after the handoff. Review touched code for dead paths,
  duplication, swallowed failures and unnecessary abstractions before delivery.

- Follow docs/TESTING.md for affected-consumer checks and focused regressions.
  Check prose-only edits for links, paths and claims; they need no device tests.
- Routine CI must not launch phone/TV emulators. Keep build, lint, portable/JVM,
  browser, signing and package-integrity checks; native device suites are targeted
  manual checks when requested, not automatic merge or release gates.
- Browser mocks, emulators, live diagnostics and human TV acceptance are separate
  evidence. Physical remote, picture/sound and power checks need a ready watcher.
- Use a feature branch and the PR template. Review staged files for private data.
  Preserve branch commits with a normal merge; never squash.
  Use gh for PR operations. The owner pre-authorizes normal merges of approved
  task PRs after all required checks pass, followed by syncing local main. Release
  publication and physical-device deployment still need specific instruction.
- Update CHANGELOG.md for visible changes. Keep current support limits accurate.
- Keep README, current guides and the bilingual website aligned with all shipped
  products: standalone phone, TV companion mode, Android TV and LG. Public images
  belong in docs/screenshots/ with provenance; use sanitized native/demo captures,
  distinguish LG browser mocks from hardware and never publish pairing secrets.
- Use Context7 for SDK/library/CLI behavior; prefer official documentation when
  it lacks coverage. Ordinary refactoring and product logic need no lookup.
