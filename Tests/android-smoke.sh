#!/usr/bin/env bash
# The emulator action executes each script input line in a separate shell.
# Keep test result handling and screenshot collection in one shell process.
set -uo pipefail
status=0
bash android/gradlew -p android connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.live=true \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
  --console plain || status=$?
mkdir -p outputs/android-qa
adb pull /sdcard/Android/data/app.yoru.anime/files/qa outputs/android-qa/ || true
exit "$status"
