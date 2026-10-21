#!/usr/bin/env python3
"""Still 0.5.1 remote regression for the dedicated emulator with 28 favorites.
Requires the emulator-fixtures.py/grid-smoke.py test setup; never run on a real TV.
"""
import os, subprocess, time, xml.etree.ElementTree as ET
from pathlib import Path
root=Path(__file__).resolve().parents[1]
sdk=Path(os.environ.get('ANDROID_HOME',Path.home()/'Library/Android/sdk'))
a=str(sdk/'platform-tools/adb');serial=os.environ.get('ANDROID_SERIAL','emulator-5554')
assert serial.startswith('emulator-'), 'Dedicated emulator required'
out=root/'docs/screenshots';out.mkdir(exist_ok=True)
def adb(*args):return subprocess.check_output([a,'-s',serial,*args],text=True).strip()
def key(n,long=False):adb('shell','input','keyevent',*(['--longpress'] if long else []),str(n));time.sleep(.2)
def tree():
 adb('shell','uiautomator','dump','/sdcard/still.xml');return ET.fromstring(adb('shell','cat','/sdcard/still.xml'))
def focus():return next(n for n in tree().iter('node') if n.get('focused')=='true')
def shot(name):
 time.sleep(.4)
 with open(out/name,'wb') as f:subprocess.run([a,'-s',serial,'exec-out','screencap','-p'],stdout=f,check=True)
def setting():
 x=ET.fromstring(adb('shell','run-as','com.jianping.tv','cat','shared_prefs/launcher.xml'))
 n=next((v for v in x if v.get('name')=='bootEnabled'),None)
 return n is not None and n.get('value')=='true'
adb('shell','am','force-stop','com.jianping.tv');adb('shell','am','start','-n','com.jianping.tv/.ui.MainActivity');time.sleep(.8)
assert focus().get('content-desc')=='Play Store'
key(22);key(22)
t=tree();assert not any(n.get('text') in ['Luma','Still','简屏'] for n in t.iter('node'))
assert any(n.get('class')=='android.widget.ImageView' and n.get('content-desc')=='YouTube' for n in t.iter('node'))
shot('still-home.png')
key(19);key(23)
t=tree()
assert all(any(n.get('text')==label for n in t.iter('node')) for label in ['桌面','应用','设备','默认桌面'])
assert focus().get('content-desc')=='默认桌面'
shot('still-settings.png')
key(20);old=setting();key(23);assert setting()!=old
assert focus().get('content-desc').startswith('开机启动')
shot('still-settings-toggle.png');key(23);assert setting()==old
key(20);key(20);key(23);time.sleep(.8)
assert focus().get('content-desc')=='刷新应用列表'
key(20);key(23)
assert any('Android' in n.get('text','') for n in tree().iter('node'))
key(4);key(4)
key(20);assert focus().get('content-desc')=='测试应用 05'
key(20);assert focus().get('content-desc')=='测试应用 11'
shot('still-third-row.png')
for _ in range(8):key(20)
for _ in range(8):key(22)
assert focus().get('content-desc')=='全部应用'
shot('still-more.png');key(23)
assert any(n.get('text')=='全部应用' for n in tree().iter('node'))
key(23,True)
assert any('移出常用' in n.get('text','') or '添加到常用' in n.get('text','') for n in tree().iter('node'))
key(4);key(4)
assert focus().get('content-desc')=='测试应用 24'
key(23);time.sleep(.4)
assert any('topResumedActivity' in line and 'com.jianping.fixture.app24/' in line for line in adb('shell','dumpsys','activity','activities').splitlines())
key(4)
print('PASS: brand-free header, icon-only hero, grouped settings, persistent toggle/no focus jump, refresh focus, diagnostics, 28 favorites, paging, All Apps, long-press, external launch/back')
