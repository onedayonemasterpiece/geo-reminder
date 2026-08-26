#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT/scripts/_common.sh"
require_one_device

RULES_FILE="${1:-$ROOT/config/rules.local.json}"
if [[ ! -f "$RULES_FILE" ]]; then
  echo "Rules file not found: $RULES_FILE" >&2
  echo "Copy config/rules.example.json to config/rules.local.json and replace all example coordinates." >&2
  exit 1
fi

python3 "$ROOT/scripts/validate-rules.py" "$RULES_FILE"

if ! adb_cmd shell pm path "$PACKAGE" >/dev/null 2>&1; then
  echo "$PACKAGE is not installed. Run scripts/install-debug.sh first." >&2
  exit 1
fi

remote="/data/local/tmp/geo-reminder-rules-$$.json"
cleanup() {
  adb_cmd shell rm -f "$remote" >/dev/null 2>&1 || true
}
trap cleanup EXIT

adb_cmd push "$RULES_FILE" "$remote" >/dev/null
adb_cmd shell run-as "$PACKAGE" cp "$remote" "files/adb-rules.json"
adb_cmd shell am broadcast \
  -n "$PACKAGE/com.onedayonemasterpiece.georeminder.AdbCommandReceiver" \
  -a "$PACKAGE.action.IMPORT_RULES"

echo
echo "Import requested. Run scripts/diagnose.sh and inspect the phone journal."
