"""Release publication must reject debug builds, foreign packages and wrong keys."""
import unittest

from tools.check_android_release import validate_reports


class ReleaseSigningChecks(unittest.TestCase):
    def test_release_requires_expected_package_and_single_persistent_signer(self):
        expected = {'applicationId': 'tv.miqat.example', 'certificateSha256': 'a' * 64}
        badging = "package: name='tv.miqat.example' versionCode='2' versionName='1.0'\n"
        certificates = 'Signer #1 certificate SHA-256 digest: ' + 'a' * 64 + '\n'
        self.assertEqual(validate_reports(badging, certificates, expected), ('1.0', 2))
        cases = [
            (badging + 'application-debuggable\n', certificates),
            (badging.replace('tv.miqat.example', 'tv.miqat.other'), certificates),
            (badging.replace("versionCode='2'", "versionCode='0'"), certificates),
            (badging, certificates.replace('a' * 64, 'b' * 64)),
            (badging, ''),
            (badging, certificates + certificates.replace('#1', '#2')),
            ('', certificates),
        ]
        for report, signature in cases:
            with self.subTest(report=report, signature=signature), self.assertRaises(ValueError):
                validate_reports(report, signature, expected)
