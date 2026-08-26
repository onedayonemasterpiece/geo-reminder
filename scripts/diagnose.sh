#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT/scripts/_common.sh"
require_one_device

printf '%s\n' "== App diagnostics =="
adb_cmd shell am broadcast \
  -n "$PACKAGE/com.onedayonemasterpiece.georeminder.AdbCommandReceiver" \
  -a "$PACKAGE.action.DIAGNOSE"

printf '\n%s\n' "== Package state (selected lines) =="
adb_cmd shell dumpsys package "$PACKAGE" | grep -E \
  'ACCESS_(FINE|BACKGROUND)_LOCATION|POST_NOTIFICATIONS|enabled=|stopped=' || true

printf '\n%s\n' "== Recent app logcat =="
adb_cmd logcat -d -t 300 -s GeoReminder:I '*:S' || true
