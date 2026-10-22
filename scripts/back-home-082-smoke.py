#!/usr/bin/env python3
"""Dedicated fixture emulator: Back-to-first-row and Back action priority.

Uses real remote key events. Never targets the user's TV; restores emulator preferences.
"""
import json
import runpy
import time
from pathlib import Path

qa = runpy.run_path(str(Path(__file__).with_name('theme-080-smoke.py')))
adb, key, prefs = (qa[n] for n in ('adb', 'key', 'prefs'))
PKG = qa['PKG']
OUT = qa['ROOT'] / 'docs/qa-082'


def focus():
    node = qa['focused']()
    assert node.get('package') == PKG, node.attrib
    return node.get('content-desc', ''), node.get('bounds')


def main():
    original = prefs()
    order = json.loads(original['appOrder'])
    assert len(order) == 28 and sum('com.jianping.fixture.' in v for v in order) == 24
    assert 'versionName=0.8.2' in adb('shell', 'dumpsys', 'package', PKG)
    OUT.mkdir(exist_ok=True)
    adb('shell', 'run-as', PKG, 'cp', 'shared_prefs/launcher.xml', 'files/qa082-backup.xml')
    results = []

    def passed(message):
        results.append(message)
        print('PASS: ' + message, flush=True)

    try:
        for light in (True, False):
            label = 'light' if light else 'dark'
            qa['start'](); qa['appearance'](); qa['select_theme'](light); key(4)
            qa['go'](0)
            first = focus()
            for _ in range(3): key(22, .1)
            selected = focus()
            key(4, .6)
            assert focus() == selected, 'Back on first row must retain its selected app'
            qa['go'](5)
            key(4, .6)
            assert focus() == first, 'Second row is still on screen but must return to first app'
            qa['go'](25)
            deep = focus()
            assert deep != first
            qa['image']().save(OUT / (label + '-before.png'))
            pid = adb('shell', 'pidof', PKG)
            key(4, .6)
            assert focus() == first, 'Back must restore the exact first-app viewport bounds'
            assert adb('shell', 'pidof', PKG) == pid, 'Back must reuse the existing launcher process'
            qa['image']().save(OUT / (label + '-after.png'))
            time.sleep(.6)
            assert focus() == first, 'An older scrolling animation must not move it down again'
            for codes in ([20] * 4 + [4], [20] * 5 + [4, 4, 4], [20, 20, 4, 20, 4]):
                adb('shell', 'input', 'keyevent', *map(str, codes))
                time.sleep(.6)
                assert focus() == first, 'Rapid Down/Back sequences must settle at the top'
            passed(label + ': first-row selection retained; second/last row returns to first app; rapid and repeated Back settle correctly')

            qa['go'](15)
            deep = focus()
            key(23, .4, True)
            assert focus()[0] == '移动位置'
            key(4, .6)
            assert focus() == deep, 'First Back must only close the menu'
            key(4, .6)
            assert focus() == first
            passed(label + ': Back closes the deep-row menu; the next Back returns to first row')

            qa['go'](15)
            deep = focus()
            key(23, .4, True); key(23, .4); key(20); key(22); key(4, .6)
            assert focus() == deep, 'Move cancellation must retain original app/viewport'
            assert prefs()['appOrder'] == original['appOrder'], 'Cancelled order must not be saved'
            key(4, .6)
            assert focus() == first
            passed(label + ': Back cancels cross-row moving without saving; the next Back returns to first row')

            qa['go'](15)
            deep = focus()
            adb('shell', 'am', 'start', '-n', 'com.jianping.fixture.app24/com.jianping.fixture.MainActivity')
            time.sleep(.3); key(4, .6)
            assert focus() == deep, 'External-app Back must still restore its prior home position'
            key(4, .6)
            assert focus() == first
            passed(label + ': Back from an external app restores the deep-row position; Back on home then returns to top')

            for _ in range(3): key(22, .1)
            selected = focus()
            key(19); assert focus()[0] == '设置'
            key(23); assert focus()[0] == '默认桌面'
            key(4, .6)
            assert focus() == selected, 'Back from settings must preserve its prior home selection'
            key(4, .6)
            assert focus() == selected
            passed(label + ': settings Back restores the home selection; subsequent Back keeps first-row selection')

        for name in ('appOrder', 'showAppNames', 'bootEnabled'):
            assert prefs().get(name) == original.get(name), name
    except BaseException:
        qa['image']().save(OUT / 'failure.png')
        raise
    finally:
        adb('shell', 'am', 'force-stop', PKG)
        adb('shell', 'run-as', PKG, 'cp', 'files/qa082-backup.xml', 'shared_prefs/launcher.xml')
        adb('shell', 'run-as', PKG, 'rm', 'files/qa082-backup.xml')
        qa['start']()
        assert prefs() == original, 'Restore the exact original preference map'
    passed('all original emulator preferences restored; no app install/uninstall, TV access or default-home changes')
    (OUT / 'back-smoke.json').write_text(json.dumps(results, indent=2, ensure_ascii=False) + '\n')


if __name__ == '__main__':
    main()
