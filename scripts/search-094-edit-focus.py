#!/usr/bin/env python3
"""Dedicated emulator: editing focus survives package updates after saved-state process recovery."""
import json
import runpy
import subprocess
import time
from pathlib import Path
q=runpy.run_path(str(Path(__file__).with_name('theme-080-smoke.py')))
adb,key,tree=(q[n] for n in ('adb','key','tree'))
PKG=q['PKG'];FIXTURE='com.jianping.fixture.app23';USAGE='shared_prefs/app_usage_v1.xml'
OUT=q['ROOT']/'docs/qa-094'
def desc():return q['desc']()
def count(n):
 for _ in range(15):
  if f'找到 {n} 个应用' in {x.get('text') for x in tree().iter('node')}:return
  time.sleep(.15)
 raise AssertionError('Catalog did not reconcile')
def main():
 assert adb('shell','pm','list','packages').count('package:com.jianping.fixture.app')==24
 assert 'versionName=0.9.4' in adb('shell','dumpsys','package',PKG)
 original=q['prefs']()
 had_usage=subprocess.run([*q['BASE'],'shell','run-as',PKG,'test','-f',USAGE]).returncode==0
 adb('shell','run-as',PKG,'cp','shared_prefs/launcher.xml','files/qa094-edit-launcher.xml')
 if had_usage:adb('shell','run-as',PKG,'cp',USAGE,'files/qa094-edit-usage.xml')
 disabled=False
 try:
  q['start']();key(19);assert desc()=='搜索';key(23,.7)
  adb('shell','input','text','cs');count(24);key(4,.6)
  selected=desc();assert selected.startswith('测试应用')
  key(23,.5);assert any(x.get('package','').startswith('com.jianping.fixture.') for x in tree().iter('node'))
  adb('shell','am','kill',PKG)
  for _ in range(25):
   pid=subprocess.run([*q['BASE'],'shell','pidof',PKG],capture_output=True,text=True)
   if not pid.stdout.strip():break
   time.sleep(.1)
  assert not pid.stdout.strip()
  key(4,1.2);assert desc()==selected
  for _ in range(8):
   if desc()=='搜索应用名称或拼音首字母':break
   key(19,.15)
  assert desc()=='搜索应用名称或拼音首字母'
  adb('shell','pm','disable-user','--user','0',FIXTURE);disabled=True
  count(23);assert desc()=='搜索应用名称或拼音首字母','A consumed restore flag must not steal editing focus'
  adb('shell','pm','enable',FIXTURE);disabled=False
  count(24);assert desc()=='搜索应用名称或拼音首字母'
  result={'actualBackgroundProcessDeath':True,'queryAndSelectionRestored':True,'editingFocusPreservedOnPackageDisable':True,'editingFocusPreservedOnPackageEnable':True}
  print('PASS: package changes keep input focus after actual process-death search recovery',flush=True)
 finally:
  if disabled:adb('shell','pm','enable',FIXTURE)
  adb('shell','am','force-stop',PKG)
  adb('shell','run-as',PKG,'cp','files/qa094-edit-launcher.xml','shared_prefs/launcher.xml')
  if had_usage:adb('shell','run-as',PKG,'cp','files/qa094-edit-usage.xml',USAGE)
  else:adb('shell','run-as',PKG,'rm','-f',USAGE)
  adb('shell','run-as',PKG,'rm','-f','files/qa094-edit-launcher.xml','files/qa094-edit-usage.xml')
  q['start']();assert q['prefs']()==original
 result['originalPreferencesUsageAndPackageAvailabilityRestored']=True
 (OUT/'editing-focus-smoke.json').write_text(json.dumps(result,indent=2)+'\n')
if __name__=='__main__':main()
