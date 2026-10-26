#!/usr/bin/env python3
"""Fixture emulator only: both menus, end-order persistence, focus and Home handoff.

Never targets the user's TV; restores the emulator's complete launcher preferences.
"""
import json
import re
import runpy
from pathlib import Path

q = runpy.run_path(str(Path(__file__).with_name('theme-080-smoke.py')))
adb, key = q['adb'], q['key']
PKG = q['PKG']
OUT = q['ROOT'] / 'docs/qa-096'


def order():
    return json.loads(q['prefs']()['appOrder'])


def focus():
    n = q['focused']()
    assert n.get('package') == PKG, n.attrib
    return n.get('content-desc'), n.get('bounds')


def choose(label):
    for _ in range(4):
        if focus()[0] == label:
            key(23, .7)
            return
        key(20)
    raise AssertionError('Menu item not reachable: ' + label)


def menu(search=False):
    key(23, .4, long=True)
    labels = {n.get('content-desc') for n in q['tree']().iter('node')}
    expected = {'移到最前', '移到最后', '应用信息', '在首页定位' if search else '移动位置'}
    assert expected <= labels, labels
    assert focus()[0] == ('移到最前' if search else '移动位置')


def main():
    original = q['prefs']()
    xml = q['prefs_xml']()
    initial = order()
    assert len(initial) == 28 and sum('com.jianping.fixture.' in i for i in initial) == 24
    assert 'versionName=0.9.6' in adb('shell', 'dumpsys', 'package', PKG)
    OUT.mkdir(exist_ok=True)
    adb('shell', 'run-as', PKG, 'cp', 'shared_prefs/launcher.xml', 'files/qa096-backup.xml')
    checks = []
    try:
        for light in (True, False):
            theme = 'light' if light else 'dark'
            configured = q['ET'].fromstring(xml)
            for n in list(configured):
                if n.get('name') == 'appearance': configured.remove(n)
            q['ET'].SubElement(configured, 'string', name='appearance').text = theme
            q['seed'](configured)
            q['start']()
            name = focus()[0]
            menu()
            key(20); key(20)
            assert focus()[0] == '移到最后'
            q['image']().save(OUT / (theme + '-home-menu.png'))
            key(23, .7)
            expected = initial[1:] + initial[:1]
            assert order() == expected, 'Home demotion must preserve every other entry'
            selected = focus()
            assert selected[0] == name
            x1, y1, x2, y2 = map(int, re.findall(r'\d+', selected[1]))
            assert 0 <= x1 < x2 <= 1920 and 190 <= y1 < y2 <= 1060, selected
            q['image']().save(OUT / (theme + '-home-last.png'))
            menu(); choose('移到最后')
            assert order() == expected and focus() == selected, 'Already last must be stable'
            q['start']()
            assert order() == expected, 'Demotion must survive a real cold restart'
            q['go'](len(initial) - 1)
            assert focus()[0] == name
            menu(); choose('移到最前')
            assert order() == initial and focus()[0] == name, 'Promotion must still work'
            menu(); choose('移动位置'); key(22); key(4, .5)
            assert order() == initial, 'Manual move cancellation must remain intact'
            before = focus()
            menu(); choose('应用信息'); key(4, .7)
            assert focus() == before, 'App information must return to the same Home app'

            key(4); key(19)
            assert focus()[0] == '搜索'
            key(23, .8); adb('shell', 'input', 'text', 'cs'); key(4, .7)
            for _ in range(3): key(20, .15)
            search_name = focus()[0]
            assert search_name.startswith('测试应用')
            fixture = int(search_name.rsplit(' ', 1)[1])
            target = next(i for i in initial if f'com.jianping.fixture.app{fixture:02d}/' in i)
            before = focus()
            menu(True); key(4, .5)
            assert focus() == before and order() == initial, 'Search menu cancel must retain focus/scroll'
            menu(True); key(20)
            assert focus()[0] == '移到最后'
            q['image']().save(OUT / (theme + '-search-menu.png'))
            key(23, .7)
            expected = [i for i in initial if i != target] + [target]
            assert order() == expected and focus()[0] == search_name
            assert any(n.get('text') == 'cs' for n in q['tree']().iter('node'))
            after = focus()
            menu(True); choose('移到最后')
            assert order() == expected and focus() == after, 'Repeated search demotion must be a no-op'
            menu(True); choose('应用信息'); key(4, .7)
            assert focus() == after, 'Search app information must remain reachable'
            menu(True); choose('在首页定位')
            assert order() == expected and focus()[0] == search_name
            q['image']().save(OUT / (theme + '-home-from-search.png'))
            key(22)
            assert focus()[0] == search_name, 'Home handoff must select the actual final card'
            q['start']()
            assert order() == expected, 'Search demotion must persist after restart'
            for option in ('bootEnabled', 'showAppNames'):
                assert q['prefs']().get(option) == original.get(option)
            checks.append({'theme': theme, 'homeAndSearchMoveLast': True,
                           'relativeOrderAndPersistence': True, 'repeatedActionStable': True,
                           'queryAndFocusPreserved': True, 'homeHandoff': True,
                           'existingFrontMoveInfoLocateAndCancel': True})
            print('PASS: ' + theme + ' Home/search move-last, persistence, focus and original actions', flush=True)
    except BaseException:
        q['image']().save(OUT / 'failure.png')
        (OUT / 'failure.xml').write_text(q['ET'].tostring(q['tree'](), encoding='unicode'))
        raise
    finally:
        adb('shell', 'am', 'force-stop', PKG)
        adb('shell', 'run-as', PKG, 'cp', 'files/qa096-backup.xml', 'shared_prefs/launcher.xml')
        adb('shell', 'run-as', PKG, 'rm', 'files/qa096-backup.xml')
        q['start']()
        assert q['prefs']() == original
    (OUT / 'move-last-smoke.json').write_text(json.dumps(
        {'checks': checks, 'originalPreferencesRestored': True}, indent=2) + '\n')
    print('PASS: original emulator preferences restored', flush=True)


if __name__ == '__main__':
    main()
