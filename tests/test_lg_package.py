"""IPK metadata validation without a TV, Android runtime or archive extraction."""
import gzip
import io
from pathlib import Path
import shutil
import subprocess
import tarfile
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


def member(name, data):
    header = f'{name+"/":<16}{0:<12}{0:<6}{0:<6}{"100644":<8}{len(data):<10}`\n'
    assert len(header) == 60
    return header.encode('ascii') + data + (b'\n' if len(data) % 2 else b'')


def package(control, path='./control', data=True):
    output = io.BytesIO()
    with tarfile.open(fileobj=output, mode='w', format=tarfile.USTAR_FORMAT) as archive:
        info = tarfile.TarInfo(path)
        info.size = len(control)
        archive.addfile(info, io.BytesIO(control))
    result = b'!<arch>\n' + member('debian-binary', b'2.0\n')
    result += member('control.tar.gz', gzip.compress(output.getvalue(), mtime=0))
    if data:
        result += member('data.tar.gz', gzip.compress(b'fixture only', mtime=0))
    return result


class LgPackageChecks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.output = Path(cls.temp.name)
        source = ROOT / 'companion/android/src/main/java/tv/miqat/companion/LgPackage.java'
        subprocess.run([shutil.which('javac'), '-encoding', 'UTF-8', '-d', str(cls.output),
                        str(source), str(ROOT / 'tests/LgPackageCheck.java')], check=True, capture_output=True)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def run_package(self, data, accept=False):
        path = self.output / 'test.ipk'
        path.write_bytes(data)
        subprocess.run([shutil.which('java'), '-cp', str(self.output), 'tv.miqat.companion.LgPackageCheck',
                        'accept' if accept else 'reject', str(path)], check=True, capture_output=True)

    def test_accepts_miqat_and_rejects_other_apps_or_ambiguous_metadata(self):
        valid = b'Package: tv.miqat.webos\nVersion: 0.2.28\nArchitecture: all\n'
        self.run_package(package(valid), True)
        for control in [valid.replace(b'tv.miqat.webos', b'tv.other.app'),
                        valid + b'Package: tv.miqat.webos\n', valid.replace(b'0.2.28', b'1;reboot'),
                        valid.replace(b'0.2.28', b'99999999999999999.0')]:
            with self.subTest(control=control):
                self.run_package(package(control))

    def test_rejects_truncation_wrong_archives_missing_and_oversized_control(self):
        valid = b'Package: tv.miqat.webos\nVersion: 0.2.28\n'
        for data in [b'not an IPK' * 20, package(valid)[:-10], package(valid, data=False),
                     package(valid, path='../control'), package(valid + b'x' * 70000)]:
            with self.subTest(length=len(data)):
                self.run_package(data)

    def test_versions_compare_numerically_for_downgrade_protection(self):
        subprocess.run([shutil.which('java'), '-cp', str(self.output),
                        'tv.miqat.companion.LgPackageCheck', 'version'], check=True, capture_output=True)
