#!/usr/bin/env bash
# Only called on the CI AVD. Exercise Android's replacement install with published Dev 6 data.
set -euo pipefail
pkg=com.evtriplog.app
store=/data/data/$pkg
adb root
adb wait-for-device
adb install -g "$1"
launch() { adb shell am start -W -n "$pkg/org.triprecorderng.MainActivity" --ez skip_alert true; }
launch
for _ in {1..20}; do adb shell test -f "$store/databases/trips.db" && break; sleep 1; done
adb shell am force-stop "$pkg"
db="$store/databases/trips.db"
query() { adb shell "sqlite3 '$db' \"$1\"" | tr -d '\r'; }
query "INSERT INTO trips(id,ended,distance_km) VALUES('dev7-upgrade-trip',1,12.5)"
query "INSERT INTO charges(id,ended,kwh) VALUES('dev7-upgrade-charge',1,7.25)"
# Seed just the two Dev 6 settings under test, preserving Android's ownership and SELinux labels.
prefs="$store/shared_prefs/triprecng.xml"
adb pull "$prefs" "$RUNNER_TEMP/dev6-prefs.xml"
python3 - "$RUNNER_TEMP/dev6-prefs.xml" <<'PY'
import sys
import xml.etree.ElementTree as ET
p=sys.argv[1]
tree=ET.parse(p)
for name in ('appearance_mode','nismo_header_mode'):
    root=tree.getroot()
    for elem in root.findall(f"*[@name='{name}']"): root.remove(elem)
    ET.SubElement(root,'int',name=name,value='1')
tree.write(p,encoding='utf-8',xml_declaration=True)
PY
adb shell "cat > '$prefs'" <"$RUNNER_TEMP/dev6-prefs.xml"
adb install -r "$2"
launch
adb shell am force-stop "$pkg"
test "$(query "SELECT distance_km FROM trips WHERE id='dev7-upgrade-trip'")" = 12.5
test "$(query "SELECT kwh FROM charges WHERE id='dev7-upgrade-charge'")" = 7.25
adb pull "$prefs" "$RUNNER_TEMP/dev7-prefs.xml"
python3 - "$RUNNER_TEMP/dev7-prefs.xml" <<'PY'
import sys
import xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
for name in ('appearance_mode','dashboard_header_mode'):
    assert root.find(f"int[@name='{name}']").get('value')=='1',name
PY
echo 'PASS signed Dev 6 replacement install, trip, charge and settings migration'
