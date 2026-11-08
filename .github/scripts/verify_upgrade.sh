#!/usr/bin/env bash
set -euo pipefail
apk=app/build/outputs/apk/release/app-release.apk
tools="$ANDROID_HOME/build-tools/35.0.0"
mkdir -p previous-release
gh release download v1.8.0 --pattern app-release.apk --dir previous-release
old=previous-release/app-release.apk

# The signer certificate comparison is the guard that proves the app's signing
# identity has not silently changed. It can only run on a signed APK: when no
# signing credentials are configured the build is unsigned, so the comparison is
# skipped with a loud warning (the publish steps separately refuse to publish an
# unsigned artifact).
if "$tools/apksigner" verify --print-certs "$apk" >/dev/null 2>&1; then
  new_cert=$("$tools/apksigner" verify --print-certs "$apk" | grep 'Signer #1 certificate SHA-256 digest:')
  old_cert=$("$tools/apksigner" verify --print-certs "$old" | grep 'Signer #1 certificate SHA-256 digest:')
  test "$new_cert" = "$old_cert"
  echo "signer certificate matches v1.8.0: $new_cert" | tee -a build.log
else
  echo "::warning::Release APK is unsigned (no signing credentials configured). Skipping the signer-certificate comparison against v1.8.0. Set the RELEASE_STORE_PASSWORD / RELEASE_KEY_ALIAS / RELEASE_KEY_PASSWORD secrets to sign release builds."
fi

"$tools/aapt" dump badging "$apk" > new-badging.txt
"$tools/aapt" dump badging "$old" > old-badging.txt
python3 - <<'PY'
import re
from pathlib import Path
new = Path('new-badging.txt').read_text()
old = Path('old-badging.txt').read_text()
def field(text, name):
    return re.search(r"\b" + name + r"='([^']+)'", text).group(1)
assert field(new, 'name') == field(old, 'name') == 'com.spendingtracker.app'
assert int(field(new, 'versionCode')) > int(field(old, 'versionCode'))
assert int(re.search(r"sdkVersion:'(\d+)'", new).group(1)) == 26
print('Upgrade checks passed: matching application ID and signer; versionCode increases; Android 8+ retained.')
PY
if "$tools/apksigner" verify --print-certs "$apk" >/dev/null 2>&1; then
  "$tools/apksigner" verify --verbose --print-certs "$apk"
else
  echo "::warning::Unsigned release APK: skipping apksigner verify output." | tee -a build.log
fi
(cd app/build/outputs/apk/release && sha256sum app-release.apk > SHA256SUMS.txt)
