#!/usr/bin/env python3
"""Dedicated API33 TV emulator: appearance defaults, persistence and visual/focus regression.

Temporarily seeds preferences; always restores them. Requires the 28-app QA fixture set.
"""
import io
import json
import re
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET
from pathlib import Path
from PIL import Image, ImageChops

ROOT = Path(__file__).resolve().parents[1]
BASE = ['/Users/mfcheer/Library/Android/sdk/platform-tools/adb', '-s', 'emulator-5554']
PKG = 'com.jianping.tv'
OUT = ROOT / 'docs/qa-080'
SHOTS = ROOT / 'docs/screenshots'
OUT.mkdir(exist_ok=True)
SHOTS.mkdir(exist_ok=True)


def adb(*args):
    return subprocess.check_output([*BASE, *args], text=True).strip()


def key(code, pause=.25, long=False):
    adb('shell', 'input', 'keyevent', *(['--longpress'] if long else []), str(code))
    time.sleep(pause)


def tree():
    for _ in range(4):
        adb('shell', 'rm', '-f', '/sdcard/qa080.xml')
        result = adb('shell', 'uiautomator', 'dump', '/sdcard/qa080.xml')
        if 'dumped to' in result:
            return ET.fromstring(adb('shell', 'cat', '/sdcard/qa080.xml'))
        time.sleep(.4)
    raise AssertionError('UIAutomator did not return a fresh hierarchy')


def focused():
    return next(n for n in tree().iter('node') if n.get('focused') == 'true')


def desc(): return focused().get('content-desc', '')


def prefs_xml(): return adb('shell', 'run-as', PKG, 'cat', 'shared_prefs/launcher.xml')


def prefs():
    return {n.get('name'): n.get('value', n.text) for n in ET.fromstring(prefs_xml())}


def image():
    return Image.open(io.BytesIO(subprocess.check_output([*BASE, 'exec-out', 'screencap', '-p']))).convert('RGB')


def shot(name):
    state = tree()
    image().save(SHOTS / f'still-080-{name}.png')
    (OUT / f'{name}.xml').write_text(ET.tostring(state, encoding='unicode'))


def assert_theme(light):
    r, g, b = image().getpixel((1880, 1040))
    assert (min(r, g, b) > 225) if light else (max(r, g, b) < 65), (r, g, b)


def start():
    adb('shell', 'am', 'force-stop', PKG)
    adb('shell', 'am', 'start', '-n', PKG + '/.ui.MainActivity')
    time.sleep(1.3)
    for _ in range(8):
        state = tree()
        current = next((n for n in state.iter('node') if n.get('focused') == 'true'), None)
        if current is not None and current.get('package') == PKG and current.get('long-clickable') == 'true': return
        # Native instrumentation/mouse input can leave the emulator in touch mode.
        # Establish remote navigation through a real direction key, including a header landing.
        key(20, .2)
        time.sleep(.4)
    raise AssertionError('Home did not finish loading')


def seed(xml):
    adb('shell', 'am', 'force-stop', PKG)
    with tempfile.NamedTemporaryFile(suffix='.xml') as f:
        f.write(ET.tostring(xml)); f.flush()
        adb('push', f.name, '/data/local/tmp/still080-prefs.xml')
    adb('shell', 'run-as', PKG, 'cp', '/data/local/tmp/still080-prefs.xml', 'shared_prefs/launcher.xml')


