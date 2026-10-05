#!/bin/sh
# Sync the pinned zzThis files into this checkout (design.md, Sync; tasks.md T005).
#
# Usage: scripts/sync-zzthis.sh [--allow-missing] <full-commit-sha>
#
#   1. Fetch the pinned files into .zzthis-pin/ (git-ignored) with zzThis
#      scripts/pin-zzthis.sh at that sha. A commit with no pin-zzthis.sh falls
#      back to zzThis scripts/pin-design.sh plus direct downloads from
#      raw.githubusercontent.com. Either way .zzthis-pin/ mirrors the zzThis
#      paths, and .zzthis-pin/PIN holds the sha.
#   2. Copy each file into place (design.md, Sync table).
#   3. Generate Localizable.xcstrings, InfoPlist.xcstrings, and strings.xml from
#      design/copy.json (runtime.md, Localization), and write the camera purpose
#      into apps/ios/project.yml when that file exists.
#   4. Write the sha to contracts/ZZTHIS-API-PIN and apps/DESIGN-PIN.
#   5. Replace the old sha with the new one in the docs that state the pin.
#
# --allow-missing tolerates the paths that arrive with the zzThis build
# (design/fonts/, NOTICE, packages/zz-core/wordlists/) when the pinned commit
# does not have them yet. Each one is printed, and any old copy of it here is
# removed, so the tree still mirrors the pin. The CI pin check runs without it.
#
# Needs sh, curl, tar, and python3. Run from anywhere; it works on the checkout
# that holds this script.
set -eu

usage() {
  echo "usage: scripts/sync-zzthis.sh [--allow-missing] <full-commit-sha>"
}

allow_missing=0
sha=
for arg in "$@"; do
  case $arg in
    --allow-missing) allow_missing=1 ;;
    -h | --help) usage; exit 0 ;;
    -*) echo "unknown option: $arg" >&2; usage >&2; exit 2 ;;
    *)
      if [ -n "$sha" ]; then usage >&2; exit 2; fi
      sha=$arg
      ;;
  esac
done
if [ -z "$sha" ]; then usage >&2; exit 2; fi
if ! printf '%s' "$sha" | grep -Eq '^[0-9a-f]{40}$'; then
  echo "the sha must be a full 40-character lowercase commit sha" >&2
  exit 2
fi

root=$(cd "$(dirname "$0")/.." && pwd)
cd "$root"

pin_file=contracts/ZZTHIS-API-PIN
if [ ! -f "$pin_file" ]; then
  echo "missing $pin_file" >&2
  exit 1
fi
old_sha=$(tr -d '[:space:]' < "$pin_file")
if ! printf '%s' "$old_sha" | grep -Eq '^[0-9a-f]{40}$'; then
  echo "$pin_file does not hold one full sha" >&2
  exit 1
fi

repo=Zero-State-LLC/zzthis
raw="https://raw.githubusercontent.com/${repo}/${sha}"
api="https://api.github.com/repos/${repo}/contents"
pin_dir=.zzthis-pin

# Destinations (design.md, Sync table).
ios_res=apps/ios/zzThat/Resources
ios_fonts=$ios_res/Fonts
ios_licenses=$ios_res/Licenses
ios_tokens=apps/ios/Packages/ZZDesign/Sources/ZZDesign/Tokens.swift
ios_openapi=apps/ios/Packages/ZZAPI/Sources/ZZAPI/openapi.yaml
ios_project=apps/ios/project.yml
android_tokens=apps/android/core-design/src/main/kotlin/llc/zerostate/zzthis/design/Tokens.kt
android_fonts=apps/android/core-design/src/main/res/font
android_licenses=apps/android/core-design/src/main/assets/licenses
android_mark=apps/android/app/src/main/res/drawable/mark_zz_code.webp
android_strings=apps/android/app/src/main/res/values/strings.xml
contract_openapi=contracts/zzthis-v1.openapi.yaml
contract_vectors=contracts/vectors.json
contract_wordlists=contracts/wordlists

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

