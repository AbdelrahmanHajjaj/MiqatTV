"""Check that the TV APK contains exactly the reviewed shared recordings."""
from pathlib import Path
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def check_apk(apk, media=ROOT / "shared/media"):
    expected = {"assets/" + file.relative_to(media).as_posix(): file
                for file in media.glob("*/*.mp3")}
    if not expected:
        raise ValueError("The canonical media set is missing")
    with zipfile.ZipFile(apk) as package:
        names = [name for name in package.namelist() if name.endswith(".mp3")]
        if len(names) != len(expected) or set(names) != set(expected):
            raise ValueError("APK recordings differ from the complete canonical set")
        for name, source in expected.items():
            if package.read(name) != source.read_bytes():
                raise ValueError("APK recording bytes changed: " + name)
    return len(expected)


if __name__ == "__main__":
    print("Verified", check_apk(Path(sys.argv[1])), "exact shared recordings in the APK.")
