# Shared catalogs

`cities.json`, `regions.json`, `methods.json`, `stations.json`, `voices.json` and
`strings.json` are canonical inputs for Android phone/TV,
LG and the browser preview. The phone projects them through companion/generate.mjs;
its additional native copy lives in companion/strings.json, and phone-only
controls in shared/data/phone-ui-contract.json. Keep existing city/method IDs and ordering so upgrades retain selected locations;
Cairo is the fallback. Current work and decisions are tracked
only in [`docs/WORK.md`](WORK.md).

Run `node tools/catalogs.mjs` from the repository root to regenerate Android's
`CityCatalog.java`, `PrayerMethod.java`, `RadioCatalog.java`, `VoiceCatalog.java`, `Text.java` and
`tools/adhan-sources.tsv`, or `npm --prefix site run gen` for all current generators.
`node tools/catalogs.mjs --check` checks without writing. `npm --prefix site test`
checks catalog validation, Java data fidelity and all method parameters against
the preserved adapter baseline.

The Android `Prefs.City` interface and persisted setting IDs are unchanged. The
preview and LG read JSON directly. `methods.json` owns ordered IDs, bilingual
labels, custom angles/intervals and method corrections. Built-in calculation
behavior stays in adhan-java/adhan-js. Edit Android adapter code in
`platforms/android-tv/src/main/templates/PrayerMethod.java.template`, then regenerate; do not edit
the generated enum.

`stations.json` preserves radio/reciter IDs, order, exact stream URLs, bilingual
names/locations and the grouping flag. Cairo remains the fallback. `Radio.Station`
and playback stay native; changing a catalog does not validate a stream's TLS or
codec on any TV. Broadcast streams come from the stations' players; reciters use
mp3quran.net's 24/7 channels. Sharjah remains excluded because its raw ADTS AAC
stream never starts with Android's MediaPlayer, and its official HLS is video.

`voices.json` owns the ordered adhan IDs/names and original download filenames.
The generated TSV lets `tools/fetch-adhans.sh` keep working with Bash/curl alone.
Packaged assets remain `adhan/<id>.mp3`; regular/Fajr defaults are `almalki` and
`chebae_fajr`. The reviewed recordings are in shared/media/ with maintainer authorization; see
[third-party notices](THIRD-PARTY.md).

`strings.json` owns the plain bilingual strings/lists shared with native formatting. `{name}` parameters and the optional `d` digit-substitution flag retain
their existing meanings. Both languages must have the same parameters/list sizes.
Edit native formatting code in `platforms/android-tv/src/main/templates/Text.java.template`, then
regenerate. Web adapters preserve the old extractor's compact literal spacing; Android
preserves native spacing (notably `saved` and `roomKeys`). Compiled baseline hashes
cover both languages/digit modes and fresh label arrays.

`format-text.json` owns the remaining formatter literals by locale. These include
prefixes/suffixes with significant spaces, labels, directions and grammatical
forms used only in one language. `{{TEXT:ar:key}}` / `{{TEXT:en:key}}` markers insert
Java literals at build time; selection, plural rules and digit formatting stay in
the adapters. Preview/LG read the same data for matching copy. Native and preview
have separate frozen edge-case baselines.

`sacred-text.json` preserves the existing An-Nisa 4:103 verse as three exact Arabic
lines and the adhan call as exact Arabic text, in both UI languages. Android uses
generated `SacredText` constants; web renderers insert escaped text with explicit
line breaks. The site footer joins the same lines with spaces. Keep code points,
diacritics and breaks unchanged; frozen hashes check the pre-extraction baseline.
