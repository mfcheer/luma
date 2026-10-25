#!/usr/bin/env python3
"""Original-desktop navigation on the dedicated emulator; restores settings/default Home."""
import json, os, runpy, subprocess, time, zipfile
from pathlib import Path
from xml.etree import ElementTree as E
q=runpy.run_path(str(Path(__file__).with_name('theme-080-smoke.py')))
def bounded_adb(*args):
    return subprocess.check_output([*q['BASE'], *map(str,args)], text=True, timeout=25).strip()
q['tree'].__globals__['adb']=bounded_adb
adb,key,tree=bounded_adb,q['key'],q['tree']; ROOT=q['ROOT']; PKG=q['PKG']
SDK=Path.home()/'Library/Android/sdk'; BT=SDK/'build-tools/35.0.0'
JAVA=Path.home()/'Library/Java/JavaVirtualMachines/openjdk-21/Contents/Home/bin'
OUT=ROOT/'docs/qa-095'; WORK=ROOT/'output/other-home-095'; OUT.mkdir(exist_ok=True);WORK.mkdir(parents=True,exist_ok=True)
original=q['prefs_xml'](); (WORK/'preferences-before.xml').write_text(original)
def resolve():return adb('shell','cmd','package','resolve-activity','--brief','-a','android.intent.action.MAIN','-c','android.intent.category.HOME').splitlines()[-1]
def default(c):adb('shell','cmd','package','set-home-activity',c)
originalHome=resolve(); installed=[]; checks=[]
def seed(light,remember=''):
    r=E.fromstring(original)
    for name,value in [('appearance','light' if light else 'dark'),('otherHome',remember)]:
        e=next((x for x in r if x.get('name')==name),None)
        if e is None:e=E.SubElement(r,'string',{'name':name})
        e.text=value
    q['seed'](r)
def settings():
    q['start']()
    for _ in range(9):key(19,.07)
    assert q['desc']()=='搜索',q['desc']()
    key(22);key(23)
    # Enter with remote navigation, including after instrumentation left touch mode.
    if q['desc']().startswith('返回'):key(20)
    assert q['desc']()=='打开电视原生桌面',q['desc']()
def selected_item():
    nodes=list(tree().iter('node'))
    return next((n.get('text') for n in nodes if n.get('selected')=='true' and n.get('resource-id')=='android:id/text1'), '')
def top():
    s=adb('shell','dumpsys','activity','activities')
    return next((line for line in s.splitlines() if 'topResumedActivity=' in line or 'mResumedActivity:' in line or 'ResumedActivity:' in line),'')
def wait_top(package):
    for _ in range(20):
        if package in top():return
        time.sleep(.2)
    raise AssertionError(top())
def image(name):q['image']().save(OUT/name)
def build_fixture(letter):
    pkg='com.jianping.homefixture.'+letter.lower()
    assert not adb('shell','pm','list','packages',pkg),'Must not replace an existing app'
    for folder in ('classes','dex','res'):(WORK/folder).mkdir(exist_ok=True)
    src=WORK/'Home.java'; src.write_text('package com.jianping.homefixture; public class Home extends android.app.Activity { public void onCreate(android.os.Bundle b) { super.onCreate(b); android.widget.TextView t=new android.widget.TextView(this); t.setText("QA Desktop '+letter+'"); t.setTextSize(36);setContentView(t); } }')
    jar=SDK/'platforms/android-35/android.jar'
    def run(args):subprocess.run([str(a) for a in args],check=True,stdout=subprocess.DEVNULL)
    run([JAVA/'javac','-source','8','-target','8','-cp',jar,'-d',WORK/'classes',src])
    run([BT/'d8','--lib',jar,'--output',WORK/'dex',WORK/'classes/com/jianping/homefixture/Home.class'])
    activity = '<activity android:name="com.jianping.homefixture.Home" android:exported="true"'+(' android:permission="android.permission.MANAGE_USERS"' if letter=='B' else '')+'><intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.HOME"/><category android:name="android.intent.category.DEFAULT"/></intent-filter></activity>'
    if letter=='B':activity += '<activity-alias android:name="com.jianping.homefixture.Entry" android:targetActivity="com.jianping.homefixture.Home" android:exported="true"><intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.DEFAULT"/></intent-filter></activity-alias>'
    manifest=WORK/'AndroidManifest.xml'; manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="'+pkg+'"><uses-sdk android:minSdkVersion="23" android:targetSdkVersion="35"/><application android:label="QA Desktop '+letter+'">'+activity+'</application></manifest>')
    apk=WORK/('home-'+letter+'.apk');run([BT/'aapt','package','-f','-M',manifest,'-I',jar,'-S',WORK/'res','-F',apk])
    with zipfile.ZipFile(apk,'a') as z:z.write(WORK/'dex/classes.dex','classes.dex')
    run([BT/'apksigner','sign','--ks',Path.home()/'.android/debug.keystore','--ks-pass','pass:android','--key-pass','pass:android',apk])
    adb('install','-r','--no-incremental',str(apk));installed.append(pkg)
    return pkg
