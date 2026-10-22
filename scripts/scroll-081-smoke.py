#!/usr/bin/env python3
"""Dedicated fixture emulator: continuous row-edge rendering and remote navigation regression."""
import json
import runpy
import subprocess
import sys
import time
from pathlib import Path
from PIL import Image, ImageChops, ImageStat

qa = runpy.run_path(str(Path(__file__).with_name('theme-080-smoke.py')))
adb, key, tree, prefs = (qa[n] for n in ('adb', 'key', 'tree', 'prefs'))
ROOT, PKG = qa['ROOT'], qa['PKG']
OUT = ROOT / 'docs/qa-081'
SHOTS = ROOT / 'docs/screenshots'
original = prefs()
order = json.loads(original['appOrder'])
assert len(order) == 28 and sum('com.jianping.fixture.' in v for v in order) == 24
assert 'versionName=0.8.1' in adb('shell', 'dumpsys', 'package', PKG)
adb('shell', 'run-as', PKG, 'cp', 'shared_prefs/launcher.xml', 'files/qa081-scroll-backup.xml')


def shot(name, preview=False):
    state = tree()
    im = qa['image']()
    im.save(OUT / (name + '.png'))
    if preview: im.save(SHOTS / ('still-081-' + name + '.png'))
    (OUT / (name + '.xml')).write_text(qa['ET'].tostring(state, encoding='unicode'))


def edge_metrics(label):
    folder = OUT / 'scroll' / label
    rows = []
    for a, b in ((310,311),(311,312),(312,313),(313,314),(622,623),(623,624),(624,625),(625,626)):
        first = Image.open(folder / f'{a}.png').convert('RGB').crop((95,164,1825,300))
        second = Image.open(folder / f'{b}.png').convert('RGB').crop((95,164,1825,300))
        rows.append(sum(ImageStat.Stat(ImageChops.difference(first, second)).mean) / 3)
    return rows


def record(label):
    path = '/sdcard/still081-scroll-' + label + '.mp4'
    process = subprocess.Popen([*qa['BASE'], 'shell', 'screenrecord', '--time-limit', '12', '--bit-rate', '5000000', path], stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
    try:
        time.sleep(.7)
        for code in [20]*4 + [19]*4 + [20]*4 + [19]*4:
            key(code, .3)
        process.wait(timeout=18)
        assert process.returncode == 0, process.stderr.read().decode()
        adb('pull', path, str(OUT / ('scroll-' + label + '.mp4')))
    finally:
        if process.poll() is None: process.terminate(); process.wait(timeout=5)


try:
    before, after = edge_metrics('before'), edge_metrics('after')
    assert max(before) > 20, 'Before images must reproduce the brightness discontinuity'
    assert max(after) < 3 and max(after) / min(after) < 1.3, after
    print('PASS: exact one-pixel samples across two row boundaries reproduce the old flash and show continuous light-theme rendering after the fix', flush=True)

    youtube = next(i for i, v in enumerate(order) if 'youtube' in v.lower())
    for light in (True, False):
        label = 'light' if light else 'dark'
        qa['start'](); qa['appearance'](); qa['select_theme'](light); key(4)
        qa['go'](youtube)
        assert qa['desc']() == 'YouTube'
        top = qa['focused']().get('bounds')
        record(label)
        assert qa['desc']() == 'YouTube' and qa['focused']().get('bounds') == top
        for _ in range(2):
            adb('shell','input','keyevent',*(['20']*4+['19']*4))
        time.sleep(.9)
        assert qa['desc']() == 'YouTube' and qa['focused']().get('bounds') == top
        shot('home' if light else 'home-dark', preview=light)
        a = qa['image']().crop((50,145,1870,1010)); time.sleep(.6)
        b = qa['image']().crop((50,145,1870,1010))
        assert ImageChops.difference(a,b).getbbox() is None, 'Scroll/light motion must settle'

        for _ in range(3): key(20,.15)
        name, bounds = qa['desc'](), qa['focused']().get('bounds')
        shot('scrolled-' + label)
        key(23,.4,True); key(23,.4); key(20); key(22); key(4,.5)
        assert qa['desc']() == name and qa['focused']().get('bounds') == bounds
        assert prefs()['appOrder'] == original['appOrder']
        adb('shell','am','start','-n','com.jianping.fixture.app24/com.jianping.fixture.MainActivity')
        time.sleep(.4); key(4,.5)
        assert qa['desc']() == name and qa['focused']().get('bounds') == bounds
        print(f'PASS: {label} recorded down/up scrolling, rapid reversals, exact focus/offset restoration, finite idle pixels, cross-row move/cancel and external app return',flush=True)

        if not light:
            subprocess.run([sys.executable, str(ROOT/'scripts/scroll-edge-probe.py'), 'dark'], check=True)
            dark = edge_metrics('dark')
            assert max(dark) < 4 and max(dark) / min(dark) < 1.5, dark

    (OUT/'scroll-edge-metrics.json').write_text(json.dumps({'region':[95,164,1825,300], 'description':'Mean absolute RGB change per one-pixel step; not a frame-rate measurement', 'before':before,'light':after,'dark':dark}, indent=2))
    print('PASS: dark-theme row-boundary pixels also remain continuous',flush=True)
    qa['start'](); qa['appearance'](); qa['select_theme'](True)
    shot('settings', preview=True)
    for k in ('appOrder','showAppNames','bootEnabled'): assert prefs().get(k)==original.get(k), k
except BaseException:
    qa['image']().save(OUT/'scroll-failure.png')
    raise
finally:
    adb('shell','am','force-stop',PKG)
    adb('shell','run-as',PKG,'cp','files/qa081-scroll-backup.xml','shared_prefs/launcher.xml')
    adb('shell','run-as',PKG,'rm','files/qa081-scroll-backup.xml')
    qa['go'](next(i for i,v in enumerate(order) if 'youtube' in v.lower()))
    assert prefs() == original, 'Restore the exact original preference map'
