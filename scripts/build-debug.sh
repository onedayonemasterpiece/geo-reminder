#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ -z "${ANDROID_HOME:-}" && -z "${ANDROID_SDK_ROOT:-}" && ! -f "$ROOT/local.properties" ]]; then
  echo "ANDROID_HOME/ANDROID_SDK_ROOT or local.properties is required" >&2
  exit 1
fi

if [[ -x "$ROOT/gradlew" ]]; then
  "$ROOT/gradlew" -p "$ROOT" --no-daemon clean testDebugUnitTest lintDebug assembleDebug
else
  "$ROOT/scripts/gradle.sh" --no-daemon clean testDebugUnitTest lintDebug assembleDebug
fi

APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
sha256sum "$APK"
echo "APK: $APK"