# fetch <url> <out>: 0 and <out> written on HTTP 200, 1 on 404, exit otherwise.
fetch() {
  fetch_url=$1
  fetch_out=$2
  fetch_code=$(curl --proto =https --proto-redir =https --silent --show-error --location \
    --retry 3 --retry-delay 2 -o "$fetch_out.part" -w '%{http_code}' \
    "$fetch_url") || {
    rm -f "$fetch_out.part"
    echo "download failed: $fetch_url" >&2
    exit 1
  }
  case $fetch_code in
    200) mv "$fetch_out.part" "$fetch_out"; return 0 ;;
    404) rm -f "$fetch_out.part"; return 1 ;;
    *)
      rm -f "$fetch_out.part"
      echo "HTTP $fetch_code for $fetch_url" >&2
      exit 1
      ;;
  esac
}

# fetch_dir <dir> <suffix>: download every <suffix> file directly in zzThis
# <dir> at the pin into .zzthis-pin/<dir>/. A directory missing at the pin
# downloads nothing. GITHUB_TOKEN, when set, is sent to api.github.com only.
fetch_dir() {
  dir_path=$1
  dir_suffix=$2
  set -- --proto =https --proto-redir =https --silent --show-error --location --retry 3 \
    --retry-delay 2 -H 'Accept: application/vnd.github+json'
  if [ -n "${GITHUB_TOKEN:-}" ]; then
    set -- "$@" -H "Authorization: Bearer ${GITHUB_TOKEN}"
  fi
  dir_code=$(curl "$@" -o "$tmp/listing.json" -w '%{http_code}' \
    "$api/$dir_path?ref=$sha") || {
    echo "listing failed: $dir_path" >&2
    exit 1
  }
  case $dir_code in
    200) ;;
    404) return 0 ;;
    *) echo "HTTP $dir_code listing $dir_path" >&2; exit 1 ;;
  esac
  python3 - "$tmp/listing.json" "$dir_suffix" > "$tmp/names" <<'PY'
import json
import re
import sys

listing = json.load(open(sys.argv[1], encoding="utf-8"))
if not isinstance(listing, list):
    sys.exit("expected a directory listing")
for entry in listing:
    name = entry.get("name", "")
    if entry.get("type") == "file" and name.endswith(sys.argv[2]):
        if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]*", name):
            sys.exit("unsafe file name in listing: " + name)
        print(name)
PY
  mkdir -p "$pin_dir/$dir_path"
  while IFS= read -r name; do
    fetch "$raw/$dir_path/$name" "$pin_dir/$dir_path/$name" || {
      echo "listed but not downloadable: $dir_path/$name" >&2
      exit 1
    }
  done < "$tmp/names"
}

# Step 1: fetch into .zzthis-pin/.
rm -rf "$pin_dir"
if fetch "$raw/scripts/pin-zzthis.sh" "$tmp/pin-zzthis.sh"; then
  echo "step 1: zzThis scripts/pin-zzthis.sh at $sha"
  sh "$tmp/pin-zzthis.sh" "$pin_dir" "$sha"
else
  echo "step 1: $sha has no scripts/pin-zzthis.sh; using scripts/pin-design.sh and direct downloads"
  if ! fetch "$raw/scripts/pin-design.sh" "$tmp/pin-design.sh"; then
    echo "$sha has neither scripts/pin-zzthis.sh nor scripts/pin-design.sh" >&2
    exit 1
  fi
  sh "$tmp/pin-design.sh" "$pin_dir/design" "$sha"
  rm -f "$pin_dir/design/PIN"
  for path in specs/005-v1-api/openapi.yaml specs/003-wordlist-checkword/vectors.json NOTICE; do
    mkdir -p "$pin_dir/$(dirname "$path")"
    fetch "$raw/$path" "$pin_dir/$path" || true
  done
  fetch_dir packages/zz-core/wordlists .txt
  printf '%s\n' "$sha" > "$pin_dir/PIN"
