#!/usr/bin/env bash
# Prints X.Y.Z for a release tag vX.Y.Z and fails for anything else.
# Usage: scripts/version-from-tag.sh v1.2.3
set -euo pipefail

tag="$1"
if [[ ! "$tag" =~ ^v([0-9]{1,4})\.([0-9]{1,3})\.([0-9]{1,3})$ ]]; then
  echo "Release tags must look like vX.Y.Z (X ≤ 2099, Y and Z < 1000), got '$tag'" >&2
  exit 1
fi
if [ "${BASH_REMATCH[1]}" -gt 2099 ]; then
  echo "Major version too large for an Android versionCode: ${BASH_REMATCH[1]}" >&2
  exit 1
fi
echo "${tag#v}"
