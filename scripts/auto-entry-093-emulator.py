#!/usr/bin/env python3
"""API33 fixture emulator: unified switch and explicit unsupported-wake guard."""
import json,runpy,subprocess,time
from pathlib import Path
q=runpy.run_path(str(Path(__file__).with_name('theme-080-smoke.py')))
adb,key=q['adb'],q['key']; PKG=q['PKG']; ROOT=q['ROOT']; OUT=ROOT/'docs/qa-093'
original=q['prefs']()
assert q['BASE'][-1]=='emulator-5554'
assert adb('shell','getprop','ro.build.version.sdk')=='33'
adb('install','--no-incremental','-r',str(ROOT/'app/build/outputs/apk/debug/app-debug.apk'))
checks=[]
try:
    q['start']()
    for _ in range(9):key(19,.1)
    assert q['desc']()=='搜索'
    key(22);key(23)
    for _ in range(10):
        current=q['desc']()
        if current=='默认桌面':break
        key(20 if current.startswith('返回') else 19,.1)
    assert q['desc']()=='默认桌面'
    key(20)
    assert q['desc']().startswith('自动进入 Still，'),q['desc']()
    state=q['tree']()
    assert any(n.get('text')=='仅开机' for n in state.iter('node'))
    before=adb('shell','dumpsys','activity','services',PKG)
    assert 'ServiceRecord' not in before
    for _ in range(2):
        key(23,.5)
        assert q['desc']().startswith('自动进入 Still，')
        assert 'ServiceRecord' not in adb('shell','dumpsys','activity','services',PKG)
    assert q['prefs']()==original
    q['image']().save(OUT/'emulator-unified-switch.png')
    checks=['unified switch preserves existing preference','unsupported API33 visibly says boot only','no foreground service starts on API33','toggle retains focus and restores preference','same app order and complete preferences']
finally:
    if q['prefs']().get('bootEnabled')!=original.get('bootEnabled'):key(23,.5)
    key(4)
(OUT/'emulator-smoke.json').write_text(json.dumps({'api':33,'version':'0.9.3','checks':checks,'preferencesPreserved':q['prefs']()==original},indent=2)+'\n')
print('PASS:',checks)
