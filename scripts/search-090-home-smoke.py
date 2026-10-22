#!/usr/bin/env python3
"""Emulator only: existing menu/settings and search Home stack behavior, no default change."""
import json
import runpy
import time
from pathlib import Path

q = runpy.run_path(str(Path(__file__).with_name('theme-080-smoke.py')))
adb, key = q['adb'], q['key']
PKG = q['PKG']


def main():
    original = q['prefs']()
    default = adb('shell', 'cmd', 'package', 'resolve-activity', '--brief', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME').splitlines()[-1]
    assert default.startswith('com.google.android.tvlauncher/'), 'This test expects the dedicated emulator original launcher'
    adb('shell', 'run-as', PKG, 'cp', 'shared_prefs/launcher.xml', 'files/qa090-home-backup.xml')
    try:
        q['start']()
        home = q['focused']().attrib
        key(23, .4, long=True)
        state = q['tree']()
        descriptions = {n.get('content-desc') for n in state.iter('node')}
        assert {'移动位置', '移到最前', '应用信息'} <= descriptions
        key(20); assert q['desc']() == '移到最前'
        key(20); assert q['desc']() == '应用信息'
        key(4, .4); assert q['focused']().get('bounds') == home['bounds']
        key(19); key(22); assert q['desc']() == '设置'
        key(23); assert q['desc']() == '默认桌面'
        key(4); assert q['focused']().get('bounds') == home['bounds']
        key(19); key(23, .8); adb('shell', 'input', 'text', 'YouTube'); key(4, .6)
        assert q['desc']() == 'YouTube'
        key(3, .7)
        assert q['focused']().get('package') == 'com.google.android.tvlauncher', 'System Home obeys the real default launcher'
        # Deliver the intent Still receives when chosen as HOME, without changing the system default.
        adb('shell', 'am', 'start', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME', '-n', PKG+'/.ui.MainActivity')
        time.sleep(.5)
        assert q['focused']().get('bounds') == home['bounds']
        key(19); key(23, .8)
        state = q['tree']()
        editor = next(n for n in state.iter('node') if n.get('class') == 'android.widget.EditText')
        assert editor.get('text') in ('', '输入名称或拼音首字母'), 'HOME removes the old search activity; a new search starts empty'
        key(4); key(4)
        assert q['prefs']()['appOrder'] == original['appOrder']
        assert adb('shell', 'cmd', 'package', 'resolve-activity', '--brief', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME').splitlines()[-1] == default
    finally:
        adb('shell', 'am', 'force-stop', PKG)
        adb('shell', 'run-as', PKG, 'cp', 'files/qa090-home-backup.xml', 'shared_prefs/launcher.xml')
        adb('shell', 'run-as', PKG, 'rm', 'files/qa090-home-backup.xml')
        q['start'](); assert q['prefs']() == original
    (q['ROOT']/'docs/qa-090/home-menu-smoke.json').write_text(json.dumps({'menuNavigationAndCancel':True,'settingsAndBack':True,'systemHomeUsesConfiguredLauncher':default,'explicitStillHomeClearsSearchStack':True,'newSearchQueryEmpty':True,'systemDefaultUnchanged':True,'originalPreferencesRestored':True},indent=2)+'\n')
    print('PASS: menu/settings navigation, configured system Home and explicit Still HOME stack, preferences and default restored/unchanged', flush=True)


if __name__ == '__main__': main()
