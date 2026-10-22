#!/usr/bin/env python3
"""Visual regression after softening native light-theme shadows; restores original preferences."""
import runpy
from pathlib import Path

qa = runpy.run_path(str(Path(__file__).with_name('theme-080-smoke.py')))
adb, key, tree = qa['adb'], qa['key'], qa['tree']
start, desc, shot, prefs = qa['start'], qa['desc'], qa['shot'], qa['prefs']
appearance, select_theme = qa['appearance'], qa['select_theme']
PKG = qa['PKG']
before = prefs()
adb('shell', 'run-as', PKG, 'cp', 'shared_prefs/launcher.xml', 'files/qa080-shadow-backup.xml')
try:
    for light in (True, False):
        start(); key(22)
        assert desc() == 'YouTube'
        appearance(); select_theme(light); shot('settings-' + ('light' if light else 'dark'))
        key(4); assert desc() == 'YouTube'
        label = 'light' if light else 'dark'
        key(22); key(21); tree()
        a = qa['image']().crop((50,145,1870,1010))
        tree(); b = qa['image']().crop((50,145,1870,1010))
        assert qa['ImageChops'].difference(a,b).getbbox() is None
        shot('home-' + label)
        key(23, .5, True); assert desc() == '移动位置'; shot('menu-' + label)
        key(23, .5); shot('moving-' + label)
        assert sum(n.get('text') == '移动中' for n in tree().iter('node')) == 1
        key(4)
        for _ in range(3): key(20)
        shot('scrolled-' + label)
        assert prefs()['appOrder'] == before['appOrder']
        assert prefs().get('showAppNames') == before.get('showAppNames')
        assert prefs().get('bootEnabled') == before.get('bootEnabled')
        print('PASS: final ' + label + ' home/idle pixels/menu/moving/scroll/settings; order/options preserved', flush=True)
finally:
    adb('shell', 'am', 'force-stop', PKG)
    adb('shell', 'run-as', PKG, 'cp', 'files/qa080-shadow-backup.xml', 'shared_prefs/launcher.xml')
    adb('shell', 'run-as', PKG, 'rm', 'files/qa080-shadow-backup.xml')
    adb('shell', 'am', 'start', '-n', PKG + '/.ui.MainActivity')
