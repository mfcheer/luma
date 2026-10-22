#!/usr/bin/env python3
"""Emulator-only design comparison with a temporary labelled/artwork fixture.

Prepare output/search090/visual-fixture/visual-fixture.apk before running. This test APK
reuses artwork read from the user's existing launcher cache, never bundled into Still.
Always restores fixture-24.apk and the emulator's original preferences.
"""
import json
import runpy
import time
import re
from pathlib import Path

q = runpy.run_path(str(Path(__file__).with_name('theme-080-smoke.py')))
adb, key = q['adb'], q['key']
PKG, ROOT = q['PKG'], q['ROOT']
OUT = ROOT / 'docs/qa-090'


def main():
    original = q['prefs']()
    gaps = {}
    name_checks = {}
    assert len(json.loads(original['appOrder'])) == 28
    fixture = ROOT / 'output/search090/visual-fixture/visual-fixture.apk'
    assert fixture.exists()
    adb('shell', 'run-as', PKG, 'cp', 'shared_prefs/launcher.xml', 'files/qa090-visual-backup.xml')
    try:
        adb('install', '--no-incremental', '-r', str(fixture))
        for light in (True, False):
            label = 'light' if light else 'dark'
            q['start'](); q['appearance'](); q['select_theme'](light)
            key(19); assert q['desc']().startswith('显示应用名称，')
            if q['prefs']().get('showAppNames', 'true') != 'false': key(23)
            key(4)
            q['image']().save(OUT / (label + '-home-final.png'))
            key(19); assert q['desc']() == '搜索'
            key(23, .7); adb('shell', 'input', 'text', 'jkj'); time.sleep(.3)
            state = q['tree']()
            assert {'找到 1 个应用', 'jkj'} <= {n.get('text') for n in state.iter('node')}
            assert any(n.get('content-desc') == '极空间' for n in state.iter('node'))
            assert not any(n.get('text') == '极空间' for n in state.iter('node')), 'Names off must hide the search label while keeping its accessible name'
            q['image']().save(OUT / (label + '-reference-state.png'))
            (OUT / (label + '-reference-state.xml')).write_text(q['ET'].tostring(state, encoding='unicode'))
            key(4, .6); assert q['desc']() == '极空间'
            image = q['image']()
            image.save(OUT / (label + '-reference-result.png'))
            state = q['tree']()
            count = next(n for n in state.iter('node') if n.get('text') == '找到 1 个应用')
            count_bottom = int(re.findall(r'\d+', count.get('bounds'))[3])
            blue_y = [y for y in range(260, 700) for x in range(100, 520)
                      if (lambda r,g,b: b > 150 and b > r * 1.6 and b > g * 1.2)(*image.getpixel((x,y)))]
            assert blue_y and min(blue_y) >= count_bottom + 8, 'Lifted artwork must remain clear of the count text'
            gaps[label] = {'countBottom':count_bottom,'focusedArtworkTop':min(blue_y),'gapPixels':min(blue_y)-count_bottom}
            key(4, .4)
            assert q['focused']().get('long-clickable') == 'true'
            assert q['prefs']()['appOrder'] == original['appOrder']
            q['appearance'](); key(19); assert q['desc']().startswith('显示应用名称，')
            key(23); assert q['prefs']().get('showAppNames') == 'true'
            key(4); key(19); assert q['desc']() == '搜索'
            key(23, .7); adb('shell', 'input', 'text', 'jkj'); time.sleep(.3)
            state = q['tree']()
            name = next(n for n in state.iter('node') if n.get('text') == '极空间')
            x0,y0,x1,y1 = map(int, re.findall(r'\d+', name.get('bounds')))
            image = q['image']()
            ink = [(x,y) for y in range(y0,y1) for x in range(x0,x1)
                   if (max(image.getpixel((x,y))) < 150 if light else min(image.getpixel((x,y))) > 110)]
            assert ink, 'Visible name must have readable text'
            ink_center = (min(x for x,y in ink) + max(x for x,y in ink)) / 2
            label_center = (x0+x1)/2
            assert abs(ink_center-label_center) < 4, ('Name ink must be centered', ink_center, label_center)
            image.save(OUT / (label + '-names-enabled.png'))
            key(4, .6); assert q['desc']() == '极空间'
            assert any(n.get('text') == '极空间' for n in q['tree']().iter('node'))
            q['image']().save(OUT / (label + '-names-enabled-focused.png'))
            key(4, .4)
            q['appearance'](); key(19); key(23); key(4)
            assert q['prefs']().get('showAppNames') == 'false'
            name_checks[label] = {'settingsToggleViaRemote':True,'hiddenLabelAbsent':True,'accessibleNameRetained':True,'visibleTextInkCenter':ink_center,'labelBoxCenter':label_center,'centerErrorPixels':abs(ink_center-label_center)}
            print('PASS: ' + label + ' icon-only and centered labels follow settings, native keyboard, clear count gap and Back restoration', flush=True)
    finally:
        adb('shell', 'am', 'force-stop', PKG)
        adb('install', '--no-incremental', '-r', '/Users/mfcheer/.cache/jianping-tools/fixtures/fixture-24.apk')
        adb('shell', 'run-as', PKG, 'cp', 'files/qa090-visual-backup.xml', 'shared_prefs/launcher.xml')
        adb('shell', 'run-as', PKG, 'rm', 'files/qa090-visual-backup.xml')
        q['start']()
        assert q['prefs']() == original
    (OUT / 'final-visual-smoke.json').write_text(json.dumps({'themes':['light','dark'], 'restoredOriginalFixtureAndPreferences':True, 'nativeIme':'emulator system keyboard; Sogou TV not tested', 'artwork':'temporary emulator fixture, never product assets'}, indent=2) + '\n')
    (OUT / 'focus-count-gap.json').write_text(json.dumps(gaps,indent=2)+'\n')
    (OUT / 'name-visibility-centering.json').write_text(json.dumps(name_checks,indent=2)+'\n')
    print('PASS: original fixture and preferences restored', flush=True)


if __name__ == '__main__': main()
