"""Verify release mode, package identity and the persistent APK signing certificate."""
import argparse
import json
from pathlib import Path
import re
import subprocess


CERTIFICATES = Path(__file__).with_name('release-certificates.json')


def package_version(badging, application_id):
    package = re.search(r"^package: name='([^'\r\n]+)' versionCode='([0-9]+)' versionName='([^'\r\n]+)'",
                        badging, re.M)
    if not package or package[1] != application_id or int(package[2]) <= 0:
        raise ValueError('APK package identity or version is invalid')
    return package[3], int(package[2])


def validate_reports(badging, certificates, expected):
    version = package_version(badging, expected['applicationId'])
    if re.search(r'^application-debuggable(?:\s|$)', badging, re.M):
        raise ValueError('Debug APKs cannot enter a release')
    digests = re.findall(r'^Signer #[0-9]+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$', certificates, re.M)
    if [digest.lower() for digest in digests] != [expected['certificateSha256']]:
        raise ValueError('APK signing certificate does not match the persistent release key')
    return version


def read_badging(apk, build_tools):
    aapt = build_tools / ('aapt.exe' if (build_tools / 'aapt.exe').exists() else 'aapt')
    return subprocess.check_output([str(aapt), 'dump', 'badging', str(apk)], encoding='utf-8')


def check_apk(apk, build_tools):
    expected = json.loads(CERTIFICATES.read_text(encoding='utf-8'))[apk.name]
    badging = read_badging(apk, build_tools)
    certificates = subprocess.check_output(['java', '-jar', str(build_tools / 'lib/apksigner.jar'),
        'verify', '--print-certs', str(apk)], encoding='utf-8')
    version, code = validate_reports(badging, certificates, expected)
    if apk.name == 'miqat.apk' and not 0 < apk.stat().st_size < 25_000_000:
        raise ValueError('Android TV release APK must be under 25 MB')
    print(f'Verified release APK: {apk.name}, {version} ({code}), {expected["certificateSha256"]}')
    return version, code


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('build_tools', type=Path)
    parser.add_argument('apks', nargs='+', type=Path)
    parser.add_argument('--version-for', metavar='PACKAGE_ID',
                        help='Read the packaged version for one APK, including debug builds')
    args = parser.parse_args()
    if args.version_for:
        if len(args.apks) != 1:
            parser.error('--version-for expects one APK')
        version, code = package_version(read_badging(args.apks[0], args.build_tools), args.version_for)
        print(f'{version} (code {code})')
        raise SystemExit(0)
    for apk in args.apks:
        check_apk(apk, args.build_tools)
