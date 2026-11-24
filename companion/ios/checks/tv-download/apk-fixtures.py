"""Small ZIP and Android binary XML inputs for the iOS APK identity check."""
import io
import struct
import sys
import warnings
import zipfile
from pathlib import Path

output = Path(sys.argv[1])
output.mkdir(parents=True, exist_ok=True)
manifest_name = "AndroidManifest.xml"
namespace = "http://schemas.android.com/apk/res/android"


def chunk(kind, extra, data):
    header_size = 8 + len(extra)
    return struct.pack("<HHI", kind, header_size, header_size + len(data)) + extra + data


def manifest(package="tv.miqat.home", version=42, major=None, utf8=True):
    strings = ["manifest", "package", package, namespace, "versionCode", "versionCodeMajor"]
    offsets = []
    pool = bytearray()
    for value in strings:
        offsets.append(len(pool))
        if utf8:
            encoded = value.encode()
            pool.extend(bytes([len(value), len(encoded)]) + encoded + b"\0")
        else:
            pool.extend(struct.pack("<H", len(value)) + value.encode("utf-16le") + b"\0\0")
    pool.extend(b"\0" * (-len(pool) % 4))
    string_pool = chunk(
        1, struct.pack("<5I", len(strings), 0, 0x100 if utf8 else 0, 28 + 4 * len(strings), 0),
        struct.pack("<" + "I" * len(offsets), *offsets) + pool,
    )
    absent = 0xFFFFFFFF

    def attribute(ns, name, raw, kind, data):
        return struct.pack("<IIIHBBI", ns, name, raw, 8, 0, kind, data)

    attributes = [attribute(absent, 1, 2, 3, 2), attribute(3, 4, absent, 16, version)]
    if major is not None:
        attributes.append(attribute(3, 5, absent, 16, major))
    root = chunk(
        0x0102, struct.pack("<II", 1, absent),
        struct.pack("<II6H", absent, 0, 20, 20, len(attributes), 0, 0, 0) + b"".join(attributes),
    )
    end = chunk(0x0103, struct.pack("<II", 1, absent), struct.pack("<II", absent, 0))
    return chunk(3, b"", string_pool + root + end)


def archive(xml, compression=zipfile.ZIP_DEFLATED, names=(manifest_name,)):
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", compression) as apk:
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            for name in names:
                apk.writestr(name, xml)
        apk.comment = b"fixture"
    return buffer.getvalue()


valid = archive(manifest())
plain_xml = manifest(major=2, utf8=False)
stored = archive(plain_xml, zipfile.ZIP_STORED)
corrupt = bytearray(stored)
corrupt[stored.index(plain_xml) + 4] ^= 1
cases = {
    "valid": valid,
    "stored-utf16": stored,
    "wrong-package": archive(manifest(package="tv.miqat.phone")),
    "zero-version": archive(manifest(version=0)),
    "duplicate-manifest": archive(manifest(), names=(manifest_name, manifest_name)),
    "missing-manifest": archive(manifest(), names=("assets/AndroidManifest.xml",)),
    "corrupt-manifest-crc": corrupt,
    "truncated-zip": valid[:-9],
    "truncated-axml": archive(manifest()[:-5]),
}
for name, data in cases.items():
    (output / (name + ".apk")).write_bytes(data)
