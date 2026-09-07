#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT/scripts/_common.sh"
require_one_device

OUT="${1:-$ROOT/battery-snapshot-$(date +%Y%m%d-%H%M%S).txt}"
{
  echo "Captured: $(date -Iseconds)"
  echo "Package: $PACKAGE"
  adb_cmd shell dumpsys batterystats "$PACKAGE"
} > "$OUT"
echo "$OUT"
