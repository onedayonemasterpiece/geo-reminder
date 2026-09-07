#!/usr/bin/env bash
set -euo pipefail

PACKAGE="${PACKAGE:-com.onedayonemasterpiece.georeminder.debug}"
ADB="${ADB:-adb}"

adb_cmd() {
  if [[ -n "${DEVICE_SERIAL:-}" ]]; then
    "$ADB" -s "$DEVICE_SERIAL" "$@"
  else
    "$ADB" "$@"
  fi
}

require_one_device() {
  command -v "$ADB" >/dev/null 2>&1 || {
    echo "adb is not available in PATH" >&2
    exit 1
  }

  mapfile -t devices < <("$ADB" devices | awk 'NR > 1 && $2 == "device" {print $1}')
  if [[ -n "${DEVICE_SERIAL:-}" ]]; then
    if ! printf '%s\n' "${devices[@]}" | grep -Fxq "$DEVICE_SERIAL"; then
      echo "DEVICE_SERIAL=$DEVICE_SERIAL is not an authorized connected device" >&2
      exit 1
    fi
    return
  fi
  if [[ ${#devices[@]} -ne 1 ]]; then
    echo "Expected exactly one authorized ADB device, found ${#devices[@]}. Set DEVICE_SERIAL explicitly." >&2
    "$ADB" devices -l >&2
    exit 1
  fi
}
