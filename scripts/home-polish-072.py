#!/usr/bin/env python3
"""Dedicated API33 TV emulator: visual states, finite motion, light tracking and reorder feedback."""
import io
import json
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path
from PIL import Image, ImageChops

ROOT = Path(__file__).resolve().parents[1]
BASE = ['/Users/mfcheer/Library/Android/sdk/platform-tools/adb', '-s', 'emulator-5554']
PKG = 'com.jianping.tv'
OUT = ROOT / 'docs/qa-072'
OUT.mkdir(exist_ok=True)
SHOTS = ROOT / 'docs/screenshots'


def adb(*args):
    return subprocess.check_output([*BASE, *args], text=True).strip()


def key(code, pause=.3, long=False):
    adb('shell', 'input', 'keyevent', *(['--longpress'] if long else []), str(code))
    time.sleep(pause)


def tree():
    for _ in range(4):
        adb('shell', 'rm', '-f', '/sdcard/qa072.xml')
        result = adb('shell', 'uiautomator', 'dump', '/sdcard/qa072.xml')
        if 'dumped to' in result:
            return ET.fromstring(adb('shell', 'cat', '/sdcard/qa072.xml'))
        time.sleep(.4)
    raise AssertionError('UIAutomator did not return a fresh hierarchy')


def focused():
    return next(n for n in tree().iter('node') if n.get('focused') == 'true')


def desc(): return focused().get('content-desc', '')


def texts(): return {n.get('text', '') for n in tree().iter('node')}


def image():
    return Image.open(io.BytesIO(subprocess.check_output([*BASE, 'exec-out', 'screencap', '-p']))).convert('RGB')


def shot(name):
    tree()  # Wait for idle; adb screenshots immediately after a key can still contain the old frame.
    image().save(SHOTS / f'still-072-{name}.png')


def prefs():
    xml = ET.fromstring(adb('shell', 'run-as', PKG, 'cat', 'shared_prefs/launcher.xml'))
    return {n.get('name'): n.get('value', n.text) for n in xml}


def start():
    adb('shell', 'am', 'force-stop', PKG)
    adb('shell', 'am', 'start', '-n', PKG + '/.ui.MainActivity')
    time.sleep(1.4)
    for _ in range(8):
        current = focused()
        if current.get('package') == PKG and current.get('long-clickable') == 'true':
            return
        time.sleep(.4)
    raise AssertionError('Home did not finish loading')


