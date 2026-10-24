#!/usr/bin/env python3
"""Real Android9 TV only: install without fixtures, test native power events, restore options/sleep."""
import argparse,io,json,re,subprocess,time,xml.etree.ElementTree as ET
from pathlib import Path
from PIL import Image
p=argparse.ArgumentParser();p.add_argument('--serial',required=True);p.add_argument('--install',action='store_true');p.add_argument('--long-standby',type=int,default=0);args=p.parse_args()
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'output/tv093-test';OUT.mkdir(parents=True,exist_ok=True)
PUB=ROOT/'docs/qa-093';PUB.mkdir(exist_ok=True)
BASE=[str(Path.home()/'Library/Android/sdk/platform-tools/adb'),'-s',args.serial];PKG='com.jianping.tv'
def adb(*a,timeout=20):return subprocess.check_output([*BASE,*a],text=True,timeout=timeout).strip()
def key(c,pause=.3):adb('shell','input','keyevent',str(c));time.sleep(pause)
def prefs():return {n.get('name'):n.get('value',n.text) for n in ET.fromstring(adb('shell','run-as',PKG,'cat','shared_prefs/launcher.xml'))}
def power():return re.search(r'mWakefulness=(\w+)',adb('shell','dumpsys','power')).group(1)
def wake():
 if power()!='Awake':
  key(224,.4)
  if power()!='Awake':key(26,.7)
 assert power()=='Awake'
def sleep():
 key(223,.7)
 if power()=='Awake':key(26,.7)
 assert power()=='Asleep'
def front():
 s=adb('shell','dumpsys','activity','activities');m=re.search(r'mResumedActivity:.*? ([\w.]+/[\w.$]+)',s)
 return m.group(1) if m else ''
def start(component):
 result=adb('shell','am','start','-n',component)
 assert 'Error' not in result,result
 time.sleep(.7)
def tree():
 adb('shell','uiautomator','dump','/sdcard/still093.xml',timeout=25)
 return ET.fromstring(adb('shell','cat','/sdcard/still093.xml'))
def desc():return next(n for n in tree().iter('node') if n.get('focused')=='true').get('content-desc','')
def shot(name):
 data=subprocess.check_output([*BASE,'exec-out','screencap','-p'],timeout=20)
 assert Image.open(io.BytesIO(data)).size==(1920,1080)
 (OUT/(name+'.png')).write_bytes(data)
def service():
 data=adb('shell','dumpsys','activity','services',PKG)
 return 'app=ProcessRecord' in data and 'isForeground=true' in data
def go_switch():
 for _ in range(3):key(4,.1)
 start(PKG+'/.ui.MainActivity')
 for _ in range(10):key(19,.1)
 assert desc()=='搜索';key(22);key(23)
 for _ in range(10):
  current=desc()
  if current=='默认桌面':break
  key(20 if current.startswith('返回') else 19,.1)
 assert desc()=='默认桌面';key(20)
 assert desc().startswith('自动进入 Still，'),desc()
def set_option(on):
 go_switch()
 if (prefs().get('bootEnabled')=='true')!=on:key(23,.6)
 assert (prefs().get('bootEnabled')=='true')==on
 assert service()==on
 key(4)
def await_home():
 began=time.monotonic()
 for _ in range(20):
  if front()==PKG+'/.ui.MainActivity':break
  time.sleep(.4)
 else:raise AssertionError('Wake did not enter Still: '+front())
 time.sleep(4)
 assert front()==PKG+'/.ui.MainActivity',front()
 assert '已进入 Still' in prefs().get('wakeAttempt',''),prefs().get('wakeAttempt')
 return round(time.monotonic()-began,2)
def cycle(name,component):
 start(component)
 assert front().split('/')[0]==component.split('/')[0],('Setup failed',component,front())
 previous=prefs().get('wakeVisibleId')
 sleep();time.sleep(2);assert service()
 wake();elapsed=await_home()
 current=prefs();assert current.get('wakeVisibleId')!=previous
 log=adb('logcat','-d','-s','StillWake:I')
 assert len(re.findall(r'request id='+current['wakeVisibleId']+r'\b',log))==1
 state=tree();assert sum(n.get('long-clickable')=='true' for n in state.iter('node'))>=5
 shot(name)
 print('PASS: '+name,flush=True)
 return {'case':name,'passed':True,'settledCheckSeconds':elapsed,'requestsForWake':1}
def kill_and_wait():
 pid=adb('shell','pidof',PKG);assert pid.isdigit()
 adb('shell','run-as',PKG,'kill','-9',pid)
 for _ in range(45):
  current=subprocess.run([*BASE,'shell','pidof',PKG],text=True,capture_output=True).stdout.strip()
  if current and current!=pid and service():return {'automaticServiceRestart':True}
  time.sleep(1)
 raise AssertionError('Sticky monitor did not restart without manually launching Still')
