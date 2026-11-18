#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
classes=$(mktemp -d)
trap 'rmdir "$classes" 2>/dev/null || find "$classes" -depth -delete' EXIT
compiler=(javac)
level=(--release 8)
if ! command -v javac >/dev/null; then
  compiler=(java com.sun.tools.javac.Main)
  level=(-source 8 -target 8 -Xlint:-options)
fi
"${compiler[@]}" "${level[@]}" -d "$classes" src/org/triprecorderng/{ChargeDetection,ChargeRecorder,Charge,Vehicle,Trip}.java \
  tools/charging-test/android/{content/Context,location/Location,os/SystemClock}.java \
  tools/charging-test/RecorderFakes.java tools/charging-test/ChargeRecorderTest.java
java -cp "$classes" org.triprecorderng.ChargeRecorderTest
