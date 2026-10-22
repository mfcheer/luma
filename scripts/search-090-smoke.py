#!/usr/bin/env python3
"""Fixture emulator only: real search/IME/navigation/state restoration, with preferences restored."""
import json
import runpy
import subprocess
import time
from pathlib import Path

q = runpy.run_path(str(Path(__file__).with_name('theme-080-smoke.py')))
adb, key, tree = (q[n] for n in ('adb', 'key', 'tree'))
PKG = q['PKG']
OUT = q['ROOT'] / 'docs/qa-090'


def focus():
    n = q['focused']()
    assert n.get('package') == PKG, n.attrib
    return n.get('content-desc'), n.get('bounds')


def texts():
    return {n.get('text', '') for n in tree().iter('node')}


def search(query):
    key(19)
    assert focus()[0] == '搜索'
    key(23, .8)
    assert focus()[0] == '搜索应用名称或拼音首字母'
    if query:
        adb('shell', 'input', 'text', query)
        time.sleep(.3)


def main():
    original = q['prefs']()
    assert len(json.loads(original['appOrder'])) == 28
    assert 'versionName=0.9.0' in adb('shell', 'dumpsys', 'package', PKG)
    OUT.mkdir(exist_ok=True)
    adb('shell', 'run-as', PKG, 'cp', 'shared_prefs/launcher.xml', 'files/qa090-backup.xml')
    passed = []
    try:
        for light in (True, False):
            label = 'light' if light else 'dark'
            q['start'](); q['appearance'](); q['select_theme'](light); key(4)
            q['go'](2); home = focus()
            q['image']().save(OUT / (label + '-home-header.png'))
            key(19); assert focus()[0] == '搜索'
            q['image']().save(OUT / (label + '-search-header-focus.png'))
            key(22); assert focus()[0] == '设置'
            q['image']().save(OUT / (label + '-settings-header-focus.png'))
            key(21); assert focus()[0] == '搜索'
            key(20); assert focus() == home, 'Header Down must restore its prior app column'
            adb('logcat', '-c')
            search('cs')
            assert '找到 24 个应用' in texts(), 'Chinese pinyin initials must work against real fixture labels'
            assert focus()[0] == '搜索应用名称或拼音首字母', 'Live results must not steal input focus'
            q['image']().save(OUT / (label + '-keyboard.png'))
            key(4, .7)
            assert focus()[0].startswith('测试应用'), 'First Back hides the IME and focuses results'
            for _ in range(3): key(20, .12)
            result = focus()
            q['image']().save(OUT / (label + '-results.png'))
            key(23, .5)
            assert any(n.get('package', '').startswith('com.jianping.fixture.') for n in tree().iter('node'))
            key(4, .6)
            assert focus() == result and 'cs' in texts(), 'App exit restores search query, result focus and scroll'
            key(23, .5)
            assert any(n.get('package', '').startswith('com.jianping.fixture.') for n in tree().iter('node'))
            adb('shell', 'am', 'kill', PKG)
            for _ in range(20):
                killed = subprocess.run([*q['BASE'], 'shell', 'pidof', PKG], capture_output=True, text=True)
                if not killed.stdout.strip(): break
                time.sleep(.1)
            assert not killed.stdout.strip(), 'The launcher process must actually be killed before recovery'
            key(4, 1.2)
            restored = focus()
            assert restored == result and 'cs' in texts(), ('Process death state', result, restored)
            key(4, .5)
            assert focus() == home, 'Closing search restores the original home selection and bounds'
            print('PASS: ' + label + ': pinyin/live input focus, IME Back, multi-row results, launch/return, process-death recovery and original home position', flush=True)
            passed.append(label + ': pinyin/IME/focus/launch/process-death/home restoration')

            search('YouTube')
            assert '找到 1 个应用' in texts()
            key(4, .6); assert focus()[0] == 'YouTube'
            key(4, .5); assert focus() == home
            search('zzzznomatch')
            assert '未找到应用，请换个关键词' in texts()
            key(4, .6); assert focus()[0] == '搜索应用名称或拼音首字母'
            key(22); assert focus()[0] == '清空搜索'
            key(23, .5)
            assert '输入名称或拼音首字母，例如 jkj' in texts()
            key(4, .5); key(4, .5)
            assert focus() == home
            assert q['prefs']()['appOrder'] == original['appOrder']
            print('PASS: ' + label + ': English matching, no results, remote clear, empty state, Back stages and unchanged home order', flush=True)
            passed.append(label + ': English/no-match/clear/empty/Back/order')

        for k in ('appOrder', 'showAppNames', 'bootEnabled'):
            assert q['prefs']().get(k) == original.get(k)
    except BaseException:
        q['image']().save(OUT / 'failure.png')
        (OUT / 'failure.xml').write_text(q['ET'].tostring(tree(), encoding='unicode'))
        raise
    finally:
        adb('shell', 'am', 'force-stop', PKG)
        adb('shell', 'run-as', PKG, 'cp', 'files/qa090-backup.xml', 'shared_prefs/launcher.xml')
        adb('shell', 'run-as', PKG, 'rm', 'files/qa090-backup.xml')
        q['start']()
        assert q['prefs']() == original
    passed.append('Original preference map restored')
    (OUT / 'search-smoke.json').write_text(json.dumps(passed, indent=2) + '\n')
    print('PASS: preferences restored', flush=True)


if __name__ == '__main__':
    main()
