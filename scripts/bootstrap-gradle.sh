#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

"$ROOT/scripts/gradle.sh" wrapper \
  --gradle-version "${GRADLE_VERSION:-8.13}" \
  --distribution-type bin

echo "Gradle wrapper ready: $ROOT/gradlew"
