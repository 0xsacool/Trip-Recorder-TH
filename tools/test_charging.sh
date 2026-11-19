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
mkdir -p "$classes/provider"
"${compiler[@]}" "${level[@]}" -d "$classes/provider" src/org/triprecorderng/{VehicleProvider,Vehicle}.java \
  tools/charging-test/android/content/Context.java tools/provider-test/{ProviderFakes,VehicleProviderTest}.java
java -cp "$classes/provider" org.triprecorderng.VehicleProviderTest
mkdir -p "$classes/billing"
"${compiler[@]}" "${level[@]}" -d "$classes/billing" src/org/triprecorderng/ChargeBilling.java tools/ChargeBillingTest.java
java -cp "$classes/billing" org.triprecorderng.ChargeBillingTest
