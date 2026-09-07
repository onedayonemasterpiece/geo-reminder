#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT/scripts/_common.sh"
require_one_device

RULE_ID="${1:-}"
args=(
  shell am broadcast
  -n "$PACKAGE/com.onedayonemasterpiece.georeminder.AdbCommandReceiver"
  -a "$PACKAGE.action.TEST_NOTIFICATION"
)
if [[ -n "$RULE_ID" ]]; then
  args+=(--es rule_id "$RULE_ID")
fi
adb_cmd "${args[@]}"
