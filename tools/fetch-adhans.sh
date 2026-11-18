#!/usr/bin/env bash
# Re-create missing shared adhan recordings from their documented source.
# Existing reviewed files are preserved; preparation keeps originals under ignored state/.
set -e
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="$ROOT/shared/media/adhan"
BASE="https://raw.githubusercontent.com/abodehq/Athan-MP3/master/Sounds"
mkdir -p "$DEST"
while IFS=$'\t' read -r id name; do
  [[ "$id" == \#* ]] && continue
  [ -f "$DEST/$id.mp3" ] && continue
  echo "  $id"
  curl -fsSL -o "$DEST/$id.mp3" "$BASE/$(printf '%s' "$name" | sed 's/ /%20/g')"
done < "$ROOT/tools/adhan-sources.tsv"
node "$ROOT/tools/prepare-adhans.mjs"
echo "Adhan recordings are in $DEST"
