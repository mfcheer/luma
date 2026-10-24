#!/usr/bin/env python3
"""Dedicated fixture emulator: aliases, usage ranking/freeze, death, live uninstall/reinstall.
Restores the full launcher and usage preferences plus the original fixture APK. Never touches a TV.
"""
import json
import runpy
import struct
import subprocess
import tempfile
import time
from pathlib import Path
q=runpy.run_path(str(Path(__file__).with_name('theme-080-smoke.py')))
adb,key,tree=(q[n] for n in ('adb','key','tree'))
PKG,ROOT=q['PKG'],q['ROOT'];OUT=ROOT/'docs/qa-094';WORK=ROOT/'output/search-094'
OUT.mkdir(exist_ok=True);WORK.mkdir(parents=True,exist_ok=True)
USAGE='shared_prefs/app_usage_v1.xml';FIXTURE='com.jianping.fixture.app24'

def focus():
 n=q['focused']();assert n.get('package')==PKG,n.attrib
 return n.get('content-desc'),n.get('bounds')

def texts():return {n.get('text','') for n in tree().iter('node')}

def wait_count(count):
 for _ in range(20):
  if f'找到 {count} 个应用' in texts():return
  time.sleep(.15)
 raise AssertionError((count,texts()))

def enter(query):
 key(19);assert focus()[0]=='搜索',focus()
 key(23,.7);assert focus()[0]=='搜索应用名称或拼音首字母'
 adb('shell','input','text',query);time.sleep(.3)

def shot(label):
 (OUT/(label+'.xml')).write_text(q['ET'].tostring(tree(),encoding='unicode'))
 q['image']().save(OUT/(label+'.png'))

def usage():
 try:
  xml=q['ET'].fromstring(adb('shell','run-as',PKG,'cat',USAGE))
 except subprocess.CalledProcessError:return {}
 vals={n.get('name'):n.get('value',n.text) for n in xml}
 return {k[6:]:struct.unpack('>d',struct.pack('>q',int(v)))[0] for k,v in vals.items() if k.startswith('score.')}

def seed_usage():
 xml=q['ET'].Element('map');now=int(time.time()*1000)
 q['ET'].SubElement(xml,'long',name='score.'+FIXTURE,value=str(struct.unpack('>q',struct.pack('>d',5.0))[0]))
 q['ET'].SubElement(xml,'long',name='time.'+FIXTURE,value=str(now))
 q['ET'].SubElement(xml,'string',name='epoch.'+FIXTURE).text='qa094-initial-generation'
 with tempfile.NamedTemporaryFile(suffix='.xml') as f:
  f.write(q['ET'].tostring(xml));f.flush();adb('push',f.name,'/data/local/tmp/still094-usage.xml')
 adb('shell','run-as',PKG,'cp','/data/local/tmp/still094-usage.xml',USAGE)

