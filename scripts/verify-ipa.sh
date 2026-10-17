#!/bin/sh
# Checks a release .ipa (tasks.md T107) before Danny takes it to App Store
# Connect. The one .app in Payload/ must pass codesign --verify --deep
# --strict, have the Release bundle id (ZQ24), the expected version and
# build, and the privacy manifest. Its embedded profile must be an App
# Store profile of the given team for that bundle id, its signed
# entitlements must match, and it must be signed by that team's
# distribution certificate, not ad hoc. The signer checks come last.
# Usage: verify-ipa.sh <app.ipa> <team id> <version> <build>
# Runs on macOS: it needs unzip, codesign, security, and plutil.
set -eu

usage="usage: verify-ipa.sh <app.ipa> <team id> <version> <build>"
ipa="${1:?$usage}"
team="${2:?$usage}"
version="${3:?$usage}"
build="${4:?$usage}"
bundle_id=com.zer0state.zzthat

fail() {
  echo "$*" >&2
  exit 1
}

[ -f "$ipa" ] || fail "$ipa is not a file."
for tool in unzip codesign security plutil; do
  command -v "$tool" >/dev/null 2>&1 || fail "verify-ipa.sh needs $tool, so it runs on macOS."
done

work="$(mktemp -d "${TMPDIR:-/tmp}/verify-ipa.XXXXXX")"
trap 'rm -rf "$work"' EXIT
unzip -q "$ipa" -d "$work/ipa" || fail "$ipa is not a zip archive."

set -- "$work"/ipa/Payload/*.app
if [ $# -ne 1 ] || [ ! -d "$1" ]; then
  fail "$ipa must hold exactly one Payload/*.app."
fi
app="$1"
name="Payload/$(basename "$app")"

# Prints one value of a plist, or nothing when the key is missing.
value() {
  plutil -extract "$2" raw -o - "$1" 2>/dev/null || true
}

if ! out="$(codesign --verify --deep --strict --verbose=2 "$app" 2>&1)"; then
  printf '%s\n' "$out" >&2
  fail "codesign could not verify $name."
fi

info="$app/Info.plist"
[ -f "$info" ] || fail "$name has no Info.plist."
actual="$(value "$info" CFBundleIdentifier)"
[ "$actual" = "$bundle_id" ] || fail "The bundle id is ${actual:-missing}, not $bundle_id."
actual="$(value "$info" CFBundleShortVersionString)"
[ "$actual" = "$version" ] || fail "The version is ${actual:-missing}, not $version."
actual="$(value "$info" CFBundleVersion)"
[ "$actual" = "$build" ] || fail "The build is ${actual:-missing}, not $build."

manifest="$app/PrivacyInfo.xcprivacy"
[ -f "$manifest" ] || fail "$name has no PrivacyInfo.xcprivacy."
plutil -lint -s "$manifest" >/dev/null || fail "$name/PrivacyInfo.xcprivacy is not a valid plist."

profile="$app/embedded.mobileprovision"
[ -f "$profile" ] || fail "$name has no embedded.mobileprovision."
security cms -D -i "$profile" > "$work/profile.plist" 2>/dev/null \
  || fail "security cms could not decode $name/embedded.mobileprovision."
actual="$(value "$work/profile.plist" TeamIdentifier.0)"
[ "$actual" = "$team" ] || fail "The profile's team is ${actual:-missing}, not $team."
actual="$(value "$work/profile.plist" Entitlements.application-identifier)"
[ "$actual" = "$team.$bundle_id" ] || fail "The profile is for ${actual:-no app id}, not $team.$bundle_id."
if plutil -extract ProvisionedDevices xml1 -o /dev/null "$work/profile.plist" 2>/dev/null; then
  fail "The profile lists devices, so it is a development or ad hoc profile, not an App Store profile."
fi
[ "$(value "$work/profile.plist" ProvisionsAllDevices)" != true ] \
  || fail "The profile provisions all devices, so it is an enterprise profile, not an App Store profile."
[ "$(value "$work/profile.plist" Entitlements.get-task-allow)" != true ] \
  || fail "The profile allows get-task-allow, so it is not an App Store profile."

codesign -d --entitlements - --xml "$app" > "$work/entitlements.plist" 2>/dev/null \
  || fail "codesign could not read the signed entitlements of $name."
actual="$(value "$work/entitlements.plist" application-identifier)"
[ "$actual" = "$team.$bundle_id" ] || fail "The signed application-identifier is ${actual:-missing}, not $team.$bundle_id."
[ "$(value "$work/entitlements.plist" get-task-allow)" != true ] \
  || fail "The signed entitlements allow get-task-allow, so this is not a distribution build."

details="$(codesign -d --verbose=2 "$app" 2>&1)"
case "$details" in
  *"Signature=adhoc"*) fail "$name is signed ad hoc, not by a distribution certificate." ;;
esac
signer_team="$(printf '%s\n' "$details" | sed -n 's/^TeamIdentifier=//p' | head -n 1)"
[ "$signer_team" = "$team" ] || fail "The signing team is ${signer_team:-missing}, not $team."
leaf="$(printf '%s\n' "$details" | sed -n 's/^Authority=//p' | head -n 1)"
root="$(printf '%s\n' "$details" | sed -n 's/^Authority=//p' | tail -n 1)"
case "$leaf" in
  "Apple Distribution: "*"($team)" | "iPhone Distribution: "*"($team)") ;;
  *) fail "The signing certificate is ${leaf:-missing}, not a distribution certificate of team $team." ;;
esac
[ "$root" = "Apple Root CA" ] || fail "The signing chain ends at ${root:-nothing}, not Apple Root CA."

echo "Verified $name: $bundle_id $version ($build), team $team, signed by $leaf, App Store profile, privacy manifest present."
