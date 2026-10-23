#!/usr/bin/env python3
"""Capture Still 0.9.0 screenshots on an explicitly selected TV; no recording or installation.

Navigates only within Still. Theme changes are restored through the UI; move drafts are
cancelled. Raw preference backup and UI dumps stay in the ignored output directory.
"""
import argparse
import io
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path
from PIL import Image

p = argparse.ArgumentParser()
p.add_argument('--serial', required=True)
args = p.parse_args()
ROOT = Path(__file__).resolve().parents[1]
ADB = str(Path.home() / 'Library/Android/sdk/platform-tools/adb')
BASE = [ADB, '-s', args.serial]
PKG = 'com.jianping.tv'
OUT = ROOT / 'output/real-tv-presentation-090'
OUT.mkdir(parents=True, exist_ok=True)


def adb(*args, timeout=15):
    return subprocess.check_output([*BASE, *args], text=True, timeout=timeout).strip()


def key(code, pause=.65, long=False):
    adb('shell', 'input', 'keyevent', *(['--longpress'] if long else []), str(code))
    time.sleep(pause)


def tree():
    for _ in range(3):
        adb('shell','rm','-f','/sdcard/still-presentation.xml')
        result = adb('shell','uiautomator','dump','/sdcard/still-presentation.xml',timeout=20)
        if 'dumped to' in result:
            return ET.fromstring(adb('shell','cat','/sdcard/still-presentation.xml'))
    raise AssertionError('No fresh TV hierarchy')


def focus():
    for _ in range(3):
        selected=next((n for n in tree().iter('node') if n.get('focused') == 'true'),None)
        if selected is not None: return selected
        time.sleep(.3)
    raise AssertionError('No focused Still control; check that TV display is awake')
def desc(): return focus().get('content-desc', '')
def prefs(): return {n.get('name'): n.get('value', n.text) for n in ET.fromstring(adb('shell','run-as',PKG,'cat','shared_prefs/launcher.xml'))}


def shot(name):
    state=tree()
    raw=subprocess.check_output([*BASE,'exec-out','screencap','-p'],timeout=20)
    im=Image.open(io.BytesIO(raw)); assert im.size == (1920,1080), im.size
    (OUT/(name+'.png')).write_bytes(raw)
    (OUT/(name+'.xml')).write_text(ET.tostring(state,encoding='unicode'))
    print('Captured',name,flush=True)


def home_top():
    # Wake when needed; check state before the power fallback, never change sleep settings.
    key(224,.7)
    power=adb('shell','dumpsys','power')
    if 'mWakefulness=Asleep' in power:
        # This MiTV firmware ignores WAKEUP. Use POWER only after confirming asleep.
        key(26,2)
    assert 'mWakefulness=Awake' in adb('shell','dumpsys','power'), 'TV is still asleep'
    adb('shell','am','start','-W','-n',PKG+'/.ui.MainActivity',timeout=20)
    time.sleep(.5)
    for _ in range(10): key(19,.2)
    assert desc() == '搜索', desc()
    key(20)
    for _ in range(4): key(21,.15)


def appearance():
    home_top(); key(19); key(22); key(23)
    labels=['默认桌面','开机启动','显示应用名称','外观主题','系统设置','刷新应用列表','设备与启动信息']
    for _ in range(8):
        current=desc()
        if current == '外观主题': return
        index=next((i for i,label in enumerate(labels) if current.startswith(label)),0)
        key(19 if index>3 else 20,.2)
    raise AssertionError('Theme row not found')


def theme(light):
    appearance(); key(23,.3)
    state=tree()
    choices=[n for n in state.iter('node') if n.get('checkable') == 'true']
    assert [n.get('text') for n in choices] == ['浅色','深色']
    selected=next(i for i,n in enumerate(choices) if n.get('selected') == 'true')
    target=0 if light else 1
    if target != selected: key(20 if target > selected else 19,.2)
    key(23); assert desc() == '外观主题'
    key(4)


def open_menu():
    # Android 9's input --longpress sends a repeat flag rather than holding the key.
    # Use a stationary long touch on the actual card bounds for capture only.
    node=focus()
    assert node.get('long-clickable') == 'true', node.get('content-desc')
    x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
    x=(x1+x2)//2; y=y1+(y2-y1)//4
    adb('shell','input','swipe',str(x),str(y),str(x),str(y),'850')
    time.sleep(.8)
    # Leave touch mode; Up is trapped on the first menu action if already focused.
    key(19,.3)
    assert desc() == '移动位置', desc()


assert adb('get-state') == 'device'
assert adb('shell','getprop','ro.product.model') == 'MiTV-ANSP0', 'Unexpected TV'
assert 'versionName=0.9.0' in adb('shell','dumpsys','package',PKG)
original=prefs()
assert len(json.loads(original['appOrder'])) >= 20
(OUT/'preferences-before.xml').write_text(adb('shell','run-as',PKG,'cat','shared_prefs/launcher.xml'))
original_light=original.get('appearance','light') != 'dark'
original_asleep='mWakefulness=Asleep' in adb('shell','dumpsys','power')
completed=False
try:
    theme(True); home_top(); shot('home-light')
    open_menu(); shot('menu-light'); key(4)
    appearance(); shot('settings-light'); key(4)
    theme(False); home_top(); shot('home-dark')
    completed=True
finally:
    theme(original_light); home_top()
    current=prefs()
    for k in ('appOrder','showAppNames','bootEnabled'):
        assert current.get(k)==original.get(k), 'User preference changed: '+k
    assert (current.get('appearance','light') != 'dark') == original_light
    if original_asleep:
        key(223,1)
        if 'mWakefulness=Awake' in adb('shell','dumpsys','power'): key(26,1)
        assert 'mWakefulness=Asleep' in adb('shell','dumpsys','power')
    (OUT/'capture-summary.json').write_text(json.dumps({'completed':completed,'model':'MiTV-ANSP0','android':9,'api':28,'version':'0.9.0','apps':len(json.loads(original['appOrder'])),'resolution':[1920,1080],'orderAndOptionsPreserved':True,'originalThemeRestored':True,'originalPowerStateRestored':True,'videoRecorded':False},indent=2))
    print('RESTORED: original order/options, theme and power state',flush=True)
print('PASS: four real TV screenshots captured, no recording',flush=True)
