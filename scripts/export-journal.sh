#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT/scripts/_common.sh"
require_one_device

OUT="${1:-$ROOT/geo-reminder-journal-$(date +%Y%m%d-%H%M%S).jsonl}"
adb_cmd shell am broadcast \
  -n "$PACKAGE/com.onedayonemasterpiece.georeminder.AdbCommandReceiver" \
  -a "$PACKAGE.action.EXPORT_JOURNAL" >/dev/null
adb_cmd exec-out run-as "$PACKAGE" cat files/adb-journal.jsonl > "$OUT"
python3 - "$OUT" <<'PY'
import json, pathlib, sys
p = pathlib.Path(sys.argv[1])
lines = p.read_text(encoding="utf-8").splitlines()
for i, line in enumerate(lines, 1):
    try:
        json.loads(line)
    except json.JSONDecodeError as exc:
        raise SystemExit(f"invalid JSONL line {i}: {exc}")
print(f"Exported {len(lines)} JSONL records to {p}")
PY
