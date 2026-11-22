"""Verify release mode, package identity and the persistent APK signing certificate."""
import argparse
import json
from pathlib import Path
import re
import subprocess


CERTIFICATES = Path(__file__).with_name('release-certificates.json')


def validate_reports(badging, certificates, expected):
    package = re.search(r"^package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'", badging, re.M)
    if not package or package[1] != expected['applicationId'] or int(package[2]) <= 0:
        raise ValueError('APK package identity or version is invalid')
    if re.search(r'^application-debuggable(?:\s|$)', badging, re.M):
        raise ValueError('Debug APKs cannot enter a release')
    digests = re.findall(r'^Signer #[0-9]+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$', certificates, re.M)
    if [digest.lower() for digest in digests] != [expected['certificateSha256']]:
        raise ValueError('APK signing certificate does not match the persistent release key')
    return package[3], int(package[2])


def check_apk(apk, build_tools):
    expected = json.loads(CERTIFICATES.read_text(encoding='utf-8'))[apk.name]
    aapt = build_tools / ('aapt.exe' if (build_tools / 'aapt.exe').exists() else 'aapt')
    badging = subprocess.check_output([str(aapt), 'dump', 'badging', str(apk)], encoding='utf-8')
    certificates = subprocess.check_output(['java', '-jar', str(build_tools / 'lib/apksigner.jar'),
        'verify', '--print-certs', str(apk)], encoding='utf-8')
    version, code = validate_reports(badging, certificates, expected)
    if apk.name == 'miqat.apk' and not 0 < apk.stat().st_size < 25_000_000:
        raise ValueError('Android TV release APK must be under 25 MB')
    print(f'Verified release APK: {apk.name}, {version} ({code}), {expected["certificateSha256"]}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('build_tools', type=Path)
    parser.add_argument('apks', nargs='+', type=Path)
    args = parser.parse_args()
    for apk in args.apks:
        check_apk(apk, args.build_tools)