assert adb('shell','getprop','ro.product.model')=='MiTV-ANSP0'
assert adb('shell','getprop','ro.build.version.sdk')=='28'
original=prefs();original_home=adb('shell','cmd','package','resolve-activity','--brief','-a','android.intent.action.MAIN','-c','android.intent.category.HOME')
(OUT/'preferences-before.xml').write_text(adb('shell','run-as',PKG,'cat','shared_prefs/launcher.xml'))
if args.install:
 old=adb('shell','pm','path',PKG).split('package:')[1]
 adb('pull',old,str(OUT/'previous.apk'),timeout=60)
 adb('install','--no-incremental','-r',str(ROOT/'app/build/outputs/apk/debug/app-debug.apk'),timeout=60)
 assert power()=='Asleep','Upgrade unexpectedly woke the TV'
 assert prefs()['appOrder']==original['appOrder']
assert 'versionName=0.9.3' in adb('shell','dumpsys','package',PKG)
checks=[];passed=False
try:
 wake();time.sleep(2)
 set_option(True)
 checks.append(cycle('home',PKG+'/.ui.MainActivity'))
 checks.append(cycle('external-calendar','com.xiaomi.mitv.calendar/.CalendarActivity'))
 checks.append(cycle('native-home','com.mitv.tvhome/.MainActivity'))
 # Closing search and menus on wake must land on the actual Home wall.
 start(PKG+'/.ui.MainActivity')
 for _ in range(10):key(19,.1)
 key(23,.6);adb('shell','input','text','jkj');key(4,.6)
 previous=prefs().get('wakeVisibleId');sleep();time.sleep(2);wake();await_home()
 assert prefs().get('wakeVisibleId')!=previous
 assert not any(n.get('text')=='搜索应用' for n in tree().iter('node'))
 checks.append({'case':'search-to-home','passed':True})
 set_option(False)
 previous=prefs().get('wakeVisibleId')
 start('com.xiaomi.mitv.calendar/.CalendarActivity');sleep();time.sleep(2);wake();time.sleep(5)
 assert not service() and prefs().get('wakeVisibleId')==previous
 checks.append({'case':'disabled-no-return-request','passed':True,'foregroundAfterWake':front().split('/')[0]})
 set_option(True)
 start('com.xiaomi.mitv.calendar/.CalendarActivity')
 restarted=kill_and_wait()
 assert front().startswith('com.xiaomi.mitv.calendar/'),'Restart while awake must not hijack the current app'
 checks.append({'case':'process-reclaimed-awake',**restarted,'noFocusSteal':True})
 checks.append(cycle('wake-after-process-recovery','com.xiaomi.mitv.calendar/.CalendarActivity'))
 start('com.xiaomi.mitv.calendar/.CalendarActivity');sleep()
 restarted=kill_and_wait();assert power()=='Asleep'
 wake();await_home();checks.append({'case':'process-reclaimed-asleep','passed':True,**restarted})
 if args.long_standby:
  start('com.xiaomi.mitv.calendar/.CalendarActivity');sleep()
  print('STANDBY: '+str(args.long_standby)+' seconds; screen stays off',flush=True)
  time.sleep(args.long_standby)
  assert service() and power()=='Asleep'
  wake();await_home();checks.append({'case':'longer-standby','seconds':args.long_standby,'passed':True})
 passed=True
except BaseException:
 shot('failure')
 (OUT/'failure-logcat.txt').write_text(adb('logcat','-d','-s','StillWake:I','AndroidRuntime:E'))
 raise
finally:
 try:
  wake();set_option(original.get('bootEnabled')=='true')
  current=prefs()
  for k in ('appOrder','bootEnabled','showAppNames','appearance'):assert current.get(k)==original.get(k),k
  assert adb('shell','cmd','package','resolve-activity','--brief','-a','android.intent.action.MAIN','-c','android.intent.category.HOME')==original_home
  start(PKG+'/.ui.MainActivity')
  (OUT/'final-logcat.txt').write_text(adb('logcat','-d','-s','StillWake:I','AndroidRuntime:E'))
 finally:sleep()
 result={'passed':passed,'version':'0.9.3','model':'MiTV-ANSP0','api':28,'apps':len(json.loads(original['appOrder'])),'checks':checks,'optionsAndOrderPreserved':True,'defaultHomePreserved':True,'finalPower':'Asleep','testFixturesInstalled':False}
 (PUB/'tv-smoke.json').write_text(json.dumps(result,indent=2)+'\n')
 print(json.dumps(result,indent=2),flush=True)
