#!/usr/bin/env python3
"""Dedicated fixture emulator: move-to-front persistence and existing menu actions.

Restores the complete original preference file; never targets the user's TV.
"""
import json
import runpy
from pathlib import Path

qa = runpy.run_path(str(Path(__file__).with_name('theme-080-smoke.py')))
adb, key, prefs = (qa[n] for n in ('adb', 'key', 'prefs'))
PKG = qa['PKG']
OUT = qa['ROOT'] / 'docs/qa-083'


def main():
    original = prefs()
    order = json.loads(original['appOrder'])
    assert len(order) == 28 and sum('com.jianping.fixture.' in v for v in order) == 24
    assert 'versionName=0.8.3' in adb('shell', 'dumpsys', 'package', PKG)
    OUT.mkdir(exist_ok=True)
    adb('shell', 'run-as', PKG, 'cp', 'shared_prefs/launcher.xml', 'files/qa083-backup.xml')
    results = []
    try:
        for light in (True, False):
            label = 'light' if light else 'dark'
            qa['start'](); qa['appearance'](); qa['select_theme'](light); key(4)
            qa['go'](0)
            top_bounds = qa['focused']().get('bounds')
            before = json.loads(prefs()['appOrder'])
            qa['go'](25)
            name = qa['desc']()
            key(23, .4, True)
            state = qa['tree']()
            assert {'移动位置', '移到最前', '应用信息'} <= {n.get('content-desc') for n in state.iter('node')}
            assert qa['desc']() == '移动位置'
            key(20); assert qa['desc']() == '移到最前'
            qa['image']().save(OUT / (label + '-menu.png'))
            key(23, .7)
            expected = [before[25]] + before[:25] + before[26:]
            assert json.loads(prefs()['appOrder']) == expected, 'Only the selected app changes relative order'
            assert qa['desc']() == name and qa['focused']().get('bounds') == top_bounds
            qa['start']()
            assert qa['desc']() == name and json.loads(prefs()['appOrder']) == expected
            # Already first: repeating the action cannot duplicate or otherwise reorder apps.
            key(23, .4, True); key(20); key(23, .7)
            assert qa['desc']() == name and json.loads(prefs()['appOrder']) == expected
            print('PASS: ' + label + ': deep-row app moves to first, others retain relative order; focus, restart persistence and repeated action verified', flush=True)
            results.append(label + ': move-to-front, focus/scroll, restart persistence, first-item idempotence')

            qa['go'](10)
            selected = qa['desc']()
            key(23, .4, True); key(20); key(20)
            assert qa['desc']() == '应用信息', 'Existing app-info action remains reachable'
            key(4, .5)
            assert qa['desc']() == selected and json.loads(prefs()['appOrder']) == expected
            key(23, .4, True); key(23, .4); key(22); key(4, .6)
            assert qa['desc']() == selected and json.loads(prefs()['appOrder']) == expected
            key(23, .4, True); key(23, .4); key(22); key(23, .6)
            saved = expected.copy(); saved[10], saved[11] = saved[11], saved[10]
            assert json.loads(prefs()['appOrder']) == saved and qa['desc']() == selected
            key(4, .6)
            assert qa['focused']().get('bounds') == top_bounds
            assert qa['desc']() == name, 'Existing home Back shortcut must still work'
            print('PASS: ' + label + ': menu cancellation, app-info navigation, manual move cancel/save and home Back unchanged', flush=True)
            results.append(label + ': menu cancel/info navigation, manual move cancel/save, home Back')
        for name in ('showAppNames', 'bootEnabled'):
            assert prefs().get(name) == original.get(name)
    finally:
        adb('shell', 'am', 'force-stop', PKG)
        adb('shell', 'run-as', PKG, 'cp', 'files/qa083-backup.xml', 'shared_prefs/launcher.xml')
        adb('shell', 'run-as', PKG, 'rm', 'files/qa083-backup.xml')
        qa['start']()
        assert prefs() == original
    results.append('Complete original preference map restored')
    (OUT / 'move-first-smoke.json').write_text(json.dumps(results, indent=2) + '\n')
    print('PASS: original preferences restored', flush=True)


if __name__ == '__main__':
    main()
