#!/usr/bin/env python3
"""Real Xiaomi TV: original desktop entry and Home return. No fixtures or preference seeding."""
import argparse, json, re, subprocess, time
from pathlib import Path
from xml.etree import ElementTree as E
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--serial',required=True);args=p.parse_args()
assert args.serial!='emulator-5554'
BASE=[str(Path.home()/'Library/Android/sdk/platform-tools/adb'),'-s',args.serial]
OUT=ROOT/'output/tv095-update';QA=ROOT/'docs/qa-095'
PKG='com.jianping.tv';NATIVE='com.mitv.tvhome'
def adb(*a):return subprocess.check_output([*BASE,*map(str,a)],text=True,timeout=20).strip()
def key(code):adb('shell','input','keyevent',code);time.sleep(.2)
def top():
 s=adb('shell','dumpsys','activity','activities')
 return next((x for x in s.splitlines() if 'mResumedActivity' in x or 'topResumedActivity=' in x),'')
def wait_top(pkg):
 for _ in range(20):
  if pkg in top():return
  time.sleep(.2)
 raise AssertionError(top())
def tree(name):
 path='/sdcard/still095-tv.xml'
 adb('shell','uiautomator','dump',path)
 s=adb('shell','cat',path);(OUT/(name+'.xml')).write_text(s)
 return E.fromstring(s)
def focus(r):return next((n.get('content-desc') for n in r.iter('node') if n.get('focused')=='true'),'')
def settings():
 adb('shell','am','start','-n',PKG+'/.ui.MainActivity');wait_top(PKG)
 key(4)
 for _ in range(8):key(19)
 r=tree('header')
 if focus(r)=='设置':key(21)
 assert focus(tree('search-header'))=='搜索'
 key(22);assert focus(tree('settings-header'))=='设置'
 key(23)
 r=tree('settings')
 for _ in range(4):
  f=focus(r)
  if f=='打开电视原生桌面':break
  key(20 if f=='返回' else 19);r=tree('settings-focus')
 assert focus(r)=='打开电视原生桌面',focus(r)
 assert any(n.get('text')=='临时打开，按 Home 返回 Still' for n in r.iter('node'))
def prefs(name):return {n.get('name'):n.get('value',n.text) for n in E.parse(name).getroot()}
assert adb('shell','getprop','ro.product.model')=='MiTV-ANSP0'
assert adb('shell','getprop','ro.build.version.sdk')=='28'
assert 'versionName=0.9.5' in adb('shell','dumpsys','package',PKG)
assert 'mWakefulness=Awake' in adb('shell','dumpsys','power')
homeBefore=adb('shell','cmd','package','resolve-activity','--brief','-a','android.intent.action.MAIN','-c','android.intent.category.HOME').splitlines()[-1]
assert homeBefore==PKG+'/.ui.MainActivity'
pretestLauncher=adb('shell','run-as',PKG,'cat','shared_prefs/launcher.xml')
(OUT/'test-before-launcher.xml').write_text(pretestLauncher)
(OUT/'test-before-usage.xml').write_text(adb('shell','run-as',PKG,'cat','shared_prefs/app_usage_v1.xml'))
initialSaved=prefs(OUT/'test-before-launcher.xml').get('otherHome','')
checks=[]
for repeat in (False,True):
 settings()
 if not repeat:
  shot=subprocess.check_output(BASE+['exec-out','screencap','-p'],timeout=20)
  (QA/'tv-settings.png').write_bytes(shot)
 key(23);wait_top(NATIVE)
 time.sleep(4)
 assert NATIVE in top(),'Still must not pull back immediately'
 saved=adb('shell','run-as',PKG,'cat','shared_prefs/launcher.xml')
 actual={n.get('name'):n.get('value',n.text) for n in E.fromstring(saved)}
 assert actual['otherHome']=='com.mitv.tvhome/com.mitv.tvhome.MainActivity'
 key(3);wait_top(PKG)
 r=tree('returned-home')
 assert any(n.get('content-desc')=='搜索' for n in r.iter('node'))
 checks.append({'case':'remembered-entry' if repeat or initialSaved else 'first-entry','openedPublicAlias':True,'nativeStaysForegroundSeconds':4,'homeReturnsToStillGrid':True})
current=adb('shell','run-as',PKG,'cat','shared_prefs/launcher.xml');(OUT/'after-launcher.xml').write_text(current)
before=prefs(OUT/'test-before-launcher.xml');after=prefs(OUT/'after-launcher.xml')
keys=['appOrder','favorites','appearance','showAppNames','bootEnabled']
assert all(before.get(k)==after.get(k) for k in keys)
usage=adb('shell','run-as',PKG,'cat','shared_prefs/app_usage_v1.xml');(OUT/'after-usage.xml').write_text(usage)
assert prefs(OUT/'test-before-usage.xml')==prefs(OUT/'after-usage.xml')
assert adb('shell','cmd','package','resolve-activity','--brief','-a','android.intent.action.MAIN','-c','android.intent.category.HOME').splitlines()[-1]==homeBefore
assert 'mWakefulness=Awake' in adb('shell','dumpsys','power')
(QA/'tv-smoke.json').write_text(json.dumps({'version':'0.9.5','device':'MiTV-ANSP0','api':28,'checks':checks,'optionsAndOrderPreserved':True,'usagePreserved':True,'defaultHomePreserved':True,'finalPage':'Still home','finalPower':'Awake','fixturesInstalled':False,'sleepWakeTested':False},ensure_ascii=False,indent=2)+'\n')
print('PASS: actual Xiaomi public alias, Home return, remembered direct entry, preferences/default Home/usage preserved')