def main():
 assert adb('shell','pm','list','packages').count('package:com.jianping.fixture.app')==24
 assert 'versionName=0.9.4' in adb('shell','dumpsys','package',PKG)
 adb('shell','input','keyevent','19')  # Leave touch mode after native instrumentation.
 original=q['prefs']();original_xml=q['prefs_xml']();order=json.loads(original['appOrder'])
 assert len(order)==28
 original_usage=subprocess.run([*q['BASE'],'shell','run-as',PKG,'test','-f',USAGE]).returncode==0
 if original_usage:adb('shell','run-as',PKG,'cp',USAGE,'files/qa094-usage.xml')
 adb('shell','run-as',PKG,'cp','shared_prefs/launcher.xml','files/qa094-launcher.xml')
 fixture_apk=WORK/'original-fixture24.apk'
 adb('pull',adb('shell','pm','path',FIXTURE).split(':',1)[1],str(fixture_apk))
 uninstalled=False;checks=[]
 try:
  for light in (True,False):
   theme='light' if light else 'dark'
   xml=q['ET'].fromstring(original_xml)
   for n in list(xml):
    if n.get('name')=='appearance':xml.remove(n)
   q['ET'].SubElement(xml,'string',name='appearance').text=theme
   q['seed'](xml);seed_usage();q['start']()
   # New aliases use actual installed YouTube, not a fixture with a copied brand/package.
   enter('yt');wait_count(1);assert focus()[0]=='搜索应用名称或拼音首字母'
   key(4,.6);assert focus()[0]=='YouTube'
   shot(theme+'-alias-yt')
   key(23,.4,long=True);assert focus()[0]=='移到最前'
   assert {'移到最前','在首页定位','应用信息'} <= {n.get('content-desc') for n in tree().iter('node')}
   key(4,.4);assert focus()[0]=='YouTube';key(4,.4)
   assert usage()=={FIXTURE:5.0},'Selecting/opening menus never records usage'
   print('PASS: '+theme+' actual YouTube alias, menu focus/cancel and no phantom usage',flush=True)
   enter('cs');wait_count(24);key(4,.6);assert focus()[0]=='测试应用 24'
   key(22);assert focus()[0]=='测试应用 01';key(21)
   for _ in range(3):key(20,.15)
   selected=focus();assert selected[0].startswith('测试应用')
   target='com.jianping.fixture.app'+selected[0].split()[-1]
   adb('logcat','-c');key(23,.5)
   assert any(n.get('package')==target for n in tree().iter('node'))
   key(4,.65);assert focus()==selected,'Normal return keeps rank, focus and exact bounds'
   logs=adb('logcat','-d','-s','StillCatalog:I','StillSearch:I')
   assert 'refresh apps=' not in logs and 'indexReady' not in logs,'Return must not rebuild directory or index'
   assert .99<usage()[target]<=1,'A search launch must count exactly once'
   key(23,.5)
   assert any(n.get('package')==target for n in tree().iter('node')), 'Second launch must be fully foreground before background kill'
   adb('shell','am','kill',PKG)
   for _ in range(25):
    state=subprocess.run([*q['BASE'],'shell','pidof',PKG],capture_output=True,text=True)
    if not state.stdout.strip():break
    time.sleep(.1)
   assert not state.stdout.strip(),'Real background process death required'
   key(4,1.2);assert focus()==selected and 'cs' in texts(),'Saved usage snapshot survives actual process death'
   assert 1.99<usage()[target]<=2,'Second launch counts once; process recreation adds none'
   print('PASS: '+theme+' frozen rank/focus through return and actual process death',flush=True)
   key(4,.5);enter('cs');wait_count(24);key(4,.65)
   assert focus()[0]=='测试应用 24';key(22);assert focus()[0]==selected[0],'A new search session learns recent usage'
   shot(theme+'-ranked-results')
   key(21);assert focus()[0]=='测试应用 24'
   adb('uninstall',FIXTURE);uninstalled=True
   wait_count(23)
   assert focus()[0].startswith('测试应用') and focus()[0]!='测试应用 24','Removing focused result leaves a valid focus'
   for _ in range(20):
    if FIXTURE not in usage():break
    time.sleep(.15)
   assert FIXTURE not in usage(),'System uninstall broadcast must clear stored aggregate'
   adb('install','-r','--no-incremental',str(fixture_apk));uninstalled=False
   wait_count(24)
   key(4,.5);enter('cs');wait_count(24);key(4,.6)
   assert focus()[0]==selected[0],'Reinstalled app must not inherit its old usage score'
   assert FIXTURE not in usage()
   key(4,.4)
   home_target=next(i for i,x in enumerate(order) if x.startswith('com.jianping.fixture.app02/'))
   q['go'](home_target);assert focus()[0]=='测试应用 02'
   before=usage().get('com.jianping.fixture.app02',0)
   key(23,.5);assert any(n.get('package')=='com.jianping.fixture.app02' for n in tree().iter('node'))
   key(4,.6)
   assert before+.99<usage()['com.jianping.fixture.app02']<=before+1.001,'Home uses same one-count launch path'
   assert q['prefs']()['appOrder']==original['appOrder'],'Usage must never reorder Home'
   checks.append({'theme':theme,'installedYouTubeAlias':True,'menuNoUsage':True,'frequencyTieBreak':True,'returnNoDirectoryWork':True,'returnFocusAndRankFrozen':True,'actualProcessDeathFrozen':True,'nextSearchLearns':True,'searchAndHomeSingleCount':True,'liveUninstallAndReinstall':True,'homeOrderUnchanged':True})
   print('PASS: '+theme+' alias, frequency, frozen return/process death, live package changes and home order',flush=True)
 except BaseException:
  shot('failure');raise
 finally:
  if uninstalled:adb('install','-r','--no-incremental',str(fixture_apk))
  adb('shell','am','force-stop',PKG)
  adb('shell','run-as',PKG,'cp','files/qa094-launcher.xml','shared_prefs/launcher.xml')
  if original_usage:adb('shell','run-as',PKG,'cp','files/qa094-usage.xml',USAGE)
  else:adb('shell','run-as',PKG,'rm','-f',USAGE)
  adb('shell','run-as',PKG,'rm','-f','files/qa094-launcher.xml','files/qa094-usage.xml')
  q['start']();assert q['prefs']()==original
  assert adb('shell','pm','list','packages').count('package:com.jianping.fixture.app')==24
 (OUT/'search-smoke.json').write_text(json.dumps({'checks':checks,'originalPreferencesAndUsageRestored':True,'fixtureApkRestored':True},ensure_ascii=False,indent=2)+'\n')
 print('PASS: original preferences, usage and fixtures restored',flush=True)

if __name__=='__main__':main()
