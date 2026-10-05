#!/usr/bin/env python3
"""String check (tasks.md T082, spec.md FR-030 and SC-7). No network.

Every generated catalog, InfoPlist.xcstrings included, must hold no em dash,
must say "demo" and never "mock", and must say "check word" and never
"checksum". The two app catalogs must also contain the words demo and check
word, so the demo badge and the check word label are present. The sync script
writes these files from zzThis design/copy.json; the pin check proves they
match it.
"""

from pathlib import Path
import json
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
LOCALIZABLE = "apps/ios/zzThat/Resources/Localizable.xcstrings"
INFOPLIST = "apps/ios/zzThat/Resources/InfoPlist.xcstrings"
ANDROID = "apps/android/app/src/main/res/values/strings.xml"
FORBIDDEN = (
    (re.compile("—"), "em dash"),
    (re.compile(r"\bmock", re.I), "says mock, not demo"),
    (re.compile(r"checksum", re.I), "says checksum, not check word"),
)
REQUIRED_TERMS = (
    (re.compile(r"\bdemo\b", re.I), "demo"),
    (re.compile(r"\bcheck word\b", re.I), "check word"),
)
errors = []


def xcstrings_values(rel):
    data = json.loads((ROOT / rel).read_text(encoding="utf-8"))
    strings = data.get("strings")
    if not isinstance(strings, dict) or not strings:
        errors.append(f"{rel}: no strings")
        return []
    values = []

    def walk(key, node):
        if isinstance(node, dict):
            unit = node.get("stringUnit")
            if isinstance(unit, dict) and isinstance(unit.get("value"), str):
                values.append((key, unit["value"]))
            for child in node.values():
                if child is not unit:
                    walk(key, child)
        elif isinstance(node, list):
            for child in node:
                walk(key, child)

    for key, entry in strings.items():
        before = len(values)
        walk(key, entry)
        if len(values) == before:
            errors.append(f"{rel}: {key} has no value")
    return values


def android_values(rel):
    root = ET.parse(ROOT / rel).getroot()
    values = []
    for node in root.iter():
        if node.tag in ("string", "item"):
            name = node.get("name") or (node.tag + " in a list")
            values.append((name, "".join(node.itertext())))
    if not values:
        errors.append(f"{rel}: no strings")
    return values


catalogs = {}
for rel, reader in (
    (LOCALIZABLE, xcstrings_values),
    (INFOPLIST, xcstrings_values),
    (ANDROID, android_values),
):
    if not (ROOT / rel).is_file():
        errors.append(f"missing {rel}; run scripts/sync-zzthis.sh at the pin")
        continue
    try:
        catalogs[rel] = reader(rel)
    except (ValueError, ET.ParseError) as exc:
        errors.append(f"{rel}: cannot parse: {exc}")

for rel, values in catalogs.items():
    for key, value in values:
        for pattern, message in FORBIDDEN:
            if pattern.search(value):
                errors.append(f"{rel}: {key}: {message}")

for rel in (LOCALIZABLE, ANDROID):
    text = " ".join(value for _, value in catalogs.get(rel, []))
    if rel not in catalogs:
        continue
    for pattern, term in REQUIRED_TERMS:
        if not pattern.search(text):
            errors.append(f"{rel}: no string says {term}")

infoplist = dict(catalogs.get(INFOPLIST, []))
if INFOPLIST in catalogs and not infoplist.get("NSCameraUsageDescription"):
    errors.append(f"{INFOPLIST}: no NSCameraUsageDescription")

if errors:
    print("\n".join(errors), file=sys.stderr)
    sys.exit(1)
count = sum(len(values) for values in catalogs.values())
print(f"ok: string check ({count} strings in {len(catalogs)} catalogs)")
