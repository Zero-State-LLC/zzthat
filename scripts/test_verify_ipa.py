"""Tests for verify-ipa.sh (tasks.md T107).

python3 -m unittest discover -s scripts -p "test_*.py" -v

The argument and missing-file tests run anywhere. The rest need macOS
(codesign, security, plutil) and openssl, and are skipped elsewhere with a
reason. They build a made-up .ipa: a copy of /usr/bin/true as the app, the
real privacy manifest, an App Store style profile signed by a throwaway key
(security cms -D decodes it without trusting the signer), and an ad hoc
signature with the Release entitlements. No Apple certificate is involved,
so a good fixture passes every check up to the signer checks and fails
there, on the ad hoc signature. Each other test breaks one thing and expects
that check's message. A full pass needs Danny's distribution certificate,
which only the release-ios job has.
"""

import os
import plistlib
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts" / "verify-ipa.sh"
PRIVACY = ROOT / "apps" / "ios" / "zzThat" / "Resources" / "PrivacyInfo.xcprivacy"
BUNDLE_ID = "com.zer0state.zzthat"
TEAM = "ABCDE12345"
VERSION = "0.1.0"
BUILD = "42"

ON_MAC = sys.platform == "darwin" and all(
    shutil.which(tool) for tool in ("codesign", "security", "plutil", "openssl", "zip", "unzip")
)


def run(*args):
    return subprocess.run(["sh", str(SCRIPT), *map(str, args)], capture_output=True, text=True)


class Arguments(unittest.TestCase):
    def test_shell_syntax(self):
        result = subprocess.run(["sh", "-n", str(SCRIPT)], capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_no_arguments(self):
        result = run()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("usage: verify-ipa.sh", result.stderr)

    def test_missing_build_argument(self):
        result = run("app.ipa", TEAM, VERSION)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("usage: verify-ipa.sh", result.stderr)

    def test_missing_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            result = run(Path(tmp) / "missing.ipa", TEAM, VERSION, BUILD)
        self.assertEqual(result.returncode, 1)
        self.assertIn("is not a file", result.stderr)


@unittest.skipUnless(ON_MAC, "needs macOS codesign, security, plutil, and openssl")
class Fixture(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.tmp, ignore_errors=True)
        self.key = self.tmp / "key.pem"
        self.cert = self.tmp / "cert.pem"
        self.check([
            "openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "1",
            "-subj", "/CN=verify-ipa test profile signer",
            "-keyout", str(self.key), "-out", str(self.cert),
        ])

    def check(self, args, cwd=None):
        subprocess.run(args, cwd=cwd, check=True, capture_output=True)

    def write_plist(self, path, data):
        path.write_bytes(plistlib.dumps(data))

    def build(self, bundle_id=BUNDLE_ID, version=VERSION, build=BUILD, privacy=True,
              profile_team=TEAM, profile_extra=None, signed_app_id=None, tamper=False):
        stage = self.tmp / "stage"
        shutil.rmtree(stage, ignore_errors=True)
        app = stage / "Payload" / "zzThat.app"
        app.mkdir(parents=True)
        shutil.copy("/usr/bin/true", app / "zzThat")
        self.write_plist(app / "Info.plist", {
            "CFBundleIdentifier": bundle_id,
            "CFBundleExecutable": "zzThat",
            "CFBundleShortVersionString": version,
            "CFBundleVersion": build,
        })
        if privacy:
            shutil.copy(PRIVACY, app / "PrivacyInfo.xcprivacy")
        profile = {
            "Name": "zzThat App Store",
            "UUID": "00000000-0000-4000-8000-000000000000",
            "TeamIdentifier": [profile_team],
            "Entitlements": {
                "application-identifier": f"{profile_team}.{BUNDLE_ID}",
                "com.apple.developer.team-identifier": profile_team,
                "get-task-allow": False,
            },
        }
        profile.update(profile_extra or {})
        self.write_plist(self.tmp / "profile.plist", profile)
        self.check([
            "openssl", "smime", "-sign", "-binary", "-nodetach", "-outform", "der",
            "-signer", str(self.cert), "-inkey", str(self.key),
            "-in", str(self.tmp / "profile.plist"), "-out", str(app / "embedded.mobileprovision"),
        ])
        self.write_plist(self.tmp / "entitlements.plist", {
            "application-identifier": signed_app_id or f"{TEAM}.{BUNDLE_ID}",
            "com.apple.developer.team-identifier": TEAM,
        })
        self.check(["codesign", "--force", "--sign", "-",
                    "--entitlements", str(self.tmp / "entitlements.plist"), str(app)])
        if tamper:
            (app / "added-after-signing.txt").write_text("changed\n")
        ipa = self.tmp / "zzThat.ipa"
        ipa.unlink(missing_ok=True)
        self.check(["zip", "-qry", str(ipa), "Payload"], cwd=stage)
        return ipa

    def assert_fails(self, ipa, message, team=TEAM, version=VERSION, build=BUILD):
        result = run(ipa, team, version, build)
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertIn(message, result.stderr)

    def test_good_fixture_stops_only_at_the_ad_hoc_signature(self):
        self.assert_fails(self.build(), "is signed ad hoc, not by a distribution certificate")

    def test_not_a_zip(self):
        ipa = self.tmp / "text.ipa"
        ipa.write_text("not a zip\n")
        self.assert_fails(ipa, "is not a zip archive")

    def test_no_app_in_payload(self):
        (self.tmp / "Payload").mkdir()
        (self.tmp / "Payload" / "readme.txt").write_text("no app\n")
        ipa = self.tmp / "empty.ipa"
        self.check(["zip", "-qry", str(ipa), "Payload"], cwd=self.tmp)
        self.assert_fails(ipa, "must hold exactly one Payload/*.app")

    def test_changed_after_signing(self):
        self.assert_fails(self.build(tamper=True), "codesign could not verify")

    def test_wrong_bundle_id(self):
        self.assert_fails(self.build(bundle_id="com.zer0state.zzthat.demo"), "The bundle id is com.zer0state.zzthat.demo")

    def test_wrong_version(self):
        self.assert_fails(self.build(), "The version is 0.1.0, not 1.0.0", version="1.0.0")

    def test_wrong_build(self):
        self.assert_fails(self.build(), "The build is 42, not 43", build="43")

    def test_missing_privacy_manifest(self):
        self.assert_fails(self.build(privacy=False), "has no PrivacyInfo.xcprivacy")

    def test_profile_of_another_team(self):
        self.assert_fails(self.build(profile_team="ZZZZZ99999"), "The profile's team is ZZZZZ99999")

    def test_expected_team_differs(self):
        self.assert_fails(self.build(), "The profile's team is ABCDE12345, not QQQQQ11111", team="QQQQQ11111")

    def test_development_profile(self):
        ipa = self.build(profile_extra={"ProvisionedDevices": ["00008110-000000000000001E"]})
        self.assert_fails(ipa, "not an App Store profile")

    def test_enterprise_profile(self):
        self.assert_fails(self.build(profile_extra={"ProvisionsAllDevices": True}), "enterprise profile")

    def test_signed_entitlements_of_another_app(self):
        ipa = self.build(signed_app_id=f"{TEAM}.com.zer0state.zzthat.local")
        self.assert_fails(ipa, "The signed application-identifier is ABCDE12345.com.zer0state.zzthat.local")


if __name__ == "__main__":
    unittest.main()