def go(index):
    start()
    for _ in range(index // 5): key(20, .15)
    for _ in range(index % 5): key(22, .15)
    time.sleep(.7)


def names(enabled):
    start(); key(19); key(23); key(20); key(20)
    assert desc().startswith('显示应用名称')
    if desc().endswith('已开启') != enabled: key(23)
    key(4)


before = prefs()
order = json.loads(before['appOrder'])
assert len(order) == 28 and len([i for i in order if 'com.jianping.fixture.' in i]) == 24
assert 'versionName=0.7.2' in adb('shell', 'dumpsys', 'package', PKG)
adb('shell', 'run-as', PKG, 'cp', 'shared_prefs/launcher.xml', 'files/qa072-backup.xml')
recorder = None
try:
    names(False)
    youtube = next(i for i, v in enumerate(order) if 'youtube' in v.lower())
    go(youtube)
    assert desc() == 'YouTube', f'Expected YouTube, actual {desc()}, index {youtube}'
    bounds = focused().get('bounds')
    assert youtube % 5 < 4
    adb('shell', 'input', 'keyevent', *(['22', '21'] * 12))
    time.sleep(1)
    assert desc() == 'YouTube' and focused().get('bounds') == bounds
    a = image().crop((50, 145, 1870, 1010)); time.sleep(.65)
    b = image().crop((50, 145, 1870, 1010))
    assert ImageChops.difference(a, b).getbbox() is None, 'Idle card/light animations must stop exactly'
    shot('home')
    print('PASS: 24 rapid reversals preserve focus/bounds; card and positional light settle to identical idle pixels', flush=True)

    # Select equal-colour fixtures across one row: blank pixels prove spatial tracking, not just colour.
    fixture_row = next(r for r in range(1, 5) if all('com.jianping.fixture.' in v for v in order[r*5:r*5+5]))
    go(fixture_row * 5)
    left = image().crop((100, 134, 1810, 160))
    for _ in range(4): key(22, .15)
    time.sleep(.8); tree()
    right = image().crop((100, 134, 1810, 160))
    assert ImageChops.difference(left, right).getbbox() is not None, 'Equal-colour focus must move ambient light spatially'
    shot('light-right')
    print('PASS: equal-colour fixtures change blank background pixels when focus position moves', flush=True)

    go(youtube)
    key(23, .02, True); key(4, .5)
    assert desc() == 'YouTube'
    key(23, .5, True)
    assert desc() == '移动位置'
    assert '快捷操作' not in texts()
    key(19); key(22); assert desc() == '移动位置'
    shot('menu')
    key(23, .6)
    assert '移动中' in texts()
    shot('moving')
    key(22); key(20)
    assert prefs()['appOrder'] == before['appOrder']
    assert sum(n.get('text') == '移动中' for n in tree().iter('node')) == 1
    shot('moving-row')
    key(4, .6)
    assert desc() == 'YouTube' and focused().get('bounds') == bounds
    assert '移动中' not in texts() and prefs()['appOrder'] == before['appOrder']
    print('PASS: compact two-item menu, interrupted entrance/Back, trapped focus, unique move badge across rows, draft-only order and exact cancel restoration', flush=True)

    for _ in range(3): key(20, .25)
    shot('scrolled')
    for _ in range(6): key(20, .08)
    for _ in range(5): key(22, .08)
    shot('last-row')
    names(True)
    go(youtube)
    assert 'YouTube' in texts()
    key(23, .5, True); key(23, .5)
    assert '移动中' in texts()
    key(4)
    assert 'YouTube' in texts() and '移动中' not in texts()
    shot('labels-on')
    key(19); key(23)
    shot('settings')
    assert prefs()['appOrder'] == before['appOrder']
    assert prefs().get('bootEnabled') == before.get('bootEnabled')
    print('PASS: scrolled and last-row states captured; label preference survives edit/cancel; app order and boot preference unchanged', flush=True)

    names(False); go(youtube)
    recorder = subprocess.Popen([*BASE, 'shell', 'screenrecord', '--time-limit', '18', '--bit-rate', '5000000', '/sdcard/still072.mp4'], stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
    time.sleep(.8)
    key(22, .65); key(21, .65); key(22, .65); key(21, .65)
    key(20, .65); key(20, .65); key(19, .65); key(19, .65)
    key(23, .65, True); key(23, .65); key(22, .65); key(4, .65)
    adb('shell', 'input', 'keyevent', *(['22', '21'] * 5)); time.sleep(1)
    recorder.wait(timeout=25)
    assert recorder.returncode == 0, recorder.stderr.read().decode()
    adb('pull', '/sdcard/still072.mp4', str(OUT / 'interactions.mp4'))
    print('PASS: actual input/animation recording captured', flush=True)
except BaseException:
    image().save(OUT / 'failure.png')
    try:
        (OUT / 'failure.xml').write_text(ET.tostring(tree(), encoding='unicode'))
    except Exception:
        pass
    raise
finally:
    if recorder and recorder.poll() is None:
        recorder.terminate(); recorder.wait(timeout=5)
    adb('shell', 'am', 'force-stop', PKG)
    adb('shell', 'run-as', PKG, 'cp', 'files/qa072-backup.xml', 'shared_prefs/launcher.xml')
    adb('shell', 'run-as', PKG, 'rm', 'files/qa072-backup.xml')
    adb('shell', 'am', 'start', '-n', PKG + '/.ui.MainActivity')
