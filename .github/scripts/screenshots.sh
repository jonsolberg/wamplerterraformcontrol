#!/usr/bin/env bash
# Installs the APK on the emulator, walks through the screens and captures screenshots + logcat.
set -x
mkdir -p shots
adb shell settings put global hide_error_dialogs 1
sleep 25
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS
adb install -r app/build/outputs/apk/release/app-release.apk
adb logcat -c
adb shell am start -n se.jonsolberg.terraform/.MainActivity
sleep 8
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS
sleep 1
shot() { adb exec-out screencap -p > "shots/$1.png"; }
SIZE=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1)
W=${SIZE%x*}; H=${SIZE#*x}
NAVY=$((H - H * 7 / 100))
shot 01-pedal
adb shell input swipe $((W / 2)) $((H * 75 / 100)) $((W / 2)) $((H * 35 / 100)) 300
sleep 1
shot 02-pedal-scrolled
adb shell input swipe $((W / 2)) $((H * 75 / 100)) $((W / 2)) $((H * 15 / 100)) 300
sleep 1
shot 03-pedal-bottom
adb shell input tap $((W / 2)) $NAVY
sleep 2
shot 04-presets
adb shell input tap $((W * 5 / 6)) $NAVY
sleep 2
shot 05-settings
adb shell input tap $((W / 6)) $NAVY
sleep 1
adb shell settings put system user_rotation 1
adb shell settings put system accelerometer_rotation 0
sleep 3
shot 06-landscape
adb logcat -d > shots/logcat.txt
adb logcat -d | grep -E "AndroidRuntime|FATAL" > shots/crashes.txt || true
exit 0
