#!/usr/bin/env python3
"""Manifest check (tasks.md T075, runtime.md Privacy). No network.

The apps are analytics-free: no analytics SDK, no advertising identifier, and
no crash reporter that uploads. This reads every package manifest, lock file,
and Android manifest in apps/, and the Apple privacy manifest, and fails on a
known analytics, advertising, attribution, or crash-upload dependency. It also
fails when the manifests the build must have are missing, so it never passes on
an empty tree.
"""

from pathlib import Path
import plistlib
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
REQUIRED = (
    "apps/ios/project.yml",
    "apps/android/settings.gradle.kts",
    "apps/android/gradle/libs.versions.toml",
)
MANIFEST_NAMES = (
    "project.yml",
    "Package.swift",
    "Package.resolved",
    "libs.versions.toml",
    "settings.gradle.kts",
    "build.gradle.kts",
    "gradle.lockfile",
    "AndroidManifest.xml",
)
SOURCE_SUFFIXES = (".swift", ".kt", ".kts")
DENY = (
    r"firebase",
    r"crashlytics",
    r"com\.google\.gms\.google-services",
    r"google-services",
    r"play-services-(ads|analytics|measurement|tagmanager)",
    r"permission\.AD_ID",
    r"google-?analytics",
    r"googletagmanager",
    r"\bsentry",
    r"bugsnag",
    r"instabug",
    r"embrace",
    r"datadog",
    r"new-?relic",
    r"appcenter",
    r"rollbar",
    r"raygun",
    r"appdynamics",
    r"dynatrace",
    r"mixpanel",
    r"amplitude",
    r"segmentio",
    r"com\.segment",
    r"analytics-(swift|kotlin|android|ios)",
    r"appsflyer",
    r"com\.adjust",
    r"adjust/ios_sdk",
    r"io\.branch",
    r"branch-sdk",
    r"flurry",
    r"heapanalytics",
    r"posthog",
    r"countly",
    r"matomo",
    r"piwik",
    r"smartlook",
    r"uxcam",
    r"fullstory",
    r"logrocket",
    r"kochava",
    r"onesignal",
    r"braze",
    r"appboy",
    r"leanplum",
    r"clevertap",
    r"localytics",
    r"facebook",
    r"fbsdk",
    r"\bAdSupport\b",
    r"\bAppTrackingTransparency\b",
    r"\bAdServices\b",
)
AD_ID_SOURCE = (
    r"ASIdentifierManager",
    r"advertisingIdentifier",
    r"AdvertisingIdClient",
    r"ATTrackingManager",
    r"\bimport\s+(AdSupport|AppTrackingTransparency)\b",
)
SKIP_PARTS = {"build", ".build", ".gradle", ".kotlin", "DerivedData", ".swiftpm", ".zzthis-pin"}
errors = []


def tracked_and_new():
    """Tracked files plus untracked files that .gitignore does not exclude."""
    try:
        result = subprocess.run(
            ["git", "ls-files", "-z", "--cached", "--others", "--exclude-standard", "--", "apps"],
            cwd=ROOT,
            check=True,
            capture_output=True,
        )
    except (OSError, subprocess.CalledProcessError) as exc:
        sys.exit(f"cannot list files with git ls-files: {exc}")
    names = sorted({n for n in result.stdout.decode("utf-8").split("\0") if n})
    return [ROOT / n for n in names if (ROOT / n).is_file() and not SKIP_PARTS.intersection(Path(n).parts)]


for rel in REQUIRED:
    if not (ROOT / rel).is_file():
        errors.append(f"missing {rel}")

paths = tracked_and_new()
manifests = [p for p in paths if p.name in MANIFEST_NAMES]
sources = [p for p in paths if p.suffix in SOURCE_SUFFIXES and p.name not in MANIFEST_NAMES]
privacy = [p for p in paths if p.suffix == ".xcprivacy"]

deny = re.compile("|".join(DENY), re.I)
for path in manifests:
    rel = path.relative_to(ROOT)
    for i, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        match = deny.search(line)
        if match:
            errors.append(f"{rel}:{i}: analytics, advertising, or crash-upload dependency ({match.group(0)})")

ad_id = re.compile("|".join(AD_ID_SOURCE))
for path in sources:
    rel = path.relative_to(ROOT)
    for i, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        match = ad_id.search(line)
        if match:
            errors.append(f"{rel}:{i}: advertising identifier ({match.group(0)})")

if not privacy:
    errors.append("missing the iOS privacy manifest (a .xcprivacy file under apps/ios)")
for path in privacy:
    rel = path.relative_to(ROOT)
    try:
        data = plistlib.loads(path.read_bytes())
    except Exception as exc:  # plistlib raises several types for bad input
        errors.append(f"{rel}: cannot parse: {exc}")
        continue
    if data.get("NSPrivacyTracking") is not False:
        errors.append(f"{rel}: NSPrivacyTracking must be false")
    if data.get("NSPrivacyTrackingDomains"):
        errors.append(f"{rel}: NSPrivacyTrackingDomains must be empty")

if errors:
    print("\n".join(errors), file=sys.stderr)
    sys.exit(1)
print(f"ok: manifest check ({len(manifests)} manifests, {len(privacy)} privacy manifests, {len(sources)} sources)")
