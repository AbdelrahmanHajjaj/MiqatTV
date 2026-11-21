# Shared audio

The maintainer approved including these 13 reviewed adhan recordings and 12
spoken clips in public Miqat builds on 18 November 2026. They are third-party
media, separate from the code's MIT licence. Sources are in
[third-party notices](../../docs/THIRD-PARTY.md).

Android and LG package these same files. Run sha256sum --check
shared/media/SHA256SUMS from the repository root to verify the complete set.
Approved promotional-ending cuts and prepared hashes are in
shared/data/adhan-edits.json. Originals, preparation stamps and review material
remain under ignored state/. Do not add unrelated or unreviewed recordings here.

Al Afasy was retired from selection and distribution at the owner’s request on
19 November 2026. The historical review remains in docs/AUDIO-AUDIT.md.

On 21 November 2026 the maintainer reviewed before/after excerpts for all 14
historically reviewed adhan voices and approved mild cleanup: a 50 Hz high-pass
and gentle, smoothed FFT denoising. The 13 retained recordings use those exact
approved cleaned bytes; Al Afasy remains excluded. Existing cuts, decoded
duration, sample rate, channel count and MP3 bitrate are retained; static
attenuation provides headroom where needed. The 12 reminder clips are unchanged.
See the [audio audit](../../docs/AUDIO-AUDIT.md).

Tracked files are the canonical reviewed outputs. Restore missing approved audio
from Git. The fetch/trim tools retrieve source material and preserve approved
cuts; they do not reproduce this offline cleanup or authorize replacement hashes.
