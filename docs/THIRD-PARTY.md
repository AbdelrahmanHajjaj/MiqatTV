# Third-party material

The root LICENSE retains the original copyright notice for reused source and
covers Miqat contributions. The components below have their own notices.

| What | Where | Licence |
|---|---|---|
| [ZXing core 3.5.4](https://github.com/zxing/zxing/tree/zxing-3.5.4) by ZXing authors | Companion pairing QR codec in the Android TV and phone shells | Apache 2.0; upstream full text under `assets/licenses/zxing/` |
| [Forge 1.4.3](https://github.com/digitalbazaar/forge/tree/v1.4.3) by Digital Bazaar and contributors | LG companion certificate creation/validation only; TLS uses Node's native implementation | BSD-3-Clause option; unabridged upstream licence in LG `service/licenses/node-forge.txt` |
| [QR Code Generator 2.0.4](https://github.com/kazuhikoarase/qrcode-generator/tree/master/js) by Kazuhiko Arase | Local LG pairing and inert browser-demo QR encoding | MIT; upstream full text in `shared/design/assets/licenses/qrcode-generator.txt`, copied into LG and preview packages |
| [adhan-java 1.2.1](https://github.com/batoulapps/adhan-java/tree/eefc4ed1b910ec144dff247f45b2b23a16c7d0c2) by Batoul Apps | Android TV and phone compile pinned sources and package the same complete notices; only minute rounding delegates to the shared rule | MIT; full text/source reference in `assets/licenses/adhan-java-{LICENSE,SOURCE}.txt` |
| [Kotlin standard library 2.2.10](https://github.com/JetBrains/kotlin/tree/v2.2.10) by JetBrains and contributors | Shared JVM/JS core runtime, bundled in Android, LG app/service and preview | Apache 2.0 and the upstream standard-library third-party notices; full texts packaged under Android `assets/licenses/kotlin/`, LG app/service `licenses/kotlin/` and preview `licenses/kotlin/` |
| [JetBrains annotations 13.0](https://repo1.maven.org/maven2/org/jetbrains/annotations/13.0/annotations-13.0.pom) | Transitive Kotlin JVM runtime dependency | Apache 2.0; license and source attribution under `assets/licenses/kotlin/` |
| [adhan-js](https://github.com/batoulapps/adhan-js) by Batoul Apps | Direct dependency of preview and LG; bundled as ES5 in LG | MIT; full notice included in the package |
| [libadb-android 3.1.1](https://github.com/MuntashirAkon/libadb-android/tree/c849886ebc6d48e7b46d967e78a6bb65c90c3b74) by Muntashir Al-Islam | Existing phone installer ADB client | Apache-2.0 option of its dual licence, with upstream COPYING/component texts under installer `assets/licenses/libadb/` |
| [spake2-java / spake2-android 2.2.1](https://github.com/MuntashirAkon/spake2-java/tree/7615ddd680b990e14513ebb66eac4cb0dbf82464) and [native submodule](https://github.com/MuntashirAkon/spake2-c/tree/0d15933e5ba3e662cb01245a7ac0dc9fca3eac31) | Existing transitive installer pairing dependency | LGPL-3.0, with retained LGPL-2.1-or-later/ISC/MIT/component attribution; full texts and pinned source/build references under installer `assets/licenses/` |
| [Conscrypt Android 2.5.3](https://github.com/google/conscrypt/tree/fd209de6167201e4f3bca5b99c6db9f08d1843c6) by the Android Open Source Project | Existing installer TLS provider | Apache-2.0; full LICENSE, NOTICE and Harmony/Netty texts under installer `assets/licenses/conscrypt/` |
| [JSch 2.28.8](https://github.com/mwiede/jsch/tree/jsch-2.28.8) | Phone LG Developer Mode SSH transport | Complete BSD-style licence wording in companion `assets/licenses/jsch/LICENSE.txt` (trailing spaces normalized); no upstream source changes |
| [BoringSSL](https://github.com/google/boringssl/tree/49f0329110a1d93a5febc2bceceedc655d995420), revision recorded in that Conscrypt AAR | Existing native Conscrypt component | Full BoringSSL/OpenSSL/ISC/component text under installer `assets/licenses/conscrypt/boringssl/` |
| [Amiri](https://github.com/aliftype/amiri) by Khaled Hosny | `shared/design/resources/font/amiri_*.ttf` | SIL Open Font Licence 1.1 |
| [Alexandria](https://fonts.google.com/specimen/Alexandria) by Mohamed Gaber and Julieta Ulanovsky | `shared/design/resources/font/alexandria_*.ttf` | SIL Open Font Licence 1.1 |
| [Marcellus](https://fonts.google.com/specimen/Marcellus) by Astigmatic | `shared/design/resources/font/marcellus_regular.ttf` | SIL Open Font Licence 1.1 |
| Adhan recordings from [abodehq/Athan-MP3](https://github.com/abodehq/Athan-MP3) | The reviewed collection is in shared/media/adhan, with approved ending cuts in shared/data/adhan-edits.json | The maintainer confirmed redistribution and approved inclusion on 18 November 2026. The source does not publish a licence; these recordings are not relicensed under the code's MIT licence. |
| Spoken reminder lines (tools/announce-lines.txt), generated using Microsoft's ar-SA-HamedNeural voice through [edge-tts](https://github.com/rany2/edge-tts) | The 12 reviewed clips are in shared/media/announce | Microsoft's speech service terms; maintainer approved distribution on 18 November 2026. |
| Quran radio stations: إذاعة القرآن الكريم in Cairo, Nablus, Qatar (two channels) and Jordan, and Shabab FM Quran (Nablus) | Played from each station's public stream, never stored | Belongs to each station. Miqat is not affiliated with any of them. |
| Reciter channels from [mp3quran.net](https://mp3quran.net) (`backup.qurango.net`) | Played from the public stream, never stored | Belongs to mp3quran.net and the reciters. Miqat is not affiliated with it. |

LG's fixed-host AlAdhan HTTPS client bundles the public USERTrust RSA CA root
from [Node's certificate source](https://github.com/nodejs/node/blob/v24.14.0/src/node_root_certs.h),
matching [Sectigo's official root](https://support.sectigo.com/Com_KnowledgeDetailPage?Id=kA03l000000c4KV).
The certificate, fingerprint, provenance and Node source notice ship under
`service/trust/usertrust-rsa.pem` and `service/licenses/online-ca.txt`. This is a
public CA certificate, with no private key or change to the TV's system trust store.

Android and LG packages include the same reviewed shared/media/ recordings.
Their SHA256SUMS file fixes the distributed bytes. Original downloads, preparation
stamps and review material stay private under ignored state/.
Voice metadata comes from `shared/data/voices.json`;
its radio list comes from `shared/data/stations.json`.

The stream addresses are in `shared/data/stations.json`. A station or reciter is removed on request from its rights holder.

The existing full font notices live in `shared/design/assets/licenses/`. Android
packages them under `assets/licenses/`; LG under `app/licenses/`; the preview build
copies them to `public/licenses/`.

Project and CleanYourTV full MIT texts are generated from root
`LICENSE` and `docs/CLEANYOURTV-LICENSE.txt` into shared and installer asset sources
by `tools/catalogs.mjs`. They ship in both Android APKs, LG app/service and preview.
Do not hand-edit these generated copies. Installer `assets/licenses/SOURCES.txt`
records exact existing dependency source revisions and rebuild references; full
dependency texts are retained verbatim from those revisions. No dependency update
or relicensing is part of this notice audit.

The fonts can be renamed or bundled under the OFL, but not sold on their own. The full text is at
https://openfontlicense.org.

## Illustrated landscape backgrounds

Six unmodified 1920×1080 JPEG illustrations are reused from Gathin's
[CleanYourTV](https://github.com/Gathin23/cleanyourtv), source checkout revision
`f9f0c5353e65c5003d5f2f45784a30c5dee19ecb`, under MIT (copyright 2026 Gathin).
The complete notice is already shipped as `licenses/CleanYourTV-MIT.txt`.
Source directory: `themes/scenes/wallpapers/`; generator: `tools/scenes_wallpapers.py`.

| Miqat asset in shared/design/assets/backgrounds/ | CleanYourTV source |
| --- | --- |
| dusk_peaks.jpg | 01-dusk-peaks.jpg |
| golden_coast.jpg | 02-golden-coast.jpg |
| moon_lake.jpg | 03-moon-lake.jpg |
| aurora_sky.jpg | 04-aurora.jpg |
| tea_hills.jpg | 08-tea-hills.jpg |
| monsoon.jpg | 12-monsoon.jpg |

These are local illustrated scenes, with a 20% black overlay applied by each
renderer for readability. They do not change color with the theme or lighting
control. No external image service or runtime image generation is used.