def go(index):
    start()
    for _ in range(index // 5): key(20, .15)
    for _ in range(index % 5): key(22, .15)
    tree()


def appearance():
    # Scroll up without changing the selected app; then enter settings through the header.
    for _ in range(7): key(19, .08)
    if desc() == '搜索': key(22)
    assert desc() == '设置'
    key(23)
    for _ in range(8):
        if desc() == '外观主题': break
        key(20)
    assert desc() == '外观主题'


def select_theme(light):
    key(23)
    state = tree()
    choices = [n for n in state.iter('node') if n.get('checkable') == 'true']
    assert [n.get('text') for n in choices] == ['浅色', '深色']
    selected = next(i for i, n in enumerate(choices) if n.get('selected') == 'true')
    target = 0 if light else 1
    if target != selected: key(20 if target > selected else 19)
    key(23)
    assert desc() == '外观主题', 'Applying a theme must retain the settings row focus'
    assert_theme(light)
    for _ in range(10):
        if prefs().get('appearance', 'light') == ('light' if light else 'dark'): break
        time.sleep(.2)
    assert prefs().get('appearance', 'light') == ('light' if light else 'dark')


def main():
    original_xml = prefs_xml()
    original = prefs()
    order = json.loads(original['appOrder'])
    assert len(order) == 28 and len([i for i in order if 'com.jianping.fixture.' in i]) == 24
    assert 'versionName=0.8.0' in adb('shell', 'dumpsys', 'package', PKG)
    adb('shell', 'run-as', PKG, 'cp', 'shared_prefs/launcher.xml', 'files/qa080-backup.xml')
    try:
        seed(ET.Element('map')); start(); assert_theme(True)
        assert len(json.loads(prefs()['appOrder'])) == 28
        print('PASS: fresh install defaults to pearl-white light theme with all 28 apps', flush=True)
        upgrade = ET.fromstring(original_xml)
        for n in list(upgrade):
            if n.get('name') == 'appearance': upgrade.remove(n)
        seed(upgrade); start(); assert_theme(True)
        assert prefs()['appOrder'] == original['appOrder']
        assert prefs().get('showAppNames') == original.get('showAppNames')
        print('PASS: upgrade without appearance preference defaults to light and preserves existing order/options', flush=True)

        # Store a distinctive boot/name combination so theme roundtrips cannot reset unrelated options.
        configured = ET.fromstring(original_xml)
        for n in list(configured):
            if n.get('name') in {'appearance', 'bootEnabled', 'showAppNames'}: configured.remove(n)
        ET.SubElement(configured, 'boolean', name='bootEnabled', value='true')
        ET.SubElement(configured, 'boolean', name='showAppNames', value='false')
        seed(configured); start()
        appearance(); shot('settings-light')
        rows = [n for n in tree().iter('node') if n.get('content-desc') == '设备与启动信息']
        assert len(rows) == 1 and list(map(int, re.findall(r'\d+', rows[0].get('bounds'))))[3] <= 1004
        before_cancel = prefs()
        key(23); shot('picker-light'); key(20); key(4)
        assert prefs() == before_cancel and desc() == '外观主题'
        select_theme(True)  # Choosing the already-active option also retains focus.
        select_theme(False); shot('settings-dark')
        key(23); shot('picker-dark'); key(4)
        # Native info dialog must change with the activity theme without requiring a restart.
        for _ in range(3): key(20)
        assert desc() == '设备与启动信息'
        key(23); shot('info-dark'); key(4)
        key(4); assert_theme(False)
        start(); assert_theme(False)
        appearance(); select_theme(True)
        for _ in range(3): key(20)
        key(23); shot('info-light'); key(4); key(4)
        start(); assert_theme(True)
        assert prefs()['appOrder'] == original['appOrder']
        assert prefs()['bootEnabled'] == 'true' and prefs()['showAppNames'] == 'false'
        print('PASS: 7 settings rows visible; picker cancel/same option; both themes apply immediately, retain focus and persist across cold starts; native dialogs follow theme', flush=True)

        youtube = next(i for i, v in enumerate(order) if 'youtube' in v.lower())
        for light in (True, False):
            go(youtube)
            if not light:
                appearance(); select_theme(False); key(4)
            label = 'light' if light else 'dark'
            assert desc() == 'YouTube'
            bounds = focused().get('bounds')
            adb('shell', 'input', 'keyevent', *(['22', '21'] * 12)); time.sleep(.9)
            assert desc() == 'YouTube' and focused().get('bounds') == bounds
            a = image().crop((50, 145, 1870, 1010)); time.sleep(.6)
            b = image().crop((50, 145, 1870, 1010))
            assert ImageChops.difference(a, b).getbbox() is None, 'Idle animation must stop'
            shot(f'home-{label}')
            key(23, .5, True); assert desc() == '移动位置'
            key(19); key(22); assert desc() == '移动位置'
            shot(f'menu-{label}')
            key(23, .5); shot(f'moving-{label}')
            assert sum(n.get('text') == '移动中' for n in tree().iter('node')) == 1
            key(22); key(20); key(4)
            assert desc() == 'YouTube' and focused().get('bounds') == bounds
            assert prefs()['appOrder'] == original['appOrder']
            for _ in range(3): key(20)
            scroll_focus = desc(); scroll_bounds = focused().get('bounds')
            shot(f'scrolled-{label}')
            # Entering settings moves toward the top and changes selectedId. Capture the actual
            # application/bounds at that entry, then require an exact return after a theme change.
            appearance()
            key(4)
            returning = desc(); returning_bounds = focused().get('bounds')
            key(19); key(23)
            for _ in range(8):
                if desc() == '外观主题': break
                key(20)
            select_theme(not light); key(4)
            assert desc() == returning and focused().get('bounds') == returning_bounds
            assert prefs()['appOrder'] == original['appOrder']
            assert prefs()['bootEnabled'] == 'true' and prefs()['showAppNames'] == 'false'
            print(f'PASS: {label} home rapid reversals/idle pixels, context focus isolation, unique move text, cross-row cancel, scroll and theme return; preferences preserved', flush=True)
            # Start the next pass with the intended theme.
            if light:
                appearance(); select_theme(False); key(4)

        # Equal-colour fixture cards isolate position changes from accent-colour changes.
        appearance(); select_theme(True); key(4)
        fixture_row = next(r for r in range(1, 5) if all('com.jianping.fixture.' in v for v in order[r*5:r*5+5]))
        go(fixture_row * 5)
        left = image().crop((100, 134, 1810, 160))
        for _ in range(4): key(22, .15)
        tree(); right = image().crop((100, 134, 1810, 160))
        diff = ImageChops.difference(left, right)
        changed = sum(pixel != (0, 0, 0) for pixel in diff.getdata())
        assert changed > 0, 'Light-theme accent must follow equal-colour cards spatially'
        (OUT / 'light-position.json').write_text(json.dumps({'crop': [100,134,1810,160], 'changedPixels': changed}))
        go(youtube)
        appearance(); key(19)
        assert desc().startswith('显示应用名称')
        key(23); key(4); shot('labels-light')
        assert prefs()['showAppNames'] == 'true'
        print(f'PASS: light-theme positional accent changes {changed} blank pixels; names remain optional', flush=True)
    except BaseException:
        image().save(OUT / 'failure.png')
        try: (OUT / 'failure.xml').write_text(ET.tostring(tree(), encoding='unicode'))
        except Exception: pass
        raise
    finally:
        adb('shell', 'am', 'force-stop', PKG)
        adb('shell', 'run-as', PKG, 'cp', 'files/qa080-backup.xml', 'shared_prefs/launcher.xml')
        adb('shell', 'run-as', PKG, 'rm', 'files/qa080-backup.xml')
        adb('shell', 'rm', '-f', '/data/local/tmp/still080-prefs.xml')
        adb('shell', 'am', 'start', '-n', PKG + '/.ui.MainActivity')


if __name__ == "__main__":
    main()
