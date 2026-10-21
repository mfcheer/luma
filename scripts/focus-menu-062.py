#!/usr/bin/env python3
"""Dedicated emulator: stress focus reversals, interrupt menu motion, and record actual UI."""
import io
import os
import json
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path
from PIL import Image, ImageChops

ROOT = Path(__file__).resolve().parents[1]
BASE = ['/Users/mfcheer/Library/Android/sdk/platform-tools/adb', '-s', 'emulator-5554']
PKG = 'com.jianping.tv'
VERSION = os.environ.get('STILL_QA_VERSION', '062')
SHOTS = ROOT / 'docs/screenshots'
MOVIES = ROOT / 'docs/recordings'
MOVIES.mkdir(exist_ok=True)


def adb(*args):
    return subprocess.check_output([*BASE, *args], text=True).strip()


def key(code, pause=.32, long=False):
    adb('shell', 'input', 'keyevent', *(['--longpress'] if long else []), str(code))
    time.sleep(pause)


def tree():
    adb('shell', 'uiautomator', 'dump', '/sdcard/qa062.xml')
    return ET.fromstring(adb('shell', 'cat', '/sdcard/qa062.xml'))


def focus():
    return next(n for n in tree().iter('node') if n.get('focused') == 'true')


def desc():
    return focus().get('content-desc', '')


def image():
    return Image.open(io.BytesIO(subprocess.check_output([*BASE, 'exec-out', 'screencap', '-p']))).convert('RGB')


def shot(name):
    time.sleep(.4)
    image().save(SHOTS / name)


def start():
    adb('shell', 'am', 'force-stop', PKG)
    adb('shell', 'am', 'start', '-n', PKG + '/.ui.MainActivity')
    time.sleep(1.1)


def prefs():
    data = ET.fromstring(adb('shell', 'run-as', PKG, 'cat', 'shared_prefs/launcher.xml'))
    return {n.get('name'): n.get('value', n.text) for n in data}


assert len(json.loads(prefs()['favorites'])) == 28, 'Existing dedicated emulator fixtures required'
before = prefs()
adb('shell', 'run-as', PKG, 'cp', 'shared_prefs/launcher.xml', 'files/qa062-backup.xml')
recorder = None
try:
    start()
    # Exercise the new card motion in the user's preferred icon-only presentation.
    key(19); key(23); key(20); key(20)
    assert desc().startswith('显示应用名称')
    if desc().endswith('已开启'):
        key(23)
    key(4); key(22); key(22)
    assert desc() == 'YouTube'
    bounds = focus().get('bounds')
    adb('shell', 'input', 'keyevent', *(['22', '21'] * 12))
    time.sleep(.9)
    assert desc() == 'YouTube', 'Rapid reversals must preserve the final logical focus'
    assert focus().get('bounds') == bounds
    a = image().crop((50, 150, 1870, 1020))
    time.sleep(.5)
    b = image().crop((50, 150, 1870, 1020))
    assert ImageChops.difference(a, b).getbbox() is None, 'Focus must settle without an idle rendering animation'
    shot(f'still-{VERSION}-home.png')
    key(23, .01, True); key(4, .45)
    assert desc() == 'YouTube', 'Back during menu entrance must restore the original card'
    key(23, .5, True)
    assert desc() == '移动位置'
    shot(f'still-{VERSION}-menu.png')
    key(19); key(22)
    assert desc() == '移动位置', 'Context panel focus must be isolated from the home grid'
    key(20)
    assert desc() == '移出首页'
    shot(f'still-{VERSION}-menu-remove.png')
    key(20); key(20)
    assert desc() == '应用信息'
    key(23, .8)
    assert 'com.android.tv.settings/.device.apps.AppManagementActivity' in adb('shell', 'dumpsys', 'activity', 'activities')
    key(4, 1)
    assert desc() == 'YouTube'
    assert not any(n.get('text') == 'YouTube' for n in tree().iter('node')), 'Icon-only preference must survive external return'
    assert prefs()['favorites'] == before['favorites']
    assert prefs().get('bootEnabled') == before.get('bootEnabled')

    start()
    recorder = subprocess.Popen([*BASE, 'shell', 'screenrecord', '--time-limit', '24', '--bit-rate', '5000000', '/sdcard/still062.mp4'], stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
    time.sleep(.8)
    key(22, .7); key(22, .8); key(21, .5); key(22, .8)
    key(20, .65); key(19, .8)
    key(23, .8, True); key(20, .7); key(20, .7); key(19, .55); key(19, .6); key(4, .7)
    adb('shell', 'input', 'keyevent', *(['22', '21'] * 6)); time.sleep(.85)
    key(23, .7, True); key(4, .6)
    key(20, .6); key(20, .8); key(19, .5); key(19, .8)
    recorder.wait(timeout=30)
    assert recorder.returncode == 0, recorder.stderr.read().decode()
    adb('pull', '/sdcard/still062.mp4', str(MOVIES / f'still-{VERSION}-focus-menu.mp4'))
    print('PASS: rapid reversals, settled idle pixels, icon-only preference, interrupted menu entry/Back, trapped focus, three action states, native app info/return, unchanged favorites/boot; real 24-second recording captured', flush=True)
finally:
    if recorder is not None and recorder.poll() is None:
        recorder.terminate()
        recorder.wait(timeout=5)
    adb('shell', 'am', 'force-stop', PKG)
    adb('shell', 'run-as', PKG, 'cp', 'files/qa062-backup.xml', 'shared_prefs/launcher.xml')
    adb('shell', 'run-as', PKG, 'rm', 'files/qa062-backup.xml')
    adb('shell', 'am', 'start', '-n', PKG + '/.ui.MainActivity')
