"""Compare compiled bilingual text with the pre-extraction output baseline."""
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def text_hashes(driver="TextCatalogCheck"):
    with tempfile.TemporaryDirectory() as output:
        out = Path(output)
        stubs = {
            "CalculationParameters.java": "package com.batoulapps.adhan; public class CalculationParameters { public double fajrAngle, ishaAngle; public int ishaInterval; }",
            "Madhab.java": "package com.batoulapps.adhan; public enum Madhab { HANAFI, SHAFI }",
            "PrayerMethod.java": "package tv.miqat.home; class PrayerMethod { String ar, en; com.batoulapps.adhan.CalculationParameters parameters = new com.batoulapps.adhan.CalculationParameters(); com.batoulapps.adhan.CalculationParameters getParameters() { return parameters; } }",
            "Radio.java": "package tv.miqat.home; class Radio { static final int PLAYING=2, CONNECTING=1, RETRYING=3, PAUSED=4; static class Station { String ar, en, whereAr, whereEn; boolean reciter; } }",
        }
        for name, source in stubs.items():
            (out / name).write_text(source, encoding="utf-8")
        subprocess.run([shutil.which("javac"), "-encoding", "UTF-8", "-d", output,
            *(str(out / name) for name in stubs),
            str(ROOT / "platforms/android-tv/src/main/java/tv/miqat/home/Text.java"),
            str(ROOT / ("tests/" + driver + ".java"))], check=True, capture_output=True)
        # Keep the pre-extraction output proof stable when new contract labels are added.
        keys = (json.loads((ROOT / "tests/fixtures/text-output-keys.json").read_text())
                if driver == "TextCatalogCheck" else sorted(json.loads((ROOT / "shared/data/strings.json").read_text(encoding="utf-8"))))
        result = subprocess.run([shutil.which("java"), "-cp", output, "tv.miqat.home." + driver, *keys],
            check=True, capture_output=True, text=True)
        return dict(line.split(":") for line in result.stdout.splitlines())


class TextChecks(unittest.TestCase):
    def test_display_branding_and_installer_translations(self):
        android = ET.parse(ROOT / "platforms/android-tv/src/main/res/values/strings.xml").getroot()
        labels = {item.attrib["name"]: item.text or "" for item in android}
        self.assertEqual(labels["app_name"], "Miqat")
        self.assertIn("Miqat", labels["remote_keys"])
        for value in labels.values():
            self.assertNotRegex(value, r"(?i)\bafnon\b|[أا]فنون|Islamic TV Launcher|واجهة التلفاز الإسلامي")
        ar_labels = {item.attrib["name"]: item.text for item in ET.parse(ROOT / "platforms/android-tv/src/main/res/values-ar/strings.xml").getroot()}
        self.assertEqual(ar_labels["app_name"], "ميقات")
        self.assertIn("ميقات", ar_labels["remote_keys"])
        translations = []
        for folder in ("values", "values-ar"):
            resources = ET.parse(ROOT / f"installer/src/main/res/{folder}/strings.xml").getroot()
            strings = {item.attrib["name"]: item.text or "" for item in resources}
            for key, value in strings.items():
                with self.subTest(folder=folder, key=key):
                    self.assertNotRegex(value, r"(?i)\bafnon\b|[أا]فنون|Islamic TV Launcher|واجهة التلفاز الإسلامي")
            translations.append(strings)
        english, arabic = translations
        self.assertEqual(english["installer_app_name"], "Miqat Installer")
        self.assertEqual(arabic["installer_app_name"], "مثبّت ميقات")
        self.assertEqual(english.keys(), arabic.keys())
        for key in english:
            with self.subTest(key=key):
                self.assertEqual(re.findall(r"%\d+\$[sd]", english[key]),
                                 re.findall(r"%\d+\$[sd]", arabic[key]))
        webos = json.loads((ROOT / "platforms/webos/app/appinfo.json").read_text(encoding="utf-8"))
        self.assertEqual(webos["title"], "Miqat")
        self.assertEqual(webos["id"], "tv.miqat.webos")

    # Miqat deliberately changes only theme/background display labels in this snapshot.
    def test_catalog_preserves_both_languages_digits_and_array_independence(self):
        expected = json.loads((ROOT / "tests/fixtures/text-output-hashes.json").read_text())
        self.assertEqual(text_hashes(), expected)

    def test_formatter_edge_cases_preserve_existing_output(self):
        expected = json.loads((ROOT / "tests/fixtures/text-format-hashes.json").read_text())
        self.assertEqual(text_hashes("TextFormatCheck"), expected)
