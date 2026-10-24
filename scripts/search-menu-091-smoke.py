#!/usr/bin/env python3
"""Dedicated fixture emulator: search context actions, Home handoff and recovery."""
import json
import os
import re
import runpy
import subprocess
import time
from pathlib import Path

q = runpy.run_path(str(Path(__file__).with_name('theme-080-smoke.py')))
adb,key=q['adb'],q['key']
PKG=q['PKG']; OUT=q['ROOT']/os.environ.get('STILL_QA_OUT','docs/qa-091')
OUT.mkdir(parents=True,exist_ok=True)


def focus():
 n=q['focused'](); assert n.get('package')==PKG,n.attrib
 return n.get('content-desc'),n.get('bounds')


def search():
 key(4); key(19); assert focus()[0]=='搜索'
 key(23,.8); adb('shell','input','text','cs'); key(4,.7)
 assert focus()[0].startswith('测试应用')


def menu():
 key(23,.5,long=True)
 state=q['tree']()
 assert {'移到最前','在首页定位','应用信息'} <= {n.get('content-desc') for n in state.iter('node')}
 assert focus()[0]=='移到最前'


def deep_result():
 for _ in range(3): key(20,.15)
 selected=focus(); assert selected[0]=='测试应用 16',selected
 return selected


def main():
 original=q['prefs'](); original_xml=q['prefs_xml'](); original_order=json.loads(original['appOrder'])
 assert len(original_order)==28
 assert ('versionName='+os.environ.get('STILL_EXPECT_VERSION','0.9.1')) in adb('shell','dumpsys','package',PKG)
 target=next(i for i in original_order if 'com.jianping.fixture.app16/' in i)
 adb('shell','run-as',PKG,'cp','shared_prefs/launcher.xml','files/qa091-backup.xml')
 checks=[]
 try:
  for light in (True,False):
   name='light' if light else 'dark'
   q['seed'](q['ET'].fromstring(original_xml)); q['start'](); q['appearance'](); q['select_theme'](light); key(4)
   search(); selected=deep_result(); menu()
   q['image']().save(OUT/(name+'-search-menu.png'))
   (OUT/(name+'-search-menu.xml')).write_text(q['ET'].tostring(q['tree'](),encoding='unicode'))
   key(21); assert focus()[0]=='移到最前', 'Menu must isolate focus from results'
   key(4,.5); assert focus()==selected,'Cancel must return to the same result and exact scroll'
   assert json.loads(q['prefs']()['appOrder'])==original_order
   menu(); key(20); assert focus()[0]=='在首页定位'; key(23,.5)
   located=focus(); assert located[0]==selected[0]
   y=int(re.findall(r'\d+',located[1])[1]); assert 190<=y<=800,'Home target must be visible'
   assert json.loads(q['prefs']()['appOrder'])==original_order,'Locate must not reorder'
   q['image']().save(OUT/(name+'-home-located.png'))
   search(); deep_result(); menu(); key(23,.6)
   assert focus()[0]==selected[0]
   changed=json.loads(q['prefs']()['appOrder'])
   assert changed==[target]+[i for i in original_order if i!=target],'Promote must preserve all other positions'
   assert any(n.get('text')=='cs' for n in q['tree']().iter('node')),'Promote must keep the query'
   q['image']().save(OUT/(name+'-promoted-result.png'))
   # Open native app information, then force a real background process death.
   menu(); key(20); key(20); assert focus()[0]=='应用信息'; key(23,.7)
   state=q['tree'](); assert any(n.get('package')=='com.android.tv.settings' for n in state.iter('node'))
   # TV app-information is translucent; cover it before requesting background kill.
   adb('shell','am','start','-n','com.jianping.fixture.app16/com.jianping.fixture.MainActivity')
   assert any(n.get('package')=='com.jianping.fixture.app16' for n in q['tree']().iter('node'))
   adb('shell','am','kill',PKG)
   for _ in range(30):
    killed=subprocess.run([*q['BASE'],'shell','pidof',PKG],text=True,capture_output=True)
    if not killed.stdout.strip():break
    time.sleep(.1)
   assert not killed.stdout.strip(),'Launcher process must really exit'
   key(4,1.2)
   if any(n.get('package')=='com.android.tv.settings' for n in q['tree']().iter('node')):key(4,1.2)
   assert focus()[0]==selected[0]
   assert any(n.get('text')=='cs' for n in q['tree']().iter('node'))
   menu(); key(23,.5); assert json.loads(q['prefs']()['appOrder'])==changed,'Repeated promotion must be a no-op'
   key(23,.6); assert any(n.get('package')=='com.jianping.fixture.app16' for n in q['tree']().iter('node'))
   key(4,.7); assert focus()[0]==selected[0]
   key(4,.5); assert focus()[0]==selected[0], 'Persisted search result must hand off the promoted Home target after process death'
   assert int(re.findall(r'\d+',focus()[1])[1])<250,'Promoted Home app must be at the first row'
   q['image']().save(OUT/(name+'-home-promoted.png'))
   for option in ('showAppNames','bootEnabled'):
    assert q['prefs']().get(option)==original.get(option)
   checks.append({'theme':name,'cancelExactFocusAndScroll':True,'isolatedMenuFocus':True,'locateWithoutReorder':True,'promotionPreservesOtherApps':True,'queryPreserved':True,'appInfoAndActualProcessDeath':True,'repeatedPromotionStable':True,'launchAndReturn':True,'homeHandoffRecovered':True})
   print('PASS: '+name+' search menu cancel, locate, promote, app info, process death and Home handoff',flush=True)
 except BaseException:
  q['image']().save(OUT/'failure.png'); (OUT/'failure.xml').write_text(q['ET'].tostring(q['tree'](),encoding='unicode'))
  raise
 finally:
  adb('shell','am','force-stop',PKG)
  adb('shell','run-as',PKG,'cp','files/qa091-backup.xml','shared_prefs/launcher.xml')
  adb('shell','run-as',PKG,'rm','files/qa091-backup.xml')
  q['start'](); assert q['prefs']()==original
 (OUT/'search-menu-smoke.json').write_text(json.dumps({'checks':checks,'originalPreferencesRestored':True},indent=2)+'\n')
 print('PASS: original emulator preferences restored',flush=True)


if __name__=='__main__':main()
