#!/usr/bin/env bash
# Fails when an APK is larger than the budget in .github/apk-size-budget (bytes).
# Usage: scripts/check-apk-size.sh path/to/app.apk
set -euo pipefail

apk="$1"
budget_file="$(dirname "$0")/../.github/apk-size-budget"
budget="$(tr -d '[:space:]' < "$budget_file")"
size="$(stat -c %s "$apk")"

echo "APK size: $size bytes (budget $budget bytes)"
if [ "$size" -gt "$budget" ]; then
  echo "::error file=$apk::APK is $size bytes, over the budget of $budget bytes (.github/apk-size-budget)"
  exit 1
fi
