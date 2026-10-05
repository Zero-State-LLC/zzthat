#!/usr/bin/env python3
"""Lint the zzThat spec tree. No network. No app build."""

from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
TEXT_SUFFIXES = (".md", ".yml", ".yaml")
REQUIRED = [
    "README.md",
    "CONTRIBUTING.md",
    "AGENTS.md",
    ".specify/memory/constitution.md",
    "specs/README.md",
    "specs/001-zzthat-apps/spec.md",
    "specs/001-zzthat-apps/plan.md",
    "specs/001-zzthat-apps/tasks.md",
    "specs/001-zzthat-apps/decisions.md",
    "specs/001-zzthat-apps/runtime.md",
    "specs/001-zzthat-apps/design.md",
    "specs/001-zzthat-apps/checklists/requirements.md",
    "contracts/zzthis-v1.openapi.yaml",
    "contracts/ZZTHIS-API-PIN",
    ".github/workflows/ci.yml",
    ".github/CODEOWNERS",
    ".github/pull_request_template.md",
]
RUNTIME_HEADINGS = [
    "## Project structure",
    "## Modules",
    "## Navigation",
    "## Screens",
    "## API client",
    "## Auth",
    "## Storage",
    "## Privacy",
    "## Accessibility",
    "## Localization",
    "## Testing",
    "## Release",
]
errors = []


def list_text_paths():
    """Tracked files plus untracked files that .gitignore does not exclude.

    Ignored build output (DerivedData, build/, .gradle/, .zzthis-pin/) is
    never linted. A failed git call is an error, not a silent skip.
    """
    try:
        result = subprocess.run(
            ["git", "ls-files", "-z", "--cached", "--others", "--exclude-standard"],
            cwd=ROOT,
            check=True,
            capture_output=True,
        )
    except (OSError, subprocess.CalledProcessError) as exc:
        errors.append(f"cannot list files with git ls-files: {exc}")
        return []
    names = {name for name in result.stdout.decode("utf-8").split("\0") if name}
    paths = []
    for name in sorted(names):
        path = ROOT / name
        if path.suffix in TEXT_SUFFIXES and path.is_file():
            paths.append(path)
    return paths


for rel in REQUIRED:
    if not (ROOT / rel).is_file():
        errors.append(f"missing {rel}")

text_paths = list_text_paths()
for path in text_paths:
    if ".git" in path.parts:
        continue
    text = path.read_text(encoding="utf-8")
    rel = path.relative_to(ROOT)
    for i, line in enumerate(text.splitlines(), 1):
        if "\u2014" in line or "\u2013" in line:
            errors.append(f"{rel}:{i}: em dash or en dash")
        if "24-hour" in line.lower() or "24 hours" in line.lower():
            errors.append(f"{rel}:{i}: 24-hour cache lifetime")
        if "checksum" in line.lower() and "check word" not in line.lower():
            errors.append(f"{rel}:{i}: checksum without check word")
        if "mock" in line.lower() and "demo" not in line.lower():
            errors.append(f"{rel}:{i}: mock without demo")

runtime = (ROOT / "specs/001-zzthat-apps/runtime.md").read_text(encoding="utf-8")
for heading in RUNTIME_HEADINGS:
    if heading not in runtime:
        errors.append(f"runtime.md missing heading {heading}")

design = (ROOT / "specs/001-zzthat-apps/design.md").read_text(encoding="utf-8")
if "pin-design.sh" not in design or "design/" not in design:
    errors.append("design.md must name pin-design.sh and design/")
if "not on zzthis" in design:
    errors.append("design.md still has the missing-tree TODO")

pin = (ROOT / "contracts/ZZTHIS-API-PIN").read_text(encoding="utf-8").strip()
if len(pin) != 40:
    errors.append("contracts/ZZTHIS-API-PIN must be one full sha")
for rel in (
    "specs/001-zzthat-apps/spec.md",
    "specs/001-zzthat-apps/runtime.md",
    "specs/001-zzthat-apps/design.md",
):
    if pin not in (ROOT / rel).read_text(encoding="utf-8"):
        errors.append(f"{rel} must record the OpenAPI pin {pin}")
openapi = (ROOT / "contracts/zzthis-v1.openapi.yaml").read_text(encoding="utf-8")
if not openapi.startswith("openapi: 3.1.0"):
    errors.append("contracts/zzthis-v1.openapi.yaml must be the pinned 3.1.0 document")
if "max-age=60, stale-while-revalidate=300" not in openapi:
    errors.append("pinned OpenAPI is missing the FR-018 cache header")

if errors:
    print("\n".join(errors), file=sys.stderr)
    sys.exit(1)
print("ok: spec lint")
