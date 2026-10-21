#!/usr/bin/env python3
"""0.3 native UI smoke on the dedicated 28-favorites emulator fixture."""
import subprocess,time,xml.etree.ElementTree as ET
from pathlib import Path
root=Path(__file__).resolve().parents[1];a=str(Path.home()/'Library/Android/sdk/platform-tools/adb')
def adb(*args):return subprocess.check_output([a,'-s','emulator-5554',*args],text=True).strip()
def key(n):adb('shell','input','keyevent',str(n));time.sleep(.16)
def tree():
 adb('shell','uiautomator','dump','/sdcard/clean.xml');return ET.fromstring(adb('shell','cat','/sdcard/clean.xml'))
def focus():return next(n for n in tree().iter('node') if n.get('focused')=='true')
def shot(name):
 with open(root/'docs/screenshots'/name,'wb') as out:subprocess.run([a,'-s','emulator-5554','exec-out','screencap','-p'],stdout=out,check=True)
adb('shell','am','force-stop','com.jianping.tv');adb('shell','am','start','-n','com.jianping.tv/.ui.MainActivity');time.sleep(.8)
assert focus().get('content-desc')=='Play Store'
key(20);assert focus().get('content-desc')=='测试应用 03'
key(22);assert focus().get('content-desc')=='测试应用 04'
key(19);assert focus().get('content-desc')=='Settings'
key(21);key(19);key(23)
t=tree();assert any(n.get('text')=='桌面与应用' for n in t.iter('node'))
shot('clean-settings.png');key(4)
key(22);key(22);shot('clean-home.png')
# Third row appears as a complete new page; hero remains horizontally centered.
key(20);key(20);t=tree();assert focus().get('content-desc')=='测试应用 11'
hero=next(n for n in t.iter('node') if n.get('class')=='android.widget.ImageView' and n.get('content-desc')=='测试应用 11')
assert hero.get('bounds')=='[832,176][1088,432]',hero.attrib
shot('clean-third-row.png')
for _ in range(8):key(20)
for _ in range(8):key(22)
assert focus().get('content-desc')=='全部应用'
shot('clean-more.png');key(23)
assert any(n.get('text')=='全部应用' for n in tree().iter('node'))
key(4);assert focus().get('content-desc')=='测试应用 24'
key(23);time.sleep(.3)
assert any('topResumedActivity' in line and 'com.jianping.fixture.app24/' in line for line in adb('shell','dumpsys','activity','activities').splitlines())
key(4)
print('PASS: six-column grid navigation; centered icon-only hero; full-row paging; compact settings; consistent All Apps tile; external launch/back')
