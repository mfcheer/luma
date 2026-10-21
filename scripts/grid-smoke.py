#!/usr/bin/env python3
"""Dedicated emulator test: 28 favorites, grid direction paths, scroll, sticky hero and More.
Creates test preferences on emulator-5554 and saves its previous XML to the tool cache.
Requires emulator-fixtures.py to have installed 24 test applications.
"""
import subprocess,time,json,xml.etree.ElementTree as ET
from pathlib import Path
root=Path(__file__).resolve().parents[1]
adbpath=str(Path.home()/'Library/Android/sdk/platform-tools/adb')
def adb(*args,**kw):return subprocess.check_output([adbpath,'-s','emulator-5554',*args],text=True,**kw).strip()
def key(n):adb('shell','input','keyevent',str(n));time.sleep(.15)
def tree():
 adb('shell','uiautomator','dump','/sdcard/grid.xml');return ET.fromstring(adb('shell','cat','/sdcard/grid.xml'))
def focus():return next(n for n in tree().iter('node') if n.get('focused')=='true')
def shot(name):
 with open(root/'docs/screenshots'/name,'wb') as out:subprocess.run([adbpath,'-s','emulator-5554','exec-out','screencap','-p'],stdout=out,check=True)
xml=adb('shell','run-as','com.jianping.tv','cat','shared_prefs/launcher.xml')
backup=Path.home()/'.cache/jianping-tools/fixtures/previous-launcher.xml'
if not backup.exists():backup.write_text(xml)
r=ET.fromstring(xml)
ids=['com.android.vending/com.google.android.finsky.tvmainactivity.TvMainActivity','com.android.tv.settings/com.android.tv.settings.MainSettings','com.google.android.youtube.tv/com.google.android.apps.youtube.tv.activity.ShellActivity','com.google.android.tv.remote.service/com.google.android.tv.remote.service.settings.SettingsActivity']+[f'com.jianping.fixture.app{i:02d}/com.jianping.fixture.MainActivity' for i in range(1,25)]
next(n for n in r if n.get('name')=='favorites').text=json.dumps(ids)
adb('shell','am','force-stop','com.jianping.tv')
subprocess.run([adbpath,'-s','emulator-5554','shell',"run-as com.jianping.tv sh -c 'cat > shared_prefs/launcher.xml'"],input=ET.tostring(r,encoding='utf-8'),check=True)
adb('shell','am','start','-n','com.jianping.tv/.ui.MainActivity');time.sleep(1)
assert focus().get('content-desc')=='Play Store'
key(20);assert focus().get('content-desc')=='测试应用 03'
key(22);assert focus().get('content-desc')=='测试应用 04'
key(19);assert focus().get('content-desc')=='Settings'
key(21);assert focus().get('content-desc')=='Play Store'
key(22);key(22);shot('grid-28-favorites.png')
# Go down through every row. Header must stay at identical screen bounds.
header=next(n for n in tree().iter('node') if n.get('text')=='简屏').get('bounds')
for _ in range(7):key(20)
t=tree();assert next(n for n in t.iter('node') if n.get('focused')=='true').get('text')=='查看更多  ·  全部应用'
assert next(n for n in t.iter('node') if n.get('text')=='简屏').get('bounds')==header
assert any(n.get('text')=='全部应用' and n.get('class')=='android.widget.TextView' for n in t.iter('node'))
shot('grid-more.png')
key(23);assert any(n.get('text')=='全部应用' for n in tree().iter('node'))
key(4)
# On returning from More, focus is restored to the last selected app in the grid.
f=focus();assert f.get('content-desc')=='测试应用 23',f.attrib
# Navigate into another row and open its real fixture Activity.
key(19);key(23);time.sleep(.5)
state=adb('shell','dumpsys','activity','activities')
assert any('topResumedActivity' in line and 'com.jianping.fixture.app' in line for line in state.splitlines())
key(4)
assert any(n.get('text')=='简屏' for n in tree().iter('node'))
print('PASS: 28 favorites; six columns; up/down/left/right; scrolling to More; sticky header; focus restoration; external app launch/back')
