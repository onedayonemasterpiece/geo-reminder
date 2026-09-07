#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT/scripts/_common.sh"

REPOSITORY="${GEO_REMINDER_REPOSITORY:-onedayonemasterpiece/geo-reminder}"
DOWNLOAD_ROOT="${GEO_REMINDER_APK_DIR:-$ROOT/.local/apk}"
APK="${1:-}"
RELEASE_TAG=""

hash_file() {
  local path="$1"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$path" | awk '{print tolower($1)}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$path" | awk '{print tolower($1)}'
  else
    echo "Neither sha256sum nor shasum is available" >&2
    exit 1
  fi
}

if [[ -z "$APK" ]]; then
  command -v gh >/dev/null 2>&1 || {
    echo "GitHub CLI (gh) is required to download the verified APK" >&2
    exit 1
  }

  RELEASE_TAG="$(
    gh api "repos/$REPOSITORY/releases?per_page=30" \
      --jq '[.[] | select(.draft == false and .prerelease == true and (.tag_name | startswith("debug-")))] | sort_by(.published_at) | reverse | .[0].tag_name // empty'
  )"
  if [[ -z "$RELEASE_TAG" ]]; then
    echo "No published debug prerelease found for $REPOSITORY" >&2
    exit 1
  fi

  DEST="$DOWNLOAD_ROOT/$RELEASE_TAG"
  rm -rf "$DEST"
  mkdir -p "$DEST"
  gh release download "$RELEASE_TAG" \
    --repo "$REPOSITORY" \
    --pattern "geo-reminder-debug.apk" \
    --pattern "geo-reminder-debug.apk.sha256" \
    --pattern "build-info.json" \
    --dir "$DEST"
  APK="$DEST/geo-reminder-debug.apk"
  CHECKSUM="$DEST/geo-reminder-debug.apk.sha256"
else
  APK="$(cd "$(dirname "$APK")" && pwd)/$(basename "$APK")"
  CHECKSUM="$APK.sha256"
fi

[[ -f "$APK" ]] || {
  echo "APK not found: $APK" >&2
  exit 1
}
[[ -f "$CHECKSUM" ]] || {
  echo "Checksum file not found: $CHECKSUM" >&2
  exit 1
}

EXPECTED_SHA="$(awk 'NR == 1 {print tolower($1)}' "$CHECKSUM")"
ACTUAL_SHA="$(hash_file "$APK")"
if [[ -z "$EXPECTED_SHA" || "$EXPECTED_SHA" != "$ACTUAL_SHA" ]]; then
  echo "APK checksum mismatch" >&2
  echo "expected: $EXPECTED_SHA" >&2
  echo "actual:   $ACTUAL_SHA" >&2
  exit 1
fi

require_one_device
if adb_cmd shell pm list packages "$PACKAGE" | grep -Fxq "package:$PACKAGE"; then
  echo "Existing app detected; updating with adb install -r. App data and journal will not be cleared."
fi

set +e
INSTALL_OUTPUT="$(adb_cmd install -r "$APK" 2>&1)"
INSTALL_STATUS=$?
set -e
printf '%s\n' "$INSTALL_OUTPUT"
if [[ $INSTALL_STATUS -ne 0 ]]; then
  echo "Install failed. The script did not uninstall the app or clear its data." >&2
  exit "$INSTALL_STATUS"
fi

adb_cmd shell am start \
  -n "$PACKAGE/com.onedayonemasterpiece.georeminder.MainActivity" >/dev/null

printf '%s\n' \
  "Installed verified APK: $APK" \
  "Release: ${RELEASE_TAG:-explicit local APK}" \
  "SHA-256: $ACTUAL_SHA" \
  "" \
  "On the phone:" \
  "1. Grant precise location." \
  "2. Open app settings and choose Location -> Allow all the time." \
  "3. Grant notifications." \
  "4. Keep Samsung battery mode Optimized initially; inspect restrictions only after a reproducible miss."
