#!/usr/bin/env bash
set -euo pipefail

adb=(adb -s "emulator-${EMULATOR_PORT:?}")
logs=state/device-simulation
mkdir -p "$logs"
printf 'Staging: %s\n' "$STAGING_TAG" | tee "$logs/candidate.log"

case "$1" in
  phone)
    package=tv.miqat.companion
    runner=$package.test/$package.CompanionCheck
    ;;
  tv)
    package=tv.miqat.home
    runner=$package.test/$package.AppearanceCycleDeviceCheck
    "${adb[@]}" shell wm size 1920x1080
    "${adb[@]}" shell wm density 160
    ;;
  *) exit 2 ;;
esac

# Smoke the actual signed package before switching to the matching debug test pair.
"${adb[@]}" install "$2"
"${adb[@]}" shell am start -W -n "$package/.MainActivity" | tee "$logs/signed-launch.log"
grep -q '^Status: ok' "$logs/signed-launch.log"
"${adb[@]}" shell pidof "$package"
"${adb[@]}" uninstall "$package"

instrument() {
  local check=$1
  shift
  timeout 420s "${adb[@]}" shell am instrument -r -w "$@" "$runner" | tee "$logs/$check.log"
  grep -q '^INSTRUMENTATION_CODE: -1' "$logs/$check.log"
  grep -Eq 'PASS:|passed:|Native prayer regression|Native global-size pages passed' "$logs/$check.log"
}

if [[ "$1" == tv ]]; then
  "${adb[@]}" install candidate/platforms/android-tv/build/outputs/apk/debug/app-debug.apk
  "${adb[@]}" install -t candidate/platforms/android-tv/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
  for check in prayer ui size online; do
    instrument "$check" -e check "$check"
  done
  exit
fi

# A cold Google APIs image can leave Pixel Launcher ANR covering the test app.
"${adb[@]}" shell am force-stop com.google.android.apps.nexuslauncher
"${adb[@]}" install -r candidate/companion/android/build/outputs/apk/debug/companion-debug.apk
"${adb[@]}" install -r -t candidate/companion/android/build/outputs/apk/androidTest/debug/companion-debug-androidTest.apk
instrument "phone-journey" -e suite phone-journey
instrument "widget-small" -e suite widget-small
instrument "phone-custom" -e suite phone-custom
instrument "phone-background-denied" -e suite phone-background-denied
instrument "public-release" -e suite public-release
instrument "phone-location-denied" -e suite phone-location-denied
"${adb[@]}" shell pm grant tv.miqat.companion android.permission.ACCESS_COARSE_LOCATION
"${adb[@]}" shell cmd location set-location-enabled true
"${adb[@]}" shell appops set tv.miqat.companion android:mock_location allow
instrument "phone-location-approx" -e suite phone-location-approx
"${adb[@]}" shell pm grant tv.miqat.companion android.permission.ACCESS_FINE_LOCATION
instrument "phone-location" -e suite phone-location
"${adb[@]}" shell pm grant tv.miqat.companion android.permission.ACCESS_BACKGROUND_LOCATION
"${adb[@]}" emu geo fix -0.1278 51.5074
instrument "phone-background-prepare" -e suite phone-background-prepare
"${adb[@]}" shell am kill tv.miqat.companion
test -z "$("${adb[@]}" shell pidof tv.miqat.companion)"
"${adb[@]}" emu geo fix -0.1278 51.5074
"${adb[@]}" shell cmd jobscheduler run -f tv.miqat.companion 41703
sleep 22
instrument "phone-background-verify" -e suite phone-background-verify
"${adb[@]}" shell pm revoke tv.miqat.companion android.permission.ACCESS_BACKGROUND_LOCATION
instrument "companion-ui"
instrument "companion-lan" -e suite lan
instrument "phone-ui" -e suite phone-ui
instrument "phone-size" -e suite phone-size -e orientation portrait
"${adb[@]}" shell pm revoke tv.miqat.companion android.permission.POST_NOTIFICATIONS
instrument "phone-denied" -e suite phone-denied
"${adb[@]}" shell pm grant tv.miqat.companion android.permission.POST_NOTIFICATIONS
instrument phone-runtime -e suite phone-runtime
