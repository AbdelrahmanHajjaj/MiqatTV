# LG webOS instructions

Read the root [AGENTS.md](../../AGENTS.md), [LG guide](../../docs/WEBOS.md) and
[test requirements](../../docs/TESTING.md).

- Target LF6300 / webOS 2.2: WebKit 538.2 and service Node 0.10.25. Shipped app
  and service must parse as ES5 and avoid unavailable APIs. Build tools use Node 24.
- Keep tv.miqat.webos and tv.miqat.webos.service aligned across metadata, Luna
  calls, storage, activities, tests and packaging. Align all four version declarations.
- Build consumes shared core/data/design and local audio. Edit canonical sources;
  never edit build/. Run shared JS generators sequentially.
- Preserve input/audio lifecycle, volume ownership, duplicate/expired callback
  handling and once-per-boot startup. The service is demand-started, not a global
  remote listener. No standby wake, system Home replacement or root recipes.
- Preserve both languages, joined Arabic, RTL/LTR, pointer/remote focus and
  return scroll position. Use shared inline numeric and choice-editor contracts.
- Browser Luna/media mocks and live diagnostics do not prove TV picture or sound.
  Get watcher readiness before audible, power or physical acceptance checks.
- TV capture APIs cannot reliably capture the video plane. Never call a desktop
  capture a TV screenshot. Keep device state, credentials and media under state/.
- No physical-TV deployment is part of repository setup. Record new-identity
  fresh-install acceptance when separately authorized; do not reuse the old ID's pass.
