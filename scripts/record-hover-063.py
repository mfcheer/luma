#!/usr/bin/env python3
"""Capture the real TV focus motion and measure the selected banner; restore emulator data."""
import io
import json
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
BASE = ['/Users/mfcheer/Library/Android/sdk/platform-tools/adb', '-s', 'emulator-5554']
PKG = 'com.jianping.tv'
SHOTS = ROOT / 'docs/screenshots'
QA = ROOT / 'docs/qa-063'


def adb(*args):
    return subprocess.check_output([*BASE, *args], text=True).strip()


def key(code, pause=.35, long=False):
    adb('shell', 'input', 'keyevent', *(['--longpress'] if long else []), str(code))
    time.sleep(pause)


def desc():
    adb('shell', 'uiautomator', 'dump', '/sdcard/hover063.xml')
    tree = ET.fromstring(adb('shell', 'cat', '/sdcard/hover063.xml'))
    return next(n.get('content-desc', '') for n in tree.iter('node') if n.get('focused') == 'true')


def shot(name):
    time.sleep(.6)
    image = Image.open(io.BytesIO(subprocess.check_output([*BASE, 'exec-out', 'screencap', '-p']))).convert('RGB')
    image.save(SHOTS / name)
    return image


def start():
    adb('shell', 'am', 'force-stop', PKG)
    adb('shell', 'am', 'start', '-n', PKG + '/.ui.MainActivity')
    time.sleep(1.1)


recorder = None
adb('shell', 'run-as', PKG, 'cp', 'shared_prefs/launcher.xml', 'files/hover063-backup.xml')
try:
    start(); key(19); key(23); key(20); key(20)
    assert desc().startswith('显示应用名称')
    if desc().endswith('已开启'):
        key(23)
    key(4); key(22); key(22)
    assert desc() == 'YouTube'
    image = shot('still-063-home.png')
    # Measure the real white banner against the 240px unfocused card width at 1080p.
    region = image.crop((780, 140, 1140, 420))
    white = region.convert('L').point(lambda p: 255 if p > 232 else 0)
    bounds = white.getbbox()
    assert bounds is not None and bounds[2] - bounds[0] >= 282, bounds
    assert bounds[1] + 140 > 172, 'Top edge must remain inside the viewport'
    corner = region.getpixel((bounds[0] + 2, bounds[1] + 2))
    assert max(corner) < 130, 'The top corner must stay rounded rather than being clipped flat'
    (QA / 'geometry.json').write_text(json.dumps({'selectedWhiteBannerBoundsInCrop': bounds,
        'cropOrigin': [780, 140], 'unfocusedCardWidthPx': 240,
        'selectedWhiteWidthPx': bounds[2] - bounds[0], 'topCornerPixel': corner}, indent=2))
    key(23, .6, True); shot('still-063-menu.png'); key(4, .5)
    key(22); key(22); shot('still-063-edge.png')
    key(20); key(20); shot('still-063-scrolled.png')
    for _ in range(8): key(20, .08)
    for _ in range(5): key(22, .08)
    assert desc() == '全部应用'
    key(23, .7); shot('still-063-drawer.png'); key(4, .5)
    start()
    recorder = subprocess.Popen([*BASE, 'shell', 'screenrecord', '--time-limit', '12',
        '--bit-rate', '6000000', '/sdcard/hover063.mp4'], stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
    time.sleep(.7)
    for code in [22, 22, 21, 22, 21, 22]:
        key(code, 1.0)
    recorder.wait(timeout=20)
    assert recorder.returncode == 0, recorder.stderr.read().decode()
    adb('pull', '/sdcard/hover063.mp4', str(ROOT / 'docs/recordings/still-063-hover.mp4'))
    print('PASS: selected banner about 1.2x, intact top corner, edge/scrolled/drawer captures, native hover recording', flush=True)
finally:
    if recorder is not None and recorder.poll() is None:
        recorder.terminate(); recorder.wait(timeout=5)
    adb('shell', 'am', 'force-stop', PKG)
    adb('shell', 'run-as', PKG, 'cp', 'files/hover063-backup.xml', 'shared_prefs/launcher.xml')
    adb('shell', 'run-as', PKG, 'rm', 'files/hover063-backup.xml')
    adb('shell', 'am', 'start', '-n', PKG + '/.ui.MainActivity')
