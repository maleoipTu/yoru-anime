#!/usr/bin/env bash
# The emulator action executes each script input line in a separate shell.
# Keep test result handling and screenshot collection in one shell process.
set -uo pipefail
if [ "${QA_VIEWPORT:-default}" = compact ]; then
  adb shell wm size 720x1600
  adb shell wm density 320
  adb shell settings put system font_scale 1.3
fi
status=0
bash android/gradlew -p android connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.live=true \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
  --console plain || status=$?
mkdir -p outputs/android-qa
adb pull /sdcard/Android/data/app.yoru.anime/files/qa outputs/android-qa/ || true
exit "$status"
