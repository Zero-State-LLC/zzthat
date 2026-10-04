#!/usr/bin/env python3
"""Lint the zzThat spec tree. No network. No app build."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
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

for rel in REQUIRED:
    if not (ROOT / rel).is_file():
        errors.append(f"missing {rel}")

text_paths = list(ROOT.rglob("*.md")) + list(ROOT.rglob("*.yml")) + list(ROOT.rglob("*.yaml"))
for path in text_paths:
    if ".git" in path.parts:
        continue
    text = path.read_text(encoding="utf-8")
    rel = path.relative_to(ROOT)
    for i, line in enumerate(text.splitlines(), 1):
        if "\u2014" in line or "\u2013" in line:
            errors.append(f"{rel}:{i}: em dash or en dash")
        if "checksum" in line.lower() and "check word" not in line.lower():
            errors.append(f"{rel}:{i}: checksum without check word")
        if "mock" in line.lower() and "demo" not in line.lower():
            errors.append(f"{rel}:{i}: mock without demo")

runtime = (ROOT / "specs/001-zzthat-apps/runtime.md").read_text(encoding="utf-8")
for heading in RUNTIME_HEADINGS:
    if heading not in runtime:
        errors.append(f"runtime.md missing heading {heading}")

design = (ROOT / "specs/001-zzthat-apps/design.md").read_text(encoding="utf-8")
if "TODO" not in design or "design/" not in design:
    errors.append("design.md must keep the TODO and the design/ path")

openapi = (ROOT / "contracts/zzthis-v1.openapi.yaml").read_text(encoding="utf-8")
if not openapi.startswith("openapi:"):
    errors.append("contracts/zzthis-v1.openapi.yaml must start with openapi:")
if "PROPOSAL" not in openapi:
    errors.append("OpenAPI file must say it is a PROPOSAL, not the live zzthis document")

if errors:
    print("\n".join(errors), file=sys.stderr)
    sys.exit(1)
print("ok: spec lint")
