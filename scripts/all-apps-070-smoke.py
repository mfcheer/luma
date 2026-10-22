#!/usr/bin/env python3
"""Dedicated 28-app emulator only: all-app home, migration, transactional order and package events."""
import json
import os
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = ['/Users/mfcheer/Library/Android/sdk/platform-tools/adb', '-s', 'emulator-5554']
PKG = 'com.jianping.tv'
FIXTURES = Path('/Users/mfcheer/.cache/jianping-tools/fixtures')
SHOTS = Path(os.environ.get('STILL_QA_SHOTS', str(ROOT / 'docs/screenshots')))
SHOTS.mkdir(parents=True, exist_ok=True)


def adb(*args):
    return subprocess.check_output([*BASE, *args], text=True).strip()


def key(code, pause=.3, long=False):
    adb('shell', 'input', 'keyevent', *(['--longpress'] if long else []), str(code))
    time.sleep(pause)


def tree():
    for _ in range(4):
        adb('shell', 'rm', '-f', '/sdcard/qa070.xml')
        result = adb('shell', 'uiautomator', 'dump', '/sdcard/qa070.xml')
        if 'dumped to' in result:
            return ET.fromstring(adb('shell', 'cat', '/sdcard/qa070.xml'))
        time.sleep(.4)
    raise AssertionError('UIAutomator did not return a fresh hierarchy')


def focus():
    return next(n for n in tree().iter('node') if n.get('focused') == 'true')


def desc():
    return focus().get('content-desc', '')


def prefs():
    xml = ET.fromstring(adb('shell', 'run-as', PKG, 'cat', 'shared_prefs/launcher.xml'))
    return {n.get('name'): n.get('value', n.text) for n in xml}


def order():
    return json.loads(prefs().get('appOrder', '[]'))


def start():
    adb('shell', 'am', 'force-stop', PKG)
    adb('shell', 'am', 'start', '-n', PKG + '/.ui.MainActivity')
    time.sleep(1.1)
    wait_count(28)
    for _ in range(8):
        current = focus()
        if current.get('package') == PKG and current.get('long-clickable') == 'true':
            return
        time.sleep(.4)
    raise AssertionError('Home did not finish loading')


def shot(name):
    time.sleep(.5)
    with (SHOTS / name).open('wb') as f:
        subprocess.run([*BASE, 'exec-out', 'screencap', '-p'], stdout=f, check=True)


def wait_order(expected):
    # SharedPreferences.apply() persists asynchronously; wait for the observable saved state.
    for _ in range(20):
        actual = order()
        if actual == expected:
            return
        time.sleep(.25)
    raise AssertionError(f'Expected saved order {expected}, actual {order()}, focused {desc()}')


def wait_count(count):
    for _ in range(16):
        if len(order()) == count:
            return
        time.sleep(.4)
    raise AssertionError(f'Expected {count} apps after package event: {order()}')


original_xml = adb('shell', 'run-as', PKG, 'cat', 'shared_prefs/launcher.xml')
original = prefs()
legacy = json.loads(original['favorites'])
assert len(legacy) == 28 and 'com.jianping.fixture.app24/' in legacy[-1]
assert (FIXTURES / 'fixture-24.apk').exists()
labels = {'Play Store', 'Settings', 'YouTube', 'Android TV Remote Service'} | {f'测试应用 {i:02}' for i in range(1, 25)}
adb('shell', 'run-as', PKG, 'mkdir', '-p', 'files')
adb('shell', 'run-as', PKG, 'cp', 'shared_prefs/launcher.xml', 'files/qa070-backup.xml')
removed = False


def seed(prefix=None):
    adb('shell', 'am', 'force-stop', PKG)
    xml = ET.fromstring(original_xml)
    for n in list(xml):
        if n.get('name') in {'favorites', 'appOrder', 'initialized', 'showAppNames'}:
            xml.remove(n)
    if prefix is not None:
        ET.SubElement(xml, 'string', name='favorites').text = json.dumps(prefix)
        ET.SubElement(xml, 'boolean', name='initialized', value='true')
    with tempfile.NamedTemporaryFile(suffix='.xml') as f:
        f.write(ET.tostring(xml)); f.flush()
        adb('push', f.name, '/data/local/tmp/still070-prefs.xml')
    adb('shell', 'run-as', PKG, 'cp', '/data/local/tmp/still070-prefs.xml', 'shared_prefs/launcher.xml')


