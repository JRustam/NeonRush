#!/usr/bin/env sh
# Capture the real app through its connected Compose UI test; no mocked game data.
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$project_dir"
adb_bin=${ADB_BIN:-adb}
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest "$@"
app_apk=app/build/outputs/apk/debug/app-debug.apk
if [ ! -f "$app_apk" ]; then
    app_apk=app/build/intermediates/apk/debug/app-debug.apk
fi
"$adb_bin" install -r "$app_apk"
"$adb_bin" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
capture_log=$(mktemp)
trap 'rm -f "$capture_log"' EXIT HUP INT TERM
"$adb_bin" shell am instrument -w \
    -e class 'com.jrustam.neonrush.GameSmokeTest#navigationLifecycleSettingsAndRealScreenshots' \
    com.jrustam.neonrush.test/androidx.test.runner.AndroidJUnitRunner > "$capture_log"
cat "$capture_log"
grep -q 'OK (1 test)' "$capture_log"
mkdir -p docs/screenshots
for name in main-menu gameplay game-over; do
    "$adb_bin" pull "/sdcard/Android/data/com.jrustam.neonrush/files/screenshots/$name.png" "docs/screenshots/$name.png"
done
