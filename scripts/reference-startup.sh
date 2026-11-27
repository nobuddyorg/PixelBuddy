#!/usr/bin/env sh
# Cold starts of the system Settings app on the connected emulator or phone, as a yardstick for its speed: the
# startup budget compares PixelBuddy with this, so a slow CI runner doesn't fail it (#15).
#
# Usage: scripts/reference-startup.sh <output file>   (one TotalTime in ms per line)
set -eu
out="$1"
mkdir -p "$(dirname "$out")"
: >"$out"
i=0
while [ "$i" -lt 12 ]; do
    i=$((i + 1))
    # -S stops the app first, so every start is cold; -W waits and prints the timings.
    total=$(adb shell am start -W -S -n com.android.settings/.Settings | sed -n 's/^TotalTime: *//p' | tr -d '\r')
    adb shell input keyevent KEYCODE_HOME
    if [ -z "$total" ]; then
        echo "No TotalTime from am start" >&2
        exit 1
    fi
    # The first two starts warm up the emulator.
    if [ "$i" -gt 2 ]; then
        echo "$total" >>"$out"
    fi
done
echo "Settings cold starts (ms): $(tr '\n' ' ' <"$out")"
