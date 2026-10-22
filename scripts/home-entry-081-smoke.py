#!/usr/bin/env python3
"""Dedicated emulator: default-home state, visible fallback, alternate launcher and diagnostics."""
import json
import re
import runpy
import subprocess
import time
from pathlib import Path

qa=runpy.run_path(str(Path(__file__).with_name('theme-080-smoke.py')))
adb,key,tree,prefs=(qa[n] for n in ('adb','key','tree','prefs'))
PKG=qa['PKG']; OUT=qa['ROOT']/'docs/qa-081'
original=prefs()
assert len(json.loads(original['appOrder']))==28


def shot(name):
    state=tree();qa['image']().save(OUT/(name+'.png'))
    (OUT/(name+'.xml')).write_text(qa['ET'].tostring(state,encoding='unicode'))
    return state


def texts(state=None): return {n.get('text','') for n in (state if state is not None else tree()).iter('node')}


def button(label):
    for _ in range(8):
        state=tree();target=next(n for n in state.iter('node') if n.get('text')==label and n.get('class')=='android.widget.Button')
        focused=next(n for n in state.iter('node') if n.get('focused')=='true')
        if focused.get('text')==label: key(23);return
        t=list(map(int,re.findall(r'\d+',target.get('bounds'))));f=list(map(int,re.findall(r'\d+',focused.get('bounds'))))
        tx=(t[0]+t[2])/2;ty=(t[1]+t[3])/2;fx=(f[0]+f[2])/2;fy=(f[1]+f[3])/2
        key((20 if ty>fy else 19) if abs(ty-fy)>30 else (22 if tx>fx else 21))
    raise AssertionError('Cannot remote-focus button '+label)


def enter():
    qa['start']();key(19);key(23)
    assert qa['desc']()=='默认桌面'


try:
    enter();key(23)
    state=shot('home-options');current=texts(state)
    assert {'默认桌面','系统桌面设置','打开其他桌面','关闭'}<=current
    assert any('当前检测：' in t for t in current)
    button('关闭');assert qa['desc']()=='默认桌面'
    print('PASS: default-home click always opens a visible state/options panel; close restores row focus',flush=True)

    key(23);button('系统桌面设置');time.sleep(1.6)
    state=shot('home-system-result')
    external=any(n.get('package') not in {PKG,'','com.android.systemui'} for n in state.iter('node'))
    fallback='系统未显示桌面选择页' in texts(state)
    assert external or fallback, 'Home settings silently returned without visible feedback'
    if fallback: button('关闭')
    else: key(4,.5)
    print('PASS: system desktop settings opens a visible system surface or explains an unavailable picker',flush=True)

    enter();key(23);button('打开其他桌面')
    assert '打开其他桌面' in texts()
    choices=shot('other-home-choices')
    items=[n.get('text') for n in choices.iter('node') if n.get('resource-id')=='android:id/text1']
    assert items==['Android TV Home'], items
    key(23,.6)
    state=shot('other-home')
    assert any(n.get('package')=='com.google.android.tvlauncher' for n in state.iter('node'))
    print('PASS: alternate Home entry opens the original TV launcher explicitly without disabling it or changing defaults',flush=True)

    enter()
    for _ in range(6):key(20)
    assert qa['desc']()=='设备与启动信息'
    key(23);state=shot('device-info')
    message=next(n.get('text') for n in state.iter('node') if n.get('resource-id')=='android:id/message')
    assert 'Home 解析组件' in message and '可用桌面入口' in message
    assert '\n当前默认桌面  android\n' not in message
    (OUT/'device-report.txt').write_text(message)
    key(4);key(4)
    for k in ('appOrder','showAppNames','bootEnabled','appearance'):assert prefs().get(k)==original.get(k),k
    print('PASS: diagnostics distinguishes resolver from real launcher, lists Home components and preserves all preferences',flush=True)
except BaseException:
    qa['image']().save(OUT/'home-entry-failure.png')
    (OUT/'home-entry-failure.xml').write_text(qa['ET'].tostring(tree(),encoding='unicode'))
    raise
finally:
    adb('shell','am','start','-n',PKG+'/.ui.MainActivity')
