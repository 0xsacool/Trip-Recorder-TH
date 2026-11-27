#!/usr/bin/env bash
# Runs on an isolated Android 10 emulator in CI. All screenshots carry the fixture label.
set -euo pipefail
root="$(pwd)"
sdkbin="$(dirname "$(find "$ANDROID_HOME/cmdline-tools" -name sdkmanager | sort -V | tail -1)")"
export PATH="$sdkbin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
sdkmanager 'emulator' 'platforms;android-34' 'build-tools;35.0.0' 'system-images;android-29;google_apis;x86_64'
out="$root/nismo-evidence"
preview="$(mktemp -d)"
mkdir -p "$out"
cp -R src res assets build.sh "$preview/"
cp tools/nismo-preview/AndroidManifest.xml "$preview/"
cp tools/nismo-preview/NismoPreviewActivity.java "$preview/src/org/triprecorderng/"
(cd "$preview" && bash build.sh)
export ANDROID_AVD_HOME="$preview/avds"
export ANDROID_USER_HOME="$preview/android-user"
mkdir -p "$ANDROID_AVD_HOME" "$ANDROID_USER_HOME"
echo no | avdmanager create avd --name gt3 --path "$ANDROID_AVD_HOME/gt3.avd" --package 'system-images;android-29;google_apis;x86_64' --force
emulator -list-avds | grep -Fx gt3
sudo setfacl -m "u:$(id -un):rw" /dev/kvm
emulator -avd gt3 -no-window -no-boot-anim -no-audio -no-snapshot -gpu swiftshader_indirect >"$out/emulator.log" 2>&1 &
emupid=$!
trap 'kill "$emupid" 2>/dev/null || true' EXIT
deadline=$((SECONDS+300))
until [[ "$(timeout 3 adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == 1 ]]; do
    if ! kill -0 "$emupid" 2>/dev/null || ((SECONDS>=deadline)); then cat "$out/emulator.log"; exit 1; fi
    sleep 2
done
adb shell wm size 720x1600
adb shell wm density 160
adb shell input keyevent 82
adb install "$preview/TripRecorderNG.apk"
pkg=org.triprecorderng.preview
start() {
    adb shell am start -S -W -n "$pkg/org.triprecorderng.NismoPreviewActivity" \
        --es scene "$1" --ez light "$2" --es motion "$3"
    sleep 2
    adb shell pidof "$pkg" >/dev/null
}
capture() { adb exec-out screencap -p >"$out/$1.png"; }
for spec in 'drive false FULL' 'drive true FULL' 'regen false REDUCED' 'parked false OFF' \
        'charging true FULL' 'results false FULL' 'missing true OFF' 'selector false FULL'; do
    read -r scene light motion <<< "$spec"
    start "$scene" "$light" "$motion"
    capture "$scene-$light-$motion"
done
# Open the real graph picker and select the other history window.
start parked false FULL
adb shell input tap 1305 550
sleep .4
adb shell uiautomator dump /sdcard/gt3-window.xml
adb pull /sdcard/gt3-window.xml "$out/selector.xml"
python3 - "$out/selector.xml" <<'PY'
import re, subprocess, sys, xml.etree.ElementTree as ET
nodes=ET.parse(sys.argv[1]).iter('node')
item=next(n for n in nodes if n.get('text','').startswith('5 min'))
x1,y1,x2,y2=map(int,re.findall(r'\d+',item.get('bounds')))
subprocess.run(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)],check=True)
PY
sleep .4
capture picker-5min
start dynamic false FULL
adb shell dumpsys gfxinfo "$pkg" reset >/dev/null
sleep 12
adb shell dumpsys gfxinfo "$pkg" framestats >"$out/gfxinfo.txt"
adb shell dumpsys meminfo "$pkg" >"$out/meminfo.txt"
grep -E 'Total frames rendered: [1-9][0-9]+' "$out/gfxinfo.txt"
adb logcat -d -b crash >"$out/crash.log"
if grep -q 'FATAL EXCEPTION' "$out/crash.log"; then cat "$out/crash.log"; exit 1; fi
