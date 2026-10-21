#!/usr/bin/env python3
"""Verify the home-only label preference using remote keys on the dedicated TV emulator."""
import json
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ADB = '/Users/mfcheer/Library/Android/sdk/platform-tools/adb'
PKG = 'com.jianping.tv'
OUT = ROOT / 'docs/screenshots'


def adb(*args):
    return subprocess.check_output([ADB, '-s', 'emulator-5554', *args], text=True).strip()


def key(code, long=False):
    adb('shell', 'input', 'keyevent', *(['--longpress'] if long else []), str(code))
    time.sleep(.3)


def tree():
    adb('shell', 'uiautomator', 'dump', '/sdcard/home-labels.xml')
    return ET.fromstring(adb('shell', 'cat', '/sdcard/home-labels.xml'))


def focus():
    return next(n for n in tree().iter('node') if n.get('focused') == 'true')


def desc():
    return focus().get('content-desc', '')


def texts():
    return {n.get('text') for n in tree().iter('node') if n.get('text')}


def preferences():
    data = ET.fromstring(adb('shell', 'run-as', PKG, 'cat', 'shared_prefs/launcher.xml'))
    return {n.get('name'): n.get('value', n.text) for n in data}


def start():
    adb('shell', 'am', 'force-stop', PKG)
    adb('shell', 'am', 'start', '-n', PKG + '/.ui.MainActivity')
    time.sleep(1)


def shot(name):
    time.sleep(.4)
    with (OUT / name).open('wb') as f:
        subprocess.run([ADB, '-s', 'emulator-5554', 'exec-out', 'screencap', '-p'], stdout=f, check=True)


def open_names():
    # The initial card is in the first row; settings remembers its selected row.
    key(19)
    assert desc() == '设置'
    key(23)
    for _ in range(6):
        if desc().startswith('显示应用名称'):
            return
        key(20)
    raise AssertionError('Name setting must be reachable with D-pad')


assert len(json.loads(preferences()['favorites'])) >= 15, 'Dedicated fixture emulator required'
adb('shell', 'run-as', PKG, 'mkdir', '-p', 'files')
adb('shell', 'run-as', PKG, 'cp', 'shared_prefs/launcher.xml', 'files/home-labels-backup.xml')
original = preferences()
try:
    start()
    assert desc() == 'Play Store'
    assert original.get('showAppNames', 'true') == 'true'
    assert 'Play Store' in texts(), 'Names visible by default on upgrade'
    key(22)
    key(22)
    assert desc() == 'YouTube'
    bounds = focus().get('bounds')
    shot('still-061-labels-on.png')
    open_names()
    assert desc() == '显示应用名称，已开启'
    key(23)
    assert desc() == '显示应用名称，已关闭', 'Toggle must retain focus and announce its state'
    assert preferences()['showAppNames'] == 'false'
    shot('still-061-settings.png')
    # Every row remains visible at once, including the bottom device action.
    assert '设备与启动信息' in texts()
    key(4)
    assert desc() == 'YouTube'
    assert focus().get('bounds') == bounds, 'Hiding labels must preserve geometry and focus'
    assert not any(t in texts() for t in ['Play Store', 'YouTube', '测试应用 01'])
    shot('still-061-icons-only.png')
    key(23, True)
    assert desc() == '移动位置'
    key(23)
    key(22)
    assert desc(), 'Icon-only cards must keep accessible names while reordering'
    key(4)
    assert desc() == 'YouTube'
    assert 'YouTube' not in texts()
    for _ in range(10):
        key(20)
    for _ in range(5):
        key(22)
    assert desc() == '全部应用'
    assert '全部应用' not in texts(), 'Home shortcut label follows the preference'
    key(23)
    assert '测试应用 01' in texts(), 'All-apps labels must remain available'
    key(4)
    start()
    assert desc() == 'Play Store'
    assert 'Play Store' not in texts(), 'Hidden names must survive process restart'
    open_names()
    assert desc() == '显示应用名称，已关闭'
    key(23)
    assert desc() == '显示应用名称，已开启'
    key(4)
    assert 'Play Store' in texts(), 'Names must restore immediately after returning home'
    assert preferences()['favorites'] == original['favorites'], 'Preference cannot change favorites'
    assert preferences().get('bootEnabled') == original.get('bootEnabled')
    print('PASS: default on, remote toggle/focus, unchanged geometry, hidden home labels, accessible names, reorder cancel, drawer labels, restart persistence, restore on, unchanged favorites/boot')
finally:
    adb('shell', 'am', 'force-stop', PKG)
    adb('shell', 'run-as', PKG, 'cp', 'files/home-labels-backup.xml', 'shared_prefs/launcher.xml')
    adb('shell', 'run-as', PKG, 'rm', 'files/home-labels-backup.xml')
    adb('shell', 'am', 'start', '-n', PKG + '/.ui.MainActivity')
