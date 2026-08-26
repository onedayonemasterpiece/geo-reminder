#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT/scripts/_common.sh"
require_one_device

APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -f "$APK" ]]; then
  "$ROOT/scripts/build-debug.sh"
fi

adb_cmd install -r "$APK"
adb_cmd shell am start -n "$PACKAGE/com.onedayonemasterpiece.georeminder.MainActivity"

echo
printf '%s\n' \
  "On the phone:" \
  "1. Grant precise location." \
  "2. Open app settings and choose Location -> Allow all the time." \
  "3. Grant notifications." \
  "4. Keep Samsung battery mode Optimized initially; remove the app from Deep sleeping apps if events are missed."
