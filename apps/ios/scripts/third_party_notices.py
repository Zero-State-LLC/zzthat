#!/usr/bin/env python3
"""Generate the iOS third-party notices at build time (tasks.md T087).

Reads the app's pinned packages from Package.resolved and, for each one, the
license and NOTICE files in its resolved checkout. Writes one plain-text file:
for each package, its name, version, and repository, then its full license
text, then its NOTICE text when it has one (Apache 2.0). The Licenses screen
shows the file as given, after the zzThis NOTICE and OFL.txt.

The build fails, with a message naming the package, when a pinned package has
no checkout or no license file, or when the lock and the packages the build
resolved disagree. Nothing is skipped silently. Standard library only.

Usage:
  third_party_notices.py --resolved PATH --source-packages DIR --out FILE
"""

import argparse
import json
import re
import sys
from pathlib import Path

SEPARATOR = "=" * 64
LICENSE_NAME = re.compile(r"^(licen[cs]e|copying)([.-].*)?$", re.IGNORECASE)
NOTICE_NAME = re.compile(r"^notice([.-].*)?$", re.IGNORECASE)


def fail(message):
    sys.exit(f"error: third-party notices: {message}")


def read_json(path):
    try:
        return json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        fail(f"cannot read {path}: {exc}")


def pins(resolved):
    """Package.resolved v2 and v3 list pins under "pins"."""
    data = read_json(resolved)
    found = data.get("pins")
    if not isinstance(found, list) or not found:
        fail(f"{resolved} lists no pinned packages")
    return found


def resolved_dependencies(source_packages):
    """The packages this build resolved, from SwiftPM's workspace state."""
    state = read_json(Path(source_packages) / "workspace-state.json")
    deps = state.get("object", {}).get("dependencies", [])
    return {
        dep["packageRef"]["identity"]: dep
        for dep in deps
        if dep.get("packageRef", {}).get("kind") == "remoteSourceControl"
    }


def text_of(path):
    try:
        return path.read_text(encoding="utf-8").strip("\n")
    except (OSError, UnicodeDecodeError) as exc:
        fail(f"cannot read {path}: {exc}")


def files_matching(checkout, pattern):
    return sorted(p for p in checkout.iterdir() if p.is_file() and pattern.match(p.name))


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--resolved", required=True)
    parser.add_argument("--source-packages", required=True)
    parser.add_argument("--out", required=True)
    args = parser.parse_args()

    checkouts = Path(args.source_packages) / "checkouts"
    if not checkouts.is_dir():
        fail(f"no resolved checkouts at {checkouts}")
    built = resolved_dependencies(args.source_packages)
    pinned = pins(args.resolved)

    pinned_ids = {pin.get("identity") for pin in pinned}
    unpinned = sorted(set(built) - pinned_ids)
    if unpinned:
        fail(f"{args.resolved} does not pin packages this build resolved: {', '.join(unpinned)}")

    sections = []
    for pin in pinned:
        identity = pin.get("identity")
        location = pin.get("location", "")
        version = pin.get("state", {}).get("version") or pin.get("state", {}).get("revision")
        if not identity or not version:
            fail(f"a pin in {args.resolved} has no identity or version: {pin}")
        dep = built.get(identity)
        if dep is None:
            fail(f"{identity} is pinned but this build did not resolve it; resolve the packages again")
        built_version = dep.get("state", {}).get("checkoutState", {}).get("version")
        if built_version and built_version != pin.get("state", {}).get("version"):
            fail(f"{identity} is pinned at {version} but the build resolved {built_version}")
        checkout = checkouts / dep.get("subpath", "")
        if not dep.get("subpath") or not checkout.is_dir():
            fail(f"no checkout for {identity} at {checkout}")
        licenses = files_matching(checkout, LICENSE_NAME)
        if not licenses:
            fail(f"{identity} {version} has no license file in {checkout}")
        notices = files_matching(checkout, NOTICE_NAME)
        name = dep.get("packageRef", {}).get("name") or identity
        parts = [f"{name} {version}\n{location}"]
        parts += [text_of(path) for path in licenses]
        parts += [text_of(path) for path in notices]
        sections.append((name.lower(), "\n\n".join(parts)))

    sections.sort()
    body = f"\n\n{SEPARATOR}\n\n".join(text for _, text in sections) + "\n"
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(body, encoding="utf-8")
    print(f"third-party notices: {len(sections)} packages -> {out}")


if __name__ == "__main__":
    main()
