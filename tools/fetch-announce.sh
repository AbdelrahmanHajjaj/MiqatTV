#!/usr/bin/env bash
# Re-create spoken shared clips using the documented voice; review changes before publishing.
#   bash tools/fetch-announce.sh              # only the missing clips
#   FORCE=1 bash tools/fetch-announce.sh      # all of them again (after changing the voice or a line)
#   VOICE=ar-EG-ShakirNeural bash tools/fetch-announce.sh   # another voice: python -m edge_tts --list-voices | grep ^ar-
set -e
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="$ROOT/shared/media/announce"
VOICE="${VOICE:-ar-SA-HamedNeural}"

PY=""
for p in python3 python; do
  if command -v "$p" >/dev/null 2>&1 && "$p" -c 'import edge_tts' 2>/dev/null; then PY="$p"; break; fi
done
if [ -z "$PY" ]; then
  echo "fetch-announce: edge-tts not found (pip install edge-tts); the spoken reminders stay silent." >&2
  exit 1
fi

mkdir -p "$DEST"
echo "voice: $VOICE"
while IFS='|' read -r name text; do
  name="${name%$'\r'}"; text="${text%$'\r'}"
  [ -n "$name" ] || continue
  out="$DEST/$name.mp3"
  [ -z "$FORCE" ] && [ -s "$out" ] && continue
  "$PY" -m edge_tts --voice "$VOICE" --text "$text" --write-media "$out" 2>/dev/null
  echo "  $name.mp3 ($(( $(wc -c < "$out") / 1024 )) KB)"
done < "$ROOT/tools/announce-lines.txt"
echo "Spoken lines are in $DEST"
