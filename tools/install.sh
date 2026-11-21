#!/usr/bin/env bash
# Build locally, back up, install and select Home. Add --exclusive to disable known stock Home packages.
# Enable prayer/remote buttons separately with tools/device.py configure after checking Home.
set -euo pipefail
. "$(dirname "$0")/env.sh"
if ! compgen -G "$ROOT/shared/media/adhan/*.mp3" >/dev/null; then
  echo 'Adhan recordings are missing. Restore the reviewed shared/media/adhan files from Git before building.' >&2
fi
(cd "$ROOT" && ./gradlew :app:assembleDebug --console=plain)
exec "${PYTHON:-python3}" "$ROOT/tools/device.py" --tv "$TV" --adb "$ADB_BIN" install "$@"