try:
    seed(); start()
    assert len(order()) == 28, 'Fresh home must include every launchable app, not five defaults'
    assert set(order()) == set(legacy)
    seen = set()
    for _ in range(6):
        state = tree()
        seen |= {n.get('content-desc') for n in state.iter('node') if n.get('focusable') == 'true'}
        assert not any(n.get('content-desc') == '全部应用' for n in state.iter('node'))
        key(20)
    assert labels <= seen, f'All installed apps must appear on the home wall: {labels - seen}'
    print('PASS: fresh home renders all 28 apps without a separate all-apps entry', flush=True)

    prefix = [legacy[2], legacy[0]]
    seed(prefix); start()
    initial = order()
    assert initial[:2] == prefix and len(initial) == 28 and set(initial) == set(legacy)
    assert desc() == 'YouTube'
    bounds = focus().get('bounds')
    key(23, long=True)
    state = tree()
    texts = {n.get('text') for n in state.iter('node')}
    assert {'移动位置', '应用信息'} <= texts
    assert not {'添加到首页', '移出首页', '收藏'} & texts
    assert desc() == '移动位置'
    key(20); key(20); assert desc() == '应用信息'
    key(4); assert desc() == 'YouTube' and focus().get('bounds') == bounds
    key(23, long=True); key(23); key(22)
    assert order() == initial, 'Moving must remain a draft until confirmed'
    key(4); assert order() == initial
    key(23, long=True); key(23); key(20); key(23)
    expected = initial.copy(); moving = expected.pop(0); expected.insert(5, moving)
    wait_order(expected)
    start(); assert order() == expected
    print('PASS: legacy prefix migrates to full list; menu only moves/info; cancel/save/restart', flush=True)

    key(19); key(23); key(20); key(20)
    assert desc() == '显示应用名称，已开启'
    key(23); key(4)
    shot('still-070-home.png')
    key(23, long=True); shot('still-070-menu.png'); key(4)
    for _ in range(8): key(20, .1)
    for _ in range(5): key(22, .1)
    assert desc() != '全部应用'
    shot('still-070-last-row.png')
    start()
    focused = desc(); bounds = focus().get('bounds')
    before_update = order()
    adb('install', '--no-incremental', '-r', str(FIXTURES / 'fixture-01.apk')); time.sleep(1)
    assert order() == before_update, 'An APK replacement must retain its prior position'
    assert desc() == focused
    adb('uninstall', 'com.jianping.fixture.app24'); removed = True
    wait_count(27)
    after_remove = [v for v in before_update if 'com.jianping.fixture.app24/' not in v]
    assert order() == after_remove
    adb('install', '--no-incremental', str(FIXTURES / 'fixture-24.apk')); removed = False
    wait_count(28)
    assert order()[:-1] == after_remove and 'com.jianping.fixture.app24/' in order()[-1]
    assert desc() == focused and focus().get('bounds') == bounds
    print('PASS: update preserves position; uninstall removes entry; new install appends automatically without losing focus', flush=True)

    # Package changes during a draft must not discard new entries when it is saved.
    before_draft = order()
    key(23, long=True); key(23); key(22)
    adb('uninstall', 'com.jianping.fixture.app24'); removed = True
    wait_count(27)
    adb('install', '--no-incremental', str(FIXTURES / 'fixture-24.apk')); removed = False
    wait_count(28)
    key(20); key(23)
    expected = before_draft.copy(); moving = expected.pop(0); expected.insert(6, moving)
    wait_order(expected)
    assert len(set(order())) == 28
    start(); assert order() == expected
    for _ in range(5): key(20, .1)
    key(22); key(22)
    assert desc() == '测试应用 24'
    bounds = focus().get('bounds'); key(23, .8)
    assert 'com.jianping.fixture.app24/' in adb('shell', 'dumpsys', 'activity', 'activities')
    key(4, .8)
    assert desc() == '测试应用 24' and focus().get('bounds') == bounds
    key(23, long=True); key(20); key(23, .8)
    assert 'com.android.tv.settings/.device.apps.AppManagementActivity' in adb('shell', 'dumpsys', 'activity', 'activities')
    key(4, .8); assert desc() == '测试应用 24'
    assert prefs()['showAppNames'] == 'false'
    assert prefs().get('bootEnabled') == original.get('bootEnabled')
    print('PASS: package events during reorder, full membership retained, launch/info and exact return, label/boot settings preserved', flush=True)
finally:
    adb('shell', 'am', 'force-stop', PKG)
    if removed:
        adb('install', '--no-incremental', str(FIXTURES / 'fixture-24.apk'))
    adb('shell', 'run-as', PKG, 'cp', 'files/qa070-backup.xml', 'shared_prefs/launcher.xml')
    adb('shell', 'run-as', PKG, 'rm', 'files/qa070-backup.xml')
    adb('shell', 'rm', '-f', '/data/local/tmp/still070-prefs.xml')
    adb('shell', 'am', 'start', '-n', PKG + '/.ui.MainActivity')
