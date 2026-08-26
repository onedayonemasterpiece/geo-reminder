#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION="${GRADLE_VERSION:-8.13}"
EXPECTED_SHA256="${GRADLE_SHA256:-20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78}"
TOOLS="$ROOT/.tools"
ZIP="$TOOLS/gradle-$VERSION-bin.zip"
DIST="$TOOLS/gradle-$VERSION"

mkdir -p "$TOOLS"
if [[ ! -x "$DIST/bin/gradle" ]]; then
  command -v curl >/dev/null 2>&1 || { echo "curl is required" >&2; exit 1; }
  command -v unzip >/dev/null 2>&1 || { echo "unzip is required" >&2; exit 1; }
  command -v sha256sum >/dev/null 2>&1 || { echo "sha256sum is required" >&2; exit 1; }

  if [[ ! -f "$ZIP" ]]; then
    echo "Downloading Gradle $VERSION from the official distribution service..."
    curl --fail --location --retry 3 --retry-all-errors \
      "https://services.gradle.org/distributions/gradle-$VERSION-bin.zip" \
      --output "$ZIP"
  fi
  actual="$(sha256sum "$ZIP" | awk '{print $1}')"
  if [[ "$actual" != "$EXPECTED_SHA256" ]]; then
    rm -f "$ZIP"
    echo "Gradle archive SHA-256 mismatch: expected $EXPECTED_SHA256, got $actual" >&2
    exit 1
  fi
  unzip -q -o "$ZIP" -d "$TOOLS"
fi

exec "$DIST/bin/gradle" -p "$ROOT" "$@"
