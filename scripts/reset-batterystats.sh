#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT/scripts/_common.sh"
require_one_device
adb_cmd shell dumpsys batterystats --reset
echo "Battery statistics reset. Record the start time and use battery-snapshot.sh later."
