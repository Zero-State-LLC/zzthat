#!/bin/sh
# The Android qualification harness (tasks.md T027): runs the shipped ML Kit
# reader over a folder of photos on a connected device or emulator, through
# app/src/androidTest/.../qual/QualHarnessTest.kt. Photos go only to the demo
# debug app's private files folder on that device (staged in /data/local/tmp
# on the way), and both copies are deleted when the run ends.
#
#   scripts/qual-android.sh quick DIR truth.csv [LIST]
#   scripts/qual-android.sh run   DIR manifest.json candidate-bundle.json device-matrix.json ENTRY OUT [LIST]
#
# LIST is the word list the codes come from (default fixture-7). quick
# prints one row per photo and a summary. run writes the ZZ-OCR-QUAL-001
# adapter-results.json to OUT for the zzThis CLI.
set -eu
cd "$(dirname "$0")/../apps/android"
PKG=com.zer0state.zzthat.demo
STAGE=/data/local/tmp/zzqual
APP=files/zzqual
mode=${1:?quick or run}
dir=${2:?photo folder}
adb get-state >/dev/null

./gradlew -q :app:installDemoDebug :app:installDemoDebugAndroidTest
cleanup() { adb shell rm -rf "$STAGE"; adb shell run-as $PKG rm -rf "$APP"; }
cleanup
trap cleanup EXIT
adb shell mkdir -p "$STAGE/images"
for f in "$dir"/*; do
  case "$(echo "$f" | tr 'A-Z' 'a-z')" in
    *.jpg|*.jpeg|*.png|*.heic|*.heif|*.tif|*.tiff) adb push -q "$f" "$STAGE/images/" ;;
  esac
done

# Files adb pushes belong to the shell user; the app copies them into its own
# private folder (debug builds allow run-as).
stage_in() {
  adb shell chmod -R a+rX "$STAGE"
  adb shell run-as $PKG mkdir -p "$APP"
  adb shell run-as $PKG cp -r "$STAGE/." "$APP/"
}

instrument() {
  log=$(adb shell am instrument -w -e class com.zer0state.zzthat.qual.QualHarnessTest "$@" \
    $PKG.test/androidx.test.runner.AndroidJUnitRunner)
  case "$log" in *"OK ("*) ;; *) echo "$log" >&2; exit 1 ;; esac
}

case "$mode" in
quick)
  adb push -q "${3:?truth.csv}" "$STAGE/truth.csv"
  stage_in
  instrument -e zzqual quick -e zzqual.list "${4:-fixture-7}"
  adb shell run-as $PKG cat "$APP/rows.csv"
  echo
  adb shell run-as $PKG cat "$APP/summary.txt"
  ;;
run)
  manifest=${3:?manifest.json}; bundle=${4:?candidate-bundle.json}; matrix=${5:?device-matrix.json}
  entry=${6:?device matrix entry}; out=${7:?output path}
  fields=$(python3 -c 'import json,sys
m=json.load(open(sys.argv[1]))
e=[e for e in m["entries"] if e["device_matrix_entry_id"]==sys.argv[2]]
assert e, "no device matrix entry "+sys.argv[2]
print(e[0]["os_version"]+"\t"+e[0]["device_class"])' "$matrix" "$entry")
  os_version=$(printf '%s' "$fields" | cut -f1); device_class=$(printf '%s' "$fields" | cut -f2)
  adb push -q "$manifest" "$STAGE/manifest.json"
  stage_in
  instrument -e zzqual run -e zzqual.list "${8:-fixture-7}" -e zzqual.entry "$entry" \
    -e zzqual.os "$os_version" -e zzqual.device "$device_class"
  adb shell run-as $PKG cat "$APP/results.json" > "$out.results"
  # The top-level fields: the raw-bytes manifest hash, and the RFC 8785 hash
  # of the bundle (objects, arrays, strings, and integers only).
  python3 - "$manifest" "$bundle" "$out.results" "$out" <<'PY'
import hashlib, json, sys
manifest, bundle_path, results, out = sys.argv[1:]
bundle = json.load(open(bundle_path))
jcs = json.dumps(bundle, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode()
adapter = dict(bundle["adapter"], platform=bundle["platform"], device_matrix_sha256=bundle["device_matrix_sha256"])
doc = {
    "schema_version": 1,
    "qualification_id": "ZZ-OCR-QUAL-001",
    "manifest_sha256": hashlib.sha256(open(manifest, "rb").read()).hexdigest(),
    "candidate_id": bundle["candidate_id"],
    "candidate_bundle_sha256": hashlib.sha256(jcs).hexdigest(),
    "adapter": adapter,
    "results": json.load(open(results)),
}
json.dump(doc, open(out, "w"), indent=2, sort_keys=True, ensure_ascii=False)
print(f"{len(doc['results'])} samples written to {out}")
PY
  rm -f "$out.results"
  ;;
*) echo "usage: qual-android.sh quick|run ..." >&2; exit 2 ;;
esac
