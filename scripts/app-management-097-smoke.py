#!/usr/bin/env python3
"""Dedicated emulator only: real IME editing and system-confirmed fixture uninstall.

Restores all launcher/name/usage preferences and reinstalls the one removed QA fixture.
Never connects to the user's TV or uninstalls a personal/system application.
"""
import argparse
import json
import re
import runpy
import subprocess
import time
from pathlib import Path

q = runpy.run_path(str(Path(__file__).with_name('theme-080-smoke.py')))
adb, key, tree = (q[n] for n in ('adb', 'key', 'tree'))
PKG = q['PKG']
FIXTURE = 'com.jianping.fixture.app16'
APK = Path.home() / '.cache/jianping-tools/fixtures/fixture-16.apk'
OUT = q['ROOT'] / 'docs/qa-097'
MENUS = {
    False: ['移动位置', '移到最前', '移到最后', '重命名', '应用信息', '卸载应用'],
    True: ['移到最前', '移到最后', '在首页定位', '重命名', '应用信息', '卸载应用'],
}


def focus():
    node = q['focused']()
    assert node.get('package') == PKG, node.attrib
    return node.get('content-desc'), node.get('bounds')


def open_menu(search=False):
    key(23, .4, long=True)
    state = tree()
    assert set(MENUS[search]) <= {(n.get('content-desc') or '').split('，', 1)[0]
                                for n in state.iter('node')}
    assert focus()[0] == MENUS[search][0]
    for n in state.iter('node'):
        if (n.get('content-desc') or '').split('，', 1)[0] in MENUS[search]:
            x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
            assert 0 <= y1 < y2 <= 1080, n.attrib


def select(label, search=False):
    for _ in range(MENUS[search].index(label)): key(20, .1)
    assert focus()[0] == label
    key(23, .7)


def edit(value):
    assert focus()[0] == '应用显示名称'
    adb('shell', 'input', 'keycombination', '113', '29')
    adb('shell', 'input', 'text', value.replace(' ', '%s'))
    key(4, .6)
    state = tree()
    field = next(n for n in state.iter('node') if n.get('content-desc') == '应用显示名称')
    assert field.get('text') == value, field.attrib
    assert any(n.get('text') == '重命名' for n in state.iter('node')), 'First Back only hides keyboard'


def save():
    key(20); assert focus()[0] == '保存'; key(23, .8)


def search(query):
    key(4); home = focus(); key(19); assert focus()[0] == '搜索'; key(23, .8)
    # Wait for the floating IME and all injected key events before dismissing it.
    state = tree()
    assert any(n.get('content-desc') == '搜索应用名称或拼音首字母'
               for n in state.iter('node'))
    adb('shell', 'input', 'text', query)
    state = tree()
    assert any(n.get('content-desc') == '搜索应用名称或拼音首字母'
               and n.get('text') == query for n in state.iter('node'))
    key(4, .7)
    return home


def names():
    if subprocess.run([*q['BASE'], 'shell', 'run-as', PKG, 'test', '-f',
                       'shared_prefs/app_names_v1.xml']).returncode != 0: return {}
    s = adb('shell', 'run-as', PKG, 'cat', 'shared_prefs/app_names_v1.xml')
    return {n.get('name'): n.get('value', n.text) for n in q['ET'].fromstring(s)}


def order(): return json.loads(q['prefs']()['appOrder'])


def wait_focus(expected):
    for _ in range(10):
        state = tree()
        current = next((n for n in state.iter('node') if n.get('focused') == 'true'), None)
        if current is not None and current.get('content-desc') == expected: return
        time.sleep(.3)
    raise AssertionError(f'Expected focus {expected}, got {current.attrib if current is not None else None}')


def wait_order_size(expected):
    for _ in range(20):
        if len(order()) == expected: return
        time.sleep(.3)
    raise AssertionError(f'Catalog did not settle at {expected} apps')


