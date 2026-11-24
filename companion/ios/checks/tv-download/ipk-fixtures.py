"""Project the existing Android fixture format into files for the iOS parser."""
import runpy
import sys
from pathlib import Path

root = Path(__file__).resolve().parents[4]
package = runpy.run_path(str(root / "tests" / "test_lg_package.py"))["package"]
output = Path(sys.argv[1])
output.mkdir(parents=True, exist_ok=True)

control = b"Package: tv.miqat.webos\nVersion: 0.2.28\nArchitecture: all\n"
valid = package(control)
corrupt_gzip = bytearray(valid)
header = corrupt_gzip.index(b"control.tar.gz/")
size = int(corrupt_gzip[header + 48:header + 58])
corrupt_gzip[header + 60 + size - 8] ^= 1

cases = {
    "valid": valid,
    "wrong-package": package(control.replace(b"tv.miqat.webos", b"tv.other.app")),
    "duplicate-field": package(control + b"Package: tv.miqat.webos\n"),
    "invalid-version": package(control.replace(b"0.2.28", b"invalid")),
    "missing-payload": package(control, data=False),
    "wrong-path": package(control, path="../control"),
    "truncated": valid[:-10],
    "overexpanded": package(control + b"x" * (1024 * 1024)),
    "invalid-gzip": corrupt_gzip,
}
for name, data in cases.items():
    (output / (name + ".ipk")).write_bytes(data)