fi
if [ "$(tr -d '[:space:]' < "$pin_dir/PIN" 2>/dev/null || true)" != "$sha" ]; then
  echo "$pin_dir/PIN does not hold $sha" >&2
  exit 1
fi

# Every path the sync reads. Core paths must exist at the pin. Late paths
# arrive with the zzThis build and are tolerated with --allow-missing.
: > "$tmp/missing_core"
: > "$tmp/missing_late"
for path in design/generated/Tokens.swift design/generated/Tokens.kt \
  design/brand/mark-zz-code.webp design/copy.json \
  specs/005-v1-api/openapi.yaml specs/003-wordlist-checkword/vectors.json; do
  [ -f "$pin_dir/$path" ] || echo "$path" >> "$tmp/missing_core"
done
has_files() {
  for f in "$1"/*"$2"; do
    [ -f "$f" ] && return 0
  done
  return 1
}
has_files "$pin_dir/design/fonts" .ttf || echo "design/fonts/*.ttf" >> "$tmp/missing_late"
[ -f "$pin_dir/design/fonts/OFL.txt" ] || echo "design/fonts/OFL.txt" >> "$tmp/missing_late"
[ -f "$pin_dir/NOTICE" ] || echo "NOTICE" >> "$tmp/missing_late"
has_files "$pin_dir/packages/zz-core/wordlists" .txt || echo "packages/zz-core/wordlists/*.txt" >> "$tmp/missing_late"

if [ -s "$tmp/missing_core" ]; then
  echo "missing at $sha (required):" >&2
  sed 's/^/  /' "$tmp/missing_core" >&2
  exit 1
fi
if [ -s "$tmp/missing_late" ]; then
  if [ "$allow_missing" -eq 1 ]; then
    echo "missing at $sha (allowed by --allow-missing):"
    sed 's/^/  /' "$tmp/missing_late"
  else
    echo "missing at $sha:" >&2
    sed 's/^/  /' "$tmp/missing_late" >&2
    echo "These arrive with the zzThis build. Pin a commit that has them, or pass --allow-missing." >&2
    exit 1
  fi
fi

# Generate the catalogs into a staging folder first, so a bad copy.json stops
# the sync before it changes the tree.
stage=$tmp/stage
mkdir -p "$stage"
python3 - "$pin_dir/design/copy.json" "$sha" "$stage" <<'PY'
import json
import re
import sys
from xml.sax.saxutils import escape as xml_escape

copy_path, sha, stage = sys.argv[1:4]
PLACEHOLDER = re.compile(r"\{([^{}]*)\}")
KNOWN = ("provider", "email")
KEY = re.compile(r"[a-z][a-z0-9_]*(\.[a-z0-9_]+)+")


def fail(message):
    sys.exit("copy.json: " + message)


with open(copy_path, encoding="utf-8") as f:
    strings = json.load(f).get("strings")
if not isinstance(strings, dict) or not strings:
    fail("no strings object")

rows = []
android_names = {}
for key, entry in strings.items():
    if not KEY.fullmatch(key):
        fail(f"{key}: key must be lowercase dotted words")
    text = entry.get("text") if isinstance(entry, dict) else None
    if not isinstance(text, str) or not text.strip():
        fail(f"{key}: text must be a non-empty string")
    if text != text.strip() or "  " in text or "\n" in text or "\t" in text:
        fail(f"{key}: text has leading, trailing, repeated, or control whitespace")
    if "%" in text:
        fail(f"{key}: a literal percent sign has no catalog mapping")
    names = PLACEHOLDER.findall(text)
    for name in names:
        if name not in KNOWN:
            fail(f"{key}: unknown placeholder {{{name}}}")
    if len(names) > 1:
        fail(f"{key}: more than one placeholder has no catalog mapping")
    if "{" in PLACEHOLDER.sub("", text) or "}" in PLACEHOLDER.sub("", text):
        fail(f"{key}: stray brace")
    android = key.replace(".", "_")
    if android in android_names:
        fail(f"{key} and {android_names[android]} map to the same Android name")
    android_names[android] = key
    rows.append((key, android, text))


def xcstrings(entries):
    """Xcode's own layout: sorted keys, two-space indent, ' : ' separators."""
    body = {
        "sourceLanguage": "en",
        "strings": {
            key: {
                "extractionState": "manual",
                "localizations": {
                    "en": {"stringUnit": {"state": "translated", "value": value}}
                },
            }
            for key, value in entries
        },
        "version": "1.0",
    }
    return json.dumps(body, ensure_ascii=False, indent=2, sort_keys=True, separators=(",", " : "))


def android_text(text):
    text = PLACEHOLDER.sub("%1$s", text)
    text = text.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"')
    text = xml_escape(text)
    if text[0] in "@?":
        text = "\\" + text
    return text


ios = [(key, PLACEHOLDER.sub("%@", text)) for key, _, text in rows]
camera = strings.get("scan.camera_purpose", {}).get("text")
if not camera:
    fail("scan.camera_purpose is missing")
if PLACEHOLDER.search(camera):
    fail("scan.camera_purpose must not have a placeholder")

with open(f"{stage}/Localizable.xcstrings", "w", encoding="utf-8") as f:
    f.write(xcstrings(ios))
with open(f"{stage}/InfoPlist.xcstrings", "w", encoding="utf-8") as f:
    f.write(xcstrings([("NSCameraUsageDescription", camera)]))
with open(f"{stage}/strings.xml", "w", encoding="utf-8") as f:
    f.write('<?xml version="1.0" encoding="utf-8"?>\n')
    f.write(f"<!-- Generated by scripts/sync-zzthis.sh from zzThis design/copy.json at {sha}. Do not edit. -->\n")
    f.write("<resources>\n")
    for _, android, text in rows:
        f.write(f'    <string name="{android}">{android_text(text)}</string>\n')
    f.write("</resources>\n")
with open(f"{stage}/camera_purpose.txt", "w", encoding="utf-8") as f:
    f.write(camera)
print(f"generated {len(rows)} strings from design/copy.json")
PY
if [ -f "$ios_project" ] &&
  ! grep -Eq '^[[:space:]]*INFOPLIST_KEY_NSCameraUsageDescription:' "$ios_project"; then
  echo "$ios_project must set INFOPLIST_KEY_NSCameraUsageDescription (design.md, Sync)" >&2
  exit 1
fi

# Step 2: copy each file into place.
echo "step 2: copy"
put() {
  mkdir -p "$(dirname "$2")"
  cp "$1" "$2"
  echo "  $2"
}
drop() {
  for f in "$@"; do
    if [ -f "$f" ]; then
      rm -f "$f"
      echo "  removed $f"
    fi
  done
}
put "$pin_dir/design/generated/Tokens.swift" "$ios_tokens"
put "$pin_dir/design/generated/Tokens.kt" "$android_tokens"
put "$pin_dir/design/brand/mark-zz-code.webp" "$android_mark"
put "$pin_dir/specs/005-v1-api/openapi.yaml" "$contract_openapi"
put "$pin_dir/specs/005-v1-api/openapi.yaml" "$ios_openapi"
put "$pin_dir/specs/003-wordlist-checkword/vectors.json" "$contract_vectors"

# Fonts: the folders hold only pinned TTF files, so old ones go first.
drop "$ios_fonts"/*.ttf "$android_fonts"/*.ttf
for font in "$pin_dir"/design/fonts/*.ttf; do
  [ -f "$font" ] || continue
  name=$(basename "$font")
  android_name=$(printf '%s' "$name" | tr 'A-Z-' 'a-z_')
  if ! printf '%s' "$android_name" | grep -Eq '^[a-z][a-z0-9_]*\.ttf$'; then
    echo "font $name has no valid Android resource name ($android_name)" >&2
    exit 1
  fi
  if [ -f "$android_fonts/$android_name" ]; then
    echo "fonts collide on the Android name $android_name" >&2
    exit 1
  fi
  put "$font" "$ios_fonts/$name"
  put "$font" "$android_fonts/$android_name"
done

for license in design/fonts/OFL.txt NOTICE; do
  name=$(basename "$license")
  if [ -f "$pin_dir/$license" ]; then
    put "$pin_dir/$license" "$ios_licenses/$name"
    put "$pin_dir/$license" "$android_licenses/$name"
  else
    drop "$ios_licenses/$name" "$android_licenses/$name"
  fi
done

drop "$contract_wordlists"/*.txt
for list in "$pin_dir"/packages/zz-core/wordlists/*.txt; do
  [ -f "$list" ] || continue
  put "$list" "$contract_wordlists/$(basename "$list")"
done

# Step 3: install the generated catalogs and the camera purpose.
echo "step 3: string catalogs"
put "$stage/Localizable.xcstrings" "$ios_res/Localizable.xcstrings"
put "$stage/InfoPlist.xcstrings" "$ios_res/InfoPlist.xcstrings"
put "$stage/strings.xml" "$android_strings"
if [ -f "$ios_project" ]; then
  python3 - "$ios_project" "$stage/camera_purpose.txt" <<'PY'
import json
import re
import sys

project, purpose_path = sys.argv[1:3]
purpose = open(purpose_path, encoding="utf-8").read()
text = open(project, encoding="utf-8").read()
line = re.compile(r"^([ \t]*INFOPLIST_KEY_NSCameraUsageDescription:)[^\n]*$", re.M)
value = json.dumps(purpose, ensure_ascii=False)
new, count = line.subn(lambda m: m.group(1) + " " + value, text)
if count == 0:
    sys.exit(project + " must set INFOPLIST_KEY_NSCameraUsageDescription (design.md, Sync)")
if new != text:
    open(project, "w", encoding="utf-8").write(new)
print(f"  {project}: INFOPLIST_KEY_NSCameraUsageDescription ({count})")
PY
else
  echo "  skipped $ios_project: not created yet"
fi

# Step 4: the one pin, in both pin files.
echo "step 4: pin $sha"
printf '%s\n' "$sha" > "$pin_file"
printf '%s\n' "$sha" > apps/DESIGN-PIN
echo "  $pin_file"
echo "  apps/DESIGN-PIN"

# Step 5: the docs that state the current pin.
echo "step 5: docs ($old_sha -> $sha)"
python3 - "$old_sha" "$sha" <<'PY'
import datetime
import re
import sys

old, new = sys.argv[1:3]
DOCS = (
    "specs/001-zzthat-apps/spec.md",
    "specs/001-zzthat-apps/runtime.md",
    "specs/001-zzthat-apps/design.md",
    "specs/001-zzthat-apps/plan.md",
    "README.md",
    "specs/README.md",
)
for rel in DOCS:
    text = open(rel, encoding="utf-8").read()
    count = text.count(old)
    if count == 0:
        print(f"  warning: {rel} does not state {old}", file=sys.stderr)
        continue
    new_text = text.replace(old, new)
    if new_text != text:
        open(rel, "w", encoding="utf-8").write(new_text)
    print(f"  {rel} ({count})")

rel = ".specify/memory/constitution.md"
lines = open(rel, encoding="utf-8").read().split("\n")
hits = [i for i, line in enumerate(lines) if line.startswith("Source pin:")]
if len(hits) != 1:
    sys.exit(f"{rel} must have one Source pin line")
i = hits[0]
if old not in lines[i]:
    sys.exit(f"{rel}: the Source pin line does not state {old}")
line = lines[i].replace(old, new)
if new != old:
    today = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d")
    date = re.compile("(`" + re.escape(new) + r"` \()\d{4}-\d{2}-\d{2}(\))")
    line, count = date.subn(lambda m: m.group(1) + today + m.group(2), line, count=1)
    if count == 0:
        sys.exit(f"{rel}: no (YYYY-MM-DD) after the sha on the Source pin line")
if line != lines[i]:
    lines[i] = line
    open(rel, "w", encoding="utf-8").write("\n".join(lines))
print(f"  {rel} (Source pin line)")
PY

echo "synced zzThis $sha"
