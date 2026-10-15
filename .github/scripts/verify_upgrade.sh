#!/usr/bin/env bash
set -euo pipefail
apk=app/build/outputs/apk/release/app-release.apk
tools="$ANDROID_HOME/build-tools/35.0.0"
mkdir -p previous-release
gh release download v1.8.0 --pattern app-release.apk --dir previous-release
old=previous-release/app-release.apk
new_cert=$("$tools/apksigner" verify --print-certs "$apk" | grep 'Signer #1 certificate SHA-256 digest:')
old_cert=$("$tools/apksigner" verify --print-certs "$old" | grep 'Signer #1 certificate SHA-256 digest:')
test "$new_cert" = "$old_cert"
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
"$tools/apksigner" verify --verbose --print-certs "$apk"
(cd app/build/outputs/apk/release && sha256sum app-release.apk > SHA256SUMS.txt)
