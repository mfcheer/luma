#!/usr/bin/env python3
"""Remote-only smoke test on a dedicated Android TV emulator (never changes a real TV's home)."""
import os, subprocess, time, xml.etree.ElementTree as ET
from pathlib import Path
ADB = os.environ.get('ADB', str(Path.home() / 'Library/Android/sdk/platform-tools/adb'))
SERIAL = os.environ.get('ANDROID_SERIAL', 'emulator-5554')
ROOT = Path(__file__).resolve().parents[1]

def adb(*args):
    return subprocess.check_output([ADB, '-s', SERIAL, *args], text=True).strip()

def key(code, long=False):
    adb('shell', 'input', 'keyevent', *(['--longpress'] if long else []), str(code))
    time.sleep(.35)

def dump():
    adb('shell', 'uiautomator', 'dump', '/sdcard/jianping.xml')
    return ET.fromstring(adb('shell', 'cat', '/sdcard/jianping.xml'))

def focused(tree):
    return next((n for n in tree.iter('node') if n.get('focused') == 'true'), None)

def assert_text(tree, text):
    assert any(text in n.get('text', '') for n in tree.iter('node')), text

def capture(name):
    (ROOT/'docs/screenshots').mkdir(exist_ok=True)
    with open(ROOT/'docs/screenshots'/name, 'wb') as f:
        subprocess.run([ADB,'-s',SERIAL,'exec-out','screencap','-p'],stdout=f,check=True)

assert SERIAL.startswith('emulator-'), 'Only run this script on a dedicated emulator'
adb('shell', 'am', 'start', '-n', 'com.jianping.tv/.ui.MainActivity')
time.sleep(2)
tree = dump()
assert_text(tree, '简屏')
assert focused(tree) is not None, 'Initial focus missing'
capture('home.png')
for _ in range(10): key(19)  # walk through grid rows to settings
key(23)
tree = dump()
assert_text(tree, '桌面与设备')
assert_text(tree, '设置默认桌面')
capture('settings.png')
key(4)
# Go below the final favorites row to More.
for _ in range(10): key(20)
key(23)
tree = dump()
assert_text(tree, '全部应用')
capture('all-apps.png')
key(23, long=True)
tree = dump()
assert any('常用' in n.get('text','') or '移动' in n.get('text','') or '移到' in n.get('text','') for n in tree.iter('node')), 'Long press menu missing'
key(4)
key(4)
assert_text(dump(), '简屏')
print('PASS: initial focus, remote settings/back, all-apps navigation, long-press management')
