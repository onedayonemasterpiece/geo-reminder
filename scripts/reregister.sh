#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT/scripts/_common.sh"
require_one_device
adb_cmd shell am broadcast \
  -n "$PACKAGE/com.onedayonemasterpiece.georeminder.AdbCommandReceiver" \
  -a "$PACKAGE.action.REREGISTER"
