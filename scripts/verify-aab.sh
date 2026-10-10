#!/bin/sh
# Checks a release .aab (tasks.md T093): jarsigner verifies its signature,
# and its signing certificate is the upload certificate, by SHA-256
# fingerprint. Usage: verify-aab.sh <bundle.aab> <expected SHA-256>
# The fingerprint may have colons and any case, as keytool prints it.
set -eu

aab="${1:?usage: verify-aab.sh <bundle.aab> <expected SHA-256>}"
expected="${2:?usage: verify-aab.sh <bundle.aab> <expected SHA-256>}"

normalize() {
  tr -d ': \n' | tr '[:lower:]' '[:upper:]'
}

if ! out="$(jarsigner -verify "$aab" 2>&1)"; then
  printf '%s\n' "$out" >&2
  echo "jarsigner could not verify $aab." >&2
  exit 1
fi
case "$out" in
  *"jar verified."*) ;;
  *)
    printf '%s\n' "$out" >&2
    echo "$aab is not signed." >&2
    exit 1
    ;;
esac

actual="$(keytool -printcert -jarfile "$aab" | sed -n 's/^[[:space:]]*SHA256:[[:space:]]*//p' | head -n 1 | normalize)"
want="$(printf '%s' "$expected" | normalize)"
if [ -z "$actual" ] || [ "$actual" != "$want" ]; then
  echo "The bundle's certificate SHA-256 is ${actual:-missing}, not the upload certificate $want." >&2
  exit 1
fi
echo "Signed with the upload certificate $want."
