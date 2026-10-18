#!/usr/bin/env bash
# Installs the APK, launches it, taps through the first screens and prints crashes.
set -u
mkdir -p smoke
APK="$(find app/build/outputs/apk -name '*.apk' | head -1)"
PKG=com.ebchat
adb install -r -g "$APK" || adb install -r "$APK"
adb logcat -c
adb logcat -b all -c || true

snap() {
  adb exec-out screencap -p > "smoke/$1.png" 2>/dev/null || true
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 && adb pull /sdcard/ui.xml "smoke/$1.xml" >/dev/null 2>&1 || true
  echo "---- screen $1: visible texts ----"
  grep -o 'text="[^"]*"' "smoke/$1.xml" 2>/dev/null | grep -v 'text=""' | head -40 || true
}

alive() { adb shell pidof "$PKG" >/dev/null 2>&1; }

# Tap the first node whose text matches (case-insensitive regex).
tap_text() {
  local f="smoke/$1.xml" pattern="$2"
  local bounds
  bounds=$(grep -oiE "text=\"($pattern)\"[^>]*bounds=\"\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]\"" "$f" 2>/dev/null | head -1 | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]')
  if [ -n "$bounds" ]; then
    set -- $(echo "$bounds" | grep -oE '[0-9]+')
    adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
    echo "tapped '$pattern'"
    return 0
  fi
  return 1
}

adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
for step in 1 2 3 4 5 6 7 8; do
  sleep 12
  snap "step$step"
  if ! alive; then echo "APP PROCESS IS NOT RUNNING after step $step"; break; fi
  tap_text "step$step" "English|Continue|Next|Get started|Skip|Start|Done|Allow|OK" || adb shell input keyevent 66
done

echo "================ CRASH BUFFER ================"
adb logcat -d -b crash | tee smoke/crash.txt
echo "================ AndroidRuntime / FATAL ================"
adb logcat -d | grep -E -A60 'FATAL EXCEPTION|AndroidRuntime: (java|kotlin|Caused)' | head -400 | tee smoke/fatal.txt
adb logcat -d > smoke/logcat-full.txt
if alive; then echo "RESULT: app still running"; else echo "RESULT: app NOT running"; fi
exit 0