def system_confirmation(confirm):
    state = tree()
    assert any(n.get('package', '') != PKG and n.get('text') == '测试应用 16'
               for n in state.iter('node')), 'System dialog must name the original app'
    if not confirm:
        key(4, .9)
        return
    # Actual confirmation through D-pad navigation, not adb uninstall.
    for _ in range(4):
        current = q['focused']()
        if any(n.get('text') == 'OK' for n in current.iter('node')): break
        key(19)
    assert any(n.get('text') == 'OK' for n in q['focused']().iter('node'))
    key(23, 1.5)
    assert FIXTURE not in adb('shell', 'pm', 'list', 'packages', FIXTURE)


def main(theme_filter='both', uninstall_only=False):
    assert 'versionName=0.9.7' in adb('shell', 'dumpsys', 'package', PKG)
    assert adb('shell', 'pm', 'list', 'packages').count('package:com.jianping.fixture.app') == 24
    assert APK.is_file()
    original_xml = q['prefs_xml']()
    original = q['prefs']()
    initial = order()
    assert len(initial) == 28
    target = next(i for i in initial if i.startswith(FIXTURE + '/'))
    slot = initial.index(target)
    OUT.mkdir(exist_ok=True)
    backups = {}
    for file in ('launcher.xml', 'app_names_v1.xml', 'app_usage_v1.xml'):
        exists = subprocess.run([*q['BASE'], 'shell', 'run-as', PKG, 'test', '-f',
                                 'shared_prefs/' + file]).returncode == 0
        backups[file] = exists
        if exists: adb('shell', 'run-as', PKG, 'cp', 'shared_prefs/' + file, 'files/qa097-' + file)
    checks = []
    try:
        for dark in (False, True):
            theme = 'dark' if dark else 'light'
            if theme_filter != 'both' and theme_filter != theme: continue
            xml = q['ET'].fromstring(original_xml)
            for n in list(xml):
                if n.get('name') == 'appearance': xml.remove(n)
            q['ET'].SubElement(xml, 'string', name='appearance').text = theme
            q['seed'](xml)
            adb('shell', 'run-as', PKG, 'rm', '-f', 'shared_prefs/app_names_v1.xml')
            adb('shell', 'run-as', PKG, 'rm', '-f', 'shared_prefs/app_usage_v1.xml')
            q['go'](slot)
            before = focus(); assert before[0] == '测试应用 16'
            if not uninstall_only:
                open_menu()
                q['image']().save(OUT / (theme + '-home-menu.png'))
                select('重命名')
                q['image']().save(OUT / (theme + '-rename-keyboard.png'))
                edit('Canceled Name'); key(4, .5)
                assert focus() == before and order() == initial, 'Cancel preserves exact app/scroll/order'
                assert 'name.' + FIXTURE not in names()
                open_menu(); select('重命名'); edit('Family Cinema'); save()
                assert focus()[0] == 'Family Cinema' and order() == initial
                q['image']().save(OUT / (theme + '-renamed-home.png'))
                q['go'](slot); assert focus()[0] == 'Family Cinema', 'Custom name persists across cold restart'
                open_menu(); select('重命名'); key(4, .5); key(20); key(21); key(21)
                assert focus()[0] == '恢复原名'; key(23, .7)
                assert focus()[0] == '测试应用 16' and 'name.' + FIXTURE not in names()
            home_before = search('cs')
            match_slot = [i for i in initial if 'com.jianping.fixture.' in i].index(target)
            for _ in range(match_slot // 5): key(20, .1)
            for _ in range(match_slot % 5): key(22, .1)
            assert focus()[0] == '测试应用 16'
            open_menu(True); select('重命名', True); edit('MovieRoom'); save()
            assert focus()[0] == 'MovieRoom'
            assert any(n.get('text') == 'cs' for n in tree().iter('node'))
            open_menu(True)
            assert any(n.get('text') == '原名称：测试应用 16' for n in tree().iter('node'))
            q['image']().save(OUT / (theme + '-search-menu.png'))
            select('卸载应用', True)
            q['image']().save(OUT / (theme + '-system-uninstall.png'))
            system_confirmation(False)
            assert focus()[0] == 'MovieRoom' and order() == initial
            assert names()['name.' + FIXTURE] == 'MovieRoom'
            if not dark:
                # New-name query, followed by uninstalling the only result.
                key(4); home_before = search('movieroom')
                assert focus()[0] == 'MovieRoom'
                assert any(n.get('text') == '找到 1 个应用' for n in tree().iter('node'))
            open_menu(True); select('卸载应用', True); system_confirmation(True)
            assert 'name.' + FIXTURE not in names(), 'Only confirmed uninstall clears customization'
            if dark:
                matches = [i for i in initial if 'com.jianping.fixture.' in i]
                remaining = [i for i in matches if i != target]
                neighbor = remaining[min(matches.index(target), len(remaining) - 1)]
                expected_name = '测试应用 ' + neighbor.split('/')[0][-2:]
                wait_focus(expected_name)
                assert any(n.get('text') == 'cs' for n in tree().iter('node'))
            else:
                wait_focus('搜索应用名称或拼音首字母')
            q['image']().save(OUT / (theme + '-after-uninstall.png'))
            key(4, .6)
            assert focus() == home_before, 'Removing a different app must retain the original Home focus'
            assert order() == [i for i in initial if i != target]
            adb('install', '--no-incremental', str(APK)); time.sleep(.8)
            q['start'](); wait_order_size(28)
            assert order()[-1] == target, 'Reinstall appends without altering existing order'
            assert 'name.' + FIXTURE not in names(), 'Reinstall starts with original name'
            if dark:
                # Exercise the Home request/result path and the final-card fallback too.
                current_order = order()
                q['go'](26); previous_last = focus()[0]
                q['go'](27); selected_last = focus()
                assert selected_last[0] == '测试应用 16'
                open_menu(); select('卸载应用'); system_confirmation(False)
                assert focus() == selected_last and order() == current_order
                open_menu(); select('卸载应用'); system_confirmation(True)
                wait_focus(previous_last)
                assert order() == current_order[:-1]
                q['image']().save(OUT / 'dark-home-after-uninstall.png')
                adb('install', '--no-incremental', str(APK)); time.sleep(.8); q['start']()
            q['go'](0); key(4)
            open_menu()
            state = tree()
            system_row = next(n for n in state.iter('node')
                              if n.get('content-desc') == '卸载应用，系统应用')
            assert system_row.get('enabled') == 'false' and system_row.get('focusable') == 'false'
            q['image']().save(OUT / (theme + '-system-app-protected.png'))
            key(4)
            checks.append({'theme': theme,
                           'originalAndCustomSearch': True, 'systemUninstallCancel': True,
                           'actualSystemConfirmedUninstall': True, 'originalHomeFocusPreserved': True,
                           'emptySearchFocus' if not dark else 'adjacentSearchFocus': True,
                           'reinstallAndSystemAppProtection': True})
            if not uninstall_only: checks[-1]['renameCancelSaveRestoreAndColdRestart'] = True
            if dark: checks[-1]['homeSystemUninstallCancelSuccessAndLastCardFallback'] = True
            print('PASS: ' + theme + ' rename/search/real system uninstall/focus recovery', flush=True)
    except BaseException:
        q['image']().save(OUT / 'failure.png')
        (OUT / 'failure.xml').write_text(q['ET'].tostring(tree(), encoding='unicode'))
        raise
    finally:
        if FIXTURE not in adb('shell', 'pm', 'list', 'packages', FIXTURE):
            adb('install', '--no-incremental', str(APK))
        adb('shell', 'am', 'force-stop', PKG)
        for file, exists in backups.items():
            if exists:
                adb('shell', 'run-as', PKG, 'cp', 'files/qa097-' + file, 'shared_prefs/' + file)
                adb('shell', 'run-as', PKG, 'rm', 'files/qa097-' + file)
            else: adb('shell', 'run-as', PKG, 'rm', '-f', 'shared_prefs/' + file)
        q['start'](); assert q['prefs']() == original
    (OUT / 'management-smoke.json').write_text(json.dumps(
        {'themeFilter': theme_filter, 'uninstallOnly': uninstall_only, 'checks': checks,
         'originalPreferencesNamesUsageAndFixtureRestored': True}, indent=2) + '\n')
    print('PASS: all original emulator data restored', flush=True)
    for file in ('failure.png', 'failure.xml'): (OUT / file).unlink(missing_ok=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--theme', choices=('both', 'light', 'dark'), default='both')
    parser.add_argument('--uninstall-only', action='store_true')
    args = parser.parse_args()
    main(args.theme, args.uninstall_only)
