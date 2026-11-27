"""Drive and verify the production Canvas targets on any emulator size/density."""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

out = Path(sys.argv[1])
def adb(*args):
    return subprocess.check_output(["adb", *map(str, args)])
def hierarchy():
    adb("shell","uiautomator","dump","/sdcard/gt3.xml")
    raw=adb("shell","cat","/sdcard/gt3.xml")
    (out/"selector.xml").write_bytes(raw)
    return list(ET.fromstring(raw).iter("node"))
def bounds(node):
    return list(map(int,re.findall(r"\d+",node.attrib["bounds"])))
def find(field,prefix,scroll=False):
    for attempt in range(5):
        nodes=hierarchy()
        for node in nodes:
            if node.get(field,"").startswith(prefix):
                return node
        area=next((n for n in nodes if n.get("scrollable")=="true"),None)
        if scroll and area is not None:
            x1,y1,x2,y2=bounds(area)
            mid=(x1+x2)//2
            adb("shell","input","swipe",mid,y2-10,mid,y1+10,350)
        time.sleep(.25)
    raise AssertionError(f"{field} {prefix!r} absent; see selector.xml")
def tap(node):
    x1,y1,x2,y2=bounds(node)
    assert x2>x1 and y2>y1, node.attrib
    adb("shell","input","tap",(x1+x2)//2,(y1+y2)//2)
    time.sleep(.3)
def choose(target, option):
    tap(find("content-desc",target))
    tap(find("text",option,True))
def capture(name):
    (out/(name+".png")).write_bytes(adb("exec-out","screencap","-p"))

choose("POWER HISTORY","5 min")
find("content-desc","POWER HISTORY (kW), 5 min")
capture("picker-5min")
slots=[("Main left","Speed"),("Lower left","Battery temperature"),("Main right","Motor power"),("Lower right","Charging voltage")]
for target,option in slots:
    choose(target+" gauge:",option)
    find("content-desc",target+" gauge: "+option)
capture("four-slots")
pkg="org.triprecorderng.preview"
adb("shell","am","start","-S","-W","-n",pkg+"/org.triprecorderng.NismoPreviewActivity","--es","scene","restore","--es","motion","FULL")
for target,option in slots:
    find("content-desc",target+" gauge: "+option)
find("content-desc","POWER HISTORY (kW), 5 min")
choose("Main left gauge:","Reset theme gauges")
for target,option in [("Main left","Motor power"),("Lower left","Battery level"),("Main right","Front motor RPM"),("Lower right","Estimated remaining range")]:
    find("content-desc",target+" gauge: "+option)
print("PASS actual picker, four independent selections, 5 minute window, process restart and reset")

def header(mode):
    adb("shell","am","start","-S","-W","-n",pkg+"/org.triprecorderng.NismoPreviewActivity",
        "--es","scene","header","--ei","header",mode)
    time.sleep(1)
def button_height(node):
    _,y1,_,y2=bounds(node)
    return y2-y1

header(0)
standard=button_height(find("content-desc","NISMO GT3 Edition."))
time.sleep(5.4)
find("content-desc","Setting")
capture("header-standard")
header(1)
compact=button_height(find("content-desc","NISMO GT3 Edition."))
assert compact>=standard, (standard,compact)
find("content-desc","Driving Behavior Q&A")
find("content-desc","Setting")
capture("header-compact")
header(-1)
time.sleep(5.5)
toggle=find("content-desc","Show dashboard header")
immersive=button_height(find("content-desc","NISMO GT3 Edition."))
assert immersive>standard, (standard,immersive)
capture("header-auto")
tap(toggle)
tap(find("content-desc","Setting"))
time.sleep(5.3)
assert not any(n.get("content-desc")=="Show dashboard header" for n in hierarchy())
print("PASS actual MainActivity Standard, Compact, Auto Hide, restore and settings. Content heights:",standard,compact,immersive)

# Start in the settings reached by restoring the hidden header. Exercise the
# actual dialogs and the Activity recreation that applies the Android window theme.
def settings_choice(row, option):
    tap(find("text", row, True))
    tap(find("text", option, True))
    time.sleep(.7)

settings_choice("Dashboard theme", "Classic")
settings_choice("Appearance", "Light")
settings_choice("Dashboard theme", "GT-R Performance")
tap(find("text", "Appearance", True))
find("text", "Light and Auto are available with Classic.")
assert not any(n.get("text") in ("Light","Auto (system)") for n in hierarchy())
tap(find("text", "OK"))
settings_choice("Dashboard theme", "Classic")
tap(find("text", "Appearance", True))
assert find("text", "Light").get("checked") == "true"
tap(find("text", "Cancel"))
print("PASS restored header to Settings, Classic Light -> GT-R dark popup -> Classic Light selection")
