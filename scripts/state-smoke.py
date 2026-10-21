#!/usr/bin/env python3
"""Persistence and actual app-launch checks on the dedicated test emulator."""
import subprocess, time, json, xml.etree.ElementTree as ET
from pathlib import Path
adb_path=str(Path.home()/'Library/Android/sdk/platform-tools/adb')
def adb(*args): return subprocess.check_output([adb_path,'-s','emulator-5554',*args],text=True).strip()
def key(n,long=False):
    adb('shell','input','keyevent',*(['--longpress'] if long else []),str(n));time.sleep(.3)
def prefs():
    xml=ET.fromstring(adb('shell','run-as','com.jianping.tv','cat','shared_prefs/launcher.xml'))
    return json.loads(next(n.text for n in xml if n.get('name')=='favorites'))
def tree():
    adb('shell','uiautomator','dump','/sdcard/state.xml');return ET.fromstring(adb('shell','cat','/sdcard/state.xml'))
def start():
    adb('shell','am','start','-n','com.jianping.tv/.ui.MainActivity');time.sleep(1)
adb('shell','pm','clear','com.jianping.tv')
start()
for _ in range(15):key(21)
before=prefs();assert before
key(23,True)
for _ in range(3):key(20)
key(23)
after=prefs();assert len(after)==len(before)-1,(before,after)
adb('shell','am','force-stop','com.jianping.tv');start();assert prefs()==after
for _ in range(10):key(20)
key(23)
for _ in range(15):key(21)
key(23,True)
key(23)
restored=prefs();assert set(restored)==set(before),(before,restored)
key(4)
for _ in range(15):key(21)
# Locate Settings using only directional remote events.
for _ in range(12):
    t=tree();f=next((n for n in t.iter('node') if n.get('focused')=='true'),None)
    if f is not None and f.get('content-desc')=='Settings': break
    key(22)
else: raise AssertionError('Settings app tile not reachable')
key(23);time.sleep(1)
state=adb('shell','dumpsys','activity','activities')
assert any('com.android.tv.settings/' in line and 'topResumedActivity' in line for line in state.splitlines())
key(4);time.sleep(.5)
assert any(n.get('text')=='简屏' for n in tree().iter('node'))
print('PASS: remove favorite, force-stop persistence, add favorite, launch external Settings and return')