try:
    assert 'versionName=0.9.5' in adb('shell','dumpsys','package',PKG)
    for light in (True,False):
        seed(light);settings()
        expectedHint='临时打开，按 Home 返回 Still' if resolve().startswith(PKG+'/') else '临时打开，不更改默认桌面'
        assert any(n.get('text')==expectedHint for n in tree().iter('node'))
        image(('light' if light else 'dark')+'-settings.png')
        for _ in range(7):key(20,.1)
        node=q['focused']();assert node.get('content-desc')=='设备与启动信息'
        bounds=list(map(int,__import__('re').findall(r'\d+',node.get('bounds'))))
        assert bounds[3]-bounds[1]>=80 and bounds[3]<=1004,bounds
        for _ in range(7):key(19,.1)
        key(20); assert q['desc']()=='默认桌面'
        assert not any(n.get('text','').startswith('临时打开') for n in tree().iter('node'))
        key(19);key(23);wait_top('com.google.android.tvlauncher')
        time.sleep(2);assert 'com.google.android.tvlauncher' in top()
        assert q['prefs']()['otherHome'].startswith('com.google.android.tvlauncher/')
        key(3);wait_top(originalHome.split('/')[0])
        adb('shell','am','start','-n',PKG+'/.ui.MainActivity');wait_top(PKG)
        assert resolve()==originalHome
        checks.append({'theme':'light' if light else 'dark','singleDirectOpen':True,'homeFollowsSystemDefault':True,'hintFocusOnly':True,'lastRowScrollsFullyVisible':True,'noImmediateReturn':True})
    a,b=build_fixture('A'),build_fixture('B')
    seed(True);settings();key(23)
    assert any(n.get('text')=='选择电视桌面' for n in tree().iter('node'))
    key(4);assert q['desc']()=='打开电视原生桌面';assert not q['prefs']().get('otherHome')
    key(23)
    assert selected_item()=='Android TV Home'
    for _ in range(6):
        if selected_item()=='QA Desktop A':break
        key(20,.1)
    assert selected_item()=='QA Desktop A',E.tostring(tree(),encoding='unicode')
    image('desktop-picker.png');key(23);wait_top(a)
    assert q['prefs']()['otherHome'].startswith(a+'/')
    adb('shell','am','start','-n',PKG+'/.ui.MainActivity');wait_top(PKG);settings();key(23);wait_top(a)
    adb('uninstall',a);installed.remove(a)
    adb('shell','am','start','-n',PKG+'/.ui.MainActivity');wait_top(PKG);settings();key(23)
    nodes=list(tree().iter('node'))
    assert any(n.get('text')=='选择电视桌面' for n in nodes)
    assert any(n.get('text')=='QA Desktop B' for n in nodes)
    assert not any(n.get('text')=='QA Desktop A' for n in nodes)
    assert not q['prefs']().get('otherHome')
    assert selected_item()=='Android TV Home'
    key(20);assert selected_item()=='QA Desktop B'
    key(23);wait_top(b)
    assert q['prefs']()['otherHome']==b+'/com.jianping.homefixture.Entry'
    checks.append({'protectedHomePublicAlias':True})
    checks.append({'multiplePicker':True,'pickerStartsOnFirstDesktop':True,'cancelRetainsFocus':True,'rememberedDirectOpen':True,'removedTargetReopensPicker':True})
finally:
    for pkg in installed:adb('uninstall',pkg)
    q['seed'](E.fromstring(original))
    if resolve()!=originalHome:default(originalHome)
    q['start']()
    assert resolve()==originalHome
    current=q['prefs']();before={e.get('name'):e.get('value',e.text) for e in E.fromstring(original)}
    # Home diagnostics may update when Home is intentionally exercised.
    changed={k for k in current.keys()|before.keys() if current.get(k)!=before.get(k)}
    assert changed<= {'homeVisible'},changed
(OUT/'home-smoke.json').write_text(json.dumps({'version':'0.9.5','checks':checks,'preferencesRestored':True,'defaultHomeRestored':True,'fixturesRemoved':True},ensure_ascii=False,indent=2)+'\n')
print('PASS:',checks)
