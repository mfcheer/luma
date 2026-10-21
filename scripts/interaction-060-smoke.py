#!/usr/bin/env python3
"""Dedicated emulator only: transactional reorder, drawer isolation, restoration and remote navigation."""
import subprocess,time,json,os,xml.etree.ElementTree as E
from pathlib import Path
root=Path(__file__).resolve().parents[1];out=root/os.environ.get('STILL_QA_SCREENSHOTS','docs/screenshots');out.mkdir(parents=True,exist_ok=True)
a='/Users/mfcheer/Library/Android/sdk/platform-tools/adb';pkg='com.jianping.tv'
def adb(*args):return subprocess.check_output([a,'-s','emulator-5554',*args],text=True).strip()
def key(code,long=False):
 adb('shell','input','keyevent',*(['--longpress'] if long else []),str(code));time.sleep(.28)
def tree():
 adb('shell','uiautomator','dump','/sdcard/qa060.xml');return E.fromstring(adb('shell','cat','/sdcard/qa060.xml'))
def focus():return next(n for n in tree().iter('node') if n.get('focused')=='true')
def desc():return focus().get('content-desc','')
def prefs():return E.fromstring(adb('shell','run-as',pkg,'cat','shared_prefs/launcher.xml'))
def favorites():return json.loads(next(n.text for n in prefs() if n.get('name')=='favorites'))
def start():
 adb('shell','am','force-stop',pkg);adb('shell','am','start','-n',pkg+'/.ui.MainActivity');time.sleep(1)
def shot(name):
 time.sleep(.5)
 with open(out/name,'wb') as f:subprocess.run([a,'-s','emulator-5554','exec-out','screencap','-p'],stdout=f,check=True)
def has(text):return any(text in n.get('text','') for n in tree().iter('node'))
assert len(favorites())==28,'Requires existing 28-favorite emulator fixture setup'
adb('shell','run-as',pkg,'mkdir','-p','files')
adb('shell','run-as',pkg,'cp','shared_prefs/launcher.xml','files/qa060-backup.xml')
try:
 start();assert desc()=='Play Store'
 key(22);key(22);assert desc()=='YouTube'
 shot('still-060-home.png')
 key(20);assert desc()=='测试应用 04'
 key(20);assert desc()=='测试应用 09'
 shot('still-060-scrolled.png')
 for _ in range(10):key(20)
 for _ in range(5):key(22)
 assert desc()=='全部应用'
 original_bounds=focus().get('bounds');shot('still-060-more.png')
 key(23);assert any(n.get('content-desc')=='全部应用面板' for n in tree().iter('node'))
 drawer_first=desc();assert drawer_first and drawer_first!='全部应用';shot('still-060-drawer.png')
 key(4);assert desc()=='全部应用';assert focus().get('bounds')==original_bounds,'Home scroll must restore exactly'
 key(23);assert desc()==drawer_first;key(23,True)
 assert desc()=='移出首页';shot('still-060-menu.png')
 key(23);assert len(favorites())==27
 key(23,True);assert desc()=='添加到首页'
 key(23);assert len(favorites())==28
 key(4);start();moving_label=desc();assert moving_label=='Play Store'
 before=favorites();key(23,True);assert desc()=='移动位置';key(23)
 assert has('方向键移动位置');key(22)
 assert favorites()==before,'Draft must never persist before confirm'
 shot('still-060-moving.png');key(4);assert favorites()==before,'Back must discard draft'
 key(23,True);key(23);key(20);key(23)
 saved=favorites();expected=before.copy();moving=expected.pop(0);expected.insert(5,moving)
 assert saved==expected,'Confirm must insert and shift neighbors in order'
 start();assert favorites()==saved,'New order must survive process restart'
 # Launch a real fixture and verify focus + viewport restoration after return.
 index=next(i for i,v in enumerate(saved) if 'fixture.app24/' in v)
 for _ in range(index//5):key(20)
 for _ in range(index%5):key(22)
 assert desc()=='测试应用 24';bounds=focus().get('bounds');key(23)
 assert any('topResumedActivity' in s and 'fixture.app24/' in s for s in adb('shell','dumpsys','activity','activities').splitlines())
 key(4);time.sleep(.8);assert desc()=='测试应用 24';assert focus().get('bounds')==bounds
 start();key(19);assert desc()=='设置';key(23);assert desc()=='默认桌面'
 shot('still-060-settings.png');key(20);key(23);assert desc().startswith('开机启动');key(23)
 key(20);key(20);key(20);key(23);time.sleep(1);assert desc()=='刷新应用列表'
 key(20);key(23);assert has('Android');key(4);key(4)
 print('PASS: 5-column navigation, partial-row scroll, drawer focus isolation/exact return, add/remove favorites, reorder cancel/save/restart, external launch/return, settings toggle/refresh/report',flush=True)
finally:
 adb('shell','am','force-stop',pkg)
 adb('shell','run-as',pkg,'cp','files/qa060-backup.xml','shared_prefs/launcher.xml')
 adb('shell','run-as',pkg,'rm','files/qa060-backup.xml')
 adb('shell','am','start','-n',pkg+'/.ui.MainActivity')
